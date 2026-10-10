package ru.coordination.approval.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.template.ApplicabilityRule;
import ru.coordination.approval.domain.template.ApplicabilityRuleRepository;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.dto.generation.EntitySnapshot;
import ru.coordination.approval.exception.NoMatchingTemplateException;

/**
 * Подбор шаблона маршрута по правилам применимости (PHASE-15, {@code 07_api_contract.md} §11.1).
 * Работает только с шаблонами в статусе {@code PUBLISHED} и возвращает кандидатов в порядке
 * предпочтения; выбор первого из них — {@link #selectBestTemplate}.
 *
 * <p><b>Почему кандидаты дедуплицируются по цепочке версий.</b> По ADR-023 версии одного шаблона —
 * это отдельные строки {@code Template}, связанные {@code parentTemplateId}, и опубликованных
 * строк в цепочке может быть несколько. Без дедупликации в кандидатах оказывались все версии, и
 * при равном приоритете выбор между ними был недетерминирован — на практике побеждал самый старый
 * шаблон, поэтому новые версии не использовались вовсе (найдено при рантайм-проверке).
 *
 * <p><b>Известное ограничение.</b> {@code attributeConditions} поддерживает операторы
 * {@code eq}, {@code gte}, {@code lte}, {@code in}; перечисленные в критерии приёмки 7 тикета
 * PHASE-15 операторы {@code gt}, {@code lt}, {@code contains} не реализованы — неизвестный оператор
 * даёт «не совпало» ({@code false}), а не {@code UnsupportedOperationException}.
 */
@Service
@RequiredArgsConstructor
public class MatchService {

    private final TemplateRepository templateRepository;
    private final ApplicabilityRuleRepository applicabilityRuleRepository;

    /**
     * Отбирает опубликованные шаблоны, применимые к снимку сущности, в порядке предпочтения.
     *
     * <p>Порядок задан тремя ключами: приоритет правила {@code ruleIdx} по возрастанию (шаблон без
     * правил — «универсальный»: он матчится всегда и получает {@code Integer.MAX_VALUE}, то есть
     * идёт последним, решение открытого вопроса 3 тикета PHASE-15), затем версия по убыванию, затем
     * {@code createdAt} по убыванию. Сортировка только по {@code ruleIdx} приводила к тому, что при
     * равном приоритете выбирался произвольный (на практике — самый старый) шаблон.
     *
     * @param entitySnapshot снимок сущности: тип, подтип и атрибуты, по которым проверяются правила;
     *                       атрибуты берутся из него, а не из {@code EntityAdapter}, чтобы подбор
     *                       учитывал данные конкретного запроса
     * @return кандидаты от лучшего к худшему; пустой список, если не подошёл ни один шаблон
     *         (решение об ошибке принимает {@link #selectBestTemplate})
     */
    public List<Template> findMatchingTemplates(EntitySnapshot entitySnapshot) {
        List<Template> allPublished = templateRepository.findByStatus(LifecycleStatus.PUBLISHED);
        Map<UUID, Template> publishedById = new HashMap<>();
        for (Template template : allPublished) {
            publishedById.put(template.getId(), template);
        }

        List<TemplateMatch> matches = new ArrayList<>();

        // Корень цепочки версий (ADR-023) для каждого шаблона — считается один раз.
        Map<UUID, UUID> rootByTemplateId = new HashMap<>();
        for (Template template : allPublished) {
            rootByTemplateId.put(template.getId(), chainRoot(template, publishedById));
        }

        // Лучшая версия в каждой цепочке: кандидатами остаются только строки с этой версией.
        Map<UUID, Integer> bestVersionByRoot = new HashMap<>();
        for (Template template : allPublished) {
            if (template.getVersion() == null) {
                continue;
            }
            UUID root = rootByTemplateId.get(template.getId());
            bestVersionByRoot.merge(root, template.getVersion(), Math::max);
        }

        for (Template template : allPublished) {
            UUID root = rootByTemplateId.get(template.getId());
            Integer bestVersion = bestVersionByRoot.get(root);

            // Дедупликация по цепочке версий: если в этой же цепочке есть опубликованная версия
            // с большим номером, строка кандидатом быть не должна. Без проверки в кандидаты
            // попадали все версии, и выбор между ними был недетерминирован — на практике
            // выбирался самый старый шаблон (найдено при рантайм-проверке).
            if (bestVersion != null && template.getVersion() != null
                    && template.getVersion() < bestVersion) {
                continue;
            }

            List<ApplicabilityRule> rules = applicabilityRuleRepository
                    .findByTemplateIdOrderByRuleIdx(template.getId());

            if (rules.isEmpty()) {
                // Universal template - matches everything
                matches.add(new TemplateMatch(template, Integer.MAX_VALUE));
                continue;
            }

            for (ApplicabilityRule rule : rules) {
                if (ruleMatches(rule, entitySnapshot)) {
                    matches.add(new TemplateMatch(template, rule.getRuleIdx()));
                    break; // First matching rule wins
                }
            }
        }

        // Сортировка: сначала приоритет (меньший ruleIdx — выше; universal — в конце), затем
        // версия по убыванию, затем дата создания по убыванию. Раньше сортировка была только по
        // ruleIdx, поэтому при равном приоритете выбирался произвольный шаблон — на практике
        // самый старый, из-за чего новые шаблоны не использовались вовсе.
        matches.sort(Comparator
                .comparingInt(TemplateMatch::priority)
                .thenComparing(match -> match.template().getVersion(), Comparator.reverseOrder())
                .thenComparing(match -> match.template().getCreatedAt(), Comparator.reverseOrder()));

        return matches.stream()
                .map(TemplateMatch::template)
                .toList();
    }

    /**
     * Корень цепочки версий (ADR-023): самый дальний предок по {@code parentTemplateId}.
     *
     * <p>Цепочка может выходить за пределы опубликованных строк (промежуточные версии бывают
     * {@code DEPRECATED}), поэтому для отсутствующих в выборке предков используется
     * {@link TemplateRepository#findById(Object)}.
     */
    private UUID chainRoot(Template template, Map<UUID, Template> publishedById) {
        UUID root = template.getId();
        UUID parentId = template.getParentTemplateId();
        Set<UUID> visited = new HashSet<>();

        while (parentId != null && visited.add(parentId)) {
            root = parentId;
            Template parent = publishedById.get(parentId);
            parentId = parent != null
                    ? parent.getParentTemplateId()
                    : templateRepository.findById(parentId)
                            .map(Template::getParentTemplateId)
                            .orElse(null);
        }

        return root;
    }

    /**
     * Берёт лучшего кандидата — первый элемент списка, уже упорядоченного
     * {@link #findMatchingTemplates}. Вызывающий код не должен переупорядочивать список.
     *
     * @param matchingTemplates результат {@link #findMatchingTemplates}; пустой список означает,
     *                          что применимого опубликованного шаблона нет
     * @throws NoMatchingTemplateException если список пуст — автоподбор без подходящего шаблона
     *                                     не может создать процесс
     */
    public Template selectBestTemplate(List<Template> matchingTemplates) {
        if (matchingTemplates.isEmpty()) {
            throw new NoMatchingTemplateException("No templates match the entity");
        }
        return matchingTemplates.get(0);
    }

    /**
     * {@code true}, если правило применимо к снимку. Пустой ({@code null}/без элементов) список
     * типов, подтипов или условий атрибутов — это «без ограничения», а не «не совпадает»: только
     * так «универсальное» правило может матчиться на любую сущность.
     */
    private boolean ruleMatches(ApplicabilityRule rule, EntitySnapshot snapshot) {
        // Check entity type
        if (rule.getEntityTypes() != null && !rule.getEntityTypes().isEmpty()) {
            boolean typeMatches = false;
            for (String type : rule.getEntityTypes()) {
                if (type.equals(snapshot.entityType())) {
                    typeMatches = true;
                    break;
                }
            }
            if (!typeMatches) {
                return false;
            }
        }

        // Check entity subtype
        if (rule.getEntitySubtypes() != null && !rule.getEntitySubtypes().isEmpty()) {
            if (snapshot.entitySubtype() == null) {
                return false;
            }
            boolean subtypeMatches = false;
            for (String subtype : rule.getEntitySubtypes()) {
                if (subtype.equals(snapshot.entitySubtype())) {
                    subtypeMatches = true;
                    break;
                }
            }
            if (!subtypeMatches) {
                return false;
            }
        }

        // Check attribute conditions
        if (rule.getAttributeConditions() != null && !rule.getAttributeConditions().isEmpty()) {
            for (Map.Entry<String, Object> entry : rule.getAttributeConditions().entrySet()) {
                String attrName = entry.getKey();
                Object condition = entry.getValue();

                if (!attributeMatches(attrName, condition, snapshot.attributes())) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Проверяет одно условие правила: {@code condition} — карта с оператором
     * ({@code eq}/{@code gte}/{@code lte}/{@code in}). Отсутствующий у сущности атрибут считается
     * несовпадением, а не ошибкой конфигурации, поэтому некорректная карта условий приводит просто
     * к тому, что шаблон не подходит.
     */
    @SuppressWarnings("unchecked")
    private boolean attributeMatches(
            String attrName,
            Object condition,
            Map<String, Object> attributes
    ) {
        Object attrValue = attributes.get(attrName);
        if (attrValue == null) {
            return false;
        }

        if (!(condition instanceof Map)) {
            return false;
        }

        Map<String, Object> conditionMap = (Map<String, Object>) condition;

        // eq operator
        if (conditionMap.containsKey("eq")) {
            return attrValue.equals(conditionMap.get("eq"));
        }

        // gte operator (greater than or equal)
        if (conditionMap.containsKey("gte")) {
            Object threshold = conditionMap.get("gte");
            if (attrValue instanceof Number && threshold instanceof Number) {
                double value = ((Number) attrValue).doubleValue();
                double min = ((Number) threshold).doubleValue();
                return value >= min;
            }
        }

        // lte operator (less than or equal)
        if (conditionMap.containsKey("lte")) {
            Object threshold = conditionMap.get("lte");
            if (attrValue instanceof Number && threshold instanceof Number) {
                double value = ((Number) attrValue).doubleValue();
                double max = ((Number) threshold).doubleValue();
                return value <= max;
            }
        }

        // in operator
        if (conditionMap.containsKey("in")) {
            Object inValues = conditionMap.get("in");
            if (inValues instanceof List) {
                return ((List<?>) inValues).contains(attrValue);
            }
        }

        return false;
    }

    private record TemplateMatch(Template template, int priority) {
    }
}

package ru.coordination.approval.engine.validation;

import java.util.List;
import java.util.Set;

/**
 * Известные типы событий — проверка {@code EVENT_UNKNOWN} из
 * {@code 05_guards_actions_registry.md} §7.1.
 *
 * <p>Источник перечня — сводная таблица событий {@code 06_event_contract.md} §20. Контракт
 * событий определяет единый формат {@code approval.<агрегат>.<действие>} (там же, §4.1), поэтому
 * событие вне этого перечня — нарушение контракта: потребитель (аналитика, уведомления,
 * интеграции) не сможет его распознать.
 *
 * <p>TODO: перечень задан в коде, потому что отдельной таблицы-реестра событий в схеме нет
 * ({@code 08_db_schema.md} описывает реестры guard/action/status, но не событий). При появлении
 * реестра событий (например, в фазе публикации событий, PHASE-19) проверку следует перевести на
 * него, чтобы реестр стал единственным источником истины.
 */
public final class KnownEventTypes {

    /** Все типы событий из {@code 06_event_contract.md} §20. */
    public static final Set<String> ALL = Set.of(
            // Процесс
            "approval.process.created",
            "approval.process.started",
            "approval.process.recalled",
            "approval.process.resumed",
            "approval.process.completed",
            "approval.process.rework-required",
            "approval.process.return-targets-available",
            "approval.process.revision-created",
            "approval.process.revision-started",
            "approval.process.auto-archived",
            "approval.process.restored",
            // Этап
            "approval.stage.activated",
            "approval.stage.completed",
            "approval.stage.rejected",
            "approval.stage.auto-approved",
            "approval.stage.cancelled",
            // Итерация этапа
            "approval.stage-iteration.created",
            "approval.stage-iteration.superseded",
            "approval.stage-iteration.completed",
            // Задачи
            "approval.task.assigned",
            "approval.task.revoked",
            "approval.task.expired",
            // Решения
            "approval.decision.recorded",
            // Замечания и комментарии
            "approval.remark.created",
            "approval.remark.activated",
            "approval.remark.marked-not-required",
            "approval.remark.auto-processed",
            "approval.remark.resolved",
            "approval.remark.rejected",
            "approval.remark.closed",
            "approval.comment.created",
            // Дополнительные согласующие
            "approval.additional-approver.assigned",
            "approval.additional-approver.recommended",
            "approval.additional-approver.revoked",
            "approval.additional-approver.removed",
            // UNIFIED
            "approval.unified.route.submitted",
            "approval.unified.route.approved",
            "approval.unified.route.rejected",
            "approval.unified.awaiting-final-decision",
            "approval.unified.final-decision.recorded",
            // Сущность и конфигурация
            "approval.entity.status.changed",
            "approval.config.published",
            "approval.config.deprecated");

    /** Класс-константа: состояние не хранит, поэтому экземпляры не создаются. */
    private KnownEventTypes() {
    }

    /**
     * {@code true}, если событие объявлено в контракте. Точное совпадение строки: имена событий
     * — часть контракта с подписчиками, поэтому «похожее» имя (опечатка, другой регистр) должно
     * стать нарушением {@code EVENT_UNKNOWN}, а не быть принятым.
     */
    public static boolean isKnown(String eventType) {
        return ALL.contains(eventType);
    }

    /** Перечень в стабильном порядке — для сообщений об ошибках и документации. */
    public static List<String> sorted() {
        return ALL.stream().sorted().toList();
    }
}

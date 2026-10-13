package ru.coordination.approval.loadtest;

import java.util.List;
import java.util.UUID;

/**
 * Тела запросов к API шаблонов для нагрузочных сценариев.
 *
 * <p>Общий класс, а не приватный метод одного сценария: тем же телом пользуются сидер
 * ({@link SeedSimulation}) и профиль администратора ({@link TemplateLifecycleSimulation}), и
 * расхождение между ними означало бы, что сид проверяет одну структуру, а нагрузка — другую.
 *
 * <p>JSON собирается текстом, а не Jackson: генератор нагрузки не должен тянуть зависимости
 * приложения (сценарии специально написаны на одном Gatling DSL), а структура тела здесь фиксирована
 * и невелика.
 */
final class TemplateJson {

    private TemplateJson() {
    }

    /**
     * Тело запроса на создание шаблона: {@code stages} этапов, в каждом один слот, согласующие
     * назначаются по кругу из {@link LoadTestConfig#approverIds()}.
     *
     * <p>{@code ruleIdx = 1} и {@code entityTypes = [entityType]} дают приоритет выше
     * «универсальных» шаблонов без правил, а {@code attributeConditions} пуст: операторы условий
     * ограничены, и нагружать подбор шаблона смысла нет — интересны транзакции движка.
     */
    static String create(String name, int stages, String entityType, UUID createdBy) {
        List<UUID> approvers = LoadTestConfig.approverIds();
        StringBuilder stagesJson = new StringBuilder();
        for (int i = 0; i < stages; i++) {
            if (i > 0) {
                stagesJson.append(',');
            }
            UUID approver = approvers.get(i % approvers.size());
            stagesJson.append("""
                    {"orderIdx":%d,"name":"Нагрузочный этап %d","description":"Этап нагрузочного прогона",\
                    "stageType":"APPROVAL","duration":3,"decisionMode":"AND","executionOrder":"PARALLEL",\
                    "isMandatory":true,"isOrderMandatory":true,"allowedReturnStages":[],\
                    "actorSlots":[{"orderIdx":1,"userId":"%s","organizationId":null,"acceptableRoles":[],\
                    "required":true,"isUserEditable":false,"isOrganizationEditable":false,"isDeletable":false}]}\
                    """.formatted(i + 1, i + 1, approver));
        }
        return """
                {"name":"%s","processType":"STANDARD","processIterationEnabled":false,"responsibleRoles":[],\
                "requiresResponsibleApproval":false,"stages":[%s],\
                "applicabilityRules":[{"ruleIdx":1,"entityTypes":["%s"],"entitySubtypes":[],\
                "attributeConditions":{}}],"createdBy":"%s"}\
                """.formatted(name, stagesJson, entityType, createdBy);
    }

    /** Тело административного действия, у которого есть только актор (публикация, форк). */
    static String actor(UUID actorId) {
        return "{\"actorId\":\"" + actorId + "\"}";
    }

    /** Тело запроса на создание процесса по типу {@code LOADTEST}. */
    static String createProcess(String entityType, UUID initiatorId) {
        return "{\"entityType\":\"" + entityType
                + "\",\"entitySubtype\":null,\"entityId\":\"#{entityId}\",\"attributes\":{},"
                + "\"initiatorId\":\"" + initiatorId + "\"}";
    }
}

package ru.coordination.approval.domain.template;

/**
 * Тип слота в унифицированной модели {@code slot_template} (03_domain_model.md §6.5).
 *
 * <p>Различие типов — не только семантическое: от него зависит, какая из связей слота заполнена
 * (check-констрейнт {@code 08_db_schema.md} §6.4), и какую роль получит созданный по слоту
 * участник ({@code RouteGenerationService.mapSlotTypeToRole}).
 */
public enum SlotType {

    /**
     * Основной участник этапа: слот принадлежит этапу ({@code stage_template_id} заполнен,
     * {@code parent_slot_id} пуст) и порождает {@code Participant} с ролью {@code APPROVER}
     * (другие роли из типа слота не выводятся — {@code RouteGenerationService.mapSlotTypeToRole}).
     */
    ACTOR,

    /**
     * Дополнительный согласующий: слот вложен в другой слот ({@code parent_slot_id} заполнен,
     * {@code stage_template_id} пуст), образуя неограниченную иерархию. В маршрут процесса такие
     * слоты пока не переносятся (PHASE-10 не реализована).
     */
    ADDITIONAL_APPROVER
}

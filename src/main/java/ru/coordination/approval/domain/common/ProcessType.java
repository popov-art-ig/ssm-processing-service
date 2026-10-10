package ru.coordination.approval.domain.common;

/**
 * Тип процесса согласования.
 * См. 01_glossary.md, 03_domain_model.md §6.2, §7.2, §8.2.
 *
 * <p>Тип задаёт, какие поля шаблона и процесса вообще осмысленны: {@code STANDARD} работает по
 * маршруту из этапов с режимом агрегации и порядком участников, {@code UNIFIED} — по
 * единственному ответственному с финальным решением ({@code 02_process_types.md});
 * {@code ValidateTemplateStructureAction} отвергает {@code decisionMode}/{@code executionOrder}
 * у UNIFIED-шаблона.
 */
public enum ProcessType {

    /** Маршрут из этапов: {@code decisionMode}, {@code executionOrder}, {@code allowedReturnStages}. */
    STANDARD,

    /**
     * Единый процесс: без этапов и агрегации решений, итог фиксирует ответственный
     * ({@code FinalDecision}). В текущей реализации не поддержан — все карты
     * {@code state_machine_config} засеяны с {@code process_type = 'STANDARD'}.
     */
    UNIFIED
}

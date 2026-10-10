package ru.coordination.approval.domain.common;

/**
 * Режим агрегации решений участников этапа (ADR-022).
 * Реализация — в guards/actions, см. 05_guards_actions_registry.md §4.2, 04_state_machines.md §5.2, §7.
 *
 * <p>Значения заявляются шаблоном этапа ({@code stage_template.decision_mode},
 * {@code 08_db_schema.md} §6.3) и копируются в этап маршрута
 * ({@link ru.coordination.approval.domain.process.StageInstance#getDecisionMode()},
 * {@code 08_db_schema.md} §8.1) — то есть фиксируются на момент старта процесса. Ниже описана
 * семантика по {@code 05_guards_actions_registry.md} §4.2 (функция {@code evaluateAggregation});
 * каждый режим заканчивается одним из результатов {@code APPROVED} /
 * {@code APPROVED_WITH_COMMENTS} / {@code REWORK} / {@code WAIT}, который guards G-S-002…G-S-004
 * сопоставляют переходам {@code StageApproved} / {@code StageApprovedWithComments} /
 * {@code StageOnRework}.
 *
 * <p><b>Расхождения спецификации и реализации.</b> Реализация разбирает режим {@code switch}
 * внутри каждого guard'а, а не в общей функции {@code evaluateAggregation}, и по части режимов
 * ведёт себя иначе, чем описано (подробно и с примерами — в Javadoc
 * {@code ru.coordination.approval.service.guard.AggregationApprovedGuard} и
 * {@code ru.coordination.approval.service.guard.AggregationApprovedWithCommentsGuard}).
 */
public enum DecisionMode {

    /**
     * Ждать решения <i>всех</i> участников итерации, без короткого замыкания: одиночный
     * {@code REJECT} не закрывает этап, пока не выскажутся остальные. Итог — {@code REWORK} при
     * любом {@code REJECT}, иначе {@code APPROVED_WITH_COMMENTS} при наличии такого решения,
     * иначе {@code APPROVED}.
     */
    AND,

    /**
     * Первое положительное решение ({@code APPROVE} или {@code APPROVE_WITH_COMMENTS}) закрывает
     * этап, не дожидаясь остальных: {@code APPROVED_WITH_COMMENTS}, если первый по
     * {@code decidedAt} положительный голос — {@code APPROVE_WITH_COMMENTS}, иначе
     * {@code APPROVED}.
     */
    ANY_APPROVE,

    /**
     * Первое решение {@code REJECT} отправляет этап на доработку ({@code REWORK}) немедленно;
     * если отклонений нет — {@code WAIT}, даже когда часть участников уже согласовала.
     */
    ANY_REJECT,

    /**
     * Самое раннее по {@code decidedAt} решение закрывает этап: {@code REJECT} → {@code REWORK},
     * {@code APPROVE_WITH_COMMENTS} → {@code APPROVED_WITH_COMMENTS}, {@code APPROVE} →
     * {@code APPROVED}. В реализации этот режим ещё не разобран по результату — расхождения
     * описаны в Javadoc {@code AggregationApprovedGuard}.
     */
    ANY_DECISION,

    /**
     * Ждать всех, но первый {@code REJECT} прерывает ожидание и даёт {@code REWORK}
     * (в отличие от {@link #AND}, где отказ не прерывает ожидание остальных);
     * при отсутствии отклонений — {@code APPROVED}/{@code APPROVED_WITH_COMMENTS}.
     */
    FIRST_REJECT_FAIL_FAST
}

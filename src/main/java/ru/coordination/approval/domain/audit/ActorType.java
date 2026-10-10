package ru.coordination.approval.domain.audit;

/**
 * Тип инициатора события аудита. См. 03_domain_model.md §9.1 (User / System / Timer —
 * приведено к стандартной Java-конвенции SCREAMING_SNAKE_CASE при хранении как varchar).
 */
public enum ActorType {

    /**
     * Действие пользователя. Так помечаются переходы, инициированные API-запросом
     * ({@code ProcessService}, {@code DecisionService}, {@code RemarkService},
     * {@code TemplateService}).
     */
    USER,

    /**
     * Действие самого движка: вложенные переходы, которые выполняют actions
     * ({@code EvaluateAggregationAction}, {@code ActivateNextStageAction},
     * {@code EvaluateProcessCompletionAction} и др.).
     */
    SYSTEM,

    /**
     * Срабатывание таймера (планировщика). Значение объявлено, но ни один переход его пока не
     * использует: джобы автоархивации и напоминаний не реализованы (см. README, «Что
     * действительно не реализовано»).
     */
    TIMER
}

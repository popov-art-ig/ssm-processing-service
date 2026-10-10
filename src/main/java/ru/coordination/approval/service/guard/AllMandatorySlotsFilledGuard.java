package ru.coordination.approval.service.guard;

import java.util.Comparator;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-004 «В маршруте заполнены все обязательные слоты» (PHASE-03, источник:
 * {@code 05_guards_actions_registry.md} §4.1). Резолвится {@code guard_registry.handler =
 * 'allMandatorySlotsFilledGuard'} (миграция V18).
 *
 * <p><b>Упрощение этой фазы</b> (тикет, раздел 2): буквальная логика по {@code
 * SlotTemplate.required} требует связи {@code StageInstance}↔{@code StageTemplate}, которой
 * ещё нет ({@code RouteGeneratorService} не существует). Вместо этого здесь проверяется то,
 * что реально смоделировано: у каждого {@code mandatory}-этапа в его текущей (последней по
 * {@code iterationIdx}) итерации есть хотя бы один участник. Не тождественно спеке (не
 * проверяет обязательность конкретного слота), но не расходится с намерением guard'а
 * («маршрут не запускается с дырами») и подлежит уточнению до буквальной per-slot проверки
 * в фазе, вводящей {@code RouteGeneratorService} (см. «Открытые вопросы» тикета, п.3).
 */
@Component("allMandatorySlotsFilledGuard")
public class AllMandatorySlotsFilledGuard implements Guard {

    /**
     * @return {@code true}, если в текущей (последней по {@code iterationIdx}) итерации каждого
     *         {@code mandatory}-этапа есть хотя бы один участник; {@code false} — при пустой
     *         итерации обязательного этапа, этапе без итераций и для чужого типа сущности
     */
    @Override
    public boolean evaluate(TransitionContext context) {
        // Guard процессного уровня: при вызове из перехода уровня STAGE возвращаем false,
        // а не падаем с ClassCastException (так сделано в остальных guards этого пакета).
        if (!(context.entity() instanceof ProcessInstance process)) {
            return false;
        }
        return process.getStages().stream()
                .filter(StageInstance::isMandatory)
                .allMatch(this::hasParticipantsInCurrentIteration);
    }

    private boolean hasParticipantsInCurrentIteration(StageInstance stage) {
        return stage.getIterations().stream()
                .max(Comparator.comparing(iteration -> iteration.getIterationIdx()))
                .map(iteration -> !iteration.getParticipants().isEmpty())
                .orElse(false);
    }
}

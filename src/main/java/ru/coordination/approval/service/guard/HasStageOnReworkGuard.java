package ru.coordination.approval.service.guard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * «Хотя бы один этап процесса в статусе {@code OnRework}» — предусловие перехода
 * {@code ProcessRework} ({@code InProgress → OnRework}, {@code SYSTEM_ACTION}) (PHASE-08).
 * Резолвится {@code guard_registry.handler = 'hasStageOnReworkGuard'} (миграция V23, код реестра
 * {@code HasStageOnRework}).
 *
 * <p><b>Контракт.</b> {@code true} — в процессе есть этап, ушедший на доработку, поэтому процесс
 * обязан перейти в {@code OnRework}; {@code false} — таких этапов нет (в том числе для чужого
 * типа сущности). Побочных эффектов нет; статус процесса пишет {@code TransitionEngine}.
 *
 * <p><b>Кода в спецификации нет.</b> В {@code 05_guards_actions_registry.md} §4.1 нет условия
 * «есть этап на доработке»: приписанный в V23 код {@code G-P-007} принадлежит
 * {@code AllStagesApproved}, а закрытие этапа в {@code OnRework} описано как результат агрегации
 * ({@code G-S-004 AggregationRework}, §4.2). Этот guard — процессный отголосок того же события:
 * этап уже ушёл в {@code OnRework}, и процесс догоняет его.
 *
 * <p><b>Почему статусы читаются из репозитория.</b> Guard вызывается как action перехода
 * закрытия этапа (через {@link ru.coordination.approval.service.action.EvaluateProcessReworkAction}),
 * когда {@code transition_config.actions} выполняются, а новый статус этапа ещё не записан
 * движком в сущность — но уже записан в БД. Проверка по in-memory коллекции
 * {@code process.getStages()} всегда давала {@code false}, и процесс не переходил в
 * {@code OnRework}.
 */
@Component("hasStageOnReworkGuard")
@RequiredArgsConstructor
public class HasStageOnReworkGuard implements Guard {

    private final StageRepository stageRepository;

    /** @return {@code true}, если хотя бы один этап процесса имеет статус {@code OnRework} */
    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof ProcessInstance process)) {
            return false;
        }

        List<String> statuses = stageRepository.findStatusesByProcessId(process.getId());
        return statuses.stream().anyMatch("OnRework"::equals);
    }
}

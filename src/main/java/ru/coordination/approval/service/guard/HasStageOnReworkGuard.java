package ru.coordination.approval.service.guard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-007 «Хотя бы один этап процесса в статусе OnRework» (PHASE-08).
 * Используется в переходе ProcessRework для автоматического перевода процесса в OnRework.
 *
 * <p>Статусы этапов читаются из {@link StageRepository}, а не из {@code process.getStages()}:
 * guard вызывается как action перехода закрытия этапа, когда этапу ещё не записан новый статус
 * (запись делает {@code TransitionEngine} после выполнения actions). Проверка по in-memory
 * коллекции всегда давала {@code false}, и процесс не переходил в {@code OnRework}.
 */
@Component("hasStageOnReworkGuard")
@RequiredArgsConstructor
public class HasStageOnReworkGuard implements Guard {

    private final StageRepository stageRepository;

    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof ProcessInstance process)) {
            return false;
        }

        List<String> statuses = stageRepository.findStatusesByProcessId(process.getId());
        return statuses.stream().anyMatch("OnRework"::equals);
    }
}

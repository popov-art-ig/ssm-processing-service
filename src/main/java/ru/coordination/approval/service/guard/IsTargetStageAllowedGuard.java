package ru.coordination.approval.service.guard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-009 «Целевой этап входит в список allowedReturnStages» (PHASE-08).
 * PHASE-08: упрощение — разрешаем возврат на любой этап не позже этапа, ушедшего на доработку.
 * TODO PHASE-14: проверить allowedReturnStages из шаблона.
 *
 * <p>Два исправления относительно первой версии:
 * <ul>
 *   <li>этапы нумеруются с 0 ({@code ProcessServiceIntegrationTest} и рабочие фикстуры создают
 *       {@code orderIdx} начиная с 0, а {@code ProcessController} восстанавливает индекс именно из
 *       {@code StageInstance.orderIdx}) — прежнее условие {@code targetStageOrderIdx >= 1} делало
 *       невозможным возврат на первый этап;</li>
 *   <li>статусы этапов читаются из {@link StageRepository}: guard вызывается внутри перехода
 *       {@code ProcessRework}, и в памяти этап ещё мог числиться {@code Active}.</li>
 * </ul>
 */
@Component("isTargetStageAllowedGuard")
@RequiredArgsConstructor
public class IsTargetStageAllowedGuard implements Guard {

    private final StageRepository stageRepository;

    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof ProcessInstance process)) {
            return false;
        }

        Integer targetStageOrderIdx = (Integer) context.parameters().get("targetStageOrderIdx");
        if (targetStageOrderIdx == null) {
            return false;
        }

        List<StageInstance> stages =
                stageRepository.findByProcessIdOrderByOrderIdx(process.getId());

        StageInstance onReworkStage = stages.stream()
                .filter(s -> "OnRework".equals(s.getStatus()))
                .findFirst()
                .orElse(null);

        if (onReworkStage == null) {
            return false;
        }

        return targetStageOrderIdx >= 0 && targetStageOrderIdx <= onReworkStage.getOrderIdx();
    }
}

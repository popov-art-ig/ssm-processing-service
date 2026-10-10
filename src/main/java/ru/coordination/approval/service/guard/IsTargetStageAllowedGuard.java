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
 * G-RT-001 «Корректная цель возврата» (PHASE-08, источник: {@code 05_guards_actions_registry.md}
 * §4.4). Резолвится {@code guard_registry.handler = 'isTargetStageAllowedGuard'} (миграция V23,
 * код реестра {@code IsTargetStageAllowed}); включён в guards перехода {@code ResumeProcess}
 * ({@code OnRework → InProgress}, {@code USER_ACTION}).
 *
 * <p><b>Контракт.</b> {@code true} — выбранный этап допустим как цель возврата: он существует
 * ({@code targetStageOrderIdx >= 0}) и не находится позже этапа, ушедшего на доработку
 * ({@code targetStageOrderIdx <= onReworkStage.orderIdx}); {@code false} — параметр не передан,
 * этапа {@code OnRework} в процессе нет или цель лежит дальше него. Побочных эффектов нет.
 *
 * <p><b>Заглушка/упрощение.</b> Спецификация требует проверять вхождение целевого этапа в
 * {@code stage.allowedReturnStages} (§4.4, а также G-RT-002 «есть несколько целей возврата» для
 * показа модалки выбора). Здесь список разрешённых целей не читается вообще: разрешён возврат на
 * любой этап не позже ушедшего на доработку, то есть ограничение по данным шаблона не работает и
 * вернуться можно на этап, который шаблон запрещал. Буквальная проверка
 * {@code allowedReturnStages} отнесена к фазе генерации маршрута (формально помечена как
 * PHASE-14 в {@code TODO} класса; по {@code doc/REMAINING_PHASES.md} генерация маршрута —
 * PHASE-15, поэтому {@code TODO} без номера тикета не даёт точной адресации замены).
 *
 * <p><b>Расхождение в коде реестра.</b> Миграция V23 приписывает условию код {@code G-P-009}, но
 * в спецификации {@code G-P-009} — это {@code AnyStageRejected} (§4.1), а корректная цель
 * возврата описана как {@code G-RT-001} (§4.4).
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

    /**
     * @return {@code true}, если {@code targetStageOrderIdx} неотрицателен и не превышает
     *         {@code orderIdx} этапа, ушедшего на доработку
     */
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

package ru.coordination.approval.service.guard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-007 «Хотя бы один этап завершён с замечаниями» (PHASE-07, источник:
 * {@code 05_guards_actions_registry.md} §4.1). Резолвится
 * {@code guard_registry.handler = 'hasCommentsGuard'} (миграция V22).
 *
 * <p>Проверяет, что хотя бы один этап процесса находится в статусе {@code ApprovedWithComments}.
 * Используется для выбора между переходами {@code ProcessApproved} и
 * {@code ProcessApprovedWithComments} при завершении процесса.
 *
 * <p>Логика формирования итога процесса (источник: {@code 02_process_types.md} §3.4):
 * если хотя бы один этап завершён как {@code ApprovedWithComments} → процесс переходит в
 * {@code ApprovedWithComments}, иначе (все этапы {@code Approved}) → процесс переходит в
 * {@code Approved}.
 */
@Component("hasCommentsGuard")
@RequiredArgsConstructor
public class HasCommentsGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();

        return process.getStages().stream()
                .anyMatch(stage -> "ApprovedWithComments".equals(stage.getStatus()));
    }
}

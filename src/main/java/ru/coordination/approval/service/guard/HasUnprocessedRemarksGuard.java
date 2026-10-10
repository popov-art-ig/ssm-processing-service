package ru.coordination.approval.service.guard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.RemarkRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * «У процесса есть необработанные замечания» (PHASE-11). Резолвится
 * {@code guard_registry.handler = 'hasUnprocessedRemarksGuard'} (миграция V24, код реестра
 * {@code HasUnprocessedRemarks}). {@code true} — в процессе есть замечания в статусах
 * {@code Open} или {@code InProgress}; {@code false} — таких замечаний нет либо сущность
 * перехода не {@link ProcessInstance}.
 *
 * <p><b>Ни в один переход не включён.</b> Поиск по {@code transition_config.guards} во всех
 * миграциях не находит {@code HasUnprocessedRemarks}: guard зарегистрирован в реестре и покрыт
 * тестом, но движок его не вызывает. Задуманное применение — блокировка возобновления
 * процесса при незакрытых замечаниях, то есть он должен был бы стоять рядом с
 * {@link AllRemarksProcessedGuard} в guards перехода {@code ResumeProcess}. Это незавершённая
 * проводка, а не рабочая проверка.
 *
 * <p><b>Кода в спецификации нет.</b> {@code 05_guards_actions_registry.md} §4.1 не описывает
 * условие «есть необработанные замечания» (в реестре — только {@code G-P-006
 * AllActiveRemarksHandled} с обратным смыслом), а приписанный в V24 код {@code G-P-010} в
 * спецификации занят {@code AllStagesCompleted}.
 *
 * <p><b>Известное расхождение по статусам.</b> Проверяются {@code Open} и {@code InProgress},
 * тогда как {@code RemarkService} создаёт замечание в статусе {@code Active}, а статуса
 * {@code Open} в {@code status_registry} для {@code entityType = REMARK} (V24) нет. Действующее
 * замечание поэтому не обнаруживается, и guard даёт {@code false}.
 */
@Component("hasUnprocessedRemarksGuard")
@RequiredArgsConstructor
public class HasUnprocessedRemarksGuard implements Guard {

    private final RemarkRepository remarkRepository;

    /**
     * @return {@code true}, если в процессе есть замечание в статусе {@code Open} или
     *         {@code InProgress}; {@code false} для чужого типа сущности (см. расхождение по
     *         статусу {@code Active} в описании класса)
     */
    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof ProcessInstance process)) {
            return false;
        }

        return remarkRepository.existsByProcessIdAndStatusIn(
                process.getId(),
                java.util.List.of("Open", "InProgress"));
    }
}

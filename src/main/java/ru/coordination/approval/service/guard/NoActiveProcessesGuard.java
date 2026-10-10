package ru.coordination.approval.service.guard;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * Guard архивации шаблона: на шаблоне нет незавершённых процессов (PHASE-14). Резолвится
 * {@code guard_registry.handler = 'noActiveProcesses'} (миграция V25); включён в guards перехода
 * {@code ArchiveTemplate} ({@code DEPRECATED → ARCHIVED}, {@code USER_ACTION}).
 *
 * <p><b>Кода в спецификации нет.</b> Условие заведено миграцией V25 вместе с state machine
 * шаблона и в {@code 05_guards_actions_registry.md} не описано: спецификация реестра не
 * рассматривает жизненный цикл шаблона (там описаны только сущности процесса). Обратной
 * ссылки на {@code G-A-001}/{@code G-A-002} §4.8 нет: те условия относятся к автоархивации
 * самого процесса по времени, а не к архивации шаблона.
 *
 * <p><b>Контракт.</b> {@code true} — все процессы, созданные по шаблону, находятся в одном из
 * финальных статусов {@code Approved}, {@code ApprovedWithComments}, {@code Rejected},
 * {@code Canceled}, поэтому шаблон можно архивировать; {@code false} — хотя бы один процесс ещё
 * не завершён (или сущность перехода не {@link Template}). Побочных эффектов нет.
 *
 * <p><b>Известное расхождение в списке финальных статусов.</b> Набор задан локальной константой и
 * не включает {@code Recalled}, хотя в {@code status_registry} этот статус процесса объявлен
 * терминальным (V23, {@code is_terminal = true}). Если бы в модуле существовал переход процесса
 * в {@code Recalled} (PHASE-09 «Отзыв и возобновление после отзыва» в текущей карте
 * отсутствует), отозванный процесс навсегда блокировал бы архивацию своего шаблона. Написание
 * {@code Canceled} (одна «l») тоже требует проверки: соответствующий код в
 * {@code status_registry} ни одной из миграций не регистрируется.
 */
@Component("noActiveProcesses")
@RequiredArgsConstructor
public class NoActiveProcessesGuard implements Guard {

    private final ProcessRepository processRepository;

    private static final List<String> TERMINAL_STATUSES = List.of(
            "Approved", "Rejected", "Canceled", "ApprovedWithComments"
    );

    /**
     * @return {@code true}, если по шаблону нет процессов вне {@code TERMINAL_STATUSES}
     *         (см. расхождение по {@code Recalled} в описании класса)
     */
    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof Template template)) {
            return false;
        }

        UUID templateId = template.getId();
        List<ProcessInstance> processes = processRepository.findByTemplateRef(templateId);

        for (ProcessInstance process : processes) {
            if (!TERMINAL_STATUSES.contains(process.getStatus())) {
                return false;
            }
        }

        return true;
    }
}

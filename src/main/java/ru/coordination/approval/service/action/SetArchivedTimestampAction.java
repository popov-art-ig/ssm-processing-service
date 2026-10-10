package ru.coordination.approval.service.action;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-P-008 «Архивировать процесс» в части временной метки: при архивации шаблона обновляет
 * {@code template.updatedAt} (PHASE-14). Резолвится
 * {@code action_registry.handler = 'setArchivedTimestamp'} (миграция V25, код реестра
 * {@code SetArchivedTimestamp}); включён в actions перехода {@code ArchiveTemplate}
 * ({@code DEPRECATED → ARCHIVED}, {@code USER_ACTION}).
 *
 * <p><b>Какое состояние меняет.</b> Только {@code updatedAt}; статус {@code ARCHIVED} записывает
 * {@code TransitionEngine} через {@code statusWriter}, который {@code TemplateService} передаёт как
 * {@code newState -> template.setStatus(LifecycleStatus.valueOf(newState))}. Явный
 * {@link TemplateRepository#save} здесь нужен как страховка: действие вызывается не из сервиса
 * шаблонов, а из движка, и не должно зависеть от того, что сущность уже managed.
 *
 * <p><b>Кода в спецификации нет.</b> В {@code 05_guards_actions_registry.md} нет действий
 * жизненного цикла шаблона — §5.1 описывает {@code A-P-008 ArchiveProcess} для
 * {@code ProcessInstance} (архивация самого процесса с сохранением {@code previousStatus} и
 * отменой задач), и это другое действие на другой сущности. Совпадение имени обманчиво: шаблон
 * архивируется без сохранения предыдущего статуса и без каскада по процессам.
 *
 * <p>Для чужого типа сущности действие молча ничего не делает: guard перехода
 * ({@code NoActiveProcesses}, {@code guard_registry.handler = 'noActiveProcesses'}) ограничивает
 * контекст шаблоном, и падение здесь было бы лишним риском для перехода.
 */
@Component("setArchivedTimestamp")
@RequiredArgsConstructor
public class SetArchivedTimestampAction implements Action {

    private final TemplateRepository templateRepository;

    /** Обновляет {@code template.updatedAt} и сохраняет шаблон. */
    @Override
    public void execute(TransitionContext context) {
        if (!(context.entity() instanceof Template template)) {
            return;
        }

        template.setUpdatedAt(Instant.now());
        templateRepository.save(template);
    }
}

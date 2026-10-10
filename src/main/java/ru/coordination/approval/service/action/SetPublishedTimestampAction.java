package ru.coordination.approval.service.action;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * Фиксирует факт публикации шаблона: {@code publishedAt = now}, {@code publishedBy = actorId}
 * (PHASE-14). Резолвится {@code action_registry.handler = 'setPublishedTimestamp'} (миграция V25,
 * код реестра {@code SetPublishedTimestamp}); включён в actions перехода {@code PublishTemplate}
 * ({@code DRAFT → PUBLISHED}, {@code USER_ACTION}), всегда после
 * {@link ValidateTemplateStructureAction}.
 *
 * <p><b>Какое состояние меняет.</b> {@code publishedAt} и {@code publishedBy}; статус
 * {@code PUBLISHED} записывает {@code TransitionEngine} через {@code statusWriter}, который
 * {@code TemplateService} передаёт как {@code newState -> template.setStatus(LifecycleStatus.valueOf(newState))}.
 * Автор публикации берётся из {@code context.actorId()} — то есть из инициатора перехода, а не из
 * {@code createdBy} шаблона: публиковать версию может другой пользователь.
 *
 * <p><b>Почему порядок «сначала валидация, потом метка».</b> {@link ValidateTemplateStructureAction}
 * бросает {@link IllegalArgumentException} на структурно некорректном шаблоне; поскольку
 * {@code TransitionEngine} выполняет actions строго по порядку в одной транзакции, отказ
 * откатывает и метку публикации — {@code PUBLISHED} без {@code publishedAt} остаться не может.
 *
 * <p><b>Кода в спецификации нет.</b> Действия жизненного цикла шаблона в
 * {@code 05_guards_actions_registry.md} не описаны; совпадение с {@code A-RV-004 StartNewRevision}
 * (§5.6) отсутствует.
 *
 * <p>Для чужого типа сущности действие молча ничего не делает — контекст перехода
 * {@code PublishTemplate} всегда {@link Template}, и падение здесь было бы лишним риском.
 */
@Component("setPublishedTimestamp")
@RequiredArgsConstructor
public class SetPublishedTimestampAction implements Action {

    private final TemplateRepository templateRepository;

    /** Записывает {@code publishedAt = now} и {@code publishedBy = context.actorId()}. */
    @Override
    public void execute(TransitionContext context) {
        if (!(context.entity() instanceof Template template)) {
            return;
        }

        template.setPublishedAt(Instant.now());
        template.setPublishedBy(context.actorId());
        templateRepository.save(template);
    }
}

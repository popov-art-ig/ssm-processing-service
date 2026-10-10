package ru.coordination.approval.service.action;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Comment;
import ru.coordination.approval.domain.process.CommentRepository;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.AuthorRole;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * «Записать комментарий участника» — создаёт запись {@link Comment}, если комментарий передан
 * (PHASE-05). Резолвится {@code action_registry.handler = 'recordCommentAction'} (миграция V20,
 * код реестра {@code RecordComment}); включён в actions перехода {@code Decide}
 * ({@code Assigned → Decided}, {@code USER_ACTION}) между {@link RecordDecisionAction} и
 * {@code EvaluateAggregation}.
 *
 * <p><b>Какое состояние меняет.</b> Добавляет строку в {@code comment}, привязанную к процессу,
 * этапу, итерации и участнику; решения и статусы не меняет. Пустой или отсутствующий
 * {@code comment} — не ошибка: действие просто ничего не делает (комментарий обязателен только для
 * решений {@code APPROVE_WITH_COMMENTS} и {@code REJECT}, и это проверяет
 * {@code DecisionService.decide} до вызова движка).
 *
 * <p><b>Почему {@code id} генерируется здесь.</b> {@code BaseEntity} объявляет {@code @Id} без
 * {@code @GeneratedValue}, то есть идентификатор задаёт приложение; без явного {@code UUID.randomUUID()}
 * Hibernate падает с «Identifier of entity ... must be manually assigned».
 *
 * <p><b>Почему {@code authorRole} жёстко равен {@code APPROVER}.</b> В контексте перехода
 * {@code Decide} автором комментария всегда является участник-согласующий; роль доп. согласующего
 * появится вместе с иерархией доп. согласующих (PHASE-10), а колонка {@code author_role} объявлена
 * {@code NOT NULL} (V8), поэтому значение обязано быть задано.
 *
 * <p><b>Кода в спецификации нет.</b> В {@code 05_guards_actions_registry.md} комментарий участника
 * не выделен в отдельное действие: §5.4 описывает {@code A-PA-001 RecordDecision} с параметром
 * {@code comment}, то есть по спеке комментарий сохраняется вместе с решением. Здесь это два
 * действия, поэтому код {@code RecordComment} существует только в реестре.
 */
@Component("recordCommentAction")
@RequiredArgsConstructor
public class RecordCommentAction implements Action {

    private final CommentRepository commentRepository;

    /**
     * Сохраняет комментарий участника.
     *
     * @param context контекст перехода {@code Decide}; необязательный параметр {@code comment},
     *                при {@code null} или пустой строке действие не делает ничего
     */
    @Override
    public void execute(TransitionContext context) {
        Participant participant = (Participant) context.entity();
        String commentText = (String) context.parameters().get("comment");

        if (commentText == null || commentText.isBlank()) {
            return;
        }

        Comment comment = Comment.builder()
                // BaseEntity объявляет @Id без @GeneratedValue — id задаёт приложение,
                // иначе Hibernate падает с "Identifier of entity ... must be manually assigned".
                .id(UUID.randomUUID())
                .process(participant.getStageIteration().getStage().getProcess())
                .stage(participant.getStageIteration().getStage())
                .stageIteration(participant.getStageIteration())
                .participant(participant)
                .authorId(context.actorId())
                // author_role объявлен NOT NULL в схеме (V8__remarks_and_comments.sql, строка 40)
                .authorRole(AuthorRole.APPROVER)
                .text(commentText)
                .createdAt(Instant.now())
                .build();

        commentRepository.save(comment);
    }
}

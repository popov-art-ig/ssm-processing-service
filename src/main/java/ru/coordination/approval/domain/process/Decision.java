package ru.coordination.approval.domain.process;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import ru.coordination.approval.domain.common.BaseEntity;

/**
 * Решение основного участника ({@link Participant}, role APPROVER/SIGNER), определяющее ход
 * процесса. <b>Не унифицировано</b> с рекомендацией доп. согласующего
 * ({@link AdditionalApprover#getRecommendation()}) — см. 01_glossary.md §10.1–10.2, ADR-016.
 *
 * <p>Единственный источник входных данных для {@code evaluateAggregation(stage)}
 * (05_guards_actions_registry.md §4.2) — управляет переходами StageStateMachine.
 *
 * См. 03_domain_model.md §7.8, 08_db_schema.md §11.1.
 */
@Entity
@Table(name = "decision")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Decision extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    /** Код decision_result_registry: APPROVE / APPROVE_WITH_COMMENTS / REJECT. */
    @Column(name = "result", length = 100, nullable = false)
    private String result;

    @Column(name = "comment")
    private String comment;

    @Column(name = "auto", nullable = false)
    @lombok.Builder.Default
    private boolean auto = false;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

package ru.coordination.approval.domain.process;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import ru.coordination.approval.domain.common.BaseEntity;

/**
 * Круг выполнения этапа. При активации этапа, если у него уже есть итерации — создаётся новая.
 * «Итерация процесса» (ProcessIteration) не хранится физически — вычисляется как
 * {@code max(stageIteration.iterationIdx)} по всем этапам процесса (03_domain_model.md §7.5).
 *
 * См. 03_domain_model.md §7.4, 08_db_schema.md §8.2.
 */
@Entity
@Table(name = "stage_iteration")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class StageIteration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private StageInstance stage;

    @Column(name = "iteration_idx", nullable = false)
    private Integer iterationIdx;

    /** Active / Completed / Superseded / Cancelled — валидируется через StatusRegistry (entityType=STAGE_ITERATION). */
    @Column(name = "status", length = 100, nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "stageIteration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<Participant> participants = new ArrayList<>();
}

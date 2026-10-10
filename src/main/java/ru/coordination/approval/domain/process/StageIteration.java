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

    /** Этап-владелец итерации; участники и решения всегда принадлежат итерации, а не этапу. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private StageInstance stage;

    /**
     * Номер круга этапа, начиная с 1 (уникален в рамках этапа —
     * {@code ux_stage_iteration}). Новая итерация создаётся при повторной активации этапа;
     * guards и actions работают с последней, то есть с максимальным номером.
     */
    @Column(name = "iteration_idx", nullable = false)
    private Integer iterationIdx;

    /**
     * Статус круга. Значения заявлены как Active / Completed / Superseded / Cancelled
     * (валидируется через {@code StatusRegistry}, entityType=STAGE_ITERATION), но переходов этапа,
     * которые меняли бы статус итерации, в картах нет: {@code SetStageCompletedAt} обновляет
     * только {@code stage_instance.completed_at}, а действия возврата меняют статусы этапов.
     */
    @Column(name = "status", length = 100, nullable = false)
    private String status;

    /** Время начала круга (момент создания итерации). */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** Время закрытия круга; {@code null}, пока итерация активна. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Время создания строки. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Участники этого круга: при каждой новой итерации состав создаётся заново, поэтому решение
     * участника относится именно к своей итерации ({@code ParticipantRepository} ищет по
     * {@code stageIterationId}, а не по этапу).
     */
    @OneToMany(mappedBy = "stageIteration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<Participant> participants = new ArrayList<>();
}

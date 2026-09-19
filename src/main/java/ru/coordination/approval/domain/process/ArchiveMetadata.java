package ru.coordination.approval.domain.process;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Метаданные архивации процесса. PK — {@code process_id} (разделяемый первичный ключ
 * с {@link ProcessInstance}, а не отдельный суррогатный id).
 *
 * См. 03_domain_model.md §7.12, 08_db_schema.md §14.1.
 */
@Entity
@Table(name = "archive_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchiveMetadata {

    @Id
    @Column(name = "process_id")
    private UUID processId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "process_id")
    private ProcessInstance process;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    /** Статус процесса до архивации. */
    @Column(name = "previous_status", length = 100, nullable = false)
    private String previousStatus;

    @Column(name = "restored_at")
    private Instant restoredAt;

    @Column(name = "restored_by")
    private UUID restoredBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArchiveMetadata other)) {
            return false;
        }
        return processId != null && processId.equals(other.getProcessId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

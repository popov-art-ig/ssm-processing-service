package ru.coordination.approval.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Singleton-настройка периодичности напоминаний, настраиваемая администратором (ADR-027).
 * Таблица всегда содержит ровно одну строку с {@code id = 1} (CHECK в БД + предзаполненный
 * INSERT в миграции). {@code ReminderJob} читает эти поля вместо хардкода периода.
 *
 * См. 08_db_schema.md §22.1, 07_api_contract.md §26.
 */
@Entity
@Table(name = "notification_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettings {

    /** Всегда 1 (singleton) — обеспечено CHECK (id = 1) на уровне БД. */
    @Id
    @Column(name = "id")
    @Builder.Default
    private Short id = 1;

    @Column(name = "reminder_enabled", nullable = false)
    @Builder.Default
    private boolean reminderEnabled = true;

    @Column(name = "reminder_interval_hours", nullable = false)
    @Builder.Default
    private Integer reminderIntervalHours = 24;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NotificationSettings other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

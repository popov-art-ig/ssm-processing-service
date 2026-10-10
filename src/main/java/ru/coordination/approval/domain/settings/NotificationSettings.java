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

    /**
     * Включены ли напоминания. По замыслу {@code false} не снимает задачу с планировщика: джоба
     * напоминаний продолжает запускаться, но не рассылает уведомления
     * ({@code 08_db_schema.md} §22.1, ADR-027). Ни одной {@code @Scheduled}-джобы в проекте пока
     * нет — настройка читается только REST-эндпоинтом {@code /admin/notification-settings}.
     */
    @Column(name = "reminder_enabled", nullable = false)
    @Builder.Default
    private boolean reminderEnabled = true;

    /**
     * Периодичность напоминаний в часах; по умолчанию 24 — прежний хардкод «раз в день».
     * Значение задумано как читаемое джобой на каждом запуске вместо константы в коде (ADR-027);
     * сейчас его отдаёт только REST-эндпоинт настроек, БД гарантирует {@code > 0}.
     */
    @Column(name = "reminder_interval_hours", nullable = false)
    @Builder.Default
    private Integer reminderIntervalHours = 24;

    /** Время последнего изменения настройки. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Администратор, изменивший настройку. Отдельной append-only истории для этой единственной
     * строки не ведётся — значимо только текущее значение ({@code 08_db_schema.md} §22.1).
     */
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

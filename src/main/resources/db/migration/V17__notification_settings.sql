-- 08_db_schema.md — Группа 18: Настройки уведомлений (notification_settings, ADR-027)
-- Singleton: ровно одна строка с id = 1. UPDATE — единственная операция изменения после
-- этой миграции; INSERT/DELETE приложением не используются.

create table notification_settings (
    id                          smallint primary key default 1,
    reminder_enabled            boolean not null default true,
    reminder_interval_hours     integer not null default 24
                                check (reminder_interval_hours > 0),
    updated_at                  timestamptz not null default now(),
    updated_by                  uuid null,

    check (id = 1)
);

insert into notification_settings (id, reminder_enabled, reminder_interval_hours)
values (1, true, 24);

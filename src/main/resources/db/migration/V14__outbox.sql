-- 08_db_schema.md — Группа 15: Outbox (outbox_event) — надёжная публикация событий в RabbitMQ.
-- Партиционирование по occurred_at (месяц) — см. 08_db_schema.md §25; в Фазе 1 создаётся
-- как обычная таблица, партиционирование вводится отдельной миграцией в последующей фазе.

create table outbox_event (
    id                      uuid primary key,
    aggregate_type          varchar(100) not null,
    aggregate_id            uuid not null,
    event_type              varchar(255) not null,
    event_version           varchar(20) not null,
    exchange                varchar(100) not null,
    routing_key             varchar(255) not null,
    payload                 jsonb not null,
    correlation_id          uuid null,
    causation_id            uuid null,
    actor_id                uuid null,
    actor_type              varchar(50) null,
    config_version          integer null,
    occurred_at             timestamptz not null default now(),
    published_at            timestamptz null,
    publish_attempts        integer not null default 0,
    last_error              text null,
    created_at              timestamptz not null default now()
);

create index ix_outbox_unpublished on outbox_event(occurred_at)
    where published_at is null;
create index ix_outbox_aggregate on outbox_event(aggregate_type, aggregate_id);
create index ix_outbox_event_type on outbox_event(event_type);

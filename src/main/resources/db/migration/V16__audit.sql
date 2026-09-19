-- 08_db_schema.md — Группа 17: Аудит (audit_event, config_audit_event)
-- Партиционирование по occurred_at (месяц) — см. §25; вводится отдельной миграцией позже.

create table audit_event (
    id                      uuid primary key,
    entity_type             varchar(100) not null,
    entity_id               uuid not null,
    action                  varchar(100) not null,
    actor_id                uuid null,
    actor_type              varchar(50) null,
    payload                 jsonb not null default '{}',
    occurred_at             timestamptz not null default now()
);

create index ix_audit_entity on audit_event(entity_type, entity_id);
create index ix_audit_actor on audit_event(actor_id);
create index ix_audit_occurred on audit_event(occurred_at);
create index ix_audit_action on audit_event(action);

create table config_audit_event (
    id                      uuid primary key,
    config_id               uuid not null references state_machine_config(id),
    config_version          integer not null,
    action                  varchar(50) not null
                            check (action in ('CREATED', 'UPDATED', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED')),
    actor_id                uuid not null,
    diff                    jsonb not null default '{}',
    comment                 text null,
    occurred_at             timestamptz not null default now()
);

create index ix_config_audit_config on config_audit_event(config_id);
create index ix_config_audit_action on config_audit_event(action);

-- 08_db_schema.md — Группа 11: Конфигурация state machine (state_machine_config, state_config, transition_config)
-- Карта переходов не изменилась при замене движка (ADR-028) — заменён только исполняющий
-- компонент (TransitionEngine вместо Spring State Machine).

create table state_machine_config (
    id                      uuid primary key,
    version                 integer not null,
    entity_type             varchar(50) not null
                            check (entity_type in ('PROCESS', 'STAGE', 'STAGE_ITERATION', 'PARTICIPANT', 'ADDITIONAL_APPROVER', 'FINAL_DECISION', 'REMARK')),
    process_type            varchar(50) null,
    status                  varchar(50) not null
                            check (status in ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED')),
    active_process_count    integer not null default 0,
    created_at              timestamptz not null default now(),
    created_by              uuid not null,
    published_at            timestamptz null,
    published_by            uuid null,
    updated_at              timestamptz not null default now(),
    version_lock            integer not null default 0
);

create unique index ux_state_machine_config_version
    on state_machine_config(entity_type, process_type, version);
create index ix_state_machine_config_status on state_machine_config(status);

create table state_config (
    id                      uuid primary key,
    config_id               uuid not null references state_machine_config(id),
    code                    varchar(100) not null,
    display_name            varchar(255) not null,
    is_initial              boolean not null default false,
    is_terminal             boolean not null default false,
    metadata                jsonb not null default '{}',
    created_at              timestamptz not null default now()
);

create unique index ux_state_config_code on state_config(config_id, code);
create index ix_state_config_config on state_config(config_id);

create table transition_config (
    id                      uuid primary key,
    config_id               uuid not null references state_machine_config(id),
    code                    varchar(100) not null,
    from_state              varchar(100) not null,
    to_state                varchar(100) not null,
    trigger                 varchar(50) not null
                            check (trigger in ('USER_ACTION', 'SYSTEM_ACTION', 'TIMER')),
    guards                  text[] not null default '{}',
    actions                 text[] not null default '{}',
    emits                   text[] not null default '{}',
    priority                integer not null default 0,
    is_active               boolean not null default true,
    created_at              timestamptz not null default now()
);

create unique index ux_transition_config_code on transition_config(config_id, code);
create index ix_transition_config_from on transition_config(config_id, from_state);
create index ix_transition_config_trigger on transition_config(config_id, trigger);

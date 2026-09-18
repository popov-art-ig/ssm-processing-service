-- 08_db_schema.md — Группа 12: Реестр guards и actions (guard_registry, action_registry)
-- Резолвятся TransitionEngine через GuardRegistry/ActionRegistry (ADR-028, 10_architecture.md §7.2.5-7.2.6).

create table guard_registry (
    code                    varchar(100) primary key,
    display_name            varchar(255) not null,
    description             text not null,
    handler                 varchar(255) not null,
    params_schema           jsonb null,
    scope                   varchar(50) not null
                            check (scope in ('GLOBAL', 'CUSTOM')),
    categories              text[] not null default '{}',
    applicable_entities     text[] not null default '{}',
    is_active               boolean not null default true,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now()
);

create index ix_guard_registry_scope on guard_registry(scope);

create table action_registry (
    code                    varchar(100) primary key,
    display_name            varchar(255) not null,
    description             text not null,
    handler                 varchar(255) not null,
    params_schema           jsonb null,
    scope                   varchar(50) not null
                            check (scope in ('GLOBAL', 'CUSTOM')),
    categories              text[] not null default '{}',
    applicable_entities     text[] not null default '{}',
    is_active               boolean not null default true,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now()
);

create index ix_action_registry_scope on action_registry(scope);

-- 08_db_schema.md — Группа 1: Справочники (status_registry, decision_result_registry)

create table status_registry (
    code                    varchar(100) primary key,
    entity_type             varchar(50) not null,
    display_name            varchar(255) not null,
    description             text null,
    is_terminal             boolean not null default false,
    category                varchar(50) null,
    metadata                jsonb not null default '{}',
    is_active               boolean not null default true,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now()
);

create index ix_status_registry_entity on status_registry(entity_type);
create index ix_status_registry_active on status_registry(is_active);

create table decision_result_registry (
    code                    varchar(100) primary key,
    display_name            varchar(255) not null,
    description             text null,
    is_positive             boolean not null default false,
    is_negative             boolean not null default false,
    is_terminal             boolean not null default true,
    metadata                jsonb not null default '{}',
    is_active               boolean not null default true,
    created_at              timestamptz not null default now()
);

insert into decision_result_registry (code, display_name, is_positive, is_negative, is_terminal) values
    ('APPROVE', 'Согласовать', true, false, true),
    ('APPROVE_WITH_COMMENTS', 'Согласовать с замечаниями', true, false, true),
    ('REJECT', 'Отклонить', false, true, true);

-- 08_db_schema.md — Группа 4: Этапы и итерации (stage_instance, stage_iteration)

create table stage_instance (
    id                      uuid primary key,
    process_id              uuid not null references process_instance(id),
    order_idx               integer not null,
    original_order_idx      integer not null,
    name                    varchar(255) null,
    description             text null,
    stage_type              varchar(50) not null,
    duration                integer not null check (duration > 0),
    decision_mode           varchar(50) null,
    execution_order         varchar(50) null,
    is_mandatory            boolean not null default false,
    is_order_mandatory      boolean not null default false,
    allowed_return_stages   integer[] null,
    status                  varchar(100) not null,
    started_at              timestamptz null,
    due_at                  timestamptz null,
    completed_at            timestamptz null,
    created_at              timestamptz not null default now()
);

create index ix_stage_instance_process on stage_instance(process_id);
create unique index ux_stage_instance_order
    on stage_instance(process_id, order_idx);
create index ix_stage_instance_status on stage_instance(status);
create index ix_stage_instance_due on stage_instance(due_at)
    where status = 'ACTIVE';

create table stage_iteration (
    id                  uuid primary key,
    stage_id            uuid not null references stage_instance(id),
    iteration_idx       integer not null,
    status              varchar(100) not null,
    started_at          timestamptz not null default now(),
    completed_at        timestamptz null,
    created_at          timestamptz not null default now()
);

create unique index ux_stage_iteration on stage_iteration(stage_id, iteration_idx);
create index ix_stage_iteration_status on stage_iteration(status);

-- 08_db_schema.md — Группа 5: Участники (participant)

create table participant (
    id                          uuid primary key,
    stage_iteration_id          uuid not null references stage_iteration(id),
    user_id                     uuid not null,
    organization_id             uuid null,
    actor_slot_ref               uuid null references slot_template(id),
    resolved_role_ref           uuid null,
    order_idx                   integer not null default 0,
    role                        varchar(50) not null
                                check (role in ('APPROVER', 'SIGNER', 'ADDITIONAL_APPROVER', 'OBSERVER')),
    status                      varchar(100) not null,
    decision                    varchar(100) null,
    is_user_editable            boolean not null default true,
    is_organization_editable    boolean not null default false,
    is_deletable                boolean not null default false,
    assigned_at                 timestamptz null,
    due_at                      timestamptz null,
    decided_at                  timestamptz null,
    created_at                  timestamptz not null default now(),
    updated_at                  timestamptz not null default now()
);

create unique index ux_participant_iteration_user
    on participant(stage_iteration_id, user_id, role);
create index ix_participant_stage_iteration on participant(stage_iteration_id);
create index ix_participant_user on participant(user_id);
create index ix_participant_status on participant(status);
create index ix_participant_due on participant(due_at)
    where status in ('ASSIGNED', 'IN_PROGRESS');
create index ix_participant_order
    on participant(stage_iteration_id, order_idx);

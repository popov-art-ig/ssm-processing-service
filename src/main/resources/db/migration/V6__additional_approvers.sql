-- 08_db_schema.md — Группа 6: Дополнительные согласующие (additional_approver)

create table additional_approver (
    id                              uuid primary key,
    participant_id                  uuid not null references participant(id),
    parent_additional_approver_id   uuid null references additional_approver(id),
    level                           integer not null default 1,
    user_id                         uuid not null,
    organization_id                 uuid null,
    assigned_by                     varchar(50) not null
                                    check (assigned_by in ('TEMPLATE', 'INITIATOR', 'PARTICIPANT', 'ADDITIONAL_APPROVER')),
    assigned_by_user_id             uuid null,
    due_at                          timestamptz null,
    due_offset                      varchar(10) not null default 'H0'
                                    check (due_offset in ('H0', 'H3', 'H8')),
    status                          varchar(100) not null,
    recommendation                  varchar(100) null,
    is_user_editable                boolean not null default true,
    is_organization_editable        boolean not null default false,
    is_deletable                    boolean not null default true,
    assigned_at                     timestamptz not null default now(),
    recommended_at                  timestamptz null,
    created_at                      timestamptz not null default now(),
    updated_at                      timestamptz not null default now(),

    check (
        (assigned_by in ('TEMPLATE', 'INITIATOR', 'PARTICIPANT')
         and parent_additional_approver_id is null)
        or
        (assigned_by = 'ADDITIONAL_APPROVER'
         and parent_additional_approver_id is not null)
    )
);

create index ix_additional_approver_participant on additional_approver(participant_id);
create index ix_additional_approver_parent on additional_approver(parent_additional_approver_id);
create index ix_additional_approver_user on additional_approver(user_id);
create index ix_additional_approver_status on additional_approver(status);

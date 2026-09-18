-- 08_db_schema.md — Группа 9: Финальное решение (final_decision)

create table final_decision (
    id                              uuid primary key,
    process_id                      uuid not null unique references process_instance(id),
    responsible_user_id             uuid not null,
    responsible_resolved_role_ref   uuid null,
    result                          varchar(100) not null,
    comment                         text null,
    recorded_at                     timestamptz not null default now(),
    created_at                      timestamptz not null default now()
);

create index ix_final_decision_process on final_decision(process_id);
create index ix_final_decision_result on final_decision(result);

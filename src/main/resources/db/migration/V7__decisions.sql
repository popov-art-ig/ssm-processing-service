-- 08_db_schema.md — Группа 7: Решения (decision)

create table decision (
    id                  uuid primary key,
    participant_id      uuid not null references participant(id),
    result               varchar(100) not null,
    comment             text null,
    auto                boolean not null default false,
    recorded_at         timestamptz not null default now(),
    created_at          timestamptz not null default now()
);

create index ix_decision_participant on decision(participant_id);
create index ix_decision_result on decision(result);
create index ix_decision_recorded on decision(recorded_at);

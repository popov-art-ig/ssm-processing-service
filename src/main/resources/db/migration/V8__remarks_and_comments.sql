-- 08_db_schema.md — Группа 8: Замечания и комментарии (remark, comment)

create table remark (
    id                  uuid primary key,
    process_id          uuid not null references process_instance(id),
    stage_id            uuid null references stage_instance(id),
    stage_iteration_id  uuid null references stage_iteration(id),
    participant_id      uuid null references participant(id),
    author_id           uuid not null,
    author_role         varchar(50) not null,
    text                text not null,
    status              varchar(100) not null,
    activated_by        uuid null,
    activated_at        timestamptz null,
    resolved_by         uuid null,
    resolved_at         timestamptz null,
    rejected_by         uuid null,
    rejected_at         timestamptz null,
    rejection_reason    text null,
    attachments         uuid[] not null default '{}',
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index ix_remark_process on remark(process_id);
create index ix_remark_stage on remark(stage_id);
create index ix_remark_stage_iteration on remark(stage_iteration_id);
create index ix_remark_status on remark(status);
create index ix_remark_author on remark(author_id);
create index ix_remark_active on remark(process_id)
    where status in ('ACTIVE', 'IN_PROGRESS');

create table comment (
    id                  uuid primary key,
    process_id          uuid not null references process_instance(id),
    stage_id            uuid null references stage_instance(id),
    stage_iteration_id  uuid null references stage_iteration(id),
    participant_id      uuid null references participant(id),
    author_id           uuid not null,
    author_role         varchar(50) not null,
    text                text not null,
    parent_id           uuid null references comment(id),
    attachments         uuid[] not null default '{}',
    created_at          timestamptz not null default now()
);

create index ix_comment_process on comment(process_id);
create index ix_comment_stage on comment(stage_id);
create index ix_comment_stage_iteration on comment(stage_iteration_id);
create index ix_comment_author on comment(author_id);
create index ix_comment_parent on comment(parent_id);

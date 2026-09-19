-- 08_db_schema.md — Группа 3: Процессы (process_instance)

create table process_instance (
    id                                  uuid primary key,
    entity_type                         varchar(100) not null,
    entity_subtype                      varchar(100) null,
    entity_id                           uuid not null,
    document_aggregate_id               uuid null,
    revision_label                      varchar(100) null,
    parent_process_id                   uuid null references process_instance(id),
    template_ref                        uuid not null references template(id),
    process_type                        varchar(50) not null,
    config_version                      integer not null,
    status                              varchar(100) not null,
    initiator_id                        uuid not null,
    responsible_user_id                 uuid null,
    responsible_resolved_role_ref       uuid null,
    created_at                          timestamptz not null default now(),
    started_at                          timestamptz null,
    completed_at                        timestamptz null,
    archived_at                         timestamptz null,
    auto_archive_scheduled_at           timestamptz null,
    version                             integer not null default 0
);

create index ix_process_entity on process_instance(entity_type, entity_subtype, entity_id);
create index ix_process_status on process_instance(status);
create index ix_process_initiator on process_instance(initiator_id);
create index ix_process_responsible on process_instance(responsible_user_id)
    where responsible_user_id is not null;
create index ix_process_aggregate on process_instance(document_aggregate_id)
    where document_aggregate_id is not null;
create index ix_process_parent on process_instance(parent_process_id)
    where parent_process_id is not null;
create unique index ux_process_revision
    on process_instance(document_aggregate_id, revision_label)
    where document_aggregate_id is not null;
create index ix_process_auto_archive on process_instance(auto_archive_scheduled_at)
    where status in ('APPROVED', 'APPROVED_WITH_COMMENTS', 'REJECTED', 'RECALLED');

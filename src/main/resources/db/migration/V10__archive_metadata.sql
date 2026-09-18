-- 08_db_schema.md — Группа 10: Архивация (archive_metadata)

create table archive_metadata (
    process_id          uuid primary key references process_instance(id),
    archived_at         timestamptz not null default now(),
    previous_status     varchar(100) not null,
    restored_at         timestamptz null,
    restored_by         uuid null,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index ix_archive_metadata_archived on archive_metadata(archived_at);

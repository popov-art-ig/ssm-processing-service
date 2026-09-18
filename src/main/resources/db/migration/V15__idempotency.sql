-- 08_db_schema.md — Группа 16: Идемпотентность (idempotency_key)
-- Заголовок Idempotency-Key, срок хранения — 24 часа (07_api_contract.md §7).

create table idempotency_key (
    key                 varchar(255) primary key,
    request_hash        varchar(64) not null,
    response_status     integer null,
    response_body       jsonb null,
    created_at          timestamptz not null default now(),
    expires_at          timestamptz not null
);

create index ix_idempotency_key_expires on idempotency_key(expires_at);

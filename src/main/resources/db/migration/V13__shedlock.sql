-- 08_db_schema.md — Группа 14: Shedlock (распределённые блокировки планировщика, ADR-013)
-- Структура фиксирована библиотекой Shedlock 6.x (net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider).

create table shedlock (
    name        varchar(64) primary key,
    lock_until  timestamptz not null,
    locked_at   timestamptz not null,
    locked_by   varchar(255) not null
);

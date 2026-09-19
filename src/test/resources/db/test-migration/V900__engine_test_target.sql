-- Тестовая таблица-цель для интеграционных тестов TransitionEngine (PHASE-02, T7/T8).
-- Отдельная миграционная папка (classpath:db/test-migration, подключается только в
-- профиле test поверх основной схемы) — не смешивается с production-миграциями
-- classpath:db/migration.

create table engine_test_target (
    id      uuid primary key,
    status  varchar(100) not null,
    version integer not null default 0
);

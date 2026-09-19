-- PHASE-03: реестры и конфигурация перехода StartProcess (Draft -> InProgress, STANDARD).
-- Источник: doc/tickets/PHASE-03-process-service-start.md, раздел 5.

-- guard_registry (08_db_schema.md §12/16.1)
insert into guard_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('IsInitiator', 'Является инициатором', 'Текущий пользователь — инициатор процесса (G-P-001)', 'isInitiatorGuard', 'GLOBAL', '{AUTHORIZATION}', '{PROCESS}', true, now(), now()),
    ('AllMandatorySlotsFilled', 'Все обязательные слоты заполнены', 'Упрощённая реализация PHASE-03: у каждого обязательного этапа есть участник в текущей итерации (G-P-004, буквальная per-slot проверка — в фазе RouteGeneratorService)', 'allMandatorySlotsFilledGuard', 'GLOBAL', '{VALIDATION}', '{PROCESS}', true, now(), now()),
    ('AllDurationsValid', 'Все сроки корректны', 'У всех этапов срок > 0 (G-P-005)', 'allDurationsValidGuard', 'GLOBAL', '{VALIDATION}', '{PROCESS}', true, now(), now());

-- action_registry (08_db_schema.md §12/16.2)
insert into action_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('SetStartedAt', 'Установить время старта', 'Фиксирует process.startedAt (A-P-002)', 'setStartedAtAction', 'GLOBAL', '{DATA}', '{PROCESS}', true, now(), now());

-- status_registry (08_db_schema.md §5.1) — коды состояний ProcessStateMachine, задействованные в этой фазе
insert into status_registry (code, entity_type, display_name, is_terminal, is_active, created_at, updated_at)
values
    ('Draft', 'PROCESS', 'Черновик', false, true, now(), now()),
    ('InProgress', 'PROCESS', 'В процессе', false, true, now(), now());

-- state_machine_config + state_config + transition_config (08_db_schema.md §15)
insert into state_machine_config (id, version, entity_type, process_type, status, active_process_count, created_at, created_by, published_at, published_by, updated_at, version_lock)
values ('11111111-0003-0000-0000-000000000001', 1, 'PROCESS', 'STANDARD', 'PUBLISHED', 0, now(), '00000000-0000-0000-0000-000000000000', now(), '00000000-0000-0000-0000-000000000000', now(), 0);

insert into state_config (id, config_id, code, display_name, is_initial, is_terminal, metadata, created_at)
values
    ('11111111-0003-0000-0000-000000000002', '11111111-0003-0000-0000-000000000001', 'Draft', 'Черновик', true, false, '{}', now()),
    ('11111111-0003-0000-0000-000000000003', '11111111-0003-0000-0000-000000000001', 'InProgress', 'В процессе', false, false, '{}', now());

insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values (
    '11111111-0003-0000-0000-000000000004', '11111111-0003-0000-0000-000000000001',
    'StartProcess', 'Draft', 'InProgress', 'USER_ACTION',
    '{IsInitiator,AllMandatorySlotsFilled,AllDurationsValid}',
    '{SetStartedAt}',
    '{approval.process.started}',
    0, true, now()
);

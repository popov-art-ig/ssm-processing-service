-- PHASE-05: реестры и конфигурация перехода Decide (Assigned -> Decided, PARTICIPANT/STANDARD).
-- Источник: doc/tickets/PHASE-05-participant-decision.md

-- guard_registry (08_db_schema.md §16.1)
insert into guard_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('IsAssignedParticipant', 'Участник назначен', 'Текущий пользователь — назначенный участник с правом принять решение (G-PA-001)', 'isAssignedParticipantGuard', 'GLOBAL', '{VALIDATION}', '{PARTICIPANT}', true, now(), now());

-- action_registry (08_db_schema.md §16.2)
insert into action_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('RecordDecision', 'Записать решение', 'Записывает решение участника в таблицу decision (A-PA-001)', 'recordDecisionAction', 'GLOBAL', '{DATA}', '{PARTICIPANT}', true, now(), now()),
    ('RecordComment', 'Записать комментарий', 'Записывает комментарий участника, если есть (A-PA-002)', 'recordCommentAction', 'GLOBAL', '{DATA}', '{PARTICIPANT}', true, now(), now());

-- status_registry (08_db_schema.md §5.1) — коды состояний ParticipantStateMachine
insert into status_registry (code, entity_type, display_name, is_terminal, is_active, created_at, updated_at)
values
    ('Assigned', 'PARTICIPANT', 'Назначен', false, true, now(), now()),
    ('Decided', 'PARTICIPANT', 'Решение принято', false, true, now(), now());

-- state_machine_config + state_config + transition_config (08_db_schema.md §15) — PARTICIPANT/STANDARD/v1
insert into state_machine_config (id, version, entity_type, process_type, status, active_process_count, created_at, created_by, published_at, published_by, updated_at, version_lock)
values ('11111111-0005-0000-0000-000000000001', 1, 'PARTICIPANT', 'STANDARD', 'PUBLISHED', 0, now(), '00000000-0000-0000-0000-000000000000', now(), '00000000-0000-0000-0000-000000000000', now(), 0);

insert into state_config (id, config_id, code, display_name, is_initial, is_terminal, metadata, created_at)
values
    ('11111111-0005-0000-0000-000000000002', '11111111-0005-0000-0000-000000000001', 'Assigned', 'Назначен', false, false, '{}', now()),
    ('11111111-0005-0000-0000-000000000003', '11111111-0005-0000-0000-000000000001', 'Decided', 'Решение принято', false, false, '{}', now());

insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values (
    '11111111-0005-0000-0000-000000000004', '11111111-0005-0000-0000-000000000001',
    'Decide', 'Assigned', 'Decided', 'USER_ACTION',
    '{IsAssignedParticipant}',
    '{RecordDecision,RecordComment}',
    '{approval.decision.recorded}',
    0, true, now()
);

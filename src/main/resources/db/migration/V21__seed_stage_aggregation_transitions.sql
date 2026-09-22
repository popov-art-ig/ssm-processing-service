-- PHASE-06: агрегация решений и закрытие этапа (evaluateAggregation).
-- Источник: doc/tickets/PHASE-06-stage-aggregation.md

-- guard_registry (08_db_schema.md §16.1)
insert into guard_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('AllParticipantsDecided', 'Все участники приняли решение', 'Все участники текущей итерации этапа в статусе Decided или AutoApproved (G-S-002)', 'allParticipantsDecidedGuard', 'GLOBAL', '{VALIDATION}', '{STAGE}', true, now(), now()),
    ('AggregationApproved', 'Агрегация: Согласован', 'Агрегация решений даёт результат «Согласован» по режиму decisionMode (G-S-003)', 'aggregationApprovedGuard', 'GLOBAL', '{VALIDATION}', '{STAGE}', true, now(), now()),
    ('AggregationApprovedWithComments', 'Агрегация: Согласован с замечаниями', 'Агрегация решений даёт результат «Согласован с замечаниями» (G-S-004)', 'aggregationApprovedWithCommentsGuard', 'GLOBAL', '{VALIDATION}', '{STAGE}', true, now(), now()),
    ('AggregationRework', 'Агрегация: На доработку', 'Агрегация решений даёт результат «На доработку» (хотя бы один Reject) (G-S-005)', 'aggregationReworkGuard', 'GLOBAL', '{VALIDATION}', '{STAGE}', true, now(), now());

-- action_registry (08_db_schema.md §16.2)
insert into action_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('EvaluateAggregation', 'Оценить агрегацию решений', 'Проверяет все решения участников и программно запускает переход этапа (A-S-004)', 'evaluateAggregationAction', 'GLOBAL', '{ORCHESTRATION}', '{PARTICIPANT}', true, now(), now());

-- status_registry (08_db_schema.md §5.1) — коды состояний StageStateMachine
insert into status_registry (code, entity_type, display_name, is_terminal, is_active, created_at, updated_at)
values
    ('Approved', 'STAGE', 'Согласован', false, true, now(), now()),
    ('ApprovedWithComments', 'STAGE', 'Согласован с замечаниями', false, true, now(), now()),
    ('OnRework', 'STAGE', 'На доработку', false, true, now(), now());

-- Добавить EvaluateAggregation в переход Decide (из V20)
update transition_config
set actions = '{RecordDecision,RecordComment,EvaluateAggregation}'
where code = 'Decide'
  and config_id = (
      select id from state_machine_config
      where entity_type = 'PARTICIPANT' and process_type = 'STANDARD' and version = 1
  );

-- state_machine_config + state_config + transition_config (08_db_schema.md §15) — STAGE/STANDARD/v1
-- Используем существующий config, созданный в V19
-- state_config для новых статусов
insert into state_config (id, config_id, code, display_name, is_initial, is_terminal, metadata, created_at)
values
    ('11111111-0006-0000-0000-000000000002', '11111111-0004-0000-0000-000000000001', 'Approved', 'Согласован', false, false, '{}', now()),
    ('11111111-0006-0000-0000-000000000003', '11111111-0004-0000-0000-000000000001', 'ApprovedWithComments', 'Согласован с замечаниями', false, false, '{}', now()),
    ('11111111-0006-0000-0000-000000000004', '11111111-0004-0000-0000-000000000001', 'OnRework', 'На доработку', false, false, '{}', now())
on conflict do nothing;

-- transition_config — три новых перехода для закрытия этапа
insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values
    (
        '11111111-0006-0000-0000-000000000005', '11111111-0004-0000-0000-000000000001',
        'StageApproved', 'Active', 'Approved', 'SYSTEM_ACTION',
        '{AllParticipantsDecided,AggregationApproved}',
        '{}',
        '{approval.stage.completed}',
        0, true, now()
    ),
    (
        '11111111-0006-0000-0000-000000000006', '11111111-0004-0000-0000-000000000001',
        'StageApprovedWithComments', 'Active', 'ApprovedWithComments', 'SYSTEM_ACTION',
        '{AllParticipantsDecided,AggregationApprovedWithComments}',
        '{}',
        '{approval.stage.completed}',
        0, true, now()
    ),
    (
        '11111111-0006-0000-0000-000000000007', '11111111-0004-0000-0000-000000000001',
        'StageOnRework', 'Active', 'OnRework', 'SYSTEM_ACTION',
        '{AllParticipantsDecided,AggregationRework}',
        '{}',
        '{approval.stage.rejected}',
        0, true, now()
    )
on conflict do nothing;

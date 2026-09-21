-- PHASE-07: активация следующего этапа и завершение процесса.
-- Источник: doc/tickets/PHASE-07-process-completion.md

-- guard_registry (08_db_schema.md §16.1)
insert into guard_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('AllStagesCompleted', 'Все этапы завершены', 'Все этапы процесса в статусе Approved, ApprovedWithComments или Completed (G-P-006)', 'allStagesCompletedGuard', 'GLOBAL', '{VALIDATION}', '{PROCESS}', true, now(), now()),
    ('HasComments', 'Есть этапы с замечаниями', 'Хотя бы один этап процесса в статусе ApprovedWithComments (G-P-007)', 'hasCommentsGuard', 'GLOBAL', '{VALIDATION}', '{PROCESS}', true, now(), now());

-- action_registry (08_db_schema.md §16.2)
insert into action_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('ActivateNextStage', 'Активировать следующий этап', 'Находит следующий по orderIdx этап и активирует его (A-S-006)', 'activateNextStageAction', 'GLOBAL', '{ORCHESTRATION}', '{STAGE}', true, now(), now()),
    ('CompleteProcess', 'Завершить процесс', 'Устанавливает completedAt на процессе (A-P-002)', 'completeProcessAction', 'GLOBAL', '{ORCHESTRATION}', '{PROCESS}', true, now(), now()),
    ('EvaluateProcessCompletion', 'Оценить завершение процесса', 'Проверяет все этапы и программно запускает переход процесса (A-S-005)', 'evaluateProcessCompletionAction', 'GLOBAL', '{ORCHESTRATION}', '{STAGE}', true, now(), now());

-- status_registry (08_db_schema.md §5.1) — коды состояний ProcessStateMachine
insert into status_registry (code, entity_type, display_name, is_terminal, is_active, created_at, updated_at)
values
    ('Approved', 'PROCESS', 'Согласован', false, true, now(), now()),
    ('ApprovedWithComments', 'PROCESS', 'Согласован с замечаниями', false, true, now(), now());

-- state_machine_config + state_config + transition_config (08_db_schema.md §15) — PROCESS/STANDARD/v1
-- Используем существующий config (предполагаем, что он был создан в предыдущих фазах)
-- Если config ещё не существует, создаём его
insert into state_machine_config (id, version, entity_type, process_type, status, active_process_count, created_at, created_by, published_at, published_by, updated_at, version_lock)
values ('11111111-0007-0000-0000-000000000001', 1, 'PROCESS', 'STANDARD', 'PUBLISHED', 0, now(), '00000000-0000-0000-0000-000000000000', now(), '00000000-0000-0000-0000-000000000000', now(), 0)
on conflict (entity_type, process_type, version) do nothing;

-- state_config для новых статусов процесса
insert into state_config (id, config_id, code, display_name, is_initial, is_terminal, metadata, created_at)
values
    ('11111111-0007-0000-0000-000000000002', '11111111-0007-0000-0000-000000000001', 'Approved', 'Согласован', false, true, '{}', now()),
    ('11111111-0007-0000-0000-000000000003', '11111111-0007-0000-0000-000000000001', 'ApprovedWithComments', 'Согласован с замечаниями', false, true, '{}', now())
on conflict do nothing;

-- transition_config — переходы для завершения процесса
-- ВАЖНО: ProcessApprovedWithComments должен быть вставлен РАНЬШЕ ProcessApproved
-- (меньший id), чтобы TransitionEngine проверял его первым (ModelFactory сортирует по code при равном priority)
insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values
    (
        '11111111-0007-0000-0000-000000000004', '11111111-0007-0000-0000-000000000001',
        'ProcessApprovedWithComments', 'InProgress', 'ApprovedWithComments', 'SYSTEM_ACTION',
        '{AllStagesCompleted,HasComments}',
        '{CompleteProcess}',
        '{approval.process.completed}',
        0, true, now()
    ),
    (
        '11111111-0007-0000-0000-000000000005', '11111111-0007-0000-0000-000000000001',
        'ProcessApproved', 'InProgress', 'Approved', 'SYSTEM_ACTION',
        '{AllStagesCompleted}',
        '{CompleteProcess}',
        '{approval.process.completed}',
        0, true, now()
    )
on conflict do nothing;

-- Обновить переходы StageApproved и StageApprovedWithComments из V21
-- Добавить actions: ActivateNextStage, EvaluateProcessCompletion
update transition_config
set actions = '{ActivateNextStage,EvaluateProcessCompletion}'
where code = 'StageApproved'
  and config_id = (
      select id from state_machine_config
      where entity_type = 'STAGE' and process_type = 'STANDARD' and version = 1
  );

update transition_config
set actions = '{ActivateNextStage,EvaluateProcessCompletion}'
where code = 'StageApprovedWithComments'
  and config_id = (
      select id from state_machine_config
      where entity_type = 'STAGE' and process_type = 'STANDARD' and version = 1
  );

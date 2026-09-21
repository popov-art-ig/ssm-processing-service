-- V23: PHASE-08 — Возврат на доработку и возобновление процесса
-- Добавляет переходы ProcessRework (InProgress → OnRework) и ResumeProcess (OnRework → InProgress),
-- регистрирует guards и actions для работы с возвратом на доработку.
-- Источник: doc/tickets/PHASE-08-process-resume.md

-- 1. guard_registry (08_db_schema.md §16.1)
insert into guard_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('HasStageOnRework', 'Есть этап на доработке', 'Хотя бы один этап процесса в статусе OnRework (G-P-007)', 'hasStageOnReworkGuard', 'GLOBAL', '{VALIDATION}', '{PROCESS}', true, now(), now()),
    ('AllRemarksProcessed', 'Все замечания обработаны', 'Все замечания процесса обработаны (G-P-008, PHASE-08: заглушка, всегда true)', 'allRemarksProcessedGuard', 'GLOBAL', '{VALIDATION}', '{PROCESS}', true, now(), now()),
    ('IsTargetStageAllowed', 'Целевой этап допустим', 'Целевой этап входит в список allowedReturnStages (G-P-009, PHASE-08: упрощение)', 'isTargetStageAllowedGuard', 'GLOBAL', '{VALIDATION}', '{PROCESS}', true, now(), now());

-- 2. action_registry (08_db_schema.md §16.2)
insert into action_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('EvaluateProcessRework', 'Оценить возврат на доработку', 'Триггер ProcessRework: переводит процесс в OnRework при появлении этапа OnRework', 'evaluateProcessReworkAction', 'GLOBAL', '{ORCHESTRATION}', '{STAGE}', true, now(), now()),
    ('RejectStagesFrom', 'Отклонить этапы', 'A-RT-002: Присваивает статус Rejected этапам между целевым и текущим', 'rejectStagesFromAction', 'GLOBAL', '{ORCHESTRATION}', '{PROCESS}', true, now(), now()),
    ('ActivateTargetStage', 'Активировать целевой этап', 'A-RT-005: Создаёт новую итерацию целевого этапа, клонирует участников, активирует', 'activateTargetStageAction', 'GLOBAL', '{ORCHESTRATION}', '{PROCESS}', true, now(), now());

-- 3. status_registry (08_db_schema.md §5.1) — коды состояний
insert into status_registry (code, entity_type, display_name, is_terminal, is_active, created_at, updated_at)
values
    ('OnRework', 'PROCESS', 'На доработке', false, true, now(), now()),
    ('Rejected', 'STAGE', 'Отклонён при возврате', false, true, now(), now());

-- 4. state_machine_config для PROCESS (создан в V18, переиспользуем)
-- state_config для новых статусов процесса
insert into state_config (id, config_id, code, display_name, is_initial, is_terminal, metadata, created_at)
values
    ('11111111-0008-0000-0000-000000000001', '11111111-0003-0000-0000-000000000001', 'OnRework', 'На доработке', false, false, '{}', now())
on conflict do nothing;

-- 5. transition_config — переход ProcessRework (InProgress → OnRework)
insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values
    (
        '11111111-0008-0000-0000-000000000002', '11111111-0003-0000-0000-000000000001',
        'ProcessRework', 'InProgress', 'OnRework', 'SYSTEM_ACTION',
        '{HasStageOnRework}',
        '{}',
        '{approval.process.on_rework}',
        0, true, now()
    )
on conflict do nothing;

-- 6. transition_config — переход ResumeProcess (OnRework → InProgress)
insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values
    (
        '11111111-0008-0000-0000-000000000003', '11111111-0003-0000-0000-000000000001',
        'ResumeProcess', 'OnRework', 'InProgress', 'USER_ACTION',
        '{IsInitiator,AllRemarksProcessed,IsTargetStageAllowed}',
        '{RejectStagesFrom,ActivateTargetStage}',
        '{approval.process.resumed}',
        0, true, now()
    )
on conflict do nothing;

-- 7. Обновление перехода StageOnRework: добавление триггера EvaluateProcessRework
update transition_config
set actions = '{EvaluateProcessRework}'
where code = 'StageOnRework'
  and config_id = (
      select id from state_machine_config
      where entity_type = 'STAGE' and process_type = 'STANDARD' and version = 1
  )
  and not ('EvaluateProcessRework' = ANY(actions));

-- 8. state_config для STAGE — добавляем статус Rejected
insert into state_config (id, config_id, code, display_name, is_initial, is_terminal, metadata, created_at)
values
    ('11111111-0008-0000-0000-000000000004', '11111111-0006-0000-0000-000000000001', 'Rejected', 'Отклонён', false, true, '{}', now())
on conflict do nothing;

-- 9. transition_config — переход ReactivateStage (OnRework → Active) для этапов
-- Используется в ActivateTargetStageAction для активации этапа после возврата
insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values
    (
        '11111111-0008-0000-0000-000000000005', '11111111-0006-0000-0000-000000000001',
        'ReactivateStage', 'OnRework', 'Active', 'SYSTEM_ACTION',
        '{AllMandatorySlotsFilled,AllDurationsValid}',
        '{AssignStageTasks}',
        '{approval.stage.activated}',
        0, true, now()
    )
on conflict do nothing;

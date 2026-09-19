-- PHASE-04: реестры и конфигурация перехода ActivateStage (Pending -> Active, STANDARD),
-- плюс обновление actions перехода StartProcess (Фаза 3), чтобы он вызывал активацию первого
-- этапа. Источник: doc/tickets/PHASE-04-stage-activation.md, разделы 1, 5.

-- guard_registry (08_db_schema.md §12/16.1)
insert into guard_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('PreviousStageCompleted', 'Предыдущий этап завершён', 'Предыдущий этап имеет финальный статус; для этапа без предыдущего (минимальный orderIdx) — всегда true (G-S-001)', 'previousStageCompletedGuard', 'GLOBAL', '{VALIDATION}', '{STAGE}', true, now(), now());

-- action_registry (08_db_schema.md §12/16.2)
insert into action_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('AssignParticipantTasks', 'Назначить задачи участникам', 'Упрощённая реализация PHASE-04: работает с участниками текущей (последней по iterationIdx) итерации этапа, создание новой итерации при активации не реализовано (требует RouteGeneratorService) (A-S-002... A-S-001 по нумерации спеки — см. тикет §4)', 'assignParticipantTasksAction', 'GLOBAL', '{DATA}', '{STAGE}', true, now(), now()),
    ('SetStageStartedAt', 'Установить время старта этапа', 'Фиксирует stage.startedAt (A-S-002)', 'setStageStartedAtAction', 'GLOBAL', '{DATA}', '{STAGE}', true, now(), now()),
    ('CalcStageDueAt', 'Вычислить срок этапа', 'stage.dueAt = stage.startedAt + stage.duration дней (A-S-003; единица измерения duration принята как дни — см. тикет §5, открытый вопрос №2)', 'calcStageDueAtAction', 'GLOBAL', '{DATA}', '{STAGE}', true, now(), now()),
    ('AssignStageTasks', 'Активировать первый этап', 'Активирует первый этап маршрута (минимальный orderIdx) через вложенный явный переход ActivateStage движка STAGE-конфига, а не прямой записью stage.status (ADR-002/ADR-028) (A-P-001)', 'assignStageTasksAction', 'GLOBAL', '{ORCHESTRATION}', '{PROCESS}', true, now(), now());

-- status_registry (08_db_schema.md §5.1) — коды состояний StageStateMachine, задействованные в этой фазе
insert into status_registry (code, entity_type, display_name, is_terminal, is_active, created_at, updated_at)
values
    ('Pending', 'STAGE', 'Ожидает', false, true, now(), now()),
    ('Active', 'STAGE', 'Активен', false, true, now(), now());

-- state_machine_config + state_config + transition_config (08_db_schema.md §15) — STAGE/STANDARD/v1
insert into state_machine_config (id, version, entity_type, process_type, status, active_process_count, created_at, created_by, published_at, published_by, updated_at, version_lock)
values ('11111111-0004-0000-0000-000000000001', 1, 'STAGE', 'STANDARD', 'PUBLISHED', 0, now(), '00000000-0000-0000-0000-000000000000', now(), '00000000-0000-0000-0000-000000000000', now(), 0);

insert into state_config (id, config_id, code, display_name, is_initial, is_terminal, metadata, created_at)
values
    ('11111111-0004-0000-0000-000000000002', '11111111-0004-0000-0000-000000000001', 'Pending', 'Ожидает', true, false, '{}', now()),
    ('11111111-0004-0000-0000-000000000003', '11111111-0004-0000-0000-000000000001', 'Active', 'Активен', false, false, '{}', now());

insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values (
    '11111111-0004-0000-0000-000000000004', '11111111-0004-0000-0000-000000000001',
    'ActivateStage', 'Pending', 'Active', 'SYSTEM_ACTION',
    '{PreviousStageCompleted}',
    '{AssignParticipantTasks,SetStageStartedAt,CalcStageDueAt}',
    '{approval.stage.activated}',
    0, true, now()
);

-- Фаза 3 (V18): StartProcess теперь активирует первый этап маршрута (AssignStageTasks первым,
-- как в примере §9.1 спеки). Обновление in-place, не новая версия конфига — на момент этой
-- фазы ни одного реального работающего процесса ни в одной среде нет (см. тикет §3).
update transition_config
set actions = '{AssignStageTasks,SetStartedAt}'
where code = 'StartProcess';

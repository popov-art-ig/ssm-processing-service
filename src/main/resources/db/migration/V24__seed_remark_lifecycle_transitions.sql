-- V24: PHASE-11 — Замечания и комментарии (Remark lifecycle)
-- Добавляет state machine для замечаний, guards, actions и переходы для управления жизненным циклом замечаний.
-- Источник: doc/tickets/PHASE-11-remarks-lifecycle.md

-- 1. guard_registry (08_db_schema.md §16.1)
insert into guard_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('IsParticipant', 'Является участником процесса', 'Текущий пользователь — участник процесса (G-R-001, PHASE-11: заглушка)', 'isParticipantGuard', 'GLOBAL', '{AUTHORIZATION}', '{REMARK}', true, now(), now()),
    ('IsRemarkAuthor', 'Является автором замечания', 'Текущий пользователь — автор замечания (G-R-002)', 'isRemarkAuthorGuard', 'GLOBAL', '{AUTHORIZATION}', '{REMARK}', true, now(), now()),
    ('IsRemarkAssignee', 'Является ответственным за замечание', 'Текущий пользователь — ответственный за обработку замечания (G-R-003)', 'isRemarkAssigneeGuard', 'GLOBAL', '{AUTHORIZATION}', '{REMARK}', true, now(), now()),
    ('HasUnprocessedRemarks', 'Есть необработанные замечания', 'У процесса есть замечания в статусах Open или InProgress (G-P-010)', 'hasUnprocessedRemarksGuard', 'GLOBAL', '{VALIDATION}', '{PROCESS}', true, now(), now());

-- 2. action_registry (08_db_schema.md §16.2)
insert into action_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('AssignRemarkToAuthor', 'Назначить замечание автору', 'A-R-001: Устанавливает assigneeId = authorId при создании замечания', 'assignRemarkToAuthorAction', 'GLOBAL', '{ORCHESTRATION}', '{REMARK}', true, now(), now()),
    ('NotifyRemarkStatusChange', 'Уведомление об изменении статуса', 'A-R-002: Публикация события об изменении статуса замечания (PHASE-11: заглушка)', 'notifyRemarkStatusChangeAction', 'GLOBAL', '{NOTIFICATION}', '{REMARK}', true, now(), now());

-- 3. status_registry — коды состояний для замечаний
insert into status_registry (code, entity_type, display_name, is_terminal, is_active, created_at, updated_at)
values
    ('Draft', 'REMARK', 'Черновик', false, true, now(), now()),
    ('Open', 'REMARK', 'Открыто', false, true, now(), now()),
    ('InProgress', 'REMARK', 'В обработке', false, true, now(), now()),
    ('Processed', 'REMARK', 'Обработано', true, true, now(), now()),
    ('Rejected', 'REMARK', 'Отклонено', true, true, now(), now());

-- 4. state_machine_config для REMARK
insert into state_machine_config (id, entity_type, process_type, version, lifecycle_status, created_by, created_at, updated_by, updated_at, config_version)
values
    ('11111111-0011-0000-0000-000000000001', 'REMARK', 'STANDARD', 1, 'PUBLISHED', '00000000-0000-0000-0000-000000000000', now(), '00000000-0000-0000-0000-000000000000', now(), 0);

-- 5. state_config для REMARK
insert into state_config (id, config_id, code, display_name, is_initial, is_terminal, metadata, created_at)
values
    ('11111111-0011-0000-0000-000000000002', '11111111-0011-0000-0000-000000000001', 'Draft', 'Черновик', true, false, '{}', now()),
    ('11111111-0011-0000-0000-000000000003', '11111111-0011-0000-0000-000000000001', 'Open', 'Открыто', false, false, '{}', now()),
    ('11111111-0011-0000-0000-000000000004', '11111111-0011-0000-0000-000000000001', 'InProgress', 'В обработке', false, false, '{}', now()),
    ('11111111-0011-0000-0000-000000000005', '11111111-0011-0000-0000-000000000001', 'Processed', 'Обработано', false, true, '{}', now()),
    ('11111111-0011-0000-0000-000000000006', '11111111-0011-0000-0000-000000000001', 'Rejected', 'Отклонено', false, true, '{}', now());

-- 6. transition_config — переходы для замечаний
-- CreateRemark: Draft → Open
insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values
    (
        '11111111-0011-0000-0000-000000000007', '11111111-0011-0000-0000-000000000001',
        'CreateRemark', 'Draft', 'Open', 'USER_ACTION',
        '{IsParticipant}',
        '{AssignRemarkToAuthor,NotifyRemarkStatusChange}',
        '{approval.remark.created}',
        0, true, now()
    );

-- StartProcessingRemark: Open → InProgress
insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values
    (
        '11111111-0011-0000-0000-000000000008', '11111111-0011-0000-0000-000000000001',
        'StartProcessingRemark', 'Open', 'InProgress', 'USER_ACTION',
        '{IsRemarkAssignee}',
        '{NotifyRemarkStatusChange}',
        '{approval.remark.in_progress}',
        0, true, now()
    );

-- ProcessRemark: InProgress → Processed
insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values
    (
        '11111111-0011-0000-0000-000000000009', '11111111-0011-0000-0000-000000000001',
        'ProcessRemark', 'InProgress', 'Processed', 'USER_ACTION',
        '{IsRemarkAssignee}',
        '{NotifyRemarkStatusChange}',
        '{approval.remark.processed}',
        0, true, now()
    );

-- RejectRemark: InProgress → Rejected
insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values
    (
        '11111111-0011-0000-0000-000000000010', '11111111-0011-0000-0000-000000000001',
        'RejectRemark', 'InProgress', 'Rejected', 'USER_ACTION',
        '{IsRemarkAuthor}',
        '{NotifyRemarkStatusChange}',
        '{approval.remark.rejected}',
        0, true, now()
    );

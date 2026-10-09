-- PHASE-11: Замечания и комментарии (Remark lifecycle).
-- Источник: doc/tickets/PHASE-11-remarks-lifecycle.md, doc/arch/05_guards_actions_registry.md §5.7.
--
-- Карта переходов замечания задана так, чтобы на каждую пару (from_state, USER_ACTION)
-- приходился ровно ОДИН активный переход. ModelFactory сортирует кандидатов по priority, затем
-- по code перехода, поэтому два перехода из одного состояния с разными to_state и совпадающими
-- предикатами приводили бы к недетерминированному выбору (побеждал бы лексикографически
-- меньший code, а не тот, который подразумевал вызывающий код).
--
-- Жизненный цикл:
--   Draft      --CreateRemark----------> Active        (A-R-001 MarkActivated)
--   Active     --StartProcessingRemark-> InProgress    (A-R-007 TakeIntoWork)
--   InProgress --ProcessRemark--------> Processed     (A-R-004 MarkResolved)
--   InProgress --RejectRemark---------> Rejected      (A-R-005 MarkRejected)
--   Proposed   --MarkNotRequired------> NotRequired   (A-R-002)
--
-- ВАЖНО про состояние InProgress. Из него доступны ДВА перехода, и оба предиката прав истинны
-- для одного и того же пользователя: AssignRemarkToAuthorAction назначает ответственным автора
-- замечания, поэтому и IsRemarkAssignee, и IsRemarkAuthor проходят для автора. ModelFactory
-- сортирует кандидатов по priority, затем по code перехода, и «ProcessRemark» лексикографически
-- меньше «RejectRemark» — то есть вызов rejectRemark уходил бы в ProcessRemark и замечание
-- получало бы статус Processed. Поэтому финальный исход выбирает вызывающий код:
-- RemarkService.processRemark переводит замечание в InProgress (takeIntoWork), а затем
-- RejectRemark применяется ТОЛЬКО при явном вызове rejectRemark — сервис для этого вызывает
-- takeIntoWork и следом отдельный переход. Чтобы выбор был однозначным, в карте оставлены оба
-- перехода из InProgress, но сервис никогда не вызывает их «наугад»: оба его метода сначала
-- детерминированно доводят замечание до InProgress, а затем запрашивают ровно один нужный код.
-- Ниже (V24) ProcessRemark из InProgress СОХРАНЁН намеренно: без него processRemark не смог бы
-- закрыть замечание, уже взятое в работу.

-- 1. guard_registry (08_db_schema.md §16.1)
insert into guard_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('IsParticipant', 'Является участником процесса', 'Текущий пользователь — участник процесса (G-R-001, PHASE-11: заглушка)', 'isParticipantGuard', 'GLOBAL', '{AUTHORIZATION}', '{REMARK}', true, now(), now()),
    ('IsRemarkAuthor', 'Является автором замечания', 'Текущий пользователь — автор замечания (G-R-002)', 'isRemarkAuthorGuard', 'GLOBAL', '{AUTHORIZATION}', '{REMARK}', true, now(), now()),
    ('IsRemarkAssignee', 'Является ответственным за замечание', 'Текущий пользователь — ответственный за обработку замечания (G-R-003)', 'isRemarkAssigneeGuard', 'GLOBAL', '{AUTHORIZATION}', '{REMARK}', true, now(), now()),
    ('IsRemarkRejection', 'Вызов является отклонением замечания', 'В параметрах перехода передан reason, то есть вызывающий код отклоняет замечание, а не обрабатывает его', 'isRemarkRejectionGuard', 'GLOBAL', '{VALIDATION}', '{REMARK}', true, now(), now()),
    ('IsRemarkResolution', 'Вызов является обработкой замечания', 'В параметрах перехода передан resolution, то есть вызывающий код обрабатывает замечание, а не отклоняет его', 'isRemarkResolutionGuard', 'GLOBAL', '{VALIDATION}', '{REMARK}', true, now(), now()),
    ('HasUnprocessedRemarks', 'Есть необработанные замечания', 'У процесса есть замечания в статусах Open или InProgress (G-P-010)', 'hasUnprocessedRemarksGuard', 'GLOBAL', '{VALIDATION}', '{PROCESS}', true, now(), now());

-- 2. action_registry (08_db_schema.md §16.2)
insert into action_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('AssignRemarkToAuthor', 'Назначить замечание автору', 'A-R-001: Устанавливает activatedBy = authorId при создании замечания', 'assignRemarkToAuthorAction', 'GLOBAL', '{ORCHESTRATION}', '{REMARK}', true, now(), now()),
    ('NotifyRemarkStatusChange', 'Уведомление об изменении статуса', 'A-R-002: Публикация события об изменении статуса замечания (PHASE-11: заглушка)', 'notifyRemarkStatusChangeAction', 'GLOBAL', '{NOTIFICATION}', '{REMARK}', true, now(), now());

-- 3. status_registry (08_db_schema.md §5.1) — коды состояний RemarkStateMachine.
-- PK составной (code, entity_type), поэтому Draft/InProgress/Active регистрируются для REMARK
-- независимо от одноимённых статусов PROCESS и STAGE (V1).
insert into status_registry (code, entity_type, display_name, is_terminal, is_active, created_at, updated_at)
values
    ('Draft', 'REMARK', 'Черновик', false, true, now(), now()),
    ('Active', 'REMARK', 'Действующее', false, true, now(), now()),
    ('InProgress', 'REMARK', 'В обработке', false, true, now(), now()),
    ('Processed', 'REMARK', 'Обработано', true, true, now(), now()),
    ('Rejected', 'REMARK', 'Отклонено', true, true, now(), now()),
    ('Proposed', 'REMARK', 'Предложено', false, true, now(), now()),
    ('NotRequired', 'REMARK', 'Не требуется', true, true, now(), now())
on conflict (code, entity_type) do nothing;

-- 4. state_machine_config для REMARK
insert into state_machine_config (id, entity_type, process_type, version, status, active_process_count, created_by, created_at, published_at, published_by, updated_at, version_lock)
values
    ('11111111-0011-0000-0000-000000000001', 'REMARK', 'STANDARD', 1, 'PUBLISHED', 0, '00000000-0000-0000-0000-000000000000', now(), now(), '00000000-0000-0000-0000-000000000000', now(), 0)
on conflict (entity_type, process_type, version) do nothing;

-- 5. state_config для REMARK
insert into state_config (id, config_id, code, display_name, is_initial, is_terminal, metadata, created_at)
values
    ('11111111-0011-0000-0000-000000000002', '11111111-0011-0000-0000-000000000001', 'Draft', 'Черновик', true, false, '{}', now()),
    ('11111111-0011-0000-0000-000000000003', '11111111-0011-0000-0000-000000000001', 'Active', 'Действующее', false, false, '{}', now()),
    ('11111111-0011-0000-0000-000000000004', '11111111-0011-0000-0000-000000000001', 'InProgress', 'В обработке', false, false, '{}', now()),
    ('11111111-0011-0000-0000-000000000005', '11111111-0011-0000-0000-000000000001', 'Processed', 'Обработано', false, true, '{}', now()),
    ('11111111-0011-0000-0000-000000000006', '11111111-0011-0000-0000-000000000001', 'Rejected', 'Отклонено', false, true, '{}', now()),
    ('11111111-0011-0000-0000-000000000011', '11111111-0011-0000-0000-000000000001', 'Proposed', 'Предложено', false, false, '{}', now()),
    ('11111111-0011-0000-0000-000000000012', '11111111-0011-0000-0000-000000000001', 'NotRequired', 'Не требуется', false, true, '{}', now())
on conflict do nothing;

-- 6. transition_config — по одному переходу на пару (from_state, USER_ACTION)
insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values
    (
        '11111111-0011-0000-0000-000000000007', '11111111-0011-0000-0000-000000000001',
        'CreateRemark', 'Draft', 'Active', 'USER_ACTION',
        '{IsParticipant}',
        '{AssignRemarkToAuthor,NotifyRemarkStatusChange}',
        '{approval.remark.created}',
        0, true, now()
    ),
    (
        '11111111-0011-0000-0000-000000000008', '11111111-0011-0000-0000-000000000001',
        'StartProcessingRemark', 'Active', 'InProgress', 'USER_ACTION',
        '{IsRemarkAssignee}',
        '{NotifyRemarkStatusChange}',
        '{approval.remark.activated}',
        0, true, now()
    ),
    (
        '11111111-0011-0000-0000-000000000009', '11111111-0011-0000-0000-000000000001',
        'ProcessRemark', 'InProgress', 'Processed', 'USER_ACTION',
        '{IsRemarkResolution}',
        '{NotifyRemarkStatusChange}',
        '{approval.remark.resolved}',
        0, true, now()
    ),
    (
        '11111111-0011-0000-0000-000000000010', '11111111-0011-0000-0000-000000000001',
        'RejectRemark', 'InProgress', 'Rejected', 'USER_ACTION',
        '{IsRemarkRejection}',
        '{NotifyRemarkStatusChange}',
        '{approval.remark.rejected}',
        0, true, now()
    ),
    (
        '11111111-0011-0000-0000-000000000013', '11111111-0011-0000-0000-000000000001',
        'MarkNotRequired', 'Proposed', 'NotRequired', 'USER_ACTION',
        '{IsParticipant}',
        '{NotifyRemarkStatusChange}',
        '{approval.remark.marked-not-required}',
        0, true, now()
    )
on conflict do nothing;

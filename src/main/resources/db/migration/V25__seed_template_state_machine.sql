-- V25: PHASE-14 — Управление шаблонами маршрутов (Template Management)
-- Добавляет state machine для шаблонов, guards, actions и переходы жизненного цикла.
-- Источник: doc/tickets/PHASE-14-template-management.md
--
-- ВАЖНО: status_registry.code — глобальный первичный ключ, поэтому статусы шаблона
-- регистрируются под уникальными кодами (Draft/Published/... уже заняты PROCESS/REMARK).
-- Template.status хранится через @Enumerated(STRING) как LifecycleStatus (DRAFT/PUBLISHED/...),
-- поэтому коды state machine совпадают с именами enum: DRAFT/PUBLISHED/DEPRECATED/ARCHIVED.

-- 1. Расширить CHECK-констрейнт entity_type на TEMPLATE
alter table state_machine_config drop constraint if exists state_machine_config_entity_type_check;
alter table state_machine_config add constraint state_machine_config_entity_type_check
    check (entity_type in ('PROCESS', 'STAGE', 'STAGE_ITERATION', 'PARTICIPANT', 'ADDITIONAL_APPROVER', 'FINAL_DECISION', 'REMARK', 'TEMPLATE'));

-- 2. guard_registry
insert into guard_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('AllMandatorySlotsValid', 'Все обязательные слоты валидны', 'Все обязательные слоты имеют userId или acceptableRoles (PHASE-14)', 'allMandatorySlotsValid', 'GLOBAL', '{VALIDATION}', '{TEMPLATE}', true, now(), now()),
    ('NoActiveProcesses', 'Нет активных процессов', 'Нет активных процессов на этом шаблоне (PHASE-14)', 'noActiveProcesses', 'GLOBAL', '{VALIDATION}', '{TEMPLATE}', true, now(), now())
on conflict (code) do nothing;

-- 3. action_registry
insert into action_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('ValidateTemplateStructure', 'Валидировать структуру шаблона', 'Проверяет структуру шаблона перед публикацией (PHASE-14)', 'validateTemplateStructure', 'GLOBAL', '{VALIDATION}', '{TEMPLATE}', true, now(), now()),
    ('SetPublishedTimestamp', 'Установить время публикации', 'Устанавливает publishedAt и publishedBy (PHASE-14)', 'setPublishedTimestamp', 'GLOBAL', '{DATA}', '{TEMPLATE}', true, now(), now()),
    ('SetArchivedTimestamp', 'Установить время архивации', 'Устанавливает updatedAt при архивации (PHASE-14)', 'setArchivedTimestamp', 'GLOBAL', '{DATA}', '{TEMPLATE}', true, now(), now())
on conflict (code) do nothing;

-- 4. status_registry — статусы TEMPLATE (коды совпадают с LifecycleStatus enum)
insert into status_registry (code, entity_type, display_name, is_terminal, is_active, created_at, updated_at)
values
    ('DRAFT', 'TEMPLATE', 'Черновик', false, true, now(), now()),
    ('PUBLISHED', 'TEMPLATE', 'Опубликован', false, true, now(), now()),
    ('DEPRECATED', 'TEMPLATE', 'Снят с публикации', false, true, now(), now()),
    ('ARCHIVED', 'TEMPLATE', 'Архивирован', true, true, now(), now())
on conflict (code) do nothing;

-- 5. state_machine_config для TEMPLATE (process_type = NULL — шаблоны обоих типов процессов)
insert into state_machine_config (id, version, entity_type, process_type, status, active_process_count, created_at, created_by, published_at, published_by, updated_at, version_lock)
values
    ('11111111-0014-0000-0000-000000000001', 1, 'TEMPLATE', null, 'PUBLISHED', 0, now(), '00000000-0000-0000-0000-000000000000', now(), '00000000-0000-0000-0000-000000000000', now(), 0)
on conflict do nothing;

-- 6. state_config для TEMPLATE
insert into state_config (id, config_id, code, display_name, is_initial, is_terminal, metadata, created_at)
values
    ('11111111-0014-0000-0000-000000000002', '11111111-0014-0000-0000-000000000001', 'DRAFT', 'Черновик', true, false, '{}', now()),
    ('11111111-0014-0000-0000-000000000003', '11111111-0014-0000-0000-000000000001', 'PUBLISHED', 'Опубликован', false, false, '{}', now()),
    ('11111111-0014-0000-0000-000000000004', '11111111-0014-0000-0000-000000000001', 'DEPRECATED', 'Снят с публикации', false, false, '{}', now()),
    ('11111111-0014-0000-0000-000000000005', '11111111-0014-0000-0000-000000000001', 'ARCHIVED', 'Архивирован', false, true, '{}', now())
on conflict do nothing;

-- 7. transition_config — переходы жизненного цикла шаблона
insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values
    (
        '11111111-0014-0000-0000-000000000006', '11111111-0014-0000-0000-000000000001',
        'PublishTemplate', 'DRAFT', 'PUBLISHED', 'USER_ACTION',
        '{AllMandatorySlotsValid}',
        '{ValidateTemplateStructure,SetPublishedTimestamp}',
        '{template.published}',
        0, true, now()
    ),
    (
        '11111111-0014-0000-0000-000000000007', '11111111-0014-0000-0000-000000000001',
        'DeprecateTemplate', 'PUBLISHED', 'DEPRECATED', 'USER_ACTION',
        '{}',
        '{}',
        '{template.deprecated}',
        0, true, now()
    ),
    (
        '11111111-0014-0000-0000-000000000008', '11111111-0014-0000-0000-000000000001',
        'ArchiveTemplate', 'DEPRECATED', 'ARCHIVED', 'USER_ACTION',
        '{NoActiveProcesses}',
        '{SetArchivedTimestamp}',
        '{template.archived}',
        0, true, now()
    )
on conflict do nothing;

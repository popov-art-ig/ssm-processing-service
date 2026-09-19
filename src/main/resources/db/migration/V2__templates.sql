-- 08_db_schema.md — Группа 2: Шаблоны маршрутов (template, stage_template, slot_template, applicability_rule)

create table template (
    id                            uuid primary key,
    name                          varchar(255) not null,
    process_type                  varchar(50) not null
                                  check (process_type in ('STANDARD', 'UNIFIED')),
    status                        varchar(50) not null
                                  check (status in ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED')),
    version                       integer not null default 1,
    parent_template_id            uuid null references template(id),
    process_iteration_enabled     boolean not null default false,
    responsible_roles             uuid[] null,
    requires_responsible_approval boolean not null default false,
    created_at                    timestamptz not null default now(),
    created_by                    uuid not null,
    updated_at                    timestamptz not null default now(),
    updated_by                    uuid null,
    published_at                  timestamptz null,
    published_by                  uuid null
);

create unique index ux_template_name_version
    on template(name, version)
    where status in ('DRAFT', 'PUBLISHED');

create index ix_template_status on template(status);
create index ix_template_process_type on template(process_type);
create index ix_template_parent on template(parent_template_id);

create table stage_template (
    id                      uuid primary key,
    template_id             uuid not null references template(id),
    order_idx               integer not null,
    name                    varchar(255) null,
    description             text null,
    stage_type              varchar(50) not null
                            check (stage_type in ('APPROVAL', 'SIGNING', 'ENDORSEMENT')),
    duration                integer null check (duration > 0),
    decision_mode           varchar(50) null
                            check (decision_mode in ('AND', 'ANY_APPROVE', 'ANY_REJECT', 'ANY_DECISION', 'FIRST_REJECT_FAIL_FAST')),
    execution_order         varchar(50) null
                            check (execution_order in ('PARALLEL', 'SEQUENTIAL')),
    is_mandatory            boolean not null default false,
    is_order_mandatory      boolean not null default false,
    allowed_return_stages   integer[] null,
    created_at              timestamptz not null default now()
);

create index ix_stage_template_template on stage_template(template_id);
create unique index ux_stage_template_order
    on stage_template(template_id, order_idx);

create table slot_template (
    id                          uuid primary key,
    parent_slot_id              uuid null references slot_template(id),
    stage_template_id           uuid null references stage_template(id),
    slot_type                   varchar(50) not null
                                check (slot_type in ('ACTOR', 'ADDITIONAL_APPROVER')),
    order_idx                   integer not null,
    user_id                     uuid null,
    organization_id             uuid null,
    acceptable_roles            uuid[] not null default '{}',
    required                    boolean not null default true,
    is_user_editable            boolean not null default true,
    is_organization_editable    boolean not null default false,
    is_deletable                boolean not null default false,
    due_offset                  varchar(10) null
                                check (due_offset in ('H0', 'H3', 'H8')),
    created_at                  timestamptz not null default now(),

    check (is_organization_editable = false or is_user_editable = true),
    check (
        (slot_type = 'ACTOR'
         and parent_slot_id is null
         and stage_template_id is not null)
        or
        (slot_type = 'ADDITIONAL_APPROVER'
         and parent_slot_id is not null
         and stage_template_id is null)
    )
);

create index ix_slot_template_parent on slot_template(parent_slot_id);
create index ix_slot_template_stage on slot_template(stage_template_id);
create index ix_slot_template_type on slot_template(slot_type);

create table applicability_rule (
    id                      uuid primary key,
    template_id             uuid not null references template(id),
    rule_idx                integer not null,
    entity_types            text[] not null default '{}',
    entity_subtypes         text[] not null default '{}',
    attribute_conditions    jsonb not null default '{}',
    created_at              timestamptz not null default now()
);

create index ix_applicability_rule_template on applicability_rule(template_id);

create table if not exists workflow_task (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    task_type varchar(64) not null,
    task_status varchar(32) not null default 'PENDING',
    source_event_id varchar(64) not null,
    assigned_to varchar(64),
    review_comment varchar(512),
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint uk_workflow_task_source unique (source_event_id, task_type)
);

create index if not exists idx_workflow_task_tenant_status_created
    on workflow_task (tenant_id, task_status, created_at desc);

create index if not exists idx_workflow_task_order
    on workflow_task (tenant_id, order_id);

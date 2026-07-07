create table if not exists decoction_task_event (
    id uuid primary key,
    task_id uuid not null references decoction_task(id),
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    event_type varchar(32) not null,
    operation_id varchar(128) not null unique,
    operator varchar(64) not null,
    event_payload jsonb not null default '{}'::jsonb,
    event_time timestamptz not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_decoction_task_event_task_created
    on decoction_task_event (task_id, created_at desc);

create index if not exists idx_decoction_task_event_type_created
    on decoction_task_event (event_type, created_at desc);

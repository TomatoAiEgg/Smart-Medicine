create table if not exists decoction_device_work_record (
    id uuid primary key,
    task_id uuid not null references decoction_task(id),
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    task_no varchar(64) not null,
    prescription_no varchar(64) not null,
    device_code varchar(64) not null,
    pail_no varchar(64),
    action_type varchar(32) not null,
    action_result varchar(16) not null,
    task_status_before varchar(32),
    task_status_after varchar(32),
    operation_id varchar(128) not null unique,
    source varchar(64) not null,
    operator varchar(64) not null,
    detail_payload jsonb not null default '{}'::jsonb,
    action_time timestamptz not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_decoction_device_work_record_task_created
    on decoction_device_work_record (task_id, created_at desc);

create index if not exists idx_decoction_device_work_record_device_created
    on decoction_device_work_record (device_code, created_at desc);

create index if not exists idx_decoction_device_work_record_action_created
    on decoction_device_work_record (action_type, action_result, created_at desc);

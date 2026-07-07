create table if not exists decoction_task (
    id uuid primary key,
    task_no varchar(64) not null unique,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    prescription_id uuid not null references prescription(id),
    prescription_no varchar(64) not null,
    device_code varchar(64) not null,
    pail_no varchar(64),
    task_status varchar(32) not null,
    bind_operation_id varchar(128) not null unique,
    start_operation_id varchar(128) unique,
    finish_operation_id varchar(128) unique,
    operator varchar(64) not null,
    started_at timestamptz,
    finished_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_decoction_task_prescription
    on decoction_task (tenant_id, prescription_id, created_at desc);

create index if not exists idx_decoction_task_status_created
    on decoction_task (task_status, created_at desc);

create unique index if not exists uk_decoction_task_prescription_active
    on decoction_task (tenant_id, prescription_id)
    where task_status in ('BOUND', 'DECOCTING');

create unique index if not exists uk_decoction_task_device_active
    on decoction_task (device_code)
    where task_status in ('BOUND', 'DECOCTING');

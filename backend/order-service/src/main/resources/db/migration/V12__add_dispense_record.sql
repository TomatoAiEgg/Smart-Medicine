create table if not exists dispense_record (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    task_id uuid not null references workflow_task(id),
    dispenser varchar(64) not null,
    dispense_comment varchar(512),
    print_status varchar(32) not null default 'PRINTED',
    dispensed_at timestamptz not null,
    created_at timestamptz not null default now(),
    constraint uk_dispense_record_task unique (task_id)
);

create index if not exists idx_dispense_record_order
    on dispense_record (tenant_id, order_id, dispensed_at desc);

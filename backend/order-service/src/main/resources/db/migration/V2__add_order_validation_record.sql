create table if not exists order_validation_record (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    event_id varchar(64) not null unique,
    validation_status varchar(32) not null,
    validation_message varchar(512),
    raw_payload jsonb not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_order_validation_record_order
    on order_validation_record (tenant_id, order_id, created_at desc);

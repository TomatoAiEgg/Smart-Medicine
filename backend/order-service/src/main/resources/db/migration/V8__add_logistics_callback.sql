create table if not exists shipment (
    id uuid primary key,
    tenant_id uuid not null,
    order_id uuid not null,
    order_no varchar(64) not null,
    logistics_no varchar(128) not null,
    logistics_company varchar(64) not null,
    logistics_status varchar(32) not null,
    pay_method varchar(32),
    pkg_weight numeric(10, 2),
    pkg_num integer,
    package_time timestamptz,
    outbound_time timestamptz,
    sign_time timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uk_shipment_order_id on shipment(order_id);
create unique index if not exists uk_shipment_logistics_no on shipment(logistics_no);
create index if not exists idx_shipment_status_created on shipment(logistics_status, created_at desc);

create table if not exists shipment_trace (
    id uuid primary key,
    tenant_id uuid not null,
    shipment_id uuid not null references shipment(id),
    order_id uuid not null,
    logistics_no varchar(128) not null,
    trace_status varchar(32) not null,
    trace_content varchar(1024),
    raw_payload jsonb,
    trace_time timestamptz not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_shipment_trace_shipment_created on shipment_trace(shipment_id, created_at desc);
create index if not exists idx_shipment_trace_logistics_no_created on shipment_trace(logistics_no, created_at desc);

create table if not exists callback_record (
    id uuid primary key,
    tenant_id uuid not null,
    order_id uuid,
    callback_type varchar(64) not null,
    business_id varchar(128) not null,
    request_url varchar(512),
    request_headers jsonb not null default '{}'::jsonb,
    request_body jsonb not null,
    response_body text,
    status varchar(32) not null,
    retry_count integer not null default 0,
    next_retry_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_callback_record_status_created on callback_record(status, created_at desc);
create index if not exists idx_callback_record_order_created on callback_record(order_id, created_at desc);
create unique index if not exists uk_callback_record_business_type on callback_record(callback_type, business_id);

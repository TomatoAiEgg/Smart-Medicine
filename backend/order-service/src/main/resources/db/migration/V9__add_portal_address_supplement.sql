create table if not exists portal_address_supplement (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    order_no varchar(64) not null,
    supplement_status varchar(32) not null default 'PENDING',
    receiver_name varchar(128) not null,
    receiver_phone varchar(64) not null,
    receiver_province varchar(64),
    receiver_city varchar(64),
    receiver_zone varchar(64),
    receiver_address varchar(512) not null,
    requester_name varchar(128),
    requester_phone varchar(64),
    remark varchar(512),
    raw_payload jsonb not null default '{}'::jsonb,
    handled_by varchar(64),
    handled_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_portal_address_supplement_order_created
    on portal_address_supplement (tenant_id, order_id, created_at desc);

create index if not exists idx_portal_address_supplement_status_created
    on portal_address_supplement (supplement_status, created_at desc);

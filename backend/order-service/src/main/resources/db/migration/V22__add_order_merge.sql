create table if not exists order_merge (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    merge_no varchar(64) not null unique,
    logistics_company varchar(64),
    logistics_no varchar(128),
    status varchar(32) not null default 'ACTIVE',
    remark varchar(512),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists order_merge_item (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    merge_id uuid not null references order_merge(id),
    order_id uuid not null references order_main(id),
    order_no varchar(64) not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    constraint uk_order_merge_item_once unique (merge_id, order_id)
);

create unique index if not exists uk_order_merge_item_active_order
    on order_merge_item (order_id)
    where active = true;

create index if not exists idx_order_merge_status_created
    on order_merge (status, created_at desc);

create index if not exists idx_order_merge_item_merge
    on order_merge_item (merge_id, active);

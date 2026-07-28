create table if not exists logistics_address_cost (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    institution_id uuid not null references institution(id),
    logistics_company varchar(64) not null,
    province varchar(64) not null,
    city varchar(64) not null default '',
    district varchar(64) not null default '',
    cost_amount numeric(12, 2) not null default 0,
    remark varchar(512),
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_logistics_address_cost unique (
        tenant_id, institution_id, logistics_company, province, city, district
    )
);

insert into logistics_address_cost (
    id, tenant_id, institution_id, logistics_company, province, city, district, cost_amount, remark, enabled
)
values (
    '11111111-2222-3333-4444-000000000301',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'DEFAULT',
    '广东省',
    '深圳市',
    '',
    8.00,
    '演示机构深圳地址物流费用',
    true
)
on conflict (tenant_id, institution_id, logistics_company, province, city, district) do nothing;

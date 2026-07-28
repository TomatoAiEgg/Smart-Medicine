create table if not exists logistics_special_rule (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    institution_id uuid not null references institution(id),
    rule_name varchar(128) not null,
    logistics_company varchar(64) not null,
    base_fee numeric(12, 2) not null default 0,
    extra_fee numeric(12, 2) not null default 0,
    free_threshold numeric(12, 2) not null default 0,
    remark varchar(512),
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_logistics_special_rule unique (tenant_id, institution_id, rule_name, logistics_company)
);

insert into logistics_special_rule (
    id, tenant_id, institution_id, rule_name, logistics_company, base_fee, extra_fee, free_threshold, remark, enabled
)
values (
    '11111111-2222-3333-4444-000000000201',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '演示机构默认物流费',
    'DEFAULT',
    8.00,
    0.00,
    0.00,
    '演示机构默认物流费用规则',
    true
)
on conflict (tenant_id, institution_id, rule_name, logistics_company) do nothing;

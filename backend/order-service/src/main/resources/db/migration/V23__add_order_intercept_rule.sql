create table if not exists order_intercept_rule (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    rule_code varchar(64) not null,
    rule_name varchar(128) not null,
    intercept_stage varchar(64) not null default 'CREATE_ORDER',
    match_field varchar(64) not null,
    match_type varchar(32) not null default 'CONTAINS',
    match_value varchar(256) not null,
    reason varchar(512) not null,
    priority int not null default 100,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_order_intercept_rule_code unique (tenant_id, rule_code)
);

insert into order_intercept_rule (
    id, tenant_id, rule_code, rule_name, intercept_stage, match_field, match_type,
    match_value, reason, priority, enabled
)
values (
    '11111111-2222-3333-4444-000000000401',
    '11111111-1111-1111-1111-111111111111',
    'demo-risk-phone',
    '演示风险电话拦截',
    'CREATE_ORDER',
    'receiverPhone',
    'EQUALS',
    '00000000000',
    '演示规则：收货电话命中风险号码',
    100,
    false
)
on conflict (tenant_id, rule_code) do nothing;

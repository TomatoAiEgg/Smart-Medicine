create table if not exists system_config (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    config_key varchar(128) not null,
    config_name varchar(128) not null,
    config_value varchar(1000) not null,
    value_type varchar(32) not null default 'STRING',
    enabled boolean not null default true,
    remark varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_system_config_key unique (tenant_id, config_key)
);

create index if not exists idx_system_config_enabled
    on system_config (tenant_id, enabled, config_key);

insert into system_config (
    id, tenant_id, config_key, config_name, config_value, value_type, enabled, remark
)
values
    (
        '11111111-2222-3333-4444-000000000801',
        '11111111-1111-1111-1111-111111111111',
        'order.auto.review.enabled',
        'Order auto review switch',
        'false',
        'BOOLEAN',
        true,
        'Demo config only; no real review workflow is triggered'
    ),
    (
        '11111111-2222-3333-4444-000000000802',
        '11111111-1111-1111-1111-111111111111',
        'sms.provider.enabled',
        'SMS provider switch',
        'false',
        'BOOLEAN',
        true,
        'Current SMS sending is recorded only; no provider call is triggered'
    )
on conflict (tenant_id, config_key) do nothing;

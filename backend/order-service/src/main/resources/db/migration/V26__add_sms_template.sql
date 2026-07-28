create table if not exists sms_template (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    template_code varchar(64) not null,
    template_name varchar(128) not null,
    template_type varchar(64) not null default 'ORDER',
    content_template varchar(1000) not null,
    signature varchar(64),
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_sms_template_code unique (tenant_id, template_code)
);

create index if not exists idx_sms_template_enabled
    on sms_template (tenant_id, enabled, updated_at desc);

insert into sms_template (
    id, tenant_id, template_code, template_name, template_type, content_template, signature, enabled
)
values (
    '11111111-2222-3333-4444-000000000601',
    '11111111-1111-1111-1111-111111111111',
    'order-created-notice',
    '订单创建通知',
    'ORDER',
    '您的处方订单{{orderNo}}已创建，当前状态：{{orderStatus}}。',
    '智慧药房',
    true
)
on conflict (tenant_id, template_code) do nothing;

create table if not exists institution_api_permission (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    institution_id uuid not null references institution(id),
    api_id uuid not null references institution_api_definition(id),
    enabled boolean not null default true,
    remark varchar(512),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_institution_api_permission unique (tenant_id, institution_id, api_id)
);

insert into institution_api_permission (
    id, tenant_id, institution_id, api_id, enabled, remark
)
values (
    '11111111-2222-3333-4444-000000000101',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '11111111-2222-3333-4444-000000000001',
    true,
    '演示医院默认授权机构下单接口'
)
on conflict (tenant_id, institution_id, api_id) do nothing;

create table if not exists herb_catalog (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    herb_code varchar(64) not null,
    herb_name varchar(256) not null,
    drug_specs varchar(128),
    drug_origin varchar(128),
    unit varchar(32),
    retail_price numeric(12, 4) not null default 0,
    enabled boolean not null default true,
    remark varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_herb_catalog_code unique (tenant_id, herb_code)
);

create index if not exists idx_herb_catalog_enabled
    on herb_catalog (tenant_id, enabled, herb_code);

insert into herb_catalog (
    id, tenant_id, herb_code, herb_name, drug_specs, drug_origin, unit, retail_price, enabled, remark
)
values (
    '11111111-2222-3333-4444-000000001001',
    '11111111-1111-1111-1111-111111111111',
    'HERB-DEMO-001',
    'Demo herb',
    '10g',
    'Demo origin',
    'g',
    0,
    true,
    'Demo herb catalog item only; no matching index or inventory is updated'
)
on conflict (tenant_id, herb_code) do nothing;

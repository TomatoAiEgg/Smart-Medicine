create table if not exists herb_area (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    area_code varchar(64) not null,
    area_name varchar(128) not null,
    enabled boolean not null default true,
    remark varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_herb_area_code unique (tenant_id, area_code)
);

create index if not exists idx_herb_area_enabled
    on herb_area (tenant_id, enabled, area_code);

insert into herb_area (
    id, tenant_id, area_code, area_name, enabled, remark
)
values (
    '11111111-2222-3333-4444-000000001101',
    '11111111-1111-1111-1111-111111111111',
    'DEFAULT-HERB-AREA',
    'Default herb area',
    true,
    'Demo area only; no inventory or routing is updated'
)
on conflict (tenant_id, area_code) do nothing;

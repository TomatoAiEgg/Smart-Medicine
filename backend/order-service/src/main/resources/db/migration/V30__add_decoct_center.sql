create table if not exists decoct_center (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    center_code varchar(64) not null,
    center_name varchar(128) not null,
    contact_name varchar(64),
    contact_phone varchar(32),
    address varchar(500),
    enabled boolean not null default true,
    remark varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_decoct_center_code unique (tenant_id, center_code)
);

create index if not exists idx_decoct_center_enabled
    on decoct_center (tenant_id, enabled, center_code);

insert into decoct_center (
    id, tenant_id, center_code, center_name, contact_name, contact_phone, address, enabled, remark
)
values (
    '11111111-2222-3333-4444-000000000901',
    '11111111-1111-1111-1111-111111111111',
    'LYT-DECOCT',
    'LYT Decoction Center',
    'operator',
    '13800000000',
    'Demo address',
    true,
    'Demo center only; no order routing or device binding is triggered'
)
on conflict (tenant_id, center_code) do nothing;

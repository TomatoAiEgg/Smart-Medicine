create table if not exists decoction_water_pail_config (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    pail_no varchar(64) not null,
    pail_name varchar(128) not null,
    decoction_center varchar(128),
    pail_group varchar(64),
    capacity_ml int,
    enabled boolean not null default true,
    remark varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_decoction_water_pail_config_no unique (tenant_id, pail_no)
);

create index if not exists idx_decoction_water_pail_config_enabled
    on decoction_water_pail_config (tenant_id, enabled, pail_no);

insert into decoction_water_pail_config (
    id, tenant_id, pail_no, pail_name, decoction_center, pail_group, capacity_ml, enabled, remark
)
values
    (
        '11111111-2222-3333-4444-000000000971',
        '11111111-1111-1111-1111-111111111111',
        'PAIL-001',
        '加水桶 PAIL-001',
        '良益堂煎煮中心',
        '默认组',
        1200,
        true,
        '系统默认加水桶'
    ),
    (
        '11111111-2222-3333-4444-000000000972',
        '11111111-1111-1111-1111-111111111111',
        'PAIL-002',
        '加水桶 PAIL-002',
        '良益堂煎煮中心',
        '默认组',
        1200,
        true,
        '系统默认加水桶'
    ),
    (
        '11111111-2222-3333-4444-000000000973',
        '11111111-1111-1111-1111-111111111111',
        'PAIL-003',
        '加水桶 PAIL-003',
        '良益堂煎煮中心',
        '默认组',
        1200,
        true,
        '系统默认加水桶'
    ),
    (
        '11111111-2222-3333-4444-000000000974',
        '11111111-1111-1111-1111-111111111111',
        'PAIL-004',
        '加水桶 PAIL-004',
        '良益堂煎煮中心',
        '默认组',
        1200,
        true,
        '系统默认加水桶'
    ),
    (
        '11111111-2222-3333-4444-000000000975',
        '11111111-1111-1111-1111-111111111111',
        'PAIL-005',
        '加水桶 PAIL-005',
        '良益堂煎煮中心',
        '默认组',
        1200,
        true,
        '系统默认加水桶'
    )
on conflict (tenant_id, pail_no) do nothing;

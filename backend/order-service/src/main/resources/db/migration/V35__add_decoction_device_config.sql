create table if not exists decoction_device_config (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    device_code varchar(64) not null,
    device_name varchar(128) not null,
    device_type varchar(32) not null,
    device_group varchar(64),
    decoction_center varchar(128),
    pda_code varchar(64),
    printer_code varchar(64),
    print_template_code varchar(64),
    enabled boolean not null default true,
    remark varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_decoction_device_config_code unique (tenant_id, device_code)
);

create index if not exists idx_decoction_device_config_enabled
    on decoction_device_config (tenant_id, enabled, device_code);

insert into decoction_device_config (
    id, tenant_id, device_code, device_name, device_type, device_group, decoction_center, enabled, remark
)
values
    (
        '11111111-2222-3333-4444-000000000951',
        '11111111-1111-1111-1111-111111111111',
        'DECOCT-001',
        '煎煮设备 DECOCT-001',
        '煎药机',
        '默认组',
        '良益堂煎煮中心',
        true,
        '系统默认煎煮设备'
    ),
    (
        '11111111-2222-3333-4444-000000000952',
        '11111111-1111-1111-1111-111111111111',
        'DECOCT-002',
        '煎煮设备 DECOCT-002',
        '煎药机',
        '默认组',
        '良益堂煎煮中心',
        true,
        '系统默认煎煮设备'
    ),
    (
        '11111111-2222-3333-4444-000000000953',
        '11111111-1111-1111-1111-111111111111',
        'DECOCT-003',
        '煎煮设备 DECOCT-003',
        '煎药机',
        '默认组',
        '良益堂煎煮中心',
        true,
        '系统默认煎煮设备'
    ),
    (
        '11111111-2222-3333-4444-000000000954',
        '11111111-1111-1111-1111-111111111111',
        'DECOCT-004',
        '煎煮设备 DECOCT-004',
        '煎药机',
        '默认组',
        '良益堂煎煮中心',
        true,
        '系统默认煎煮设备'
    ),
    (
        '11111111-2222-3333-4444-000000000955',
        '11111111-1111-1111-1111-111111111111',
        'DECOCT-005',
        '煎煮设备 DECOCT-005',
        '煎药机',
        '默认组',
        '良益堂煎煮中心',
        true,
        '系统默认煎煮设备'
    )
on conflict (tenant_id, device_code) do nothing;

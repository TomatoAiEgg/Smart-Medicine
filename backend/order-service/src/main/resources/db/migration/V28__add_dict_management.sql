create table if not exists dict_type (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    type_code varchar(64) not null,
    type_name varchar(128) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_dict_type_code unique (tenant_id, type_code)
);

create table if not exists dict_item (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    type_id uuid not null references dict_type(id),
    item_code varchar(64) not null,
    item_name varchar(128) not null,
    item_value varchar(256),
    sort_no int not null default 0,
    enabled boolean not null default true,
    remark varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_dict_item_code unique (type_id, item_code)
);

create index if not exists idx_dict_type_enabled
    on dict_type (tenant_id, enabled, type_code);

create index if not exists idx_dict_item_type_sort
    on dict_item (tenant_id, type_id, enabled, sort_no, item_code);

insert into dict_type (id, tenant_id, type_code, type_name, enabled)
values (
    '11111111-2222-3333-4444-000000000701',
    '11111111-1111-1111-1111-111111111111',
    'prescription_type',
    '处方类型',
    true
)
on conflict (tenant_id, type_code) do nothing;

insert into dict_item (
    id, tenant_id, type_id, item_code, item_name, item_value, sort_no, enabled, remark
)
values
    (
        '11111111-2222-3333-4444-000000000711',
        '11111111-1111-1111-1111-111111111111',
        '11111111-2222-3333-4444-000000000701',
        'DECOCTION',
        '代煎',
        'DECOCTION',
        10,
        true,
        '演示处方类型'
    ),
    (
        '11111111-2222-3333-4444-000000000712',
        '11111111-1111-1111-1111-111111111111',
        '11111111-2222-3333-4444-000000000701',
        'SELF_DECOCTION',
        '自煎',
        'SELF_DECOCTION',
        20,
        true,
        '演示处方类型'
    )
on conflict (type_id, item_code) do nothing;

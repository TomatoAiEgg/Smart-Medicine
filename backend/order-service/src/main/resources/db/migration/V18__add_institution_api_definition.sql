create table if not exists institution_api_definition (
    id uuid primary key,
    api_code varchar(128) not null unique,
    api_name varchar(128) not null,
    request_method varchar(16) not null,
    request_path varchar(256) not null,
    description varchar(512),
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

insert into institution_api_definition (
    id, api_code, api_name, request_method, request_path, description, enabled
)
values (
    '11111111-2222-3333-4444-000000000001',
    'createOrder',
    '机构下单',
    'POST',
    '/api/institution/createOrder',
    '机构通过网关推送订单和处方明细',
    true
)
on conflict (api_code) do nothing;

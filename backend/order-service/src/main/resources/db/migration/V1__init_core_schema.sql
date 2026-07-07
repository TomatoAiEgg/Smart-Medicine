create table if not exists tenant (
    id uuid primary key,
    tenant_code varchar(64) not null unique,
    tenant_name varchar(128) not null,
    tenant_type varchar(32) not null default 'PLATFORM',
    status varchar(32) not null default 'ENABLED',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists institution (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    institution_code varchar(64) not null,
    institution_name varchar(128) not null,
    institution_type varchar(32) not null default 'HOSPITAL',
    status varchar(32) not null default 'ENABLED',
    storage_type varchar(32),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_institution_tenant_code unique (tenant_id, institution_code)
);

create table if not exists institution_app (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    institution_id uuid not null references institution(id),
    app_key varchar(128) not null unique,
    app_secret varchar(256) not null,
    sign_type varchar(32) not null default 'HMAC_SHA256',
    callback_url varchar(512),
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists institution_ip_whitelist (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    institution_id uuid not null references institution(id),
    ip_range varchar(128) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists order_main (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    institution_id uuid not null references institution(id),
    order_no varchar(64) not null,
    external_order_no varchar(128) not null,
    status varchar(32) not null,
    patient_name varchar(128),
    patient_phone varchar(64),
    receiver_name varchar(128),
    receiver_phone varchar(64),
    receiver_province varchar(64),
    receiver_city varchar(64),
    receiver_zone varchar(64),
    receiver_address varchar(512),
    address_type varchar(32),
    callback_url varchar(512),
    raw_payload jsonb not null default '{}'::jsonb,
    version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_order_main_order_no unique (order_no),
    constraint uk_order_main_external unique (tenant_id, institution_id, external_order_no)
);

create index if not exists idx_order_main_tenant_status_created
    on order_main (tenant_id, status, created_at desc);

create table if not exists order_status_log (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    from_status varchar(32),
    to_status varchar(32) not null,
    operator_type varchar(32) not null,
    operator_id varchar(64),
    source varchar(64) not null,
    reason varchar(512),
    created_at timestamptz not null default now()
);

create index if not exists idx_order_status_log_order
    on order_status_log (tenant_id, order_id, created_at desc);

create table if not exists prescription (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    institution_id uuid not null references institution(id),
    order_id uuid not null references order_main(id),
    prescription_no varchar(64) not null,
    external_prescription_no varchar(128) not null,
    prescription_type varchar(32),
    status varchar(32) not null,
    doctor_name varchar(128),
    diagnosis varchar(512),
    raw_payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_prescription_no unique (tenant_id, prescription_no),
    constraint uk_prescription_external unique (tenant_id, institution_id, external_prescription_no)
);

create index if not exists idx_prescription_order
    on prescription (tenant_id, order_id);

create table if not exists prescription_detail (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    prescription_id uuid not null references prescription(id),
    drug_code varchar(128),
    drug_name varchar(256),
    platform_drug_code varchar(128),
    platform_drug_name varchar(256),
    dose varchar(64),
    unit varchar(32),
    special_usage varchar(256),
    sort_no int not null default 0,
    batch_no varchar(128),
    validation_tips varchar(512),
    created_at timestamptz not null default now()
);

create index if not exists idx_prescription_detail_prescription
    on prescription_detail (tenant_id, prescription_id, sort_no);

create table if not exists event_outbox (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    event_id varchar(64) not null unique,
    event_type varchar(64) not null,
    aggregate_type varchar(64) not null,
    aggregate_id varchar(64) not null,
    payload jsonb not null,
    status varchar(32) not null default 'NEW',
    retry_count int not null default 0,
    next_retry_at timestamptz,
    created_at timestamptz not null default now(),
    published_at timestamptz
);

create index if not exists idx_event_outbox_status_created
    on event_outbox (status, created_at);

create table if not exists message_consume_log (
    id uuid primary key,
    consumer_group varchar(128) not null,
    message_id varchar(128),
    event_id varchar(64) not null,
    status varchar(32) not null,
    created_at timestamptz not null default now(),
    constraint uk_message_consume_event unique (consumer_group, event_id)
);

create table if not exists api_access_log (
    id uuid primary key,
    tenant_id uuid,
    institution_id uuid,
    app_key varchar(128),
    request_path varchar(256) not null,
    request_ip varchar(128),
    result_code varchar(64),
    created_at timestamptz not null default now()
);

insert into tenant (id, tenant_code, tenant_name, tenant_type, status)
values ('11111111-1111-1111-1111-111111111111', 'demo-tenant', '演示租户', 'PLATFORM', 'ENABLED')
on conflict (id) do nothing;

insert into institution (id, tenant_id, institution_code, institution_name, institution_type, status)
values (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'demo-hospital',
    '演示医院',
    'HOSPITAL',
    'ENABLED'
)
on conflict (id) do nothing;

insert into institution_app (
    id, tenant_id, institution_id, app_key, app_secret, sign_type, callback_url, enabled
)
values (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'demo-app',
    'demo-secret',
    'HMAC_SHA256',
    'http://example.invalid/callback',
    true
)
on conflict (id) do nothing;

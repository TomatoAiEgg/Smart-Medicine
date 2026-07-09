create table if not exists integration_message (
    id uuid primary key,
    tenant_id uuid,
    source_type varchar(64) not null,
    source_system varchar(128) not null,
    external_message_id varchar(128) not null,
    message_type varchar(64) not null,
    business_key varchar(128),
    process_status varchar(32) not null default 'PENDING',
    normalized_payload jsonb not null default '{}'::jsonb,
    raw_payload text not null default '',
    failure_reason varchar(512),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    processed_at timestamptz,
    constraint uk_integration_message_source unique (source_type, source_system, external_message_id)
);

create index if not exists idx_integration_message_status_created
    on integration_message (process_status, created_at desc);

create index if not exists idx_integration_message_business
    on integration_message (source_type, business_key);

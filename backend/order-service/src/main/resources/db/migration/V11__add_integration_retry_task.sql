create table if not exists integration_retry_task (
    id uuid primary key,
    message_id uuid not null references integration_message(id),
    task_type varchar(64) not null,
    target_system varchar(128) not null,
    business_key varchar(128),
    request_url varchar(512) not null,
    request_headers jsonb not null default '{}'::jsonb,
    request_body text not null default '',
    response_body text,
    task_status varchar(32) not null default 'PENDING',
    retry_count integer not null default 0,
    next_retry_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    processed_at timestamptz,
    constraint uk_integration_retry_message_type unique (message_id, task_type)
);

create index if not exists idx_integration_retry_status_next
    on integration_retry_task (task_status, next_retry_at nulls first, created_at);

create index if not exists idx_integration_retry_business
    on integration_retry_task (task_type, business_key);

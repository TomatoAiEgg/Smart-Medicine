alter table event_outbox
    add column if not exists topic varchar(128),
    add column if not exists tag varchar(128),
    add column if not exists source varchar(64),
    add column if not exists max_retry_count int not null default 10,
    add column if not exists last_error text,
    add column if not exists updated_at timestamptz not null default now();

update event_outbox
set status = 'PUBLISHED',
    updated_at = now()
where status = 'SENT';

update event_outbox
set status = 'PUBLISH_FAILED',
    updated_at = now()
where status = 'FAILED';

update event_outbox
set topic = coalesce(topic, 'zhyf-order-event'),
    tag = coalesce(tag, event_type),
    source = coalesce(source, 'legacy-migration'),
    max_retry_count = greatest(max_retry_count, 1),
    updated_at = now();

create index if not exists idx_event_outbox_status_retry
    on event_outbox (status, next_retry_at, created_at);

create index if not exists idx_event_outbox_aggregate
    on event_outbox (aggregate_type, aggregate_id);

alter table message_consume_log
    add column if not exists topic varchar(128),
    add column if not exists tag varchar(128),
    add column if not exists aggregate_id varchar(64),
    add column if not exists consume_started_at timestamptz,
    add column if not exists consume_finished_at timestamptz,
    add column if not exists retry_count int not null default 0,
    add column if not exists last_error text,
    add column if not exists trace_endpoint varchar(256),
    add column if not exists updated_at timestamptz not null default now();

update message_consume_log
set status = 'FAILED_RETRYABLE',
    retry_count = greatest(retry_count, 1),
    updated_at = now()
where status = 'FAILED';

update message_consume_log
set consume_started_at = coalesce(consume_started_at, created_at),
    consume_finished_at = case
        when status = 'SUCCESS' then coalesce(consume_finished_at, updated_at)
        else consume_finished_at
    end,
    updated_at = now();

create index if not exists idx_message_consume_status_updated
    on message_consume_log (status, updated_at desc);

create index if not exists idx_message_consume_event
    on message_consume_log (event_id);

create table if not exists dead_letter_record (
    id uuid primary key,
    event_id varchar(64) not null,
    topic varchar(128),
    tag varchar(128),
    consumer_group varchar(128),
    aggregate_id varchar(64),
    payload_snapshot jsonb not null default '{}'::jsonb,
    error_message text,
    retry_count int not null default 0,
    status varchar(32) not null default 'OPEN',
    operator varchar(64),
    remark varchar(512),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_dead_letter_status_created
    on dead_letter_record (status, created_at desc);

create index if not exists idx_dead_letter_event
    on dead_letter_record (event_id);

create table if not exists operator_user (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    username varchar(64) not null,
    display_name varchar(128) not null,
    role_code varchar(64),
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_operator_user_tenant_username unique (tenant_id, username)
);

create table if not exists operation_log (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid,
    prescription_id uuid,
    event_id varchar(64),
    operator varchar(64),
    action varchar(64) not null,
    result varchar(32) not null,
    reason varchar(512),
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_operation_log_order_created
    on operation_log (tenant_id, order_id, created_at desc);

create index if not exists idx_operation_log_action_created
    on operation_log (action, created_at desc);

create table if not exists prescription_audit_record (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    prescription_id uuid references prescription(id),
    task_id uuid references workflow_task(id),
    audit_result varchar(32) not null,
    auditor varchar(64) not null,
    audit_comment varchar(512),
    audited_at timestamptz not null,
    created_at timestamptz not null default now(),
    constraint uk_prescription_audit_task unique (task_id)
);

create index if not exists idx_prescription_audit_order
    on prescription_audit_record (tenant_id, order_id, audited_at desc);

create table if not exists prescription_recheck_record (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    prescription_id uuid references prescription(id),
    task_id uuid references workflow_task(id),
    recheck_result varchar(32) not null,
    rechecker varchar(64) not null,
    recheck_comment varchar(512),
    rechecked_at timestamptz not null,
    created_at timestamptz not null default now(),
    constraint uk_prescription_recheck_task unique (task_id)
);

create index if not exists idx_prescription_recheck_order
    on prescription_recheck_record (tenant_id, order_id, rechecked_at desc);

create unique index if not exists uk_shipment_trace_business_event
    on shipment_trace (logistics_no, trace_status, trace_time);

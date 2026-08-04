create table if not exists order_address_change_record (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    before_address jsonb not null default '{}'::jsonb,
    after_address jsonb not null default '{}'::jsonb,
    change_reason varchar(512),
    operator varchar(64) not null,
    request_id varchar(128),
    changed_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create index if not exists idx_order_address_change_order
    on order_address_change_record (tenant_id, order_id, changed_at desc);

create table if not exists prescription_change_record (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    prescription_id uuid not null references prescription(id),
    change_type varchar(64) not null,
    before_snapshot jsonb not null default '{}'::jsonb,
    after_snapshot jsonb not null default '{}'::jsonb,
    change_reason varchar(512),
    operator varchar(64) not null,
    request_id varchar(128),
    changed_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create index if not exists idx_prescription_change_prescription
    on prescription_change_record (tenant_id, prescription_id, changed_at desc);

create table if not exists import_task (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    import_type varchar(64) not null,
    source_file_name varchar(256) not null,
    file_sha256 varchar(64),
    task_status varchar(32) not null default 'PENDING',
    total_count integer not null default 0,
    success_count integer not null default 0,
    failure_count integer not null default 0,
    requested_by varchar(128),
    failure_reason text,
    version integer not null default 0,
    started_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_import_task_tenant_status_created
    on import_task (tenant_id, task_status, created_at desc);

create table if not exists import_task_error (
    id uuid primary key,
    task_id uuid not null references import_task(id) on delete cascade,
    row_no integer,
    business_key varchar(128),
    error_code varchar(64),
    error_message varchar(1000) not null,
    raw_data jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_import_task_error_task_row
    on import_task_error (task_id, row_no);

create table if not exists sms_batch_task (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    template_id uuid not null references sms_template(id),
    task_name varchar(128) not null,
    task_status varchar(32) not null default 'PENDING',
    total_count integer not null default 0,
    success_count integer not null default 0,
    failure_count integer not null default 0,
    requested_by varchar(128),
    failure_reason text,
    version integer not null default 0,
    started_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_sms_batch_task_tenant_status_created
    on sms_batch_task (tenant_id, task_status, created_at desc);

create table if not exists sms_batch_recipient (
    id uuid primary key,
    batch_task_id uuid not null references sms_batch_task(id) on delete cascade,
    receiver_phone varchar(32) not null,
    receiver_name varchar(64),
    related_order_no varchar(64),
    variables jsonb not null default '{}'::jsonb,
    send_status varchar(32) not null default 'PENDING',
    send_record_id uuid references sms_send_record(id),
    failure_reason varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_sms_batch_recipient_task_status
    on sms_batch_recipient (batch_task_id, send_status, created_at);

create table if not exists print_task (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    task_type varchar(64) not null,
    task_name varchar(128) not null,
    task_status varchar(32) not null default 'PENDING',
    template_id uuid references label_template(id),
    print_payload jsonb not null default '{}'::jsonb,
    total_count integer not null default 0,
    success_count integer not null default 0,
    failure_count integer not null default 0,
    requested_by varchar(128),
    failure_reason text,
    version integer not null default 0,
    started_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_print_task_tenant_status_created
    on print_task (tenant_id, task_status, created_at desc);

create table if not exists shipment_followup_record (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    shipment_id uuid not null references shipment(id),
    order_id uuid not null references order_main(id),
    followup_type varchar(64) not null,
    followup_status varchar(32) not null default 'PENDING',
    contact_name varchar(128),
    contact_phone varchar(64),
    followup_result varchar(512),
    next_followup_at timestamptz,
    operator varchar(64),
    version integer not null default 0,
    followed_up_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_shipment_followup_due
    on shipment_followup_record (tenant_id, followup_status, next_followup_at, created_at);

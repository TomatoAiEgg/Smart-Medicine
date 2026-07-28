create table if not exists sms_send_record (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    template_id uuid not null references sms_template(id),
    template_code varchar(64) not null,
    template_name varchar(128) not null,
    receiver_phone varchar(32) not null,
    receiver_name varchar(64),
    related_order_no varchar(64),
    signature varchar(64),
    content varchar(1000) not null,
    variables jsonb not null default '{}'::jsonb,
    send_status varchar(32) not null,
    provider_message_id varchar(128),
    failure_reason varchar(500),
    retry_count int not null default 0,
    operator varchar(64),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    sent_at timestamptz
);

create index if not exists idx_sms_send_record_created
    on sms_send_record (tenant_id, created_at desc);

create index if not exists idx_sms_send_record_receiver
    on sms_send_record (tenant_id, receiver_phone, created_at desc);

create index if not exists idx_sms_send_record_status
    on sms_send_record (tenant_id, send_status, created_at desc);

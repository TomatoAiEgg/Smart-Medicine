alter table label_print_record
    add column if not exists printer_code varchar(128),
    add column if not exists printer_name varchar(128),
    add column if not exists provider varchar(64),
    add column if not exists provider_task_no varchar(128),
    add column if not exists request_param text,
    add column if not exists response_body text,
    add column if not exists updated_at timestamptz not null default now();

create index if not exists idx_label_print_record_channel_status_created
    on label_print_record (tenant_id, print_channel, print_status, created_at desc);

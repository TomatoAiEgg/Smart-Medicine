create table if not exists export_task (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    task_type varchar(64) not null,
    task_name varchar(128) not null,
    task_status varchar(32) not null default 'PENDING',
    query_param text not null default '{}',
    file_name varchar(256),
    content_type varchar(128),
    file_content bytea,
    row_count int,
    file_size_bytes int,
    failure_reason text,
    requested_by varchar(128),
    retry_count int not null default 0,
    started_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_export_task_tenant_status_created
    on export_task (tenant_id, task_status, created_at desc);

create index if not exists idx_export_task_tenant_type_created
    on export_task (tenant_id, task_type, created_at desc);

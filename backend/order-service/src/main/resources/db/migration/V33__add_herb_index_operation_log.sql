create table if not exists herb_index_operation_log (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    index_id uuid references herb_index(id),
    institution_id uuid not null references institution(id),
    institution_code varchar(64) not null,
    institution_name varchar(128) not null,
    external_herb_code varchar(128) not null,
    external_herb_name varchar(256) not null,
    herb_id uuid not null references herb_catalog(id),
    herb_code varchar(64) not null,
    herb_name varchar(256) not null,
    action_type varchar(32) not null,
    operator varchar(64) not null default 'admin',
    remark varchar(500),
    created_at timestamptz not null default now()
);

create index if not exists idx_herb_index_operation_log_query
    on herb_index_operation_log (tenant_id, created_at desc, action_type);

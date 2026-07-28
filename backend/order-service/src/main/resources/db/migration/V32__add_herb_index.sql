create table if not exists herb_index (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    institution_id uuid not null references institution(id),
    external_herb_code varchar(128) not null,
    external_herb_name varchar(256) not null,
    herb_id uuid not null references herb_catalog(id),
    match_type varchar(32) not null default 'MANUAL',
    enabled boolean not null default true,
    remark varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_herb_index_external unique (tenant_id, institution_id, external_herb_code)
);

create index if not exists idx_herb_index_herb
    on herb_index (tenant_id, herb_id, enabled);

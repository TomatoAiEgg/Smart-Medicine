alter table tenant
    add column if not exists version integer not null default 0,
    add column if not exists created_by varchar(64),
    add column if not exists updated_by varchar(64);

alter table institution
    add column if not exists version integer not null default 0,
    add column if not exists created_by varchar(64),
    add column if not exists updated_by varchar(64);

alter table operator_user
    add column if not exists password_hash varchar(255),
    add column if not exists password_algorithm varchar(32) not null default 'BCRYPT',
    add column if not exists credential_status varchar(32) not null default 'PENDING',
    add column if not exists password_changed_at timestamptz,
    add column if not exists last_login_at timestamptz,
    add column if not exists login_failed_count integer not null default 0,
    add column if not exists locked_until timestamptz,
    add column if not exists version integer not null default 0,
    add column if not exists created_by varchar(64),
    add column if not exists updated_by varchar(64);

create unique index if not exists uk_operator_user_tenant_id
    on operator_user (tenant_id, id);

create unique index if not exists uk_institution_tenant_id
    on institution (tenant_id, id);

create table if not exists admin_role (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    role_code varchar(64) not null,
    role_name varchar(128) not null,
    data_scope_type varchar(32) not null default 'TENANT',
    built_in boolean not null default false,
    enabled boolean not null default true,
    version integer not null default 0,
    created_by varchar(64),
    updated_by varchar(64),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_admin_role_tenant_code unique (tenant_id, role_code),
    constraint uk_admin_role_tenant_id unique (tenant_id, id)
);

create index if not exists idx_admin_role_tenant_enabled
    on admin_role (tenant_id, enabled, created_at desc);

create table if not exists admin_menu (
    id uuid primary key,
    parent_id uuid references admin_menu(id),
    menu_code varchar(64) not null unique,
    menu_name varchar(128) not null,
    menu_type varchar(32) not null default 'MENU',
    route_path varchar(256),
    component_key varchar(128),
    permission_code varchar(128),
    icon_key varchar(64),
    sort_order integer not null default 0,
    visible boolean not null default true,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_admin_menu_parent_sort
    on admin_menu (parent_id, sort_order, menu_code);

create table if not exists admin_permission (
    id uuid primary key,
    permission_code varchar(128) not null unique,
    permission_name varchar(128) not null,
    resource_type varchar(32) not null default 'API',
    http_method varchar(16),
    resource_pattern varchar(512),
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists admin_user_role (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    user_id uuid not null,
    role_id uuid not null,
    created_by varchar(64),
    created_at timestamptz not null default now(),
    constraint uk_admin_user_role unique (tenant_id, user_id, role_id),
    constraint fk_admin_user_role_user foreign key (tenant_id, user_id)
        references operator_user(tenant_id, id) on delete cascade,
    constraint fk_admin_user_role_role foreign key (tenant_id, role_id)
        references admin_role(tenant_id, id) on delete cascade
);

create index if not exists idx_admin_user_role_user
    on admin_user_role (tenant_id, user_id);

create table if not exists admin_role_menu (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    role_id uuid not null,
    menu_id uuid not null references admin_menu(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint uk_admin_role_menu unique (tenant_id, role_id, menu_id),
    constraint fk_admin_role_menu_role foreign key (tenant_id, role_id)
        references admin_role(tenant_id, id) on delete cascade
);

create table if not exists admin_role_permission (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    role_id uuid not null,
    permission_id uuid not null references admin_permission(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint uk_admin_role_permission unique (tenant_id, role_id, permission_id),
    constraint fk_admin_role_permission_role foreign key (tenant_id, role_id)
        references admin_role(tenant_id, id) on delete cascade
);

create table if not exists admin_role_institution_scope (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    role_id uuid not null,
    institution_id uuid not null,
    created_at timestamptz not null default now(),
    constraint uk_admin_role_institution unique (tenant_id, role_id, institution_id),
    constraint fk_admin_role_institution_role foreign key (tenant_id, role_id)
        references admin_role(tenant_id, id) on delete cascade,
    constraint fk_admin_role_institution_institution foreign key (tenant_id, institution_id)
        references institution(tenant_id, id) on delete cascade
);

create index if not exists idx_admin_role_institution_scope_role
    on admin_role_institution_scope (tenant_id, role_id);

create table if not exists admin_user_institution_scope (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    user_id uuid not null,
    institution_id uuid not null,
    created_at timestamptz not null default now(),
    constraint uk_admin_user_institution unique (tenant_id, user_id, institution_id),
    constraint fk_admin_user_institution_user foreign key (tenant_id, user_id)
        references operator_user(tenant_id, id) on delete cascade,
    constraint fk_admin_user_institution_institution foreign key (tenant_id, institution_id)
        references institution(tenant_id, id) on delete cascade
);

create index if not exists idx_admin_user_institution_scope_user
    on admin_user_institution_scope (tenant_id, user_id);

create table if not exists integration_source_binding (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    institution_id uuid,
    source_type varchar(64) not null,
    source_system varchar(128) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_integration_source_binding unique (source_type, source_system),
    constraint fk_integration_source_binding_institution foreign key (tenant_id, institution_id)
        references institution(tenant_id, id)
);

create index if not exists idx_integration_source_binding_tenant
    on integration_source_binding (tenant_id, enabled, source_type);

insert into admin_role (
    id, tenant_id, role_code, role_name, data_scope_type, built_in, enabled
)
select
    md5(t.id::text || ':TENANT_ADMIN')::uuid,
    t.id,
    'TENANT_ADMIN',
    '租户管理员',
    'TENANT',
    true,
    true
from tenant t
on conflict (tenant_id, role_code) do nothing;

insert into admin_role (
    id, tenant_id, role_code, role_name, data_scope_type, built_in, enabled
)
select
    md5(u.tenant_id::text || ':' || u.role_code)::uuid,
    u.tenant_id,
    u.role_code,
    u.role_code,
    'TENANT',
    false,
    true
from operator_user u
where u.role_code is not null and btrim(u.role_code) <> ''
group by u.tenant_id, u.role_code
on conflict (tenant_id, role_code) do nothing;

insert into admin_user_role (id, tenant_id, user_id, role_id)
select
    md5(u.id::text || ':' || r.id::text)::uuid,
    u.tenant_id,
    u.id,
    r.id
from operator_user u
join admin_role r
  on r.tenant_id = u.tenant_id
 and r.role_code = u.role_code
where u.role_code is not null and btrim(u.role_code) <> ''
on conflict (tenant_id, user_id, role_id) do nothing;

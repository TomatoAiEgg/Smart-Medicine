create table if not exists order_problem_registration (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    institution_id uuid references institution(id),
    order_no varchar(64) not null,
    external_order_no varchar(128),
    problem_type varchar(64) not null default 'ORDER',
    problem_reason varchar(512) not null,
    handling_plan varchar(512) not null,
    amount numeric(12, 2) not null default 0,
    status varchar(32) not null default 'OPEN',
    operator varchar(64) not null,
    remark varchar(512),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    processed_at timestamptz,
    closed_at timestamptz
);

create index if not exists idx_order_problem_registration_status
    on order_problem_registration (status, updated_at desc);

create index if not exists idx_order_problem_registration_order_no
    on order_problem_registration (order_no);

create table if not exists order_problem_registration_action (
    id uuid primary key,
    registration_id uuid not null references order_problem_registration(id),
    action varchar(32) not null,
    from_status varchar(32),
    to_status varchar(32),
    operator varchar(64) not null,
    remark varchar(512),
    created_at timestamptz not null default now()
);

create index if not exists idx_order_problem_registration_action_registration
    on order_problem_registration_action (registration_id, created_at desc);

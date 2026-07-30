create table if not exists label_print_record (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    prescription_id uuid not null references prescription(id),
    order_no varchar(64) not null,
    external_order_no varchar(128) not null,
    prescription_no varchar(64) not null,
    external_prescription_no varchar(128),
    institution_name varchar(128) not null,
    patient_name varchar(128),
    print_status varchar(32) not null,
    print_channel varchar(32) not null default 'BROWSER',
    template_id uuid references label_template(id),
    template_name varchar(128),
    failure_reason varchar(512),
    operator varchar(64),
    retry_of uuid references label_print_record(id),
    created_at timestamptz not null default now()
);

create index if not exists idx_label_print_record_tenant_status_created
    on label_print_record (tenant_id, print_status, created_at desc);

create index if not exists idx_label_print_record_prescription_created
    on label_print_record (prescription_no, created_at desc);

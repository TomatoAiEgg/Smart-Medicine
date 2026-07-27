alter table order_main
    add column if not exists delivery_time timestamptz,
    add column if not exists batch_no varchar(64),
    add column if not exists order_remark varchar(512);

alter table prescription
    add column if not exists hospital_type varchar(32),
    add column if not exists dose_count int,
    add column if not exists decoction_count int,
    add column if not exists decoction_unit_price numeric(12, 4),
    add column if not exists decoction_total_price numeric(12, 2),
    add column if not exists total_amount numeric(12, 2),
    add column if not exists department_name varchar(128),
    add column if not exists ward_name varchar(128),
    add column if not exists bed_no varchar(64),
    add column if not exists medication_method varchar(256),
    add column if not exists medication_instruction varchar(512),
    add column if not exists prescription_remark varchar(512);

alter table prescription_detail
    add column if not exists drug_specs varchar(128),
    add column if not exists drug_origin varchar(128),
    add column if not exists quantity numeric(12, 4),
    add column if not exists unit_price numeric(12, 4),
    add column if not exists settlement_unit_price numeric(12, 4),
    add column if not exists total_price numeric(12, 2),
    add column if not exists settlement_total_price numeric(12, 2),
    add column if not exists remark varchar(512);

create index if not exists idx_order_main_delivery_time
    on order_main (tenant_id, delivery_time);

create index if not exists idx_order_main_batch_no
    on order_main (tenant_id, batch_no);

create index if not exists idx_prescription_hospital_type
    on prescription (tenant_id, hospital_type);

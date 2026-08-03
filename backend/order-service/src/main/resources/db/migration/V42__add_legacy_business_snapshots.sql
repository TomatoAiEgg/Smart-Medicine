alter table order_main
    add column if not exists legacy_company_num varchar(64),
    add column if not exists storage_type varchar(32),
    add column if not exists create_ip varchar(128),
    add column if not exists order_time timestamptz,
    add column if not exists classes varchar(64),
    add column if not exists order_pkg_weight numeric(10, 2),
    add column if not exists order_pkg_num integer,
    add column if not exists logistics_receivables_money numeric(12, 2),
    add column if not exists logistics_pay_method varchar(32),
    add column if not exists logistics_type varchar(32),
    add column if not exists logistics_mode varchar(32),
    add column if not exists sp_order_id varchar(128),
    add column if not exists logis_id varchar(128),
    add column if not exists area_level varchar(64),
    add column if not exists route_code varchar(128),
    add column if not exists base_product_no varchar(128),
    add column if not exists package_time timestamptz,
    add column if not exists outbound_time timestamptz,
    add column if not exists sign_time timestamptz;

alter table prescription
    add column if not exists patient_age varchar(64),
    add column if not exists patient_month_age varchar(64),
    add column if not exists patient_day_age varchar(64),
    add column if not exists patient_gender varchar(32),
    add column if not exists patient_card_no varchar(128),
    add column if not exists treat_card varchar(128),
    add column if not exists patient_tel varchar(64),
    add column if not exists is_pregnant varchar(32),
    add column if not exists herb_type varchar(32),
    add column if not exists wj_type varchar(32),
    add column if not exists doctor_tel varchar(64),
    add column if not exists hospital_name varchar(128),
    add column if not exists hospital_num varchar(64),
    add column if not exists order_handle_floor varchar(64),
    add column if not exists jyj_decoction_plan varchar(64),
    add column if not exists jyj_decoction_advice varchar(512),
    add column if not exists label_size varchar(64),
    add column if not exists bind_no varchar(128),
    add column if not exists drugs_money numeric(12, 2),
    add column if not exists audit_flow_pic_url varchar(512),
    add column if not exists audit_reason varchar(512),
    add column if not exists audit_result varchar(32),
    add column if not exists dispense_flow_pic_url varchar(512),
    add column if not exists recheck_flow_pic_url varchar(512);

alter table prescription_detail
    add column if not exists dc_goods_num varchar(128),
    add column if not exists dc_goods_name varchar(256),
    add column if not exists roots_goods_name varchar(256),
    add column if not exists supplier_name varchar(256),
    add column if not exists med_per_dose numeric(12, 4),
    add column if not exists med_per_day numeric(12, 4),
    add column if not exists detail_status varchar(32),
    add column if not exists note varchar(512),
    add column if not exists water_absorb_ratio numeric(12, 4);

alter table dispense_record
    add column if not exists prescription_id uuid references prescription(id),
    add column if not exists dispense_flow_pic_url varchar(512);

alter table prescription_audit_record
    add column if not exists audit_flow_pic_url varchar(512);

alter table prescription_recheck_record
    add column if not exists recheck_flow_pic_url varchar(512);

create table if not exists prescription_decoction_profile (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    order_id uuid not null references order_main(id),
    prescription_id uuid not null references prescription(id),
    water_sum varchar(64),
    real_water varchar(64),
    soak_operator varchar(64),
    soak_time_start timestamptz,
    soak_time_end timestamptz,
    boil_status varchar(32),
    boil_solution varchar(512),
    boil_time_one varchar(64),
    boil_time_two varchar(64),
    boil_time_start timestamptz,
    boil_time_end timestamptz,
    boil_operator varchar(64),
    boil_recheck_operator varchar(64),
    out_med_time_start timestamptz,
    out_med_time_end timestamptz,
    out_med_operator varchar(64),
    pack_time_start timestamptz,
    pack_time_end timestamptz,
    pack_operator varchar(64),
    profile_payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_prescription_decoction_profile unique (tenant_id, prescription_id)
);

create index if not exists idx_order_main_legacy_company_time
    on order_main (tenant_id, legacy_company_num, order_time desc);

create index if not exists idx_order_main_storage_created
    on order_main (tenant_id, storage_type, created_at desc);

create index if not exists idx_prescription_patient_gender_age
    on prescription (tenant_id, patient_gender, patient_age);

create index if not exists idx_dispense_record_prescription
    on dispense_record (tenant_id, prescription_id, dispensed_at desc);

create index if not exists idx_prescription_decoction_profile_order
    on prescription_decoction_profile (tenant_id, order_id, updated_at desc);

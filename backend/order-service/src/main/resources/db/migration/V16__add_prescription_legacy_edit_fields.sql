alter table prescription
    add column if not exists boil_times int,
    add column if not exists is_within smallint,
    add column if not exists per_pack_num smallint,
    add column if not exists per_pack_dose int;


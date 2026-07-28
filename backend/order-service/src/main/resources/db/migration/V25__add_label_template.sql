create table if not exists label_template (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    template_code varchar(64) not null,
    template_name varchar(128) not null,
    scope_type varchar(64) not null default 'GLOBAL',
    institution_id uuid references institution(id),
    prescription_type varchar(64),
    label_width_mm int not null default 90,
    label_height_mm int not null default 60,
    content_template text not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_label_template_code unique (tenant_id, template_code)
);

create index if not exists idx_label_template_enabled
    on label_template (tenant_id, enabled, updated_at desc);

insert into label_template (
    id, tenant_id, template_code, template_name, scope_type, prescription_type,
    label_width_mm, label_height_mm, content_template, enabled
)
values (
    '11111111-2222-3333-4444-000000000501',
    '11111111-1111-1111-1111-111111111111',
    'default-decoction-label',
    '默认代煎处方标签',
    'GLOBAL',
    'DECOCTION',
    90,
    60,
    '机构：{{institutionName}}\n处方：{{prescriptionNo}}\n患者：{{patientName}}\n剂数：{{doseCount}}\n配送：{{deliveryTime}}\n地址：{{receiverAddress}}',
    true
)
on conflict (tenant_id, template_code) do nothing;

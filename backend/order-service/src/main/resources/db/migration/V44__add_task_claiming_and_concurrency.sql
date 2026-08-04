alter table integration_message
    add column if not exists version integer not null default 0;

update integration_message m
set tenant_id = o.tenant_id
from order_main o
where m.tenant_id is null
  and m.business_key is not null
  and (o.order_no = m.business_key or o.external_order_no = m.business_key);

update integration_message
set tenant_id = (select id from tenant order by created_at, id limit 1)
where tenant_id is null;

alter table integration_message
    alter column tenant_id set not null;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_integration_message_tenant'
    ) then
        alter table integration_message
            add constraint fk_integration_message_tenant foreign key (tenant_id) references tenant(id);
    end if;
end $$;

alter table integration_retry_task
    add column if not exists tenant_id uuid,
    add column if not exists claimed_by varchar(128),
    add column if not exists claimed_at timestamptz,
    add column if not exists claim_expires_at timestamptz,
    add column if not exists last_error text,
    add column if not exists version integer not null default 0;

update integration_retry_task t
set tenant_id = m.tenant_id
from integration_message m
where t.message_id = m.id
  and t.tenant_id is null;

alter table integration_retry_task
    alter column tenant_id set not null;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_integration_retry_task_tenant'
    ) then
        alter table integration_retry_task
            add constraint fk_integration_retry_task_tenant foreign key (tenant_id) references tenant(id);
    end if;
end $$;

create index if not exists idx_integration_retry_claimable
    on integration_retry_task (task_status, next_retry_at, claim_expires_at, created_at);

alter table callback_record
    add column if not exists claimed_by varchar(128),
    add column if not exists claimed_at timestamptz,
    add column if not exists claim_expires_at timestamptz,
    add column if not exists last_error text,
    add column if not exists version integer not null default 0;

create index if not exists idx_callback_record_claimable
    on callback_record (status, next_retry_at, claim_expires_at, created_at);

alter table workflow_task
    add column if not exists claimed_by varchar(128),
    add column if not exists claimed_at timestamptz,
    add column if not exists claim_expires_at timestamptz,
    add column if not exists version integer not null default 0;

create index if not exists idx_workflow_task_claimable
    on workflow_task (tenant_id, task_status, claim_expires_at, created_at);

alter table prescription
    add column if not exists version integer not null default 0;

alter table decoction_task
    add column if not exists version integer not null default 0;

alter table shipment
    add column if not exists version integer not null default 0;

alter table sms_send_record
    add column if not exists version integer not null default 0;

alter table label_print_record
    add column if not exists version integer not null default 0,
    add column if not exists updated_at timestamptz not null default now();

alter table export_task
    add column if not exists version integer not null default 0;

alter table operation_log
    add column if not exists request_id varchar(128),
    add column if not exists client_ip varchar(128),
    add column if not exists before_snapshot jsonb,
    add column if not exists after_snapshot jsonb,
    add column if not exists updated_at timestamptz not null default now();

create index if not exists idx_operation_log_request
    on operation_log (request_id)
    where request_id is not null;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_shipment_tenant'
    ) then
        alter table shipment
            add constraint fk_shipment_tenant foreign key (tenant_id) references tenant(id);
    end if;
    if not exists (
        select 1 from pg_constraint where conname = 'fk_shipment_order'
    ) then
        alter table shipment
            add constraint fk_shipment_order foreign key (order_id) references order_main(id);
    end if;
    if not exists (
        select 1 from pg_constraint where conname = 'fk_shipment_trace_tenant'
    ) then
        alter table shipment_trace
            add constraint fk_shipment_trace_tenant foreign key (tenant_id) references tenant(id);
    end if;
    if not exists (
        select 1 from pg_constraint where conname = 'fk_shipment_trace_order'
    ) then
        alter table shipment_trace
            add constraint fk_shipment_trace_order foreign key (order_id) references order_main(id);
    end if;
    if not exists (
        select 1 from pg_constraint where conname = 'fk_callback_record_tenant'
    ) then
        alter table callback_record
            add constraint fk_callback_record_tenant foreign key (tenant_id) references tenant(id);
    end if;
    if not exists (
        select 1 from pg_constraint where conname = 'fk_callback_record_order'
    ) then
        alter table callback_record
            add constraint fk_callback_record_order foreign key (order_id) references order_main(id);
    end if;
end $$;

create index if not exists idx_order_main_admin_created
    on order_main (tenant_id, created_at desc, id, institution_id);

create index if not exists idx_prescription_admin_order_no
    on prescription (tenant_id, order_id, prescription_no desc);

create index if not exists idx_shipment_trace_tenant_shipment_created
    on shipment_trace (tenant_id, shipment_id, created_at desc);

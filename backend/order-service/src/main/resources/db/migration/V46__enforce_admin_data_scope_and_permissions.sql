alter table message_consume_log
    add column if not exists tenant_id uuid;

update message_consume_log m
set tenant_id = o.tenant_id
from event_outbox o
where o.event_id = m.event_id
  and m.tenant_id is null;

update message_consume_log
set tenant_id = (select id from tenant order by created_at, id limit 1)
where tenant_id is null;

alter table message_consume_log
    alter column tenant_id set not null;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_message_consume_log_tenant'
    ) then
        alter table message_consume_log
            add constraint fk_message_consume_log_tenant foreign key (tenant_id) references tenant(id);
    end if;
end
$$;

create index if not exists idx_message_consume_log_tenant_updated
    on message_consume_log (tenant_id, updated_at desc);

alter table dead_letter_record
    add column if not exists tenant_id uuid;

update dead_letter_record d
set tenant_id = o.tenant_id
from event_outbox o
where o.event_id = d.event_id
  and d.tenant_id is null;

update dead_letter_record
set tenant_id = (select id from tenant order by created_at, id limit 1)
where tenant_id is null;

alter table dead_letter_record
    alter column tenant_id set not null;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_dead_letter_record_tenant'
    ) then
        alter table dead_letter_record
            add constraint fk_dead_letter_record_tenant foreign key (tenant_id) references tenant(id);
    end if;
end
$$;

create index if not exists idx_dead_letter_record_tenant_updated
    on dead_letter_record (tenant_id, updated_at desc);

insert into admin_permission (
    id, permission_code, permission_name, resource_type, http_method, resource_pattern
)
select
    md5('admin-permission:' || p.permission_code)::uuid,
    p.permission_code,
    p.permission_name,
    'API',
    p.http_method,
    p.resource_pattern
from (values
    ('order:read', '订单查询', 'GET', '/order-api/api/admin/**'),
    ('order:write', '订单操作', null, '/order-api/api/admin/**'),
    ('order:export', '订单导出', null, '/order-api/api/admin/export-tasks/**'),
    ('system:read', '系统配置查询', 'GET', '/order-api/api/admin/{operators,dict,system}/**'),
    ('system:write', '系统配置维护', null, '/order-api/api/admin/{operators,dict,system}/**'),
    ('drug:read', '药品资料查询', 'GET', '/order-api/api/admin/{herbs,herb-*}/**'),
    ('drug:write', '药品资料维护', null, '/order-api/api/admin/{herbs,herb-*}/**'),
    ('institution:read', '机构资料查询', 'GET', '/order-api/api/admin/{institutions,institution-*}/**'),
    ('institution:write', '机构资料维护', null, '/order-api/api/admin/{institutions,institution-*}/**'),
    ('workflow:read', '工作流任务查询', 'GET', '/workflow-api/api/admin/workflow/**'),
    ('workflow:write', '工作流任务处理', null, '/workflow-api/api/admin/workflow/**'),
    ('sms:read', '短信记录查询', 'GET', '/message-api/api/admin/sms/**'),
    ('sms:write', '短信模板维护', null, '/message-api/api/admin/sms/templates/**'),
    ('sms:send', '短信发送', 'POST', '/message-api/api/admin/sms/send-single'),
    ('decoction:read', '煎药配置查询', 'GET', '/decoction-api/admin/decoction/**'),
    ('decoction:write', '煎药配置维护', null, '/decoction-api/admin/decoction/**'),
    ('ops:read', '运维数据查询', 'GET', '/ops-api/api/admin/ops/**'),
    ('ops:write', '运维任务处理', null, '/ops-api/api/admin/ops/**'),
    ('logistics:read', '物流数据查询', 'GET', '/logistics-api/api/admin/logistics/**'),
    ('logistics:write', '物流任务处理', null, '/logistics-api/api/admin/logistics/**'),
    ('callback:read', '回调记录查询', 'GET', '/callback-api/api/admin/callback-records/**'),
    ('callback:replay', '回调重放', null, '/callback-api/api/admin/callback-records/**'),
    ('portal:read', '门户订单查询', 'GET', '/portal-api/api/portal/**'),
    ('portal:write', '门户地址补录', null, '/portal-api/api/portal/**'),
    ('report:read', '报表查询', 'GET', '/report-api/api/admin/reports/**'),
    ('report:export', '报表导出', 'GET', '/report-api/api/admin/reports/*.csv'),
    ('integration:read', '集成记录查询', 'GET', '/integration-api/api/admin/integration/**'),
    ('integration:write', '集成模拟操作', null, '/integration-api/api/integration/**'),
    ('integration:retry', '集成任务重试', null, '/integration-api/api/admin/integration/retry-tasks/**')
) as p(permission_code, permission_name, http_method, resource_pattern)
on conflict (permission_code) do update
set permission_name = excluded.permission_name,
    resource_type = excluded.resource_type,
    http_method = excluded.http_method,
    resource_pattern = excluded.resource_pattern,
    enabled = true,
    updated_at = now();

insert into admin_role_permission (id, tenant_id, role_id, permission_id)
select
    md5(r.id::text || ':' || p.id::text)::uuid,
    r.tenant_id,
    r.id,
    p.id
from admin_role r
cross join admin_permission p
where r.role_code = 'TENANT_ADMIN'
  and r.enabled = true
  and p.enabled = true
on conflict (tenant_id, role_id, permission_id) do nothing;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'zhyf_admin_scoped') then
        create role zhyf_admin_scoped nologin;
    end if;
    execute format('grant zhyf_admin_scoped to %I', current_user);
end
$$;

grant usage on schema public to zhyf_admin_scoped;
grant select, insert, update, delete on all tables in schema public to zhyf_admin_scoped;
grant usage, select, update on all sequences in schema public to zhyf_admin_scoped;
alter default privileges in schema public
    grant select, insert, update, delete on tables to zhyf_admin_scoped;
alter default privileges in schema public
    grant usage, select, update on sequences to zhyf_admin_scoped;

create or replace function public.zhyf_admin_institution_allowed(p_institution_id uuid)
returns boolean
language sql
stable
as $function$
    select current_setting('zhyf.admin_tenant_wide', true) = 'true'
        or p_institution_id is null
        or p_institution_id = any(
            coalesce(
                string_to_array(
                    nullif(current_setting('zhyf.admin_institution_ids', true), ''),
                    ','
                )::uuid[],
                array[]::uuid[]
            )
        )
$function$;

create or replace function public.zhyf_admin_order_allowed(p_tenant_id uuid, p_order_id uuid)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $function$
    select current_setting('zhyf.admin_tenant_wide', true) = 'true'
        or exists (
            select 1
            from public.order_main o
            where o.tenant_id = p_tenant_id
              and o.id = p_order_id
              and public.zhyf_admin_institution_allowed(o.institution_id)
        )
$function$;

create or replace function public.zhyf_admin_order_key_allowed(p_tenant_id uuid, p_order_key text)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $function$
    select current_setting('zhyf.admin_tenant_wide', true) = 'true'
        or exists (
            select 1
            from public.order_main o
            where o.tenant_id = p_tenant_id
              and (
                  o.id::text = p_order_key
                  or o.order_no = p_order_key
                  or o.external_order_no = p_order_key
              )
              and public.zhyf_admin_institution_allowed(o.institution_id)
        )
$function$;

create or replace function public.zhyf_admin_prescription_allowed(p_tenant_id uuid, p_prescription_id uuid)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $function$
    select current_setting('zhyf.admin_tenant_wide', true) = 'true'
        or exists (
            select 1
            from public.prescription p
            where p.tenant_id = p_tenant_id
              and p.id = p_prescription_id
              and public.zhyf_admin_institution_allowed(p.institution_id)
        )
$function$;

create or replace function public.zhyf_admin_order_merge_allowed(p_tenant_id uuid, p_merge_id uuid)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $function$
    select current_setting('zhyf.admin_tenant_wide', true) = 'true'
        or exists (
            select 1
            from public.order_merge_item i
            where i.tenant_id = p_tenant_id
              and i.merge_id = p_merge_id
              and public.zhyf_admin_order_allowed(i.tenant_id, i.order_id)
        )
$function$;

create or replace function public.zhyf_admin_integration_source_allowed(
    p_tenant_id uuid,
    p_source_type text,
    p_source_system text
)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $function$
    select current_setting('zhyf.admin_tenant_wide', true) = 'true'
        or exists (
            select 1
            from public.integration_source_binding b
            where b.tenant_id = p_tenant_id
              and b.source_type = p_source_type
              and b.source_system = p_source_system
              and b.enabled = true
              and public.zhyf_admin_institution_allowed(b.institution_id)
        )
$function$;

revoke all on function public.zhyf_admin_order_allowed(uuid, uuid) from public;
revoke all on function public.zhyf_admin_order_key_allowed(uuid, text) from public;
revoke all on function public.zhyf_admin_prescription_allowed(uuid, uuid) from public;
revoke all on function public.zhyf_admin_order_merge_allowed(uuid, uuid) from public;
revoke all on function public.zhyf_admin_integration_source_allowed(uuid, text, text) from public;
grant execute on function public.zhyf_admin_institution_allowed(uuid) to zhyf_admin_scoped;
grant execute on function public.zhyf_admin_order_allowed(uuid, uuid) to zhyf_admin_scoped;
grant execute on function public.zhyf_admin_order_key_allowed(uuid, text) to zhyf_admin_scoped;
grant execute on function public.zhyf_admin_prescription_allowed(uuid, uuid) to zhyf_admin_scoped;
grant execute on function public.zhyf_admin_order_merge_allowed(uuid, uuid) to zhyf_admin_scoped;
grant execute on function public.zhyf_admin_integration_source_allowed(uuid, text, text) to zhyf_admin_scoped;

do $$
declare
    table_row record;
    scope_expression text;
    relation_expression text;
begin
    for table_row in
        select c.table_schema, c.table_name
        from information_schema.columns c
        join information_schema.tables t
          on t.table_schema = c.table_schema
         and t.table_name = c.table_name
         and t.table_type = 'BASE TABLE'
        where c.table_schema = 'public'
          and c.column_name = 'tenant_id'
        order by c.table_name
    loop
        scope_expression := format(
            'tenant_id = nullif(current_setting(''zhyf.admin_tenant_id'', true), '''')::uuid'
        );
        relation_expression := null;

        if table_row.table_name = 'institution' then
            relation_expression := 'public.zhyf_admin_institution_allowed(id)';
        elsif table_row.table_name = 'order_merge' then
            relation_expression := 'public.zhyf_admin_order_merge_allowed(tenant_id, id)';
        elsif table_row.table_name = 'sms_send_record' then
            relation_expression := 'public.zhyf_admin_order_key_allowed(tenant_id, related_order_no)';
        elsif table_row.table_name in ('event_outbox', 'message_consume_log', 'dead_letter_record') then
            relation_expression := 'public.zhyf_admin_order_key_allowed(tenant_id, aggregate_id)';
        elsif table_row.table_name = 'integration_message' then
            relation_expression :=
                'public.zhyf_admin_integration_source_allowed(tenant_id, source_type, source_system)';
        elsif table_row.table_name = 'integration_retry_task' then
            relation_expression := 'current_setting(''zhyf.admin_tenant_wide'', true) = ''true''';
        elsif table_row.table_name in ('export_task', 'import_task', 'print_task', 'sms_batch_task') then
            relation_expression := 'current_setting(''zhyf.admin_tenant_wide'', true) = ''true''';
        else
            if exists (
                select 1 from information_schema.columns c
                where c.table_schema = table_row.table_schema
                  and c.table_name = table_row.table_name
                  and c.column_name = 'institution_id'
            ) then
                relation_expression := 'public.zhyf_admin_institution_allowed(institution_id)';
            end if;
            if exists (
                select 1 from information_schema.columns c
                where c.table_schema = table_row.table_schema
                  and c.table_name = table_row.table_name
                  and c.column_name = 'order_id'
            ) then
                relation_expression := concat_ws(
                    ' or ',
                    relation_expression,
                    'public.zhyf_admin_order_allowed(tenant_id, order_id)'
                );
            end if;
            if exists (
                select 1 from information_schema.columns c
                where c.table_schema = table_row.table_schema
                  and c.table_name = table_row.table_name
                  and c.column_name = 'prescription_id'
            ) then
                relation_expression := concat_ws(
                    ' or ',
                    relation_expression,
                    'public.zhyf_admin_prescription_allowed(tenant_id, prescription_id)'
                );
            end if;
        end if;

        if relation_expression is not null then
            scope_expression := scope_expression || ' and (' || relation_expression || ')';
        end if;

        execute format(
            'alter table %I.%I enable row level security',
            table_row.table_schema,
            table_row.table_name
        );
        execute format(
            'drop policy if exists zhyf_admin_data_scope on %I.%I',
            table_row.table_schema,
            table_row.table_name
        );
        execute format(
            'create policy zhyf_admin_data_scope on %I.%I for all to zhyf_admin_scoped using (%s) with check (%s)',
            table_row.table_schema,
            table_row.table_name,
            scope_expression,
            scope_expression
        );
    end loop;
end
$$;

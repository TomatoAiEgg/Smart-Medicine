create or replace function public.zhyf_revoke_admin_user_sessions(
    p_tenant_id uuid,
    p_user_id uuid,
    p_reason text
)
returns void
language sql
security definer
set search_path = pg_catalog, public
as $function$
    update public.admin_auth_session
    set status = 'REVOKED',
        revoked_at = coalesce(revoked_at, now()),
        revoke_reason = left(coalesce(p_reason, 'AUTHORIZATION_CHANGED'), 64),
        updated_at = now(),
        version = version + 1
    where tenant_id = p_tenant_id
      and user_id = p_user_id
      and status = 'ACTIVE'
$function$;

create or replace function public.zhyf_revoke_admin_role_sessions(
    p_tenant_id uuid,
    p_role_id uuid,
    p_reason text
)
returns void
language sql
security definer
set search_path = pg_catalog, public
as $function$
    update public.admin_auth_session s
    set status = 'REVOKED',
        revoked_at = coalesce(s.revoked_at, now()),
        revoke_reason = left(coalesce(p_reason, 'AUTHORIZATION_CHANGED'), 64),
        updated_at = now(),
        version = s.version + 1
    where s.tenant_id = p_tenant_id
      and s.status = 'ACTIVE'
      and exists (
          select 1
          from public.admin_user_role ur
          where ur.tenant_id = p_tenant_id
            and ur.role_id = p_role_id
            and ur.user_id = s.user_id
      )
$function$;

revoke all on function public.zhyf_revoke_admin_user_sessions(uuid, uuid, text) from public;
revoke all on function public.zhyf_revoke_admin_role_sessions(uuid, uuid, text) from public;
grant execute on function public.zhyf_revoke_admin_user_sessions(uuid, uuid, text) to zhyf_admin_scoped;
grant execute on function public.zhyf_revoke_admin_role_sessions(uuid, uuid, text) to zhyf_admin_scoped;

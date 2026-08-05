create table if not exists admin_auth_session (
    id uuid primary key,
    tenant_id uuid not null,
    user_id uuid not null,
    refresh_token_hash varchar(64) not null,
    status varchar(16) not null default 'ACTIVE',
    access_expires_at timestamptz not null,
    refresh_expires_at timestamptz not null,
    last_rotated_at timestamptz not null default now(),
    revoked_at timestamptz,
    revoke_reason varchar(64),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint uk_admin_auth_session_tenant_id unique (tenant_id, id),
    constraint uk_admin_auth_session_refresh_hash unique (refresh_token_hash),
    constraint fk_admin_auth_session_user foreign key (tenant_id, user_id)
        references operator_user(tenant_id, id) on delete cascade,
    constraint ck_admin_auth_session_status check (status in ('ACTIVE', 'REVOKED'))
);

create index if not exists idx_admin_auth_session_user_active
    on admin_auth_session (tenant_id, user_id, refresh_expires_at desc)
    where status = 'ACTIVE';

create index if not exists idx_admin_auth_session_refresh_expiry
    on admin_auth_session (refresh_expires_at)
    where status = 'ACTIVE';

alter table admin_auth_session enable row level security;
drop policy if exists zhyf_admin_data_scope on admin_auth_session;
create policy zhyf_admin_data_scope on admin_auth_session
    for all to zhyf_admin_scoped
    using (false)
    with check (false);
revoke all on admin_auth_session from zhyf_admin_scoped;

create or replace function public.zhyf_revoke_admin_user_sessions(
    p_tenant_id uuid,
    p_user_id uuid,
    p_reason text
)
returns void
language sql
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

create or replace function public.zhyf_revoke_sessions_on_user_security_change()
returns trigger
language plpgsql
as $function$
begin
    if old.enabled is distinct from new.enabled
        or old.password_hash is distinct from new.password_hash
        or old.password_algorithm is distinct from new.password_algorithm
        or old.credential_status is distinct from new.credential_status then
        perform public.zhyf_revoke_admin_user_sessions(new.tenant_id, new.id, 'USER_SECURITY_CHANGED');
    end if;
    return new;
end
$function$;

create or replace function public.zhyf_revoke_sessions_on_user_scope_change()
returns trigger
language plpgsql
as $function$
begin
    if tg_op = 'DELETE' then
        perform public.zhyf_revoke_admin_user_sessions(
            old.tenant_id,
            old.user_id,
            'USER_AUTHORIZATION_CHANGED'
        );
        return old;
    end if;
    perform public.zhyf_revoke_admin_user_sessions(
        new.tenant_id,
        new.user_id,
        'USER_AUTHORIZATION_CHANGED'
    );
    return new;
end
$function$;

create or replace function public.zhyf_revoke_sessions_on_role_change()
returns trigger
language plpgsql
as $function$
begin
    if tg_op = 'DELETE' then
        perform public.zhyf_revoke_admin_role_sessions(
            old.tenant_id,
            old.role_id,
            'ROLE_AUTHORIZATION_CHANGED'
        );
        return old;
    end if;
    perform public.zhyf_revoke_admin_role_sessions(
        new.tenant_id,
        new.role_id,
        'ROLE_AUTHORIZATION_CHANGED'
    );
    return new;
end
$function$;

create or replace function public.zhyf_revoke_sessions_on_role_definition_change()
returns trigger
language plpgsql
as $function$
begin
    if old.role_code is distinct from new.role_code
        or old.data_scope_type is distinct from new.data_scope_type
        or old.enabled is distinct from new.enabled then
        perform public.zhyf_revoke_admin_role_sessions(new.tenant_id, new.id, 'ROLE_DEFINITION_CHANGED');
    end if;
    return new;
end
$function$;

drop trigger if exists trg_operator_user_revoke_sessions on operator_user;
create trigger trg_operator_user_revoke_sessions
after update of enabled, password_hash, password_algorithm, credential_status on operator_user
for each row execute function public.zhyf_revoke_sessions_on_user_security_change();

drop trigger if exists trg_admin_user_role_revoke_sessions on admin_user_role;
create trigger trg_admin_user_role_revoke_sessions
after insert or update or delete on admin_user_role
for each row execute function public.zhyf_revoke_sessions_on_user_scope_change();

drop trigger if exists trg_admin_user_scope_revoke_sessions on admin_user_institution_scope;
create trigger trg_admin_user_scope_revoke_sessions
after insert or update or delete on admin_user_institution_scope
for each row execute function public.zhyf_revoke_sessions_on_user_scope_change();

drop trigger if exists trg_admin_role_permission_revoke_sessions on admin_role_permission;
create trigger trg_admin_role_permission_revoke_sessions
after insert or update or delete on admin_role_permission
for each row execute function public.zhyf_revoke_sessions_on_role_change();

drop trigger if exists trg_admin_role_scope_revoke_sessions on admin_role_institution_scope;
create trigger trg_admin_role_scope_revoke_sessions
after insert or update or delete on admin_role_institution_scope
for each row execute function public.zhyf_revoke_sessions_on_role_change();

drop trigger if exists trg_admin_role_revoke_sessions on admin_role;
create trigger trg_admin_role_revoke_sessions
after update of role_code, data_scope_type, enabled on admin_role
for each row execute function public.zhyf_revoke_sessions_on_role_definition_change();

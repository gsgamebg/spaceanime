-- SpaceAnime admin panel, phase 0: staff roles, MFA-gated identity check, audit trail,
-- and removal of the obsolete Stripe sandbox objects. Review, then run once in the Supabase SQL editor.
-- The Stripe sandbox tables held no data (checked before writing this migration).

create schema if not exists spaceanime_private;
revoke all on schema spaceanime_private from public, anon, authenticated;
grant usage on schema spaceanime_private to service_role;

-- ------------------------------------------------------------ Stripe sandbox cleanup
drop function if exists public.record_stripe_sandbox_event(text, text);
drop function if exists public.begin_stripe_sandbox_reconciliation(text, text, uuid);
drop function if exists public.commit_stripe_sandbox_reconciliation(text, text, uuid, bigint, boolean, text, text);
drop function if exists public.find_stripe_sandbox_customer_owner(text);
drop function if exists public.find_stripe_sandbox_customer(uuid);
drop function if exists public.bind_stripe_sandbox_customer(uuid, text);
drop function if exists public.authorize_financial_read();
drop table if exists spaceanime_private.sandbox_reconciliation_heads;
drop table if exists spaceanime_private.sandbox_entitlements;
drop table if exists spaceanime_private.sandbox_customer_links;

-- ------------------------------------------------------------ Staff roles
-- owner: everything. finance: money, VIP, ads. support: users and tickets. moderator: reports and comments.
-- Roles are assigned only here, by SQL, never from the app or the panel.
alter table spaceanime_private.staff_roles drop constraint if exists staff_roles_role_check;
delete from spaceanime_private.staff_roles where role not in ('owner', 'finance', 'support', 'moderator');
alter table spaceanime_private.staff_roles
  add constraint staff_roles_role_check check (role in ('owner', 'finance', 'support', 'moderator'));

-- ------------------------------------------------------------ Audit trail
alter table spaceanime_private.staff_audit add column if not exists detail jsonb;
create index if not exists staff_audit_created_idx on spaceanime_private.staff_audit (created_at desc);

-- Who am I? Called by the backend with the staff member's own token. Roles are returned only when
-- the session passed the second factor (aal2); otherwise the caller learns nothing about its roles.
create or replace function public.spaceanime_staff_me()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_aal2 boolean := coalesce((select auth.jwt())->>'aal', '') = 'aal2';
begin
  if v_uid is null then return null; end if;
  return jsonb_build_object(
    'user_id', v_uid,
    'aal2', v_aal2,
    'roles', case when v_aal2 then coalesce(
      (select jsonb_agg(r.role order by r.role) from spaceanime_private.staff_roles r where r.user_id = v_uid),
      '[]'::jsonb) else '[]'::jsonb end
  );
end;
$$;
revoke all on function public.spaceanime_staff_me() from public, anon;
grant execute on function public.spaceanime_staff_me() to authenticated;

-- Audit writes and reads: backend only (service role).
create or replace function public.spaceanime_staff_audit_write(p_actor uuid, p_action text, p_target_type text, p_target_id text, p_detail jsonb)
returns void
language sql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
  insert into spaceanime_private.staff_audit (actor_id, action, target_type, target_id, detail)
  values (p_actor, left(p_action, 80), left(p_target_type, 80), left(p_target_id, 200), p_detail)
$$;

create or replace function public.spaceanime_staff_audit_list(p_limit integer default 100)
returns jsonb
language sql
stable
security definer
set search_path = pg_catalog, spaceanime_private
as $$
  select coalesce(jsonb_agg(row_to_json(a)), '[]'::jsonb) from (
    select id, actor_id, action, target_type, target_id, detail, created_at
      from spaceanime_private.staff_audit order by created_at desc, id desc limit greatest(1, least(coalesce(p_limit, 100), 500))
  ) a
$$;

revoke all on function public.spaceanime_staff_audit_write(uuid, text, text, text, jsonb) from public, anon, authenticated;
revoke all on function public.spaceanime_staff_audit_list(integer) from public, anon, authenticated;
grant execute on function public.spaceanime_staff_audit_write(uuid, text, text, text, jsonb) to service_role;
grant execute on function public.spaceanime_staff_audit_list(integer) to service_role;
grant select, insert on spaceanime_private.staff_audit to service_role;
grant usage on all sequences in schema spaceanime_private to service_role;

-- ------------------------------------------------------------ First owner (edit the e-mail, run once)
-- insert into spaceanime_private.staff_roles (user_id, role)
-- select id, 'owner' from auth.users where email = 'SEU-EMAIL@exemplo.com'
-- on conflict do nothing;

-- Admin panel, step 1A: read-only summaries for the overview, finance and ads screens.
-- Backend only (service role). Nothing here writes data; e-mails are masked before leaving the database.

create or replace function spaceanime_private.mask_email(p_email text)
returns text language sql immutable
set search_path = pg_catalog
as $$
  select case when p_email is null or position('@' in p_email) = 0 then null
    else left(split_part(p_email, '@', 1), 2) || '***@' || split_part(p_email, '@', 2) end
$$;

create or replace function public.spaceanime_admin_overview()
returns jsonb
language sql
stable
security definer
set search_path = pg_catalog, spaceanime_private
as $$
  select jsonb_build_object(
    'generated_at', now(),
    'users', jsonb_build_object(
      'total', (select count(*) from auth.users),
      'new_7d', (select count(*) from auth.users where created_at > now() - interval '7 days'),
      'new_30d', (select count(*) from auth.users where created_at > now() - interval '30 days')),
    'active', jsonb_build_object(
      'd1', (select count(distinct install_id) from spaceanime_private.telemetry_sessions where received_at > now() - interval '1 day'),
      'd7', (select count(distinct install_id) from spaceanime_private.telemetry_sessions where received_at > now() - interval '7 days'),
      'd30', (select count(distinct install_id) from spaceanime_private.telemetry_sessions where received_at > now() - interval '30 days')),
    'vip', jsonb_build_object(
      'active_total', (select count(*) from spaceanime_private.vip_memberships where expires_at > now()),
      'by_plan', coalesce((select jsonb_object_agg(plan, n) from (
        select plan, count(*) as n from spaceanime_private.vip_memberships where expires_at > now() group by plan) p), '{}'::jsonb)),
    'revenue', jsonb_build_object(
      'month_cents', coalesce((select sum(amount_cents) from spaceanime_private.vip_orders
        where status = 'paid' and paid_at >= date_trunc('month', now())), 0),
      'last30_cents', coalesce((select sum(amount_cents) from spaceanime_private.vip_orders
        where status = 'paid' and paid_at > now() - interval '30 days'), 0),
      'total_cents', coalesce((select sum(amount_cents) from spaceanime_private.vip_orders where status = 'paid'), 0),
      'paid_orders_month', (select count(*) from spaceanime_private.vip_orders
        where status = 'paid' and paid_at >= date_trunc('month', now()))),
    'orders', jsonb_build_object(
      'open', (select count(*) from spaceanime_private.vip_orders
        where status in ('created', 'pending') and created_at > now() - interval '40 minutes'),
      'abandoned_30d', (select count(*) from spaceanime_private.vip_orders
        where status in ('created', 'pending', 'expired') and created_at > now() - interval '30 days'
          and created_at <= now() - interval '40 minutes'))
  )
$$;

create or replace function public.spaceanime_admin_finance(p_limit integer default 50)
returns jsonb
language sql
stable
security definer
set search_path = pg_catalog, spaceanime_private
as $$
  select jsonb_build_object(
    'generated_at', now(),
    'by_status_30d', coalesce((select jsonb_object_agg(status, n) from (
      select status, count(*) as n from spaceanime_private.vip_orders
       where created_at > now() - interval '30 days' group by status) s), '{}'::jsonb),
    -- Should always be 0: a paid order whose buyer has no membership means a failed activation.
    'paid_without_membership', (select count(*) from spaceanime_private.vip_orders o
      where o.status = 'paid' and not exists (select 1 from spaceanime_private.vip_memberships m where m.user_id = o.user_id)),
    'orders', coalesce((select jsonb_agg(row_to_json(x)) from (
      select o.id, o.plan, o.amount_cents, o.status, o.mp_payment_id, o.created_at, o.paid_at,
             spaceanime_private.mask_email(u.email) as email
        from spaceanime_private.vip_orders o left join auth.users u on u.id = o.user_id
       order by o.created_at desc
       limit greatest(1, least(coalesce(p_limit, 50), 200))) x), '[]'::jsonb)
  )
$$;

create or replace function public.spaceanime_admin_ads_config()
returns jsonb
language sql
stable
security definer
set search_path = pg_catalog, spaceanime_private
as $$
  select coalesce((select jsonb_build_object(
    'enabled', c.ads_enabled, 'provider', c.ads_provider,
    'home_banner', c.home_banner, 'catalog_banner', c.catalog_banner,
    'interstitial_enabled', c.interstitial_enabled, 'interstitial_every_episodes', c.interstitial_every_episodes,
    'rewarded_enabled', c.rewarded_enabled, 'rewarded_pass_minutes', c.rewarded_pass_minutes,
    'updated_at', c.updated_at)
    from spaceanime_private.app_runtime_config c where c.singleton = true), '{}'::jsonb)
$$;

revoke all on function spaceanime_private.mask_email(text) from public, anon, authenticated;
revoke all on function public.spaceanime_admin_overview() from public, anon, authenticated;
revoke all on function public.spaceanime_admin_finance(integer) from public, anon, authenticated;
revoke all on function public.spaceanime_admin_ads_config() from public, anon, authenticated;
grant execute on function public.spaceanime_admin_overview() to service_role;
grant execute on function public.spaceanime_admin_finance(integer) to service_role;
grant execute on function public.spaceanime_admin_ads_config() to service_role;
grant usage on schema spaceanime_private to service_role;
grant execute on function spaceanime_private.mask_email(text) to service_role;

-- SpaceAnime 0.7.24: VIP via Pix (Mercado Pago) and ad formats controlled remotely.
-- Safe to run more than once. Only the backend (service_role) writes VIP data; the app never does.

create schema if not exists spaceanime_private;
revoke all on schema spaceanime_private from public, anon, authenticated;
grant usage on schema spaceanime_private to service_role;

-- ---------------------------------------------------------------- VIP
create table if not exists spaceanime_private.vip_orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  plan text not null check (plan in ('ouro', 'platina', 'ametista')),
  amount_cents integer not null check (amount_cents in (1000, 2000, 3000)),
  status text not null default 'created' check (status in ('created', 'pending', 'paid', 'expired', 'failed')),
  mp_payment_id bigint unique,
  created_at timestamptz not null default now(),
  paid_at timestamptz
);
create index if not exists vip_orders_user_idx on spaceanime_private.vip_orders (user_id, created_at desc);

create table if not exists spaceanime_private.vip_memberships (
  user_id uuid primary key references auth.users(id) on delete cascade,
  plan text not null check (plan in ('ouro', 'platina', 'ametista')),
  expires_at timestamptz not null,
  updated_at timestamptz not null default now()
);

alter table spaceanime_private.vip_orders enable row level security;
alter table spaceanime_private.vip_memberships enable row level security;
revoke all on table spaceanime_private.vip_orders, spaceanime_private.vip_memberships from public, anon, authenticated;
grant select, insert, update on table spaceanime_private.vip_orders, spaceanime_private.vip_memberships to service_role;

create or replace function spaceanime_private.vip_price(p_plan text)
returns integer language sql immutable
set search_path = pg_catalog
as $$ select case p_plan when 'ouro' then 1000 when 'platina' then 2000 when 'ametista' then 3000 end $$;

-- New order for a plan. Price comes from the database. At most 5 open orders per user per hour.
create or replace function public.spaceanime_vip_create_order(p_user uuid, p_plan text)
returns uuid language plpgsql security definer
set search_path = pg_catalog, spaceanime_private
as $$
declare
  v_price integer := spaceanime_private.vip_price(p_plan);
  v_id uuid;
begin
  if v_price is null then raise exception 'vip_invalid_plan'; end if;
  update spaceanime_private.vip_orders set status = 'expired'
   where user_id = p_user and status in ('created', 'pending') and created_at < now() - interval '1 hour';
  if (select count(*) from spaceanime_private.vip_orders
       where user_id = p_user and status in ('created', 'pending') and created_at > now() - interval '1 hour') >= 5 then
    raise exception 'vip_too_many_orders';
  end if;
  insert into spaceanime_private.vip_orders (user_id, plan, amount_cents) values (p_user, p_plan, v_price) returning id into v_id;
  return v_id;
end $$;

create or replace function public.spaceanime_vip_attach_payment(p_order uuid, p_payment_id bigint)
returns void language plpgsql security definer
set search_path = pg_catalog, spaceanime_private
as $$
begin
  update spaceanime_private.vip_orders set mp_payment_id = p_payment_id, status = 'pending'
   where id = p_order and status = 'created' and mp_payment_id is null;
  if not found then raise exception 'vip_order_not_attachable'; end if;
end $$;

-- Order status for its owner only (null for anyone else).
create or replace function public.spaceanime_vip_order_status(p_order uuid, p_user uuid)
returns jsonb language sql stable security definer
set search_path = pg_catalog, spaceanime_private
as $$
  select jsonb_build_object('status', o.status, 'payment_id', o.mp_payment_id)
    from spaceanime_private.vip_orders o
   where o.id = p_order and o.user_id = p_user
$$;

-- Marks an order paid after the backend read an approved Pix from Mercado Pago, and adds 30 days.
-- Idempotent: a second call for the same order changes nothing.
create or replace function public.spaceanime_vip_confirm_payment(p_order uuid, p_payment_id bigint, p_amount_cents integer)
returns jsonb language plpgsql security definer
set search_path = pg_catalog, spaceanime_private
as $$
declare
  v_order spaceanime_private.vip_orders%rowtype;
begin
  select * into v_order from spaceanime_private.vip_orders where id = p_order for update;
  if not found then return jsonb_build_object('status', 'unknown'); end if;
  if v_order.status = 'paid' then return jsonb_build_object('status', 'paid'); end if;
  if v_order.mp_payment_id is distinct from p_payment_id or v_order.amount_cents <> p_amount_cents then
    return jsonb_build_object('status', 'rejected');
  end if;
  update spaceanime_private.vip_orders set status = 'paid', paid_at = now() where id = p_order;
  insert into spaceanime_private.vip_memberships as m (user_id, plan, expires_at, updated_at)
  values (v_order.user_id, v_order.plan, now() + interval '30 days', now())
  on conflict (user_id) do update
    set plan = excluded.plan,
        expires_at = greatest(m.expires_at, now()) + interval '30 days',
        updated_at = now();
  return jsonb_build_object('status', 'paid');
end $$;

create or replace function public.spaceanime_vip_status(p_user uuid)
returns jsonb language sql stable security definer
set search_path = pg_catalog, spaceanime_private
as $$
  select coalesce(
    (select jsonb_build_object('active', true, 'plan', m.plan, 'expires_at', m.expires_at)
       from spaceanime_private.vip_memberships m
      where m.user_id = p_user and m.expires_at > now()),
    jsonb_build_object('active', false))
$$;

revoke all on function public.spaceanime_vip_create_order(uuid, text) from public, anon, authenticated;
revoke all on function public.spaceanime_vip_attach_payment(uuid, bigint) from public, anon, authenticated;
revoke all on function public.spaceanime_vip_order_status(uuid, uuid) from public, anon, authenticated;
revoke all on function public.spaceanime_vip_confirm_payment(uuid, bigint, integer) from public, anon, authenticated;
revoke all on function public.spaceanime_vip_status(uuid) from public, anon, authenticated;
grant execute on function public.spaceanime_vip_create_order(uuid, text) to service_role;
grant execute on function public.spaceanime_vip_attach_payment(uuid, bigint) to service_role;
grant execute on function public.spaceanime_vip_order_status(uuid, uuid) to service_role;
grant execute on function public.spaceanime_vip_confirm_payment(uuid, bigint, integer) to service_role;
grant execute on function public.spaceanime_vip_status(uuid) to service_role;

-- ---------------------------------------------------------------- Ads (schema 2)
alter table spaceanime_private.app_runtime_config
  add column if not exists ads_provider text not null default 'admob',
  add column if not exists interstitial_enabled boolean not null default false,
  add column if not exists interstitial_every_episodes integer not null default 3,
  add column if not exists rewarded_enabled boolean not null default false,
  add column if not exists rewarded_pass_minutes integer not null default 120;

do $$ begin
  alter table spaceanime_private.app_runtime_config
    add constraint app_runtime_config_provider_check check (ads_provider in ('admob', 'startio')),
    add constraint app_runtime_config_interstitial_check check (interstitial_every_episodes between 1 and 20),
    add constraint app_runtime_config_rewarded_check check (rewarded_pass_minutes between 15 and 1440);
exception when duplicate_object then null; end $$;

-- Schema 1 (spaceanime_public_app_config) stays unchanged for apps up to 0.7.23.
create or replace function public.spaceanime_public_app_config_v2()
returns jsonb language sql stable security definer
set search_path = pg_catalog, spaceanime_private
as $$
  select jsonb_build_object(
    'schemaVersion', 2,
    'ads', jsonb_build_object(
      'enabled', c.ads_enabled,
      'provider', c.ads_provider,
      'homeBanner', c.home_banner,
      'catalogBanner', c.catalog_banner,
      'interstitialEnabled', c.interstitial_enabled,
      'interstitialEveryEpisodes', c.interstitial_every_episodes,
      'rewardedEnabled', c.rewarded_enabled,
      'rewardedPassMinutes', c.rewarded_pass_minutes))
  from spaceanime_private.app_runtime_config c
  where c.singleton = true
$$;
revoke all on function public.spaceanime_public_app_config_v2() from public;
grant execute on function public.spaceanime_public_app_config_v2() to anon, authenticated, service_role;

-- Example: Start.io with banners, an interstitial every 3 episodes and the 2-hour rewarded pass.
-- update spaceanime_private.app_runtime_config
--    set ads_enabled = true, ads_provider = 'startio', home_banner = true, catalog_banner = true,
--        interstitial_enabled = true, interstitial_every_episodes = 3,
--        rewarded_enabled = true, rewarded_pass_minutes = 120, updated_at = now()
--  where singleton = true;

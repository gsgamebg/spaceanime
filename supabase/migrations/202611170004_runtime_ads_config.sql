-- Apply and verify in a development Supabase project before production.
create schema if not exists spaceanime_private;
revoke all on schema spaceanime_private from public, anon, authenticated;

create table spaceanime_private.app_runtime_config (
  singleton boolean primary key default true check (singleton),
  ads_enabled boolean not null default false,
  home_banner boolean not null default false,
  catalog_banner boolean not null default false,
  updated_at timestamptz not null default now()
);

insert into spaceanime_private.app_runtime_config(singleton, ads_enabled, home_banner, catalog_banner)
values (true, false, false, false)
on conflict (singleton) do nothing;

alter table spaceanime_private.app_runtime_config enable row level security;
revoke all on table spaceanime_private.app_runtime_config from public, anon, authenticated;
grant usage on schema spaceanime_private to service_role;
grant select, insert, update, delete on table spaceanime_private.app_runtime_config to service_role;

create or replace function public.spaceanime_public_app_config()
returns jsonb
language sql
stable
security definer
set search_path = pg_catalog, spaceanime_private
as $$
  select jsonb_build_object(
    'schemaVersion', 1,
    'ads', jsonb_build_object(
      'enabled', config.ads_enabled,
      'homeBanner', config.home_banner,
      'catalogBanner', config.catalog_banner
    )
  )
  from spaceanime_private.app_runtime_config as config
  where config.singleton = true;
$$;

revoke all on function public.spaceanime_public_app_config() from public, anon, authenticated;
grant execute on function public.spaceanime_public_app_config() to anon, authenticated;

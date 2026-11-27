-- Admin panel, step 1B: versions and devices (from telemetry) and source health history.
-- Read functions are backend-only. No IP address leaves the database; devices are aggregated.

-- ------------------------------------------------------------ Source health history
create table if not exists spaceanime_private.source_health_runs (
  id bigint generated always as identity primary key,
  run_at timestamptz not null default now(),
  name text not null check (char_length(name) between 1 and 60),
  ok boolean not null,
  ms integer not null check (ms between 0 and 600000),
  detail text check (char_length(detail) <= 200)
);
create index if not exists source_health_runs_name_idx on spaceanime_private.source_health_runs (name, run_at desc);
create index if not exists source_health_runs_run_idx on spaceanime_private.source_health_runs (run_at);
alter table spaceanime_private.source_health_runs enable row level security;
revoke all on table spaceanime_private.source_health_runs from public, anon, authenticated;
grant select, insert, delete on table spaceanime_private.source_health_runs to service_role;

-- One monitor run = a batch of checks. Keeps 30 days.
create or replace function public.spaceanime_source_health_record(p_checks jsonb)
returns integer
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
declare v_count integer;
begin
  if jsonb_typeof(p_checks) <> 'array' or jsonb_array_length(p_checks) not between 1 and 30 then
    raise exception 'source_health_invalid';
  end if;
  insert into spaceanime_private.source_health_runs (name, ok, ms, detail)
  select left(c->>'name', 60), (c->>'ok')::boolean, least(greatest((c->>'ms')::integer, 0), 600000), left(c->>'detail', 200)
    from jsonb_array_elements(p_checks) c;
  get diagnostics v_count = row_count;
  delete from spaceanime_private.source_health_runs where run_at < now() - interval '30 days';
  return v_count;
end $$;

create or replace function public.spaceanime_admin_sources()
returns jsonb
language sql
stable
security definer
set search_path = pg_catalog, spaceanime_private
as $$
  select jsonb_build_object(
    'generated_at', now(),
    'latest', coalesce((select jsonb_agg(row_to_json(l) order by l.name) from (
      select distinct on (name) name, ok, ms, detail, run_at
        from spaceanime_private.source_health_runs order by name, run_at desc) l), '[]'::jsonb),
    'stats_7d', coalesce((select jsonb_agg(row_to_json(s) order by s.name) from (
      select name, count(*) as total, count(*) filter (where ok) as ok_count, round(avg(ms))::integer as avg_ms
        from spaceanime_private.source_health_runs where run_at > now() - interval '7 days' group by name) s), '[]'::jsonb),
    'recent_failures', coalesce((select jsonb_agg(row_to_json(f)) from (
      select name, ms, detail, run_at from spaceanime_private.source_health_runs
       where not ok order by run_at desc limit 20) f), '[]'::jsonb)
  )
$$;

-- ------------------------------------------------------------ Versions and devices
create or replace function public.spaceanime_admin_versions()
returns jsonb
language sql
stable
security definer
set search_path = pg_catalog, spaceanime_private
as $$
  with latest as (
    select distinct on (install_id) install_id, platform, app_version, app_code, os_version, device_model, locale, received_at
      from spaceanime_private.telemetry_sessions
     where received_at > now() - interval '30 days'
     order by install_id, received_at desc)
  select jsonb_build_object(
    'generated_at', now(),
    'installs_30d', (select count(*) from latest),
    'newest_code', (select max(app_code) from latest),
    'by_version', coalesce((select jsonb_agg(row_to_json(v) order by v.app_code desc, v.platform) from (
      select app_version, app_code, platform, count(*) as n from latest group by app_version, app_code, platform) v), '[]'::jsonb),
    'by_platform', coalesce((select jsonb_object_agg(platform, n) from (
      select platform, count(*) as n from latest group by platform) p), '{}'::jsonb),
    'top_devices', coalesce((select jsonb_agg(row_to_json(d)) from (
      select device_model, count(*) as n from latest where device_model is not null
       group by device_model order by n desc, device_model limit 10) d), '[]'::jsonb),
    'top_os', coalesce((select jsonb_agg(row_to_json(o)) from (
      select platform, os_version, count(*) as n from latest where os_version is not null
       group by platform, os_version order by n desc, os_version limit 8) o), '[]'::jsonb),
    'top_locales', coalesce((select jsonb_agg(row_to_json(l)) from (
      select locale, count(*) as n from latest where locale is not null
       group by locale order by n desc, locale limit 6) l), '[]'::jsonb)
  )
$$;

revoke all on function public.spaceanime_source_health_record(jsonb) from public, anon, authenticated;
revoke all on function public.spaceanime_admin_sources() from public, anon, authenticated;
revoke all on function public.spaceanime_admin_versions() from public, anon, authenticated;
grant execute on function public.spaceanime_source_health_record(jsonb) to service_role;
grant execute on function public.spaceanime_admin_sources() to service_role;
grant execute on function public.spaceanime_admin_versions() to service_role;
grant usage on all sequences in schema spaceanime_private to service_role;

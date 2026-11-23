-- Basic telemetry: one row per app launch (at most every 6h per install). Apply in a development project first.
-- Personal data: IP address. Rows are kept for 90 days and are only reachable by service_role.
create schema if not exists spaceanime_private;
revoke all on schema spaceanime_private from public, anon, authenticated;

create table spaceanime_private.telemetry_sessions (
  id bigint generated always as identity primary key,
  install_id uuid not null,
  received_at timestamptz not null default now(),
  ip inet,
  platform text not null check (platform in ('android', 'windows')),
  app_version text not null check (app_version ~ '^[0-9]{1,4}\.[0-9]{1,4}\.[0-9]{1,4}$'),
  app_code integer not null check (app_code > 0),
  os_version text check (char_length(os_version) <= 64),
  device_model text check (char_length(device_model) <= 96),
  locale text check (char_length(locale) <= 16),
  time_zone text check (char_length(time_zone) <= 64)
);
create index telemetry_sessions_install_idx on spaceanime_private.telemetry_sessions (install_id, received_at desc);
create index telemetry_sessions_received_idx on spaceanime_private.telemetry_sessions (received_at);

alter table spaceanime_private.telemetry_sessions enable row level security;
revoke all on table spaceanime_private.telemetry_sessions from public, anon, authenticated;
grant usage on schema spaceanime_private to service_role;
grant select, insert, delete on table spaceanime_private.telemetry_sessions to service_role;

create or replace function public.spaceanime_record_telemetry(p_event jsonb, p_ip text)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
declare
  v_install uuid := (p_event->>'install_id')::uuid;
  v_ip inet := case when p_ip is null or p_ip = '' then null else p_ip::inet end;
begin
  -- Retention: drop rows older than 90 days in small batches.
  delete from spaceanime_private.telemetry_sessions
  where id in (select id from spaceanime_private.telemetry_sessions where received_at < now() - interval '90 days' limit 500);

  if exists (
    select 1 from spaceanime_private.telemetry_sessions
    where install_id = v_install and received_at > now() - interval '6 hours'
  ) then
    return false;
  end if;

  insert into spaceanime_private.telemetry_sessions
    (install_id, ip, platform, app_version, app_code, os_version, device_model, locale, time_zone)
  values (
    v_install, v_ip,
    p_event->>'platform', p_event->>'app_version', (p_event->>'app_code')::integer,
    nullif(p_event->>'os_version', ''), nullif(p_event->>'device_model', ''),
    nullif(p_event->>'locale', ''), nullif(p_event->>'time_zone', '')
  );
  return true;
end;
$$;

revoke all on function public.spaceanime_record_telemetry(jsonb, text) from public, anon, authenticated;
grant execute on function public.spaceanime_record_telemetry(jsonb, text) to service_role;

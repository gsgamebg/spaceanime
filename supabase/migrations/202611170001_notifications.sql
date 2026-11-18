-- Apply only after review in a development Supabase project.
create schema if not exists spaceanime_private;
revoke all on schema spaceanime_private from public, anon, authenticated;

create table spaceanime_private.notification_devices (
  user_id uuid not null references auth.users(id) on delete cascade,
  device_id text not null check (length(device_id) between 1 and 128),
  push_token text not null check (length(push_token) between 1 and 4096),
  preferences jsonb not null check (
    jsonb_typeof(preferences) = 'object'
    and jsonb_typeof(preferences->'anime') is not distinct from 'boolean'
    and jsonb_typeof(preferences->'manga') is not distinct from 'boolean'
    and jsonb_typeof(preferences->'announcements') is not distinct from 'boolean'
  ),
  time_zone text not null check (length(time_zone) between 1 and 100),
  digest_time time not null default '20:00',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, device_id),
  unique (push_token)
);

create table spaceanime_private.notification_subscriptions (
  user_id uuid not null references auth.users(id) on delete cascade,
  media_type text not null check (media_type in ('anime', 'manga')),
  source_id text not null check (source_id in ('anitube', 'animesdigital', 'dex', 'yomu')),
  content_id text not null check (length(content_id) between 1 and 180),
  audio_variant text not null default 'none' check (audio_variant in ('dubbed', 'subtitled', 'none')),
  created_at timestamptz not null default now(),
  primary key (user_id, media_type, source_id, content_id, audio_variant)
);

create table spaceanime_private.notification_releases (
  id bigint generated always as identity primary key,
  media_type text not null check (media_type in ('anime', 'manga')),
  source_id text not null,
  source_item_id text not null check (length(source_item_id) between 1 and 220),
  title text not null check (length(title) between 1 and 300),
  source_url text not null check (length(source_url) between 1 and 2048),
  observed_at timestamptz not null default now(),
  unique (media_type, source_id, source_item_id)
);

create table spaceanime_private.notification_digest_items (
  user_id uuid not null references auth.users(id) on delete cascade,
  local_date date not null,
  release_id bigint not null references spaceanime_private.notification_releases(id) on delete cascade,
  queued_at timestamptz not null default now(),
  primary key (user_id, local_date, release_id)
);

create table spaceanime_private.notification_digests (
  user_id uuid not null references auth.users(id) on delete cascade,
  local_date date not null,
  status text not null check (status in ('pending', 'sent', 'failed')),
  attempt_count integer not null default 0 check (attempt_count between 0 and 20),
  sent_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (user_id, local_date)
);

create table spaceanime_private.notification_announcements (
  id uuid primary key default gen_random_uuid(),
  message_id text not null unique check (length(message_id) between 1 and 120),
  title text not null check (length(title) between 1 and 160),
  body text not null check (length(body) between 1 and 2000),
  critical boolean not null default false,
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id) on delete set null
);

alter table spaceanime_private.notification_devices enable row level security;
alter table spaceanime_private.notification_subscriptions enable row level security;
alter table spaceanime_private.notification_releases enable row level security;
alter table spaceanime_private.notification_digest_items enable row level security;
alter table spaceanime_private.notification_digests enable row level security;
alter table spaceanime_private.notification_announcements enable row level security;
revoke all on all tables in schema spaceanime_private from public, anon, authenticated;
revoke all on all sequences in schema spaceanime_private from public, anon, authenticated;

create or replace function public.spaceanime_notification_upsert_device(
  p_user_id uuid,
  p_device_id text,
  p_push_token text,
  p_preferences jsonb,
  p_time_zone text,
  p_digest_time time
) returns boolean
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
begin
  if p_user_id is null or length(p_device_id) not between 1 and 128
     or length(p_push_token) not between 1 and 4096
     or jsonb_typeof(p_preferences) <> 'object'
     or jsonb_typeof(p_preferences->'anime') is distinct from 'boolean'
     or jsonb_typeof(p_preferences->'manga') is distinct from 'boolean'
     or jsonb_typeof(p_preferences->'announcements') is distinct from 'boolean'
     or p_time_zone is null or length(p_time_zone) not between 1 and 100
     or p_digest_time is null then
    raise exception 'invalid_notification_device';
  end if;
  insert into notification_devices(user_id, device_id, push_token, preferences, time_zone, digest_time)
  values (p_user_id, p_device_id, p_push_token, p_preferences, p_time_zone, p_digest_time)
  on conflict (user_id, device_id) do update
    set push_token = excluded.push_token,
        preferences = excluded.preferences,
        time_zone = excluded.time_zone,
        digest_time = excluded.digest_time,
        updated_at = now();
  return true;
end;
$$;

create or replace function public.spaceanime_notification_update_preferences(
  p_user_id uuid,
  p_device_id text,
  p_preferences jsonb,
  p_time_zone text,
  p_digest_time time
) returns boolean
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
begin
  if p_user_id is null or length(p_device_id) not between 1 and 128
     or jsonb_typeof(p_preferences) <> 'object'
     or jsonb_typeof(p_preferences->'anime') is distinct from 'boolean'
     or jsonb_typeof(p_preferences->'manga') is distinct from 'boolean'
     or jsonb_typeof(p_preferences->'announcements') is distinct from 'boolean'
     or p_time_zone is null or length(p_time_zone) not between 1 and 100
     or p_digest_time is null then
    raise exception 'invalid_notification_preferences';
  end if;
  update notification_devices
    set preferences = p_preferences,
        time_zone = p_time_zone,
        digest_time = p_digest_time,
        updated_at = now()
    where user_id = p_user_id and device_id = p_device_id;
  return found;
end;
$$;

create or replace function public.spaceanime_notification_delete_device(
  p_user_id uuid,
  p_device_id text
) returns boolean
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
begin
  if p_user_id is null or length(p_device_id) not between 1 and 128 then
    raise exception 'invalid_notification_device';
  end if;
  delete from notification_devices where user_id = p_user_id and device_id = p_device_id;
  if not exists (
    select 1 from notification_devices
    where user_id = p_user_id
      and (
        (preferences->>'anime')::boolean
        or (preferences->>'manga')::boolean
        or (preferences->>'announcements')::boolean
      )
  ) then
    delete from notification_subscriptions where user_id = p_user_id;
  end if;
  return true;
end;
$$;

revoke all on function public.spaceanime_notification_upsert_device(uuid, text, text, jsonb, text, time) from public, anon, authenticated;
revoke all on function public.spaceanime_notification_update_preferences(uuid, text, jsonb, text, time) from public, anon, authenticated;
revoke all on function public.spaceanime_notification_delete_device(uuid, text) from public, anon, authenticated;
grant execute on function public.spaceanime_notification_upsert_device(uuid, text, text, jsonb, text, time) to service_role;
grant execute on function public.spaceanime_notification_update_preferences(uuid, text, jsonb, text, time) to service_role;
grant execute on function public.spaceanime_notification_delete_device(uuid, text) to service_role;

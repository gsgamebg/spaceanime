alter table spaceanime_private.notification_releases
  add column if not exists content_id text,
  add column if not exists audio_variant text not null default 'none';

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'notification_releases_content_id_check'
      and conrelid = 'spaceanime_private.notification_releases'::regclass
  ) then
    alter table spaceanime_private.notification_releases
      add constraint notification_releases_content_id_check
      check (content_id is null or length(content_id) between 1 and 180);
  end if;
  if not exists (
    select 1 from pg_constraint
    where conname = 'notification_releases_audio_variant_check'
      and conrelid = 'spaceanime_private.notification_releases'::regclass
  ) then
    alter table spaceanime_private.notification_releases
      add constraint notification_releases_audio_variant_check
      check (audio_variant in ('dubbed', 'subtitled', 'none'));
  end if;
end;
$$;

alter table spaceanime_private.notification_digests
  drop constraint if exists notification_digests_status_check,
  add constraint notification_digests_status_check
    check (status in ('pending', 'processing', 'sent', 'failed'));
alter table spaceanime_private.notification_digests
  add column if not exists lease_until timestamptz;

create table if not exists spaceanime_private.notification_scan_state (
  source_id text primary key check (source_id in ('anitube', 'animesdigital', 'dex')),
  last_claimed_at timestamptz not null
);
alter table spaceanime_private.notification_scan_state enable row level security;
revoke all on spaceanime_private.notification_scan_state from public, anon, authenticated;

create or replace function public.spaceanime_notification_cleanup_user(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
begin
  if p_user_id is null then
    raise exception 'invalid_notification_user';
  end if;

  delete from notification_subscriptions s
  where s.user_id = p_user_id
    and not exists (
      select 1 from notification_devices d
      where d.user_id = s.user_id
        and case s.media_type
          when 'anime' then (d.preferences->>'anime')::boolean
          when 'manga' then (d.preferences->>'manga')::boolean
          else false
        end
    );

  delete from notification_digest_items i
  where i.user_id = p_user_id
    and not exists (
      select 1
      from notification_subscriptions s
      join notification_devices d on d.user_id = s.user_id
      join notification_releases r
        on r.media_type = s.media_type
       and r.source_id = s.source_id
       and r.content_id = s.content_id
       and r.audio_variant = s.audio_variant
      where s.user_id = i.user_id
        and r.id = i.release_id
        and case s.media_type
          when 'anime' then (d.preferences->>'anime')::boolean
          when 'manga' then (d.preferences->>'manga')::boolean
          else false
        end
    );

  delete from notification_digests d
  where d.user_id = p_user_id
    and d.status in ('pending', 'failed')
    and not exists (
      select 1 from notification_digest_items i
      where i.user_id = d.user_id and i.local_date = d.local_date
    );
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
  if p_user_id is null or p_device_id is null or length(p_device_id) not between 1 and 128
     or jsonb_typeof(p_preferences) is distinct from 'object'
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
  if found then
    perform public.spaceanime_notification_cleanup_user(p_user_id);
    return true;
  end if;
  return false;
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
declare
  v_deleted boolean;
begin
  if p_user_id is null or p_device_id is null or length(p_device_id) not between 1 and 128 then
    raise exception 'invalid_notification_device';
  end if;
  delete from notification_devices where user_id = p_user_id and device_id = p_device_id;
  v_deleted := found;
  perform public.spaceanime_notification_cleanup_user(p_user_id);
  return v_deleted;
end;
$$;

create or replace function public.spaceanime_notification_sync_subscriptions(
  p_user_id uuid,
  p_subscriptions jsonb
) returns integer
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
declare
  v_count integer;
begin
  if p_user_id is null or jsonb_typeof(p_subscriptions) is distinct from 'array' then
    raise exception 'invalid_notification_subscriptions';
  end if;
  if jsonb_array_length(p_subscriptions) > 500 then
    raise exception 'invalid_notification_subscriptions';
  end if;
  if exists (
    select 1
    from jsonb_array_elements(p_subscriptions) e(value)
    where jsonb_typeof(e.value) is distinct from 'object'
       or coalesce((select array_agg(k order by k) from jsonb_object_keys(e.value) k),
                   array[]::text[]) <> array['audio_variant', 'content_id', 'media_type', 'source_id']::text[]
       or jsonb_typeof(e.value->'media_type') is distinct from 'string'
       or jsonb_typeof(e.value->'source_id') is distinct from 'string'
       or jsonb_typeof(e.value->'content_id') is distinct from 'string'
       or jsonb_typeof(e.value->'audio_variant') is distinct from 'string'
       or (e.value->>'media_type' = 'anime' and e.value->>'source_id' not in ('anitube', 'animesdigital'))
       or (e.value->>'media_type' = 'manga' and e.value->>'source_id' not in ('dex', 'yomu'))
       or e.value->>'media_type' not in ('anime', 'manga')
       or length(e.value->>'content_id') not between 1 and 180
       or e.value->>'content_id' !~ '^[A-Za-z0-9._:/-]+$'
       or e.value->>'audio_variant' not in ('dubbed', 'subtitled', 'none')
       or (e.value->>'media_type' = 'manga' and e.value->>'audio_variant' <> 'none')
  ) then
    raise exception 'invalid_notification_subscriptions';
  end if;

  delete from notification_subscriptions where user_id = p_user_id;
  insert into notification_subscriptions(user_id, media_type, source_id, content_id, audio_variant)
  select p_user_id, x.media_type, x.source_id, x.content_id, x.audio_variant
  from jsonb_to_recordset(p_subscriptions) as x(
    media_type text, source_id text, content_id text, audio_variant text
  )
  where exists (
    select 1 from notification_devices d
    where d.user_id = p_user_id
      and case x.media_type
        when 'anime' then (d.preferences->>'anime')::boolean
        when 'manga' then (d.preferences->>'manga')::boolean
        else false
      end
  )
  on conflict do nothing;
  get diagnostics v_count = row_count;
  perform public.spaceanime_notification_cleanup_user(p_user_id);
  return v_count;
end;
$$;

create or replace function public.spaceanime_notification_list_subscriptions(
  p_media_type text,
  p_source_id text,
  p_after text,
  p_limit integer
) returns table(content_id text, audio_variant text)
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
begin
  if p_media_type is null or p_source_id is null
     or p_media_type not in ('anime', 'manga')
     or (p_media_type = 'anime' and p_source_id not in ('anitube', 'animesdigital'))
     or (p_media_type = 'manga' and p_source_id not in ('dex', 'yomu'))
     or p_after is null or p_limit not between 1 and 500 then
    raise exception 'invalid_notification_subscription_query';
  end if;
  return query
    select distinct s.content_id, s.audio_variant
    from notification_subscriptions s
    join notification_devices d on d.user_id = s.user_id
    where s.media_type = p_media_type
      and s.source_id = p_source_id
      and s.content_id > p_after
      and case s.media_type
        when 'anime' then (d.preferences->>'anime')::boolean
        when 'manga' then (d.preferences->>'manga')::boolean
        else false
      end
    order by s.content_id, s.audio_variant
    limit p_limit;
end;
$$;

create or replace function public.spaceanime_notification_claim_scan(
  p_source_id text,
  p_now timestamptz
) returns boolean
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
declare
  v_claimed integer;
begin
  if p_source_id is null or p_source_id not in ('anitube', 'animesdigital', 'dex') or p_now is null then
    raise exception 'invalid_notification_scan';
  end if;
  insert into notification_scan_state(source_id, last_claimed_at)
  values (p_source_id, p_now)
  on conflict (source_id) do update
    set last_claimed_at = excluded.last_claimed_at
    where notification_scan_state.last_claimed_at <= excluded.last_claimed_at - interval '6 hours';
  get diagnostics v_claimed = row_count;
  return v_claimed = 1;
end;
$$;

create or replace function public.spaceanime_notification_record_release(p_release jsonb)
returns bigint
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
declare
  v_media_type text;
  v_source_id text;
  v_source_item_id text;
  v_content_id text;
  v_audio_variant text;
  v_title text;
  v_source_url text;
  v_release_id bigint;
  v_created boolean := false;
begin
  if jsonb_typeof(p_release) is distinct from 'object' then
    raise exception 'invalid_notification_release';
  end if;
  v_media_type := p_release->>'media_type';
  v_source_id := p_release->>'source_id';
  v_source_item_id := p_release->>'source_item_id';
  v_content_id := p_release->>'content_id';
  v_audio_variant := p_release->>'audio_variant';
  v_title := p_release->>'title';
  v_source_url := p_release->>'source_url';
  if v_media_type is null or v_source_id is null or v_source_item_id is null
     or v_content_id is null or v_audio_variant is null or v_title is null or v_source_url is null
     or (v_media_type = 'anime' and v_source_id not in ('anitube', 'animesdigital'))
     or (v_media_type = 'manga' and v_source_id not in ('dex', 'yomu'))
     or v_media_type not in ('anime', 'manga')
     or length(v_source_item_id) not between 1 and 220
     or length(v_content_id) not between 1 and 180
     or v_audio_variant not in ('dubbed', 'subtitled', 'none')
     or (v_media_type = 'manga' and v_audio_variant <> 'none')
     or length(v_title) not between 1 and 300
     or length(v_source_url) not between 1 and 2048 then
    raise exception 'invalid_notification_release';
  end if;
  if (v_media_type = 'anime' and v_source_id = 'anitube' and v_source_url !~ '^https://www\.anitube\.zip/')
     or (v_media_type = 'anime' and v_source_id = 'animesdigital' and v_source_url !~ '^https://animesdigital\.org/')
     or (v_media_type = 'manga' and v_source_url !~ '^https://api\.mangadex\.org/') then
    raise exception 'invalid_notification_release_url';
  end if;

  insert into notification_releases(media_type, source_id, source_item_id, content_id, audio_variant, title, source_url)
  values (v_media_type, v_source_id, v_source_item_id, v_content_id, v_audio_variant, v_title, v_source_url)
  on conflict (media_type, source_id, source_item_id) do nothing
  returning id into v_release_id;
  v_created := v_release_id is not null;
  if not v_created then
    select r.id into v_release_id
    from notification_releases r
    where r.media_type = v_media_type and r.source_id = v_source_id
      and r.source_item_id = v_source_item_id;
  end if;
  if v_created then
    insert into notification_digest_items(user_id, local_date, release_id)
    select distinct s.user_id,
      case
        when (p_now_local.local_time <= d.digest_time) then p_now_local.local_date
        else p_now_local.local_date + 1
      end,
      v_release_id
    from notification_subscriptions s
    join notification_devices d on d.user_id = s.user_id
    cross join lateral (
      select (now() at time zone d.time_zone)::date as local_date,
             (now() at time zone d.time_zone)::time as local_time
    ) p_now_local
    where s.media_type = v_media_type and s.source_id = v_source_id
      and s.content_id = v_content_id and s.audio_variant = v_audio_variant
      and case v_media_type
        when 'anime' then (d.preferences->>'anime')::boolean
        when 'manga' then (d.preferences->>'manga')::boolean
        else false
      end
    on conflict do nothing;
  end if;
  return v_release_id;
end;
$$;

create or replace function public.spaceanime_notification_claim_due_digests(
  p_now timestamptz,
  p_limit integer
) returns table(user_id uuid, local_date date, title text, body text, push_tokens text[])
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
begin
  if p_now is null or p_limit not between 1 and 100 then
    raise exception 'invalid_notification_digest_claim';
  end if;

  insert into notification_digests(user_id, local_date, status)
  select distinct i.user_id, i.local_date, 'pending'
  from notification_digest_items i
  join notification_releases r on r.id = i.release_id
  join notification_devices d on d.user_id = i.user_id
  where i.local_date = (p_now at time zone d.time_zone)::date
    and (p_now at time zone d.time_zone)::time >= d.digest_time
    and (
      ((r.media_type = 'anime') and (d.preferences->>'anime')::boolean)
      or ((r.media_type = 'manga') and (d.preferences->>'manga')::boolean)
    )
  on conflict (user_id, local_date) do nothing;

  return query
  with claimable as (
    select d.user_id as claimed_user_id, d.local_date as claimed_local_date
    from notification_digests d
    where d.attempt_count < 20
      and (
        d.status in ('pending', 'failed')
        or (d.status = 'processing' and d.lease_until <= p_now)
      )
      and exists (
        select 1
        from notification_digest_items i
        join notification_releases r on r.id = i.release_id
        join notification_devices device on device.user_id = i.user_id
        where i.user_id = d.user_id and i.local_date = d.local_date
          and i.local_date = (p_now at time zone device.time_zone)::date
          and (p_now at time zone device.time_zone)::time >= device.digest_time
          and (
            (r.media_type = 'anime' and (device.preferences->>'anime')::boolean)
            or (r.media_type = 'manga' and (device.preferences->>'manga')::boolean)
          )
      )
    order by d.local_date, d.user_id
    limit p_limit
    for update skip locked
  ),
  claimed as (
    update notification_digests d
      set status = 'processing',
          attempt_count = d.attempt_count + 1,
          lease_until = p_now + interval '10 minutes',
          updated_at = p_now
    from claimable c
    where d.user_id = c.claimed_user_id and d.local_date = c.claimed_local_date
    returning d.user_id, d.local_date
  )
  select c.user_id,
         c.local_date,
         'Novidades dos títulos que você acompanha'::text,
         left(string_agg(distinct r.title, ', ' order by r.title), 280),
         array_agg(distinct device.push_token)
  from claimed c
  join notification_digest_items i
    on i.user_id = c.user_id and i.local_date = c.local_date
  join notification_releases r on r.id = i.release_id
  join notification_devices device on device.user_id = c.user_id
  where (
    (r.media_type = 'anime' and (device.preferences->>'anime')::boolean)
    or (r.media_type = 'manga' and (device.preferences->>'manga')::boolean)
  )
  group by c.user_id, c.local_date;
end;
$$;

create or replace function public.spaceanime_notification_finish_digest(
  p_user_id uuid,
  p_local_date date,
  p_result text
) returns boolean
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
begin
  if p_user_id is null or p_local_date is null or p_result not in ('sent', 'failed') then
    raise exception 'invalid_notification_digest_result';
  end if;
  update notification_digests
    set status = p_result,
        sent_at = case when p_result = 'sent' then now() else null end,
        lease_until = null,
        updated_at = now()
    where user_id = p_user_id and local_date = p_local_date and status = 'processing';
  return found;
end;
$$;

create or replace function public.spaceanime_notification_remove_invalid_tokens(p_tokens text[])
returns integer
language plpgsql
security definer
set search_path = pg_catalog, spaceanime_private
as $$
declare
  v_count integer;
  v_user_ids uuid[];
  v_user_id uuid;
begin
  if p_tokens is null or cardinality(p_tokens) > 500 then
    raise exception 'invalid_notification_tokens';
  end if;
  select array_agg(distinct user_id), count(*)
    into v_user_ids, v_count
  from notification_devices where push_token = any(p_tokens);
  delete from notification_devices where push_token = any(p_tokens);
  foreach v_user_id in array coalesce(v_user_ids, array[]::uuid[]) loop
    perform public.spaceanime_notification_cleanup_user(v_user_id);
  end loop;
  return coalesce(v_count, 0);
end;
$$;

revoke all on function public.spaceanime_notification_cleanup_user(uuid) from public, anon, authenticated;
revoke all on function public.spaceanime_notification_update_preferences(uuid, text, jsonb, text, time) from public, anon, authenticated;
revoke all on function public.spaceanime_notification_delete_device(uuid, text) from public, anon, authenticated;
revoke all on function public.spaceanime_notification_sync_subscriptions(uuid, jsonb) from public, anon, authenticated;
revoke all on function public.spaceanime_notification_list_subscriptions(text, text, text, integer) from public, anon, authenticated;
revoke all on function public.spaceanime_notification_claim_scan(text, timestamptz) from public, anon, authenticated;
revoke all on function public.spaceanime_notification_record_release(jsonb) from public, anon, authenticated;
revoke all on function public.spaceanime_notification_claim_due_digests(timestamptz, integer) from public, anon, authenticated;
revoke all on function public.spaceanime_notification_finish_digest(uuid, date, text) from public, anon, authenticated;
revoke all on function public.spaceanime_notification_remove_invalid_tokens(text[]) from public, anon, authenticated;

grant execute on function public.spaceanime_notification_cleanup_user(uuid) to service_role;
grant execute on function public.spaceanime_notification_update_preferences(uuid, text, jsonb, text, time) to service_role;
grant execute on function public.spaceanime_notification_delete_device(uuid, text) to service_role;
grant execute on function public.spaceanime_notification_sync_subscriptions(uuid, jsonb) to service_role;
grant execute on function public.spaceanime_notification_list_subscriptions(text, text, text, integer) to service_role;
grant execute on function public.spaceanime_notification_claim_scan(text, timestamptz) to service_role;
grant execute on function public.spaceanime_notification_record_release(jsonb) to service_role;
grant execute on function public.spaceanime_notification_claim_due_digests(timestamptz, integer) to service_role;
grant execute on function public.spaceanime_notification_finish_digest(uuid, date, text) to service_role;
grant execute on function public.spaceanime_notification_remove_invalid_tokens(text[]) to service_role;

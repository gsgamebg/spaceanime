-- Apply and verify in a development Supabase project before production.
create schema if not exists spaceanime_private;
revoke all on schema spaceanime_private from public, anon, authenticated;

create table spaceanime_private.episode_playback_markers (
  source_id text not null check (source_id in ('anitube', 'animesdigital')),
  content_id text not null check (length(content_id) between 1 and 220 and content_id ~ '^[A-Za-z0-9._:/-]+$'),
  episode_id text not null check (length(episode_id) between 1 and 220 and episode_id ~ '^[A-Za-z0-9._:/-]+$'),
  intro_start_ms bigint,
  intro_end_ms bigint,
  outro_start_ms bigint,
  outro_end_ms bigint,
  updated_at timestamptz not null default now(),
  primary key (source_id, content_id, episode_id),
  check ((intro_start_ms is null) = (intro_end_ms is null)),
  check (intro_start_ms is null or (intro_start_ms >= 0 and intro_end_ms > intro_start_ms)),
  check ((outro_start_ms is null) = (outro_end_ms is null)),
  check (outro_start_ms is null or (outro_start_ms >= 0 and outro_end_ms > outro_start_ms))
);

alter table spaceanime_private.episode_playback_markers enable row level security;
revoke all on table spaceanime_private.episode_playback_markers from public, anon, authenticated;
grant usage on schema spaceanime_private to service_role;
grant select, insert, update, delete on table spaceanime_private.episode_playback_markers to service_role;

create or replace function public.spaceanime_public_episode_markers(
  p_source_id text,
  p_content_id text,
  p_episode_id text
) returns jsonb
language plpgsql
stable
security definer
set search_path = pg_catalog, spaceanime_private
as $$
declare
  marker spaceanime_private.episode_playback_markers%rowtype;
begin
  if p_source_id not in ('anitube', 'animesdigital')
     or p_content_id is null or length(p_content_id) not between 1 and 220
     or p_content_id !~ '^[A-Za-z0-9._:/-]+$'
     or p_episode_id is null or length(p_episode_id) not between 1 and 220
     or p_episode_id !~ '^[A-Za-z0-9._:/-]+$' then
    raise exception 'invalid_episode_marker_key';
  end if;

  select * into marker
    from spaceanime_private.episode_playback_markers
    where source_id = p_source_id
      and content_id = p_content_id
      and episode_id = p_episode_id;

  if not found then
    return null;
  end if;

  return jsonb_build_object(
    'source_id', marker.source_id,
    'content_id', marker.content_id,
    'episode_id', marker.episode_id,
    'intro_start_ms', marker.intro_start_ms,
    'intro_end_ms', marker.intro_end_ms,
    'outro_start_ms', marker.outro_start_ms,
    'outro_end_ms', marker.outro_end_ms
  );
end;
$$;

revoke all on function public.spaceanime_public_episode_markers(text, text, text) from public, anon, authenticated;
grant execute on function public.spaceanime_public_episode_markers(text, text, text) to anon, authenticated;

-- Estrutura inicial privada; serviços, políticas e regras de negócio serão adicionados em migrações futuras.
alter table public.profiles add column if not exists username text unique check (username is null or username ~ '^[a-z0-9_]{3,24}$');
alter table public.profiles add column if not exists biography text not null default '' check (char_length(biography) <= 500);
alter table public.profiles add column if not exists avatar_asset_id text not null default 'default' check (char_length(avatar_asset_id) between 1 and 80);
alter table public.profiles add column if not exists banner_asset_id text not null default 'default' check (char_length(banner_asset_id) between 1 and 80);
create table if not exists spaceanime_private.comments (
 id bigint generated always as identity primary key,
 user_id uuid not null references auth.users(id) on delete cascade,
 content_type text not null check(content_type in ('anime','manga','news')),
 content_id text not null check(char_length(content_id) between 1 and 200),
 body text not null check(char_length(body) between 1 and 2000),
 status text not null default 'visible' check(status in ('visible','hidden','removed')),
 created_at timestamptz not null default now()
);
create index if not exists comments_content_idx on spaceanime_private.comments(content_type,content_id,created_at desc);
create table if not exists spaceanime_private.reports (
 id bigint generated always as identity primary key,
 reporter_id uuid not null references auth.users(id) on delete cascade,
 comment_id bigint not null references spaceanime_private.comments(id) on delete cascade,
 reason text not null check(char_length(reason) between 1 and 500),
 status text not null default 'open' check(status in ('open','reviewed','closed')),
 created_at timestamptz not null default now(), unique(reporter_id,comment_id)
);
create table if not exists spaceanime_private.challenges (
 id text primary key, title text not null, xp_reward integer not null check(xp_reward between 1 and 10000),
 active boolean not null default false
);
create table if not exists spaceanime_private.tickets (
 id bigint generated always as identity primary key, user_id uuid not null references auth.users(id) on delete cascade,
 subject text not null check(char_length(subject) between 1 and 150),
 status text not null default 'open' check(status in ('open','in_progress','closed')),
 created_at timestamptz not null default now()
);
create table if not exists spaceanime_private.ticket_messages (
 id bigint generated always as identity primary key, ticket_id bigint not null references spaceanime_private.tickets(id) on delete cascade,
 author_id uuid references auth.users(id) on delete set null, body text not null check(char_length(body) between 1 and 4000),
 created_at timestamptz not null default now()
);
create table if not exists spaceanime_private.profile_assets (
 id text primary key check(char_length(id) between 1 and 80), kind text not null check(kind in ('avatar','banner')),
 enabled boolean not null default false
);
create table if not exists spaceanime_private.notifications (
 id bigint generated always as identity primary key, user_id uuid not null references auth.users(id) on delete cascade,
 title text not null check(char_length(title) between 1 and 150), body text not null check(char_length(body) <= 1000),
 read_at timestamptz, created_at timestamptz not null default now()
);
alter table spaceanime_private.comments enable row level security;
alter table spaceanime_private.reports enable row level security;
alter table spaceanime_private.challenges enable row level security;
alter table spaceanime_private.tickets enable row level security;
alter table spaceanime_private.ticket_messages enable row level security;
alter table spaceanime_private.profile_assets enable row level security;
alter table spaceanime_private.notifications enable row level security;
revoke all on all tables in schema spaceanime_private from public, anon, authenticated;
revoke all on all sequences in schema spaceanime_private from public, anon, authenticated;

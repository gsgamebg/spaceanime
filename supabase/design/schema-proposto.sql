-- RASCUNHO PARA REVISÃO. NÃO EXECUTAR EM PRODUÇÃO.
-- Esta proposta não concede acesso ao cliente. Antes da publicação são necessários
-- testes de autorização, políticas por operação e revisão de pagamentos/moderação.
begin;
create schema if not exists spaceanime_private;
revoke all on schema spaceanime_private from public, anon, authenticated;

-- Perfis públicos são separados de dados privados de autenticação (auth.users).
create table spaceanime_private.profile_draft (
  user_id uuid primary key references auth.users(id) on delete cascade,
  username text unique not null check (username ~ '^[a-z0-9_]{3,24}$'),
  display_name text not null check (char_length(display_name) between 1 and 80),
  biography text not null default '' check (char_length(biography) <= 500),
  avatar_asset_id text not null default 'default',
  banner_asset_id text not null default 'default',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table spaceanime_private.comments_draft (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  content_type text not null check (content_type in ('anime','manga','news')),
  content_id text not null check (char_length(content_id) between 1 and 200),
  body text not null check (char_length(body) between 1 and 2000),
  status text not null default 'visible' check (status in ('visible','hidden','removed')),
  created_at timestamptz not null default now()
);
create index on spaceanime_private.comments_draft(content_type,content_id,created_at desc);
create table spaceanime_private.reports_draft (
  id bigint generated always as identity primary key,
  reporter_id uuid not null references auth.users(id) on delete cascade,
  comment_id bigint not null references spaceanime_private.comments_draft(id) on delete cascade,
  reason text not null check (char_length(reason) between 1 and 500),
  status text not null default 'open' check (status in ('open','reviewed','closed')),
  created_at timestamptz not null default now(),
  unique (reporter_id, comment_id)
);
-- Somente serviço confiável deve registrar XP: sem escrita direta do app.
create table spaceanime_private.xp_events_draft (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  event_type text not null,
  event_key text not null,
  points integer not null check (points between 1 and 10000),
  created_at timestamptz not null default now(),
  unique(user_id,event_type,event_key)
);
create table spaceanime_private.challenges_draft (
  id text primary key,
  title text not null,
  xp_reward integer not null check (xp_reward between 1 and 10000),
  active boolean not null default false
);
create table spaceanime_private.roles_draft (
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('admin','sales_manager','moderator','marketing_manager')),
  granted_at timestamptz not null default now(),
  primary key(user_id,role)
);
create table spaceanime_private.tickets_draft (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  subject text not null check (char_length(subject) between 1 and 150),
  status text not null default 'open' check(status in ('open','in_progress','closed')),
  created_at timestamptz not null default now()
);
create table spaceanime_private.audit_draft (
  id bigint generated always as identity primary key,
  actor_id uuid references auth.users(id) on delete set null,
  action text not null,
  target_type text not null,
  target_id text not null,
  created_at timestamptz not null default now()
);
-- Guarda apenas identificadores e estados do provedor; jamais cartão/CVV.
create table spaceanime_private.entitlements_draft (
  user_id uuid primary key references auth.users(id) on delete cascade,
  provider text not null,
  provider_reference text not null,
  status text not null check(status in ('active','inactive','cancelled','expired')),
  expires_at timestamptz
);
-- Garantia adicional: sem políticas, todas as tabelas permanecem fechadas ao cliente.
alter table spaceanime_private.profile_draft enable row level security;
alter table spaceanime_private.comments_draft enable row level security;
alter table spaceanime_private.reports_draft enable row level security;
alter table spaceanime_private.xp_events_draft enable row level security;
alter table spaceanime_private.challenges_draft enable row level security;
alter table spaceanime_private.roles_draft enable row level security;
alter table spaceanime_private.tickets_draft enable row level security;
alter table spaceanime_private.audit_draft enable row level security;
alter table spaceanime_private.entitlements_draft enable row level security;
revoke all on all tables in schema spaceanime_private from public, anon, authenticated;
revoke all on all sequences in schema spaceanime_private from public, anon, authenticated;
rollback; -- rascunho deliberadamente não altera o banco mesmo se executado

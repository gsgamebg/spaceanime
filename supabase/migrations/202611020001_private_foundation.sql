-- Fundação restrita para futura integração. NÃO aplicada automaticamente.
-- Pré-requisitos: testes em projeto de desenvolvimento e autorização do proprietário.
-- Sem políticas abertas: clientes anon/authenticated não acessam estas tabelas.
create schema if not exists spaceanime_private;
revoke all on schema spaceanime_private from public, anon, authenticated;

create table if not exists spaceanime_private.staff_roles (
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('admin','sales_manager','moderator','marketing_manager')),
  granted_at timestamptz not null default now(),
  primary key (user_id, role)
);
create table if not exists spaceanime_private.billing_customers (
  user_id uuid primary key references auth.users(id) on delete cascade,
  provider text not null check (provider in ('stripe','mercadopago')),
  external_customer_id text not null,
  created_at timestamptz not null default now(),
  unique (provider, external_customer_id)
);
create table if not exists spaceanime_private.billing_events (
  provider text not null check (provider in ('stripe','mercadopago')),
  external_event_id text not null,
  event_type text not null,
  received_at timestamptz not null default now(),
  processed_at timestamptz,
  primary key (provider, external_event_id)
);
create table if not exists spaceanime_private.entitlements (
  user_id uuid not null references auth.users(id) on delete cascade,
  entitlement text not null,
  active boolean not null default false,
  expires_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (user_id, entitlement)
);
create table if not exists spaceanime_private.xp_events (
  user_id uuid not null references auth.users(id) on delete cascade,
  event_type text not null,
  event_key text not null,
  points integer not null check (points between 1 and 10000),
  created_at timestamptz not null default now(),
  primary key (user_id, event_type, event_key)
);
create table if not exists spaceanime_private.staff_audit (
  id bigint generated always as identity primary key,
  actor_id uuid references auth.users(id) on delete set null,
  action text not null,
  target_type text not null,
  target_id text not null,
  created_at timestamptz not null default now()
);
alter table spaceanime_private.staff_roles enable row level security;
alter table spaceanime_private.billing_customers enable row level security;
alter table spaceanime_private.billing_events enable row level security;
alter table spaceanime_private.entitlements enable row level security;
alter table spaceanime_private.xp_events enable row level security;
alter table spaceanime_private.staff_audit enable row level security;
revoke all on all tables in schema spaceanime_private from public, anon, authenticated;
revoke all on all sequences in schema spaceanime_private from public, anon, authenticated;

-- Vínculos de clientes de teste separados dos dados que poderão ser usados em produção.
create table if not exists spaceanime_private.sandbox_customer_links (
  user_id uuid primary key references auth.users(id) on delete cascade,
  customer_id text not null unique check (customer_id ~ '^cus_[A-Za-z0-9]+$'),
  created_at timestamptz not null default now()
);
alter table spaceanime_private.sandbox_customer_links enable row level security;
revoke all on spaceanime_private.sandbox_customer_links from public,anon,authenticated;
grant select,insert on spaceanime_private.sandbox_customer_links to service_role;

create or replace function public.find_stripe_sandbox_customer_owner(p_customer_id text)
returns uuid language sql security invoker set search_path = '' as $$
 select c.user_id from spaceanime_private.sandbox_customer_links c
 where c.customer_id=p_customer_id and p_customer_id ~ '^cus_[A-Za-z0-9]+$' limit 1;
$$;
create or replace function public.find_stripe_sandbox_customer(p_user_id uuid)
returns text language sql security invoker set search_path = '' as $$
 select c.customer_id from spaceanime_private.sandbox_customer_links c where c.user_id=p_user_id limit 1;
$$;
create or replace function public.bind_stripe_sandbox_customer(p_user_id uuid,p_customer_id text)
returns text language plpgsql security invoker set search_path = '' as $$
declare result text;
begin
 if p_customer_id is null or p_customer_id !~ '^cus_[A-Za-z0-9]+$' then raise exception 'invalid sandbox customer'; end if;
 insert into spaceanime_private.sandbox_customer_links(user_id,customer_id) values(p_user_id,p_customer_id)
 on conflict(user_id) do nothing;
 select customer_id into result from spaceanime_private.sandbox_customer_links where user_id=p_user_id;
 return result;
end;
$$;
revoke all on function public.find_stripe_sandbox_customer_owner(text) from public,anon,authenticated;
revoke all on function public.find_stripe_sandbox_customer(uuid) from public,anon,authenticated;
revoke all on function public.bind_stripe_sandbox_customer(uuid,text) from public,anon,authenticated;
grant execute on function public.find_stripe_sandbox_customer_owner(text) to service_role;
grant execute on function public.find_stripe_sandbox_customer(uuid) to service_role;
grant execute on function public.bind_stripe_sandbox_customer(uuid,text) to service_role;

create or replace function public.begin_stripe_sandbox_reconciliation(p_event_id text,p_customer_id text,p_user_id uuid)
returns bigint language plpgsql security invoker set search_path = '' as $$
declare next_generation bigint;
begin
 if not exists(select 1 from spaceanime_private.sandbox_customer_links c where c.user_id=p_user_id and c.customer_id=p_customer_id)
    or not exists(select 1 from spaceanime_private.billing_events e where e.provider='stripe' and e.external_event_id=p_event_id) then
   raise exception 'unknown customer or event';
 end if;
 insert into spaceanime_private.sandbox_reconciliation_heads(customer_id,generation) values(p_customer_id,1)
 on conflict(customer_id) do update set generation=spaceanime_private.sandbox_reconciliation_heads.generation+1
 returning generation into next_generation;
 return next_generation;
end;
$$;
create or replace function public.commit_stripe_sandbox_reconciliation(p_event_id text,p_customer_id text,p_user_id uuid,p_generation bigint,p_active boolean,p_tier text,p_subscription_id text)
returns boolean language plpgsql security invoker set search_path = '' as $$
declare current_generation bigint;
begin
 if not exists(select 1 from spaceanime_private.sandbox_customer_links c where c.user_id=p_user_id and c.customer_id=p_customer_id)
    or not exists(select 1 from spaceanime_private.billing_events e where e.provider='stripe' and e.external_event_id=p_event_id) then
   raise exception 'unknown customer or event';
 end if;
 select generation into current_generation from spaceanime_private.sandbox_reconciliation_heads where customer_id=p_customer_id for update;
 if current_generation is null or current_generation <> p_generation then return false; end if;
 insert into spaceanime_private.sandbox_entitlements(user_id,customer_id,active,tier,subscription_id)
 values(p_user_id,p_customer_id,p_active,p_tier,p_subscription_id)
 on conflict(user_id) do update set customer_id=excluded.customer_id,active=excluded.active,tier=excluded.tier,subscription_id=excluded.subscription_id,updated_at=now();
 update spaceanime_private.billing_events set processed_at=now() where provider='stripe' and external_event_id=p_event_id;
 return true;
end;
$$;
revoke all on function public.begin_stripe_sandbox_reconciliation(text,text,uuid) from public,anon,authenticated;
revoke all on function public.commit_stripe_sandbox_reconciliation(text,text,uuid,bigint,boolean,text,text) from public,anon,authenticated;
grant execute on function public.begin_stripe_sandbox_reconciliation(text,text,uuid) to service_role;
grant execute on function public.commit_stripe_sandbox_reconciliation(text,text,uuid,bigint,boolean,text,text) to service_role;

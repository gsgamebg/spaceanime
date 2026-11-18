-- Resolução de vínculo criada pelo backend; nunca confiar em dados do evento como dono.
create or replace function public.find_stripe_sandbox_customer_owner(p_customer_id text)
returns uuid language sql security invoker set search_path = '' as $$
 select c.user_id from spaceanime_private.billing_customers c
 where c.provider='stripe' and c.external_customer_id=p_customer_id and p_customer_id ~ '^cus_[A-Za-z0-9]+$'
 limit 1;
$$;
revoke all on function public.find_stripe_sandbox_customer_owner(text) from public,anon,authenticated;
grant execute on function public.find_stripe_sandbox_customer_owner(text) to service_role;

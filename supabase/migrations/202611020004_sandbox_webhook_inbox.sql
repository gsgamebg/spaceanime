-- Fila mínima de eventos do sandbox, sem conteúdo do pagamento ou dados pessoais.
-- Ainda não ativa nem altera benefícios e não confirma processamento de eventos.
create or replace function public.record_stripe_sandbox_event(p_event_id text, p_event_type text)
returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if p_event_id is null or p_event_id !~ '^evt_[A-Za-z0-9]{1,120}$'
     or p_event_type is null or p_event_type !~ '^[a-z][a-z0-9_.]{1,100}$' then
    raise exception 'invalid event';
  end if;
  insert into spaceanime_private.billing_events(provider, external_event_id, event_type)
    values ('stripe', p_event_id, p_event_type)
    on conflict (provider, external_event_id) do nothing;
  return found;
end;
$$;
revoke all on function public.record_stripe_sandbox_event(text, text) from public, anon, authenticated;
grant usage on schema spaceanime_private to service_role;
grant select (provider, external_event_id), insert (provider, external_event_id, event_type)
  on spaceanime_private.billing_events to service_role;
grant execute on function public.record_stripe_sandbox_event(text, text) to service_role;

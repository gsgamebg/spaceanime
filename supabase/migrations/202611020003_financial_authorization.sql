-- Authorization and audit for aggregate financial reads. No user can assign a role here.
create or replace function public.authorize_financial_read()
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare allowed boolean;
begin
  if (select auth.uid()) is null or coalesce((select auth.jwt())->>'aal','') <> 'aal2' then
    return false;
  end if;
  select exists(select 1 from spaceanime_private.staff_roles r
    where r.user_id = (select auth.uid()) and r.role in ('admin','sales_manager')) into allowed;
  if allowed then
    insert into spaceanime_private.staff_audit(actor_id,action,target_type,target_id)
      values ((select auth.uid()),'attempt_financial_summary','financial_summary','stripe_sandbox');
  end if;
  return allowed;
end;
$$;
revoke all on function public.authorize_financial_read() from public, anon;
grant execute on function public.authorize_financial_read() to authenticated;


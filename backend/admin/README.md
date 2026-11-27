# Painel administrativo do SpaceAnime

Site estático (sem build) entregue pelo **mesmo serviço do Render** da API, em `/admin/`. Login com e-mail, senha e **verificação em duas etapas obrigatória**. Todo dado vem de `/api/admin/*`; este site não tem nenhuma chave privilegiada.

## Publicar

Nada além do deploy normal do backend: a pasta `backend/admin/` vai junto. Não precisa de site separado nem de `ADMIN_ORIGIN` (mesma origem, sem CORS).

## Primeiro acesso

1. A migração `supabase/migrations/202611260001_admin_foundation.sql` já deve estar aplicada.
2. No SQL Editor do Supabase, torne a sua conta `owner` (troque o e-mail):
   ```sql
   insert into spaceanime_private.staff_roles (user_id, role)
   select id, 'owner' from auth.users where email = 'SEU-EMAIL@exemplo.com'
   on conflict do nothing;
   ```
3. Abra `https://spaceanime.onrender.com/admin/`, entre com e-mail e senha e, na primeira vez, escaneie o QR Code no app autenticador.

Confirme no Supabase, em Authentication → Multi-Factor, que TOTP está habilitado.

Papéis (`owner`, `finance`, `support`, `moderator`) só são atribuídos por SQL. A matriz de permissões está em `backend/admin.js`.

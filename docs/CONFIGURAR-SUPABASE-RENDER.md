# Preparação segura: Supabase, Render e SpaceAnime

**Estado atual:** nada foi conectado, aplicado ou publicado. O app mantém perfis, biblioteca e comentários locais. Não existem ainda login, Google, XP, pagamentos, cargos ou painel administrativos em funcionamento. `schema-proposto.sql` é apenas um desenho: termina com ROLLBACK e não cria tabelas permanentes. A migração anterior de perfis também não deve ser aplicada em produção sem testes.

## Chaves: o que pode ficar no aplicativo

- URL do projeto Supabase e chave **publishable** identificam o projeto e podem ser distribuídas no app. Elas não autorizam o usuário: Auth + RLS e grants decidem o acesso. A URL vista na captura não é uma senha.
- Chave `sb_secret_...`, `service_role`, senha do banco, segredos OAuth, chaves de pagamento e assinatura de webhook são **privados**: jamais no APK, EXE, repositório, screenshots, logs ou chat. Se expostos, revogar/rotacionar no painel do provedor.
- Os segredos do backend devem ficar apenas nas variáveis de ambiente do serviço privado no Render ou em outro ambiente seguro. O app chama somente endpoints HTTPS definidos e recebe respostas limitadas; o backend verifica o token de usuário com o Supabase, expiração, emissor e público antes de autorizar cada ação.
- Não aceitar cargo, nível, VIP, preço, tempo de uso nem saldo informados pelo cliente como prova. Aplicar RLS e mínimos privilégios; a chave secreta pode ignorar RLS, portanto seu uso exige autorização e auditoria no servidor.

## Etapa A — preparar o Supabase sem compartilhar segredos

1. No painel do projeto, abra **Settings / API Keys**. Localize URL do projeto e chave publishable (ou chave anon legada, se for o caso). Guarde-as localmente para a futura configuração. Não envie a chave secret.
2. Em **Authentication / Sign In / Providers**, verifique Email e confirmação de email. Configure também recuperação de senha; antes de disponibilizar ao público, configure um serviço SMTP próprio em **Authentication / Emails / SMTP Settings** e teste. O SMTP padrão é apenas para avaliação, com restrições de destinatários e envio.
3. Em **Authentication / Providers / Google**, habilite Google apenas depois de criar os IDs necessários no Google Cloud. Siga a documentação oficial para Android e desktop, configure os redirecionamentos autorizados de cada plataforma e informe o segredo exclusivamente no painel Supabase. Nunca fixe redirect genérico sem implementação e validação de esquema/state/PKCE.
4. Use ambiente de desenvolvimento separado. Antes de criar tabelas em produção, revise e converta o desenho `supabase/design/schema-proposto.sql` em migrações versionadas. Ele não deve ser executado como migração: possui `ROLLBACK` proposital. Defina RLS e grants por tabela, com testes automatizados para anônimo, dono, outro usuário e cada cargo. Não exponha `spaceanime_private` na Data API.
5. Faça backup e planeje exclusão de conta e dados antes de armazenar informações reais. O app ainda não tem fluxo de migração dos dados locais; não apague nem substitua os perfis existentes.

## Etapa B — Render

1. O `render.yaml` atual cria **um site estático de distribuição de atualizações**, não um backend de login. Mantenha-o separado; não coloque segredos nele.
2. Um backend novo deverá ser criado e testado localmente, com endpoints limitados, autenticação, autorização, limites de requisição, validação de entrada e logs sem segredos. Ainda não há esse backend no repositório, portanto não há URL para colar no app nesta versão.
3. Quando o código do backend estiver pronto e em um repositório, crie um **Web Service** separado no Render, defina build/start, health check e variáveis em **Environment**. Os nomes exatos das variáveis virão do backend implementado; não invente chaves nem use uma variável no cliente para um segredo.
4. Primeiro implante em ambiente de teste, confira HTTPS e health check sem dados sensíveis, faça testes de usuário normal e de permissões. Somente depois configure a URL pública HTTPS do backend na versão do app e realize um teste completo de login.

## Modelo proposto

- **Auth:** Supabase Auth é o registro de identidade, email/senha e Google; não crie tabela de senhas próprias.
- **Perfil:** username, nome, biografia e códigos de imagem; separar informação pública de dados particulares. Avatar/banner ficam como assets versionados no aplicativo ou armazenamento de arquivos próprio, não como bytes no banco. Usar IDs de uma lista confiável, sem aceitar URLs arbitrárias; se houver imagens externas no futuro, controlar origem e conteúdo.
- **Comentários:** autor, tipo/ID da obra, texto, horário e estado. Inserção e edição limitadas ao dono; visibilidade, denúncias, bloqueios, limites de uso, revisão e exclusão precisam de regras e testes antes de ativar. Comentários existentes são locais e não migrarão automaticamente.
- **XP:** desafios configurados no servidor; evento idempotente por usuário/atividade; limites por tempo e contabilização somente pelo servidor. Cinco horas é uma condição proposta, não prova verificável por si só: cliente pode mentir, ficar aberto sem uso ou operar offline. Definir como medir sessões, teto diário e antispam antes de conceder XP. Exibir nível é cosmético, não concede poderes.
- **Denúncias/tickets:** guardar motivo e situação; moderador acessa apenas o necessário. Histórico de ações administrativas é separado e auditado.
- **Pagamentos/VIP:** pagamentos por provedor, webhook autenticado e idempotente no servidor. Banco guarda IDs, estado e datas; jamais número de cartão, CVV ou dados desnecessários. Nenhuma cobrança ou plano está implementado.

## Matriz de acessos pretendida

| Papel | Permissões a implementar | Restrições |
| --- | --- | --- |
| Administrador | Gerenciar configurações, cargos, moderação, planos e auditoria | MFA; registrar ações sensíveis; proteção contra perda do último administrador |
| Gerente de vendas | Indicadores financeiros permitidos, preços, permissões de VIP e mensagens aprovadas | Sem administrar cargos ou acessar senhas; alteração de preço e VIP auditada |
| Moderador | Ver denúncias, revisar comentários e biografias, aplicar punições previstas e responder tickets | Sem acesso financeiro ou concessão de cargos; punições auditadas |
| Gerente de marketing | Ver métricas agregadas de aquisição e campanhas | Não acessar finanças, mensagens privadas ou dados pessoais desnecessários |

A implementação efetiva exigirá política de privacidade, consentimento de métricas, retenção, controle de acesso no servidor por ação e autenticação reforçada. Cargo é armazenado em tabela protegida, nunca em campo editável do perfil ou metadado controlado pelo usuário.

## Documentação oficial

- https://supabase.com/docs/guides/getting-started/api-keys
- https://supabase.com/docs/guides/database/postgres/row-level-security
- https://supabase.com/docs/guides/auth/social-login/auth-google
- https://supabase.com/docs/guides/auth/auth-smtp
- https://render.com/docs/configure-environment-variables
## Estado da conexão em 02/11/2026

O projeto Supabase SpaceAnime foi acessado e recebeu as migrações `spaceanime_profiles_foundation`, `spaceanime_private_foundation` e `spaceanime_community_foundation`. Criadas 14 tabelas (perfil público e 13 privadas), vazias. RLS está habilitado em todas; perfis autenticados têm políticas para o próprio usuário e as tabelas privadas não permitem acesso direto de clientes anônimos/autenticados. Não foram criados usuários, cargos ou planos.

As tabelas privadas sem políticas aparecem como informativas no assessor do Supabase: estão fechadas intencionalmente. O assessor também indicou dois avisos sobre permissão de execução da função existente `public.rls_auto_enable()`, que retorna `event_trigger`; ela não foi alterada nesta etapa. Essas verificações não substituem testes completos de autenticação, políticas e autorização por função.

Não há login, sincronização, comentários compartilhados, XP, administração ou pagamentos implementados no app. O backend para Render ainda não foi implantado e o checkout permanece bloqueado. Não reaplicar manualmente as migrações já registradas no projeto.

## Teste de conta por email — 0.7.13

Na aba Perfil → Conta há cadastro e entrada por email/senha diretamente no Supabase Auth. A sessão é apenas em memória, sem persistir o token, e é descartada ao expirar ou fechar o app. Ainda não há redefinição de senha, login Google, sincronização de perfis ou integração com Premium. O endpoint público confirmou email habilitado, cadastro habilitado e confirmação de email exigida. A verificação de login com credenciais falsas foi recusada (HTTP 400); nenhum usuário real foi criado. Cadastro, confirmação e login bem-sucedido ainda precisam ser testados pelo proprietário com seu email.

O serviço SMTP padrão do Supabase restringe destinatários e volume: para abrir cadastro a usuários externos, configurar SMTP próprio e redirecionamentos de confirmação. Não desativar confirmação de email em produção só para passar o teste. Orientação: https://supabase.com/docs/guides/auth/auth-smtp.
# Perfil da conta (0.7.15)

Em **Entrar**, o usuário pode associar o nome e a biografia do perfil local a uma conta que ainda não tem perfil online. Para recuperar esses dados em outro aparelho, use **Consultar perfil da conta**, confira a prévia e confirme **Usar nome e bio neste aparelho**. Uma conta com perfil existente não é sobrescrita ao associar. Dados incompatíveis com os limites locais são recusados.

Esta é uma transferência manual e parcial: edições posteriores, nome de usuário, foto, banner, favoritos e histórico ainda não são sincronizados. A sessão permanece apenas na memória. O fluxo de cadastro, email de confirmação e transferência deve ser testado com uma conta real do próprio usuário. O checkout continua desativado: os testes locais não comprovam pagamento ou ativação de plano.


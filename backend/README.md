# Backend SpaceAnime — preparação para Render

Este serviço é uma base de desenvolvimento, não um backend completo nem pronto para produção. Ele não integra o aplicativo ainda. Não cria usuários, perfis, preços, assinaturas ou cobranças; não entrega benefícios e não altera o banco.

- `GET /health`: mostra somente estado técnico público.
- `GET /api/me`: exige token de usuário Supabase e confirma a identidade com `/auth/v1/user` usando chave publicável; sem configuração retorna erro.
- `POST /api/billing/checkout`: retorna 503 sempre, inclusive no sandbox, até haver checkout, webhooks e concessão durável de benefícios testados.
- `POST /webhooks/stripe`: verifica assinatura com o corpo bruto; eventos válidos retornam 503 para que não sejam considerados processados. Não configure esse endpoint no ambiente real enquanto o processamento não existir.

## Preparar repositório para Render

1. Crie um repositório Git privado para esta cópia; inclua `backend/`, a migração revisada e o blueprint. Nunca inclua `node_modules`, `.env`, chaves, tokens, perfis ou dados privados. Este arquivo não contém segredos reais.
2. Conecte o repositório ao Render. Para usar o blueprint, publique `render-backend.yaml` como `render.yaml` na raiz do repositório que contenha a pasta `backend/`. Se já houver um blueprint de atualizações nesse repositório, não o substitua sem mesclar ou separar os projetos.
3. Alternativamente, crie um **Web Service** Node manualmente; Root Directory: `backend`, Build: `npm ci --omit=dev`, Start: `npm start`, Health Check: `/health`. Configure `SUPABASE_URL` e `SUPABASE_PUBLISHABLE_KEY` em Environment. A URL do backend será pública; HTTPS é fornecido pelo Render.
4. Confira `/health` e informe ao assistente apenas a URL pública do serviço. O app ainda não usa essa URL; nenhum fluxo de conta ou pagamento deve ser oferecido como funcional.
5. Não envie segredos ao chat nem os inclua no aplicativo. Para trabalho futuro na Stripe, crie um sandbox isolado, uma chave restrita de teste com as menores permissões possíveis, e configure-a apenas no servidor. Não configure chaves de produção nem registre webhook real enquanto eventos retornarem 503.

## Antes de ativar pagamentos

Implementar Checkout Sessions de teste após autenticação, com preços aprovados definidos no servidor; verificar webhooks usando corpo bruto; processar eventos com transação e idempotência persistente; reconciliar estados, eventos atrasados, renovação, inadimplência, cancelamento, estorno e disputa; testar tudo em sandbox antes de ativar. Um redirecionamento de sucesso não comprova pagamento. Não se deve ativar cobrança real nem marcar VIP até essas etapas estarem implementadas e verificadas.

As tabelas privadas preparadas são uma fundação, não o banco completo. RLS e acesso estão fechados para clientes. O SQL não foi aplicado ao projeto Supabase. OAuth Google, comentários, XP, funções de administração e pagamentos permanecem pendentes.
## Estado da conexão

As três migrações de fundação foram aplicadas ao projeto SpaceAnime em 02/11/2026. As tabelas privadas permanecem inacessíveis aos clientes. Não reaplique as migrações no mesmo projeto; não há login ou cobrança pronta. O assessor de segurança reportou avisos sobre função preexistente de evento; revisar antes da publicação.
## Consulta financeira preparada

O backend fornece `GET /api/admin/finance/summary` apenas para Stripe sandbox, após validar a sessão Supabase e consultar `authorize_financial_read()`. Essa função exige MFA (`aal2`) e cargo `admin` ou `sales_manager` em tabela protegida; registra tentativa autorizada. Sem cargo, MFA ou chave de testes, a resposta é negativa. Nenhum cargo foi atribuído. A rota ainda não foi implantada, nem existe tela financeira no app. Foram testados oito cenários locais, com mocks; acesso real com contas e MFA ainda não foi testado.

O assessor Supabase alerta sobre uma função `SECURITY DEFINER` executável por usuários autenticados. Isso inclui `authorize_financial_read`, exposição intencional para a verificação descrita acima, mas que precisa de testes com usuários reais de cada papel e revisão antes de produção. Não habilite uma chave de produção. O endpoint recusa chaves live.

## Componente de checkout de sandbox

`billing.js` prepara a criação de sessão de assinatura em ambiente Stripe de testes. Ele permite apenas os seis lookup keys autorizados, confere os valores/intervalos/BRL retornados pela Stripe, valida URLs de retorno, vincula a requisição ao ID do usuário e usa chave de idempotência. As chamadas foram verificadas por testes locais com mocks, não com pagamento real nem sessão Stripe real.

O componente ainda não é chamado pela rota pública. `POST /api/billing/checkout` continua retornando 503 e o webhook continua sem registrar eventos ou conceder benefícios. Antes de habilitar, é necessário criar processamento durável e idempotente, vincular com segurança o usuário ao cliente Stripe, reconciliar estado atual de assinatura, tratar renovações, falhas, cancelamentos, estornos e disputas, testar autenticação e webhooks reais em sandbox e configurar as URLs de retorno.

## Fila de eventos Stripe em sandbox

A migração `spaceanime_sandbox_webhook_inbox` foi aplicada ao projeto Supabase. A função `public.record_stripe_sandbox_event` é acessível apenas ao papel `service_role` e registra somente identificador e tipo do evento; o identificador único evita registros repetidos. O teste no banco usou transação revertida, confirmou inserção seguida de duplicata e não deixou dados de teste. O backend aceita apenas eventos com assinatura válida e `livemode=false`; para registrar a fila, espera `SUPABASE_SECRET_KEY` no servidor. Não informe essa chave ao chat nem a coloque no app, no Git ou em arquivos distribuídos.

A fila não processa ou concede benefícios. O webhook continua respondendo 503 depois de guardar o evento, para que a Stripe não considere o evento concluído. **Não configure nem ative um webhook Stripe real.** O checkout continua bloqueado até implementar reconciliação idempotente e testar autenticação, renovação, falha, cancelamento, reembolso e disputa no sandbox. A chave `SUPABASE_SECRET_KEY` pode ser configurada como segredo somente do backend para as RPCs privadas de notificações; não a coloque no app nem a compartilhe no chat.

## Reconciliação e XP: componentes locais, ainda desativados

`entitlements.js` verifica snapshots de assinaturas de sandbox: exige paginação completa, confirmação de risco, cliente correspondente, preço e moeda corretos, e status ativo. Eventos assinados de assinatura, pagamento e risco são classificados para reconciliação. O componente é puro: não consulta Stripe nem altera banco por si só. Ele não está conectado ao webhook e não constitui um processador completo; antes de ativar é necessário obter e vincular o cliente Stripe de maneira segura, consultar o estado atual, tratar paginação/conflitos de concorrência e reconciliar estornos, disputas e fraude.

`xp.js` implementa multiplicadores do titular: Basic 1x, Platina 3,3x, Premium 4,4x. Para preservar pontos inteiros, os resultados fracionários são arredondados para baixo. A função não está ligada ao cliente nem ao banco; no futuro o plano deve vir de direito confirmado pelo servidor e os eventos de XP devem ser idempotentes e sujeitos a limites contra abuso.

O backend ainda retorna 503 para checkout e confirmação dos webhooks. A habilitação exige desenvolvimento adicional e testes completos contra o sandbox. Essas alterações locais não foram publicadas no Render.

## Notificações FCM — implementação local, ainda não implantada

Foram adicionadas rotas autenticadas para registrar/remover um dispositivo, atualizar preferências e sincronizar IDs acompanhados:

- `POST /api/v1/notifications/devices`
- `PUT /api/v1/notifications/preferences`
- `PUT /api/v1/notifications/subscriptions`
- `DELETE /api/v1/notifications/devices/:device_id`
- `GET` ou `POST /api/workers/daily-digest` (protegida por `CRON_SECRET`)

O usuário é obtido exclusivamente do token Supabase validado no servidor; campos de identidade enviados pelo cliente são rejeitados. Registro exige consentimento para pelo menos uma categoria. A rota não devolve nem registra o token push. O acesso ao banco usa RPCs privadas e `SUPABASE_SECRET_KEY`, somente no backend. Falhas de armazenamento retornam erro e não são tratadas como sucesso. O worker coleta lançamentos do Anitube, AnimesDigital e MangaDex; títulos do Yomu ainda não geram notificações.

O worker aceita a autorização existente `Authorization: Bearer <CRON_SECRET>` em POST, o header `x-cron-secret` em GET/POST ou `?secret=<CRON_SECRET>` em GET/POST. A comparação do segredo é feita em tempo constante; segredos ausentes ou inválidos não executam o worker. O proprietário informou que aplicou `202611170001_notifications.sql`. A migração `202611170002_notification_delivery.sql` é nova, permanece pendente e precisa ser aplicada ao Supabase antes de implantar o worker.

Para usar o UptimeRobot gratuito, cadastre uma verificação HTTPS GET para `/api/workers/daily-digest?secret=...` e use o valor já configurado em `CRON_SECRET`; não cole o segredo no repositório ou no chat. Segredos em query strings podem ficar armazenados no painel do monitor e em logs de infraestrutura; trate a URL como uma senha, use um segredo forte e exclusivo para este worker e rotacione-o se for exposto. Desative/exclua o serviço Cron `spaceanime-daily-digest` no Render para não manter os dois agendadores. O blueprint `render-backend.yaml` ainda declara esse Cron Job; não o sincronize sem antes remover ou desativar esse serviço se não quiser usar o Cron do Render.

Variáveis do Web Service: `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, `FIREBASE_PRIVATE_KEY` e `CRON_SECRET`. Variáveis do Cron Job: `BACKEND_BASE_URL` e `CRON_SECRET`. Nunca coloque a chave Firebase Admin, a chave secreta Supabase ou `CRON_SECRET` no app, no repositório, em logs ou no chat; `FIREBASE_PRIVATE_KEY` aceita quebras de linha reais ou `\n` escapados.

## Cliente Android

O cliente usa `composeApp/google-services.json` para o pacote `app.anim`. As notificações começam desativadas; o app pede a permissão Android somente quando o usuário ativa uma categoria em **Perfil → Configurações → Notificações**. O registro autenticado envia somente o token do dispositivo, preferências e IDs acompanhados. O serviço FCM atualiza tokens renovados, exibe mensagens recebidas e abre o app ao tocar. Desativar todas as categorias remove o dispositivo no servidor; sair da conta tenta removê-lo antes de encerrar a sessão. O desktop não inicializa Firebase.

Validação local do cliente: `gradlew.bat :composeApp:desktopTest :composeApp:assembleDebug`; isso não publica o APK nem testa entrega em aparelho real.

**Ainda não há entrega ativa:** o código Android está implementado localmente, mas o blueprint não foi sincronizado/publicado e a migração 0002 permanece pendente. Após configurar, aplique a migração, publique o backend/Render Blueprint e teste o Cron e um dispositivo Android real antes de afirmar que notificações estão funcionando. Nenhum serviço ou migração foi alterado automaticamente por este projeto local.

## Reconciliação de sandbox — preparação

Em 02/11/2026 foi aplicada a migração `spaceanime_sandbox_reconciliation`, que cria tabelas isoladas para a geração de reconciliação e os direitos de teste. A leitura e a escrita são restritas ao serviço. Uma transação de teste revertida confirmou que uma geração antiga não sobrescreve uma mais recente e que não restaram registros de teste.

`reconcile.js` prepara a consulta do estado atual do cliente Stripe para eventos de assinatura/fatura/checkout e exige associação de cliente feita pelo servidor. Reembolsos, disputas e alertas de fraude são colocados em estado de revisão, sem confirmação automática. Os testes usam provedores de dados simulados. O adaptador real de persistência, a vinculação segura do cliente Stripe e o tratamento de risco ainda não estão conectados. A rota checkout permanece desabilitada e o webhook continua retornando 503: **não ativar cobrança nem configurar o webhook real**.

## Adaptador de persistência em sandbox

Foi aplicada também a migração `spaceanime_sandbox_customer_lookup`; somente `service_role` pode consultar o vínculo privado. O adaptador `sandbox-store.js` acessa apenas RPCs no Supabase configurado e recusa configurações ou respostas inválidas. Seu verificador de risco retorna sempre falso por segurança. Com ele, `reconcile.js` ainda não pode liberar um plano; falta implementar vinculação do usuário ao cliente Stripe, validação completa de reembolso/disputa e ligar o processamento à fila e ao webhook. Nenhuma alteração local foi publicada no Render.

## Vínculo de cliente do sandbox

Foi aplicada a migração `spaceanime_sandbox_customer_links`: clientes de teste usam uma tabela separada dos dados de produção. `sandbox-customer.js` prepara a criação/verificação do cliente com chave de idempotência e vínculo autoritativo armazenado no banco; `billing.js` exige esse cliente na sessão. O teste no banco foi revertido e confirmou que uma segunda tentativa não substitui o vínculo existente. Todas essas rotas permanecem desconectadas do checkout público; não há pagamento ativo.

A associação Stripe, o checkout e a reconciliação ainda utilizam mocks nos testes. É necessário testar o fluxo completo com um usuário real de testes, validar acesso no banco e implementar tratamento de disputas, fraudes e reembolsos antes de ativar o webhook. Não foi feita publicação no Render.

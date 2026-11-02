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

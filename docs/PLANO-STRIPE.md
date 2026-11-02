# Preparação Stripe do SpaceAnime

Contexto: aplicativos Android e Windows, futuro plano premium e identidade Supabase. O plugin e o planejador Stripe não estavam disponíveis nesta sessão. Orientação baseada nas habilidades oficiais `stripe-best-practices`, instaladas a pedido do usuário, e deve ser validada com o planejador e a conta conectada antes de operar.

1. Inicialmente, sandbox dedicado, Checkout hospedado e Billing para assinatura; preço selecionado no servidor. Portal do cliente para cancelar ou alterar assinatura quando estiver implementado. Não fornecer dados de cartão ao backend/app.
2. O endpoint deve validar o token Supabase no servidor e usar uma chave restrita de teste com privilégio mínimo. O aplicativo nunca contém segredo Stripe.
3. Webhooks obrigatórios: assinatura verificada sobre corpo bruto; armazenamento durável e idempotente dos eventos; checkout completo apenas se o pagamento estiver confirmado; eventos assíncronos, renovações, falhas, cancelamentos, reembolsos e disputas precisam de reconciliação por servidor antes de conceder ou remover acesso.
4. Checkout está deliberadamente desativado. O código existente não cria sessões nem efetiva pagamentos, não processa eventos e não gerencia VIP. Webhook responde 503 mesmo após assinatura válida para não confirmar processamento inexistente.
5. Connect é destinado a conectar vendedores/recebedores e dividir ou transferir pagamentos. O requisito foi solicitado, mas o modelo de recebedores ainda não está definido; não criar contas conectadas automaticamente. Invoicing e Tax também dependem de modelo fiscal e configuração da conta.
6. Tax: antes de coletar automaticamente, confirmar jurisdição, registro ativo e código fiscal adequado com profissional responsável e testar em sandbox. Não ativar automaticamente sem essas informações.
7. O Mercado Pago pode ser integrado como provedor separado no futuro; não há implementação dele nesta versão e não é possível comparar ativação sem dados da conta ou do negócio.

## Planejador Stripe consultado

A conta SpaceAnime foi consultada em modo real apenas para planejamento e leitura: não havia produtos nem preços. Nenhuma cobrança ou produto foi criado. O planejador gerou uma árvore para assinaturas; a escolha final depende do preço, benefícios e forma de distribuição Android. Connect não foi ativado; não há definição de repasses a vendedores. Recomenda-se sandbox isolado e validar impostos e elegibilidade da loja antes de produção.

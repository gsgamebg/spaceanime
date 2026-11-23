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
## Catálogo proposto pelo proprietário — 02/11/2026

| Plano | Mensal | Anual |
|---|---:|---:|
| Basic | R$ 12,99 | R$ 128,00 |
| Platina | R$ 16,89 | R$ 142,00 |
| Premium | R$ 24,99 | R$ 234,00 |

Basic: sem anúncios, acesso privilegiado ao Discord e descontos. Platina: suporte 24h, sem anúncios, privilégios no Discord, avatares/banners exclusivos e XP multiplicado por 3,3. Premium: suporte 24h, privilégios no Discord, acesso antecipado a versões, sem anúncios, avatares/banners exclusivos, XP multiplicado por 4,4, prioridade em tickets/sugestões, nome no mural de contribuidores e cupons. A regra de multiplicação do XP ainda precisa ser implementada e validada no servidor. Esses benefícios ainda não existem e não devem ser prometidos como disponíveis.

Distribuição Android inicialmente por APK fora da Play Store. Antes de qualquer venda validar legislação, licenças de conteúdo, capacidade real de suporte 24h, cancelamento, entrega, privacidade e regras de distribuição. Stripe Tax permanece pendente de análise fiscal. Nenhum Product, Price, assinatura ou pagamento foi criado na Stripe.

## Catálogo na Stripe — área de testes

Em 02/11/2026, foram cadastrados na área restrita de testes três Products: SpaceAnime Basic, Platina e Premium, cada um com Price recorrente mensal e anual em BRL. Foram lidos de volta e conferidos:

| Chave de consulta | Frequência | Valor |
|---|---|---:|
| basic_mensal | mês | R$ 12,99 |
| basic_anual | ano | R$ 128,00 |
| platina_mensal | mês | R$ 16,89 |
| platina_anual | ano | R$ 142,00 |
| premium_mensal | mês | R$ 24,99 |
| premium_anual | ano | R$ 234,00 |

Os `lookup_key` são identificadores não secretos da área de testes. Não houve criação de produto na conta real, nem assinatura, checkout ou cobrança. O app/backend ainda não usam esses preços e o checkout segue bloqueado até a implementação de processamento durável e idempotente de webhooks e sincronização dos benefícios. Tax e regras tributárias permanecem pendentes.

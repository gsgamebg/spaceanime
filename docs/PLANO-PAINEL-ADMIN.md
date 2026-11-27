# Plano do painel administrativo do SpaceAnime

Substitui o esboço de `PAINEL-ADMIN-FUTURO.md`. Este é um plano: nada aqui está implementado.

## 1. Objetivo

Um painel só para o proprietário (e, depois, equipe) acompanhar e controlar o SpaceAnime sem mexer em SQL, variáveis do Render ou logs:

1. **Ver** quantas pessoas usam o app, em que versão, quanto entra de dinheiro e se os anúncios e as fontes funcionam.
2. **Agir** sobre anúncios, versão obrigatória, VIP, avisos e moderação.
3. **Provar** quem mudou o quê (auditoria).

## 2. O que já existe e será reaproveitado

| Dado ou peça | Onde está hoje | Uso no painel |
|---|---|---|
| Instalações e versões | `telemetry_sessions` (IP, aparelho, versão, idioma; apagada em 90 dias) | Usuários ativos, versões, aparelhos, países aproximados |
| VIP | `vip_orders`, `vip_memberships` | Receita, pedidos pendentes, assinantes, concessão manual |
| Anúncios | `app_runtime_config` (liga/desliga) | Edição dos switches com histórico |
| Resultado dos anúncios | Só em log do Render (`[ads] event`) | **Precisa virar tabela** (ver 4) |
| Fontes de vídeo e mangá | Workflow `source-health.yml` (issue no GitHub) | **Precisa gravar o resultado** (ver 4) |
| Versão e atualização obrigatória | Variável `SPACEANIME_RELEASE_JSON` no Render | **Precisa ir para o banco** (ver 4) |
| Notificações | `notification_*` | Avisos para todos, contagem de dispositivos |
| Comentários, denúncias, tickets | `comments`, `reports`, `tickets` (tabelas criadas, sem uso) | Moderação e suporte |
| Papéis e auditoria | `staff_roles`, `staff_audit`, `authorize_financial_read` (exige MFA `aal2`) | Base da autorização |

Limpeza prevista: o código de Stripe em sandbox (`/webhooks/stripe`, `/api/billing/checkout`, `/api/admin/finance/summary`, `billing.js`, `entitlements.js`, `reconcile.js`) ficou obsoleto com o Mercado Pago. Remover antes do painel, para não existir uma segunda "receita".

## 3. Telas

### Fase 1: leitura (visão geral)

1. **Visão geral**
   - Usuários ativos em 24 h / 7 d / 30 d, instalações novas por dia.
   - Receita do mês (Pix aprovado), assinantes ativos por plano, pedidos pendentes e expirados.
   - Estado das fontes (verde/vermelho, última checagem) e dos anúncios (taxa de preenchimento).
2. **Usuários**
   - Busca por e-mail ou ID, status VIP, data de cadastro, versão do app.
   - Sem senha, token ou dado de pagamento; IP só mascarado (ex.: `189.12.xxx.xxx`).
3. **Versões e aparelhos**
   - Distribuição por versão (quanto falta para a atualização obrigatória), Android x Windows, modelos mais comuns.
4. **Financeiro**
   - Pedidos e pagamentos com situação, valor e ID do Mercado Pago; conciliação (pago no Mercado Pago e não liberado aqui, ou o contrário).
5. **Anúncios**
   - Por provedor e formato: carregou, falhou, exibiu; motivos de falha mais comuns. Responde "o Start.io está entregando?".
6. **Fontes**
   - Histórico de cada verificação (anime, mangá, vídeo) e tempo de resposta.

### Fase 2: controles

7. **Configuração de anúncios**: provedor, banners, intersticial (a cada N episódios), vídeo premiado (minutos do passe). Cada salvamento gera auditoria.
8. **Lançamento e atualização obrigatória**: cadastrar versão, links e SHA-256 (hoje colados à mão no Render), com validação e botão "tornar obrigatória".
9. **VIP manual**: conceder, estender ou revogar (cortesia, reembolso, erro). Sempre com motivo.
10. **Avisos**: mensagem para todos os dispositivos Android, com limite de envio e pré-visualização.

### Fase 3: comunidade e suporte

11. **Moderação**: fila de denúncias, ocultar comentário, banir.
12. **Suporte**: tickets e respostas; pedidos LGPD (exportar ou apagar os dados de um usuário), com registro.

### Fase 4: alertas

13. Avisos para o proprietário: fonte fora do ar, Pix pago mas VIP não liberado, falha em massa de anúncios, versão nova com mais falhas. Canal: e-mail ou push.

## 4. Dados novos necessários

| Tabela | Conteúdo | Quem grava |
|---|---|---|
| `ads_daily` | Contadores por dia, provedor, formato e evento. **Contadores, não eventos individuais.** | Endpoint `/api/v1/ads/events` (já recebe o evento; hoje só imprime) |
| `source_health_runs` | Verificação, resultado, tempo, detalhe | Workflow `source-health.yml`, com um segredo próprio |
| `app_releases` | Versão, código, notas, links, SHA-256, `required_from` | Painel |
| `admin_settings_history` | Antes e depois de cada mudança de configuração | Backend |
| `vip_grants` | Concessões manuais com motivo e autor | Backend |
| `staff_roles` (já existe) | Ampliar os papéis: `owner`, `support`, `moderator` | Só via SQL pelo proprietário |

Mudança no que já existe: o backend passa a ler a versão atual de `app_releases` (com cache curto) em vez da variável `SPACEANIME_RELEASE_JSON`. O endpoint `/updates.json` e os links `/downloads/...` não mudam para o app.

## 5. Arquitetura

```
Navegador (painel)  ──HTTPS──►  Backend Node (Render, já existente)  ──►  Supabase (service role, só no servidor)
   login + MFA                   /api/admin/*  (nova família de rotas)
```

- **Frontend:** site estático (sem build) em `backend/admin/`, entregue pelo mesmo serviço do Render em `/admin/`: mesma origem, sem CORS, nenhum custo extra e nenhuma chave privilegiada no navegador. Pode migrar para um site separado se o painel crescer.
- **Backend:** rotas `/api/admin/*` no mesmo serviço do Render, reaproveitando `readJsonBody`, limite de corpo e o padrão de autenticação atual.
- **Autenticação em toda rota admin:**
  1. Token do Supabase válido.
  2. Nível de autenticação `aal2` (segundo fator obrigatório).
  3. Papel em `staff_roles` conferido no servidor com uma função única `authorize_staff(papel_mínimo)` (generalização de `authorize_financial_read`).
  4. Escritas registram `staff_audit`; nenhum papel é atribuível pela interface.
- **Cabeçalhos e CORS:** só a origem do painel; CSP restritiva; `Cache-Control: no-store`.
- **Consultas pesadas:** agregações feitas no banco (funções SQL ou visões), com cache de 60 s no backend, para o painel não derrubar o Supabase nem o Render gratuito.

## 6. Segurança e privacidade (LGPD)

- Painel visível só com login + MFA; sessão curta; saída automática por inatividade.
- Menor privilégio por papel (tabela na seção 8): `support` não vê financeiro; `moderator` não vê e-mail completo nem IP.
- IP e aparelho aparecem só mascarados e só para `owner`; retenção de 90 dias continua.
- Exportar e apagar dados de um usuário sai registrado na auditoria e atende pedidos LGPD.
- Nenhum token, senha ou segredo aparece no painel, nos logs nem na auditoria.
- A política de privacidade ganha um parágrafo sobre acesso administrativo.
- Teste automático: usuário comum e anônimo recebem 401/403 em **todas** as rotas `/api/admin/*`.

## 7. Entrega em etapas

| Etapa | Entrega | Pronto quando |
|---|---|---|
| 0. Base | Remover Stripe sandbox; `authorize_staff`; papéis `owner`/`finance`/`support`/`moderator`; login com MFA; esqueleto do painel; teste de acesso negado | Só o owner com MFA entra |
| 1A. Receita (leitura) | Visão geral, financeiro e conciliação, VIP, anúncios (configuração) | Números batem com o Supabase e o Mercado Pago |
| 1B. Estabilidade (leitura) | Usuários ativos, versões e aparelhos, fontes | Distribuição de versões bate com a telemetria |
| 2. Observabilidade | `ads_daily` e `source_health_runs`; telas de resultado dos anúncios e histórico das fontes | Dá para responder "o Start.io entrega?" sem log |
| 3. Controles | Anúncios, lançamentos (fim da variável do Render), VIP manual, avisos | Toda mudança sai na auditoria |
| 4. Comunidade | Moderação, suporte, pedidos LGPD | Fila de denúncias funcionando |
| 5. Alertas | Notificações ao proprietário | Falha simulada gera aviso |

As etapas 1A e 1B andam em paralelo, pois não usam as mesmas tabelas. Cada etapa é publicada e validada antes da seguinte; nenhuma exige APK novo, exceto a coleta de resultado de anúncios (já na `0.7.25` planejada).

## 8. Decisões

Decididas:
1. **Quem usa:** você e a equipe (suporte e moderação). Os papéis entram já na etapa 0 (ver abaixo).
2. **Prioridade:** receita **e** estabilidade, em paralelo. A etapa 1 entrega as duas frentes de leitura juntas.

Em aberto (podem ser decididas durante a etapa 0):
3. Hospedagem do site do painel: estático gratuito (Render Static ou Vercel).
4. Visual: português, escuro e roxo, mesma identidade do app.
5. Alertas: por e-mail, por push no seu celular, ou os dois.

### Papéis e o que cada um vê

| Papel | Vê | Pode alterar |
|---|---|---|
| `owner` (você) | Tudo, inclusive IP mascarado e auditoria | Tudo, inclusive papéis (só por SQL) |
| `finance` | Financeiro, VIP, anúncios | VIP manual (com motivo) |
| `support` | Usuários (sem IP), tickets, VIP (só leitura) | Responder tickets, pedidos LGPD |
| `moderator` | Denúncias e comentários, sem e-mail completo | Ocultar comentário, banir |

Convites de equipe: só o `owner` cria a conta e atribui o papel; todos entram com MFA obrigatório.

## 9. Riscos

- **Render gratuito dorme:** o primeiro acesso demora; aceitável para um painel interno, mas os alertas não podem depender dele; o monitor das fontes já roda fora, no GitHub.
- **Contadores de anúncios** dependem do app novo; antes disso o painel mostra só a configuração.
- **Dados pessoais no painel:** o risco maior; por isso papéis, mascaramento e auditoria entram na etapa 0, e não no fim.

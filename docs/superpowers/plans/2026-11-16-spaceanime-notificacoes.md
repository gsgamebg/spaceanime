# Plano: notificações Android e comunicados

> **Dependência:** executar depois do plano `2026-11-16-spaceanime-catalogo-mangas.md`, que fornece o armazenamento local de mangás seguidos. Preferências, assinatura de dispositivos e serviços deste plano só sincronizam os dados autorizados no desenho.

**Objetivo:** permitir opt-in separado para episódios de anime, capítulos de mangá e comunicados; enviar push Android enquanto o app estiver fechado; mostrar comunicados/estado de atualização no Windows somente enquanto o app estiver aberto.

**Arquitetura:** cliente Android com FCM; endpoints autenticados pelo token Supabase da sessão; tabelas Supabase privadas operadas pelo backend; worker Render agendado para coleta e digest. Escanear fontes a cada seis horas e enviar no máximo um digest diário por usuário, no horário local escolhido (padrão 20:00 `America/Sao_Paulo`); comunicados críticos autorizados podem ser imediatos.

**Referência:** `docs/superpowers/specs/2026-11-16-spaceanime-discovery-library-player-notifications-design.md`.

## Restrições e limites de segurança

- Sincronizar somente IDs opacos de fonte/conteúdo, variante de áudio quando necessária, preferências, fuso horário/horário de digest e token do dispositivo. Nunca sincronizar histórico, posição de reprodução, credenciais ou biblioteca completa.
- RLS fechado para acesso direto do cliente às tabelas de token/assinatura. Autenticar e autorizar cada operação no backend; credenciais privilegiadas somente em variáveis seguras do Render.
- Exigir opt-in antes de pedir permissão Android e antes de registrar token. Preferência desligada implica nenhuma mensagem personalizada.
- Remover token e inscrições em opt-out/sign-out; tratar token expirado/inválido e permitir nova inscrição. Não colocar tokens ou payloads sensíveis em logs.
- Nunca enviar uma notificação por hora. Deduplicar servidor-side por release e por usuário/data local; isolar falha de uma fonte sem interromper as outras.
- FCM, Supabase e Render só ficam “configurados” depois de provisionamento externo e teste real. Builds locais sem Firebase devem continuar possíveis, mas a interface deve explicar que push ainda não está configurado.
- Comunicados críticos exigem autenticação administrativa separada e expiração; não adicionar painel administrativo neste escopo. Windows recebe comunicados em sessão ativa, sem push após fechamento.

## Pontos de revisão

1. Registro, alteração de preferência e exclusão exigem sessão válida e operam apenas sobre o usuário autenticado? Testar ownership, token ausente/inválido, sign-out e RLS.
2. Nenhuma mensagem personalizada é enviada sem opt-in; toggles independentes de anime/mangá/comunicados são respeitados? Testar combinações.
3. Um evento de catálogo gera no máximo um item por usuário e o digest no máximo um envio por data local, inclusive em retry/worker concorrente? Testar idempotência e fusos.
4. Falha de um provedor, token ou envio não cancela os demais; tokens inválidos são removidos sem registrar segredo? Testar falhas injetadas e redação dos logs.
5. Permissão é pedida só após ação explícita, Windows não depende do SDK Android e app continua compilando sem credenciais Firebase? Compilar plataformas e verificar os estados opt-in/não configurado.

## Tarefa 1: modelo de dados privado e API autenticada

**Arquivos principais:** migração SQL nova em `supabase/migrations/`, `backend/server.js`, novos módulos de notificações e `backend/*.test.js`.

- [ ] Escrever testes HTTP para ausência/token Supabase inválido, usuário A tentando alterar usuário B, registro/upsert, atualização de preferências, remoção de token no opt-out/sign-out, payload excessivo, método/rota inválidos e serviço Supabase indisponível.
- [ ] Executar `node --test backend/notifications-api.test.js` e confirmar falha dos casos novos antes de adicionar rotas.
- [ ] Criar migração aditiva com tabelas de dispositivos, preferências/inscrições, releases observadas, itens de digest e comunicados; incluir chaves únicas necessárias para idempotência e políticas RLS negando acesso direto anônimo/autenticado. Não alterar tabelas existentes de cobrança/perfil.
- [ ] Criar endpoints versionados de registro, preferências e remoção. Validar formato/tamanho dos campos e derivar `user_id` exclusivamente da sessão Supabase validada pelo backend. Usar service credential somente no servidor e resposta sem token/segredos.
- [ ] Criar cliente de API Kotlin que envia somente a whitelist de IDs/variantes e preferências. Expor acesso ao token de sessão por método explícito de `AccountState`; não persistir nem registrar token no cliente.
- [ ] Executar testes do backend e testes Kotlin do cliente; revisar SQL com `supabase db lint`/validação local se disponível. Sem acesso à instância do usuário, registrar migração como pendente de aplicação externa.

## Tarefa 2: preferências, consentimento e registro Android

**Arquivos principais:** `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, fontes Android em `composeApp/src/androidMain/`, perfil/configuração em `composeApp/src/commonMain/kotlin/app/anim/profile/` ou tela existente equivalente, `AccountState` expect/actual e testes.

- [ ] Escrever testes do modelo de preferências para opt-in independente, horário/fuso válido, opt-out, sign-out e token atualizado. Cobrir estado não configurado sem Firebase e estado sem permissão.
- [ ] Implementar uma seção clara de notificações nas configurações de perfil com opções separadas (anime, mangá, comunicados), horário local e explicação de uso. Não solicitar permissão ao abrir a tela.
- [ ] Adicionar Firebase Messaging à variante Android sem incluir `google-services.json` ou chaves no Git. Tornar configuração Firebase ausente um estado de configuração explícito e manter compilação Android/Desktop local; nunca iniciar instância silenciosamente com valores inventados.
- [ ] Solicitar permissão Android apenas depois de o usuário ligar uma opção que requer push. Só após permissão e autenticação registrar token; sincronizar mudanças de favoritos anime e mangás seguidos apenas enquanto as preferências correspondentes estiverem habilitadas.
- [ ] Ao desligar preferências ou sair da conta, remover inscrição/token remoto antes de limpar a sessão; propagar falha de remoção para mensagem/retry, sem alegar sucesso. Atualização de token FCM deve substituir o token antigo de forma idempotente.
- [ ] Remover prompts de permissão de notificações existentes que ocorram automaticamente no início ou ao abrir o player; manter notificações locais de reprodução separadas das notificações de novidades.
- [ ] Rodar testes e `.\gradlew.bat :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinDesktop`. Validar no Android permissão concedida/negada e no Windows que preferências e comunicações não iniciam push.

## Tarefa 3: coleta, deduplicação, digest e comunicados

**Arquivos principais:** novo worker/módulos em `backend/`, novos testes de worker, `backend/package.json`, lockfile correspondente e `render-backend.yaml`.

- [ ] Escrever testes com relógio e provedores injetados para varredura a cada seis horas, cursor independente, outage isolado, identificação estável de release, preferências opt-out, digest diário por fuso, retry concorrente e token FCM inválido.
- [ ] Implementar worker com interfaces injetáveis de catálogo, store e sender. Usar somente endpoints/fonte e formato já suportados; se uma fonte não oferece feed verificável, não inventar releases. Uma falha em uma fonte é registrada sem descartar atualizações coletadas pelas outras.
- [ ] Fazer fila e envio idempotentes, impondo unicidade de release/usuário e de digest por usuário/data local no banco, não somente em memória. Agrupar itens de anime e mangá no mesmo resumo diário; respeitar horário escolhido, padrão 20:00 `America/Sao_Paulo`.
- [ ] Integrar FCM somente no servidor com credencial por ambiente; remover tokens inválidos e nunca registrar token ou credencial. Comunicados ordinários entram no digest; críticos exigem autorização administrativa, deduplicação e expiração.
- [ ] Adicionar serviço Cron ao blueprint Render com execução horária para avaliar elegibilidade, mantendo coleta efetiva a cada seis horas. Configurar limites/timeout e variáveis sem valores secretos no YAML; garantir que a rota pública de saúde não exponha estado privado.
- [ ] Executar `node --test backend/notifications-api.test.js backend/notifications-worker.test.js` e `npm test` em `backend`; validar worker em modo simulado sem credenciais, garantindo zero envios reais.

## Tarefa 4: recebimento Android e comunicados no Windows

**Arquivos principais:** serviço Android FCM, canal/notificação Android existente, tela/estado de comunicados comum e ponto de inicialização em `App.kt`.

- [ ] Adicionar testes para tratamento de payload válido/inválido, expiração, deep link seguro para o app, duplicata de message ID e preferência desligada; verificar que nenhum URL arbitrário é aberto.
- [ ] Receber push Android e gerar notificação com canal estável, texto essencial e destino interno validado. No primeiro plano, não duplicar notificação se o mesmo comunicado já estiver visível; tratar eventos expirados sem apresentar alerta atrasado.
- [ ] Adicionar painel/bandeja de comunicados no app para Android e Windows enquanto aberto. Integrar aviso de atualização existente sem substituir o mecanismo que verifica versão obrigatória.
- [ ] Garantir que o Windows não inicialize classes/dependências Android e não prometa entrega depois de o processo fechar. Preservar notificações locais de playback.
- [ ] Rodar `node --test backend/notifications-api.test.js backend/notifications-worker.test.js backend/notifications-payload.test.js`, testes de notificação local existentes, `npm test`, `.\gradlew.bat :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinDesktop` e testes desktop focados.

## Verificação do plano

`node --test backend/notifications-api.test.js backend/notifications-worker.test.js backend/notifications-payload.test.js`  
`npm test` (em `backend`)  
`.\gradlew.bat :composeApp:desktopTest --tests "app.anim.notifications.*" :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinDesktop`

**Validação de produção separada:** aplicar migração no Supabase, configurar projeto Firebase/credencial no Render, publicar o cron e testar opt-in, push fechado, opt-out e token revogado em conta/dispositivo de teste. Não declarar produção ativa antes desses testes.

# Plano: atualização obrigatória e publicação

> **Execução:** preservar o contrato público existente e fazer toda publicação falhar fechada. Não executar deploy nem substituir artefatos sem aprovação e credenciais do responsável.

**Objetivo:** tornar releases Android/Windows verificáveis e repetíveis, preservar a assinatura Android e garantir que uma falha de rede não remova um bloqueio obrigatório já apresentado.

**Arquitetura:** manter o manifesto atual compatível; testar a decisão do cliente; validar metadados, URLs e hashes antes de publicar; manter chaves e credenciais somente no ambiente externo.

**Referência:** `docs/superpowers/specs/2026-11-16-spaceanime-discovery-library-player-notifications-design.md`, `docs/ATUALIZACOES.md`.

## Restrições

- Não instalar APK silenciosamente; a instalação continua explícita pelo usuário.
- Um manifesto válido só bloqueia quando o código da versão publicada é superior ao instalado. Manifesto ausente/inválido não cria um bloqueio novo; falha/regressão posterior não remove bloqueio já exibido naquela sessão.
- Não afirmar que o bloqueio do cliente impede acesso aos serviços ou que o download é verificado pelo app.
- Validar ambos artefatos e hashes antes de gravar/publicar o manifesto; exigir HTTPS e os hosts de publicação já aprovados.
- Releases Android precisam da mesma chave de assinatura do canal instalado. Nunca gerar/substituir chave nem registrar keystore, senha, token ou JSON de serviço no Git.
- APK/EXE, deploy, Firebase, Render e credenciais são dependências operacionais externas, não resultado presumido de testes locais.

## Pontos de revisão

1. Código igual/menor, erro ou manifesto inválido não abre bloqueio; código maior válido abre e o bloqueio permanece diante de falhas subsequentes? Teste reducer/cliente.
2. Hash malformado/incorreto, metadado inválido, URL HTTP ou host não aprovado impede qualquer publicação? Testar que não sobra manifesto publicado.
3. Release exige código superior ao mínimo declarado e tem versão/formato corretos nos dois artefatos? Testar validação do script antes de baixar/publicar.
4. Assinatura release exige configuração completa fora do repositório e os builds debug continuam funcionando sem ela? Testar os dois caminhos sem expor valores de ambiente.
5. Documentação diferencia claramente compilação, staging, publicação, atualização manual e validação em dispositivo? Revisar `docs/ATUALIZACOES.md`.

## Tarefa 1: preservar e testar o bloqueio no cliente

**Arquivos principais:** `composeApp/src/commonMain/kotlin/app/anim/update/Updates.kt`, `composeApp/src/commonMain/kotlin/app/anim/App.kt`, `composeApp/src/desktopTest/kotlin/app/anim/update/UpdatesTest.kt`.

- [ ] Adicionar testes para atualização válida, versão atual/antiga, manifesto indisponível antes do bloqueio, erro após bloqueio e resposta inválida após bloqueio.
- [ ] Executar `.\gradlew.bat :composeApp:desktopTest --tests "app.anim.update.UpdatesTest"` e confirmar falha dos casos de estado que ainda não estejam cobertos.
- [ ] Extrair ou ajustar a decisão de estado de atualização para que somente uma resposta válida e superior crie o bloqueio, e uma falha posterior não o limpe durante a sessão. Manter a validação de origem HTTPS e hash já existente.
- [ ] Rodar os testes e compilar Desktop/Android; confirmar que o feed permanece compatível com schemaVersion atual e que o Windows continua usando o contrato existente.

## Tarefa 2: validação de staging de artefatos

**Arquivos principais:** `distribution/build.py`, `distribution/test_build.py`, `distribution/release-0.7.19.json` (somente como exemplo vigente) e `render-backend.yaml`.

- [ ] Criar testes para código de release não monotônico, versão inválida, hash faltante, URL de artefato fora de HTTPS, redirecionamento HTTP, download maior que o limite, hash incorreto e falha do segundo artefato. Confirmar que `updates.json` só é escrito após os dois downloads validados.
- [ ] Executar `python -m unittest distribution.test_build` e confirmar falha dos casos novos antes da implementação.
- [ ] Adicionar requisito explícito de código mínimo anterior à criação de manifesto (por exemplo `MINIMUM_SUPPORTED_CODE`), validar `RELEASE_CODE` maior que esse valor e validar a forma dos URLs/artefatos com o mesmo contrato de hosts aprovado pelo feed.
- [ ] Manter downloads em temporários, comparar SHA-256 e montar o manifesto somente após ambos os pacotes estarem completos. Garantir limpeza dos temporários em falha e nenhum manifesto parcial.
- [ ] Rodar `python -m unittest distribution.test_build`; fazer um staging controlado com artefatos de teste e comparar bytes/hash/manifesto. Não publicar os artefatos.

## Tarefa 3: build assinado e instruções de release

**Arquivos principais:** `composeApp/build.gradle.kts`, `distribution/`, configuração de workflows existente e `docs/ATUALIZACOES.md`.

- [ ] Definir validação de configuração para tarefa Android release: assinatura via variáveis de ambiente documentadas (`SPACEANIME_KEYSTORE_FILE`, `SPACEANIME_KEYSTORE_PASSWORD`, `SPACEANIME_KEY_ALIAS`, `SPACEANIME_KEY_PASSWORD`); tarefa release falha com mensagem clara se alguma faltar e build debug não depende delas.
- [ ] Escrever testes/validação de configuração para o build sem valores secretos; conferir que propriedades e logs não imprimem senhas. Confirmar que nenhum keystore/config local foi adicionado ao controle de versão.
- [ ] Verificar tarefas reais disponíveis com `.\gradlew.bat :composeApp:tasks --all`; implementar ou ajustar o script de release para compilar APK assinado e pacote Windows ZIP a partir das tarefas existentes, sem inventar nomes de task nem substituir a distribuição MSI existente.
- [ ] Atualizar `docs/ATUALIZACOES.md` com ordem: conferir versão/código, compilar, verificar assinatura e artefatos, calcular hash, staging, validar URLs públicas, publicar manifesto por último, instalar manualmente e testar upgrade em dispositivo.
- [ ] Executar `python -m unittest distribution.test_build`, `.\gradlew.bat :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinDesktop` e os testes de update. Executar `assembleRelease` somente quando o proprietário fornecer a configuração segura localmente.

## Verificação do plano

`python -m unittest distribution.test_build`  
`.\gradlew.bat :composeApp:desktopTest --tests "app.anim.update.UpdatesTest" :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinDesktop`

**Fora da validação local:** preservação da chave real, disponibilidade pública, instalação/upgrade em aparelho e rollout Render dependem de artefatos e acessos do responsável.


# Validação 0.6.0 — layout SpaceAnime

- Build final `:composeApp:desktopTest :composeApp:assembleDebug`: sucesso.
- Suíte local: 40 testes, 36 aprovados, 4 opt-in ignorados, zero falhas/erros.
- Rodada separada com `ANIM_SOURCE_SMOKE=1` e `*DiscoveryTest*`: quatro testes aprovados, incluindo consulta real nos dois servidores e vínculo por episódio. Servidor Space 1 retornou 20 episódios recentes e 51 títulos; Space 2, 20 episódios e 10 títulos. Quantidades variam com o catálogo.
- APK debug: app.anim, versão 0.6.0, versionCode 8, Android mínimo 26. Não testado em dispositivo físico nesta etapa.
- Interface sem automação visual, conforme pedido. Não foi afirmada fidelidade pixel a pixel, ausência de todos os erros de execução ou auditoria completa de acessibilidade/segurança.
- Compilador mantém aviso de depreciação do carregador de ícone da janela desktop; não impede o build.
- Detalhamento de cada etapa e arquivos em SPACEANIME-DESIGN.md.

---
# Validação

## SpaceAnime 0.5.0 — 2026-10-30

- Builds desktop e APK concluídos. Android mantém applicationId app.anim, versionCode 7 e versionName 0.5.0.
- 27 testes Kotlin passaram; três integrações opt-in ficaram ignoradas. Incluem persistência/limite/corrupção de perfis, bibliotecas separadas, expiração e limites de cache, resolução não cacheada de streams e rejeição de manifests/URLs de atualização inválidos.
- Dois testes Python do gerador de manifesto passaram. Publicação no Render e download real de atualização não foram testados: não há domínio nem repositório criado. UPDATE_MANIFEST_URL permanece vazia.
- Runtime do pacote SpaceAnime.exe carregou Java, libVLC e plugins com código de saída zero, sem abrir interface.
- Logo SVG própria e derivados PNG/ICO, ícone vetorial Android, paleta e tipografia integrados. Nenhum teste visual executado nesta etapa, conforme preferência do usuário.
- Preservados caminho da biblioteca Windows e applicationId Android. Não há migração destrutiva nem login/coleta/pagamentos ativos. As melhorias de desempenho não foram quantificadas em aparelhos.

## Pacote Windows portátil — 2026-10-30

- `desktopTest createDistributable` concluído; 20 testes passaram, três integrações opt-in ignoradas, sem falhas.
- Executável Anim.exe gerado pelo jpackage, com runtime Java incluído. O script copia o runtime VLC completo com plugins e avisos para o pacote.
- Executado `Anim.exe --check-runtime` com PATH restrito ao Windows e sem ANIM_VLC_HOME/VLC_PLUGIN_PATH: encerramento com código zero após carregar libVLC e enumerar filtros de vídeo. Esse modo não abre interface.
- O runtime do jpackage não inclui java.exe; a verificação usa o próprio executável distribuído.
- Não houve teste visual, reprodução neste pacote nem execução em outro computador. A verificação cobre o carregamento nativo na máquina de desenvolvimento usando o runtime incluído.

## Blogger 0.4.2 — 2026-10-30

- Reproduzida falha no episódio 1 de Overlord IV (AniTube): URL Blogger de 720p retornava HTTP 403.
- Resolução alterada para solicitar URLs atuais ao endpoint do player Blogger, sem reutilizar os links incorporados no HTML. Referer e User-Agent são enviados ao player.
- Após a alteração, ambas as opções, 720p e 360p, retornaram HTTP 206 com Content-Type video/mp4 e bytes de mídia. Verificação feita com Range e leitura limitada, sem abrir a interface ou baixar o episódio completo.
- Adicionado teste de regressão com URL antiga no HTML e URL nova na resposta do endpoint; verifica seleção da URL nova e cabeçalhos. Teste de acesso real opcional: `ANIM_BLOGGER_SMOKE=1`.
- Essa verificação confirma acesso à mídia; a reprodução visual continua a cargo do usuário.

## Versão 0.4.1 — 2026-10-30

- `desktopTest desktopJar assembleDebug --offline --console=plain --max-workers=2`: BUILD SUCCESSFUL, 45 tarefas, 15 executadas.
- 19 testes passaram, sem falhas: seis de persistência, cinco de regras do player e oito de fontes. Dois testes opt-in (player nativo e acesso às fontes ao vivo) ficaram ignorados nesta execução.
- Compilados carregamento automático de episódios ao abrir favoritos, filtro textual, filtro de não assistidos, inversão da ordem e preservação de posição na nova tentativa. Os testes existentes não automatizam esses fluxos de interface.
- APK debug 0.4.1, versionCode 5, em `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.
- O usuário relatou sucesso nos testes anteriores e assumiu a validação visual. Nenhuma automação visual foi realizada nesta entrega. A execução Android não foi verificada independentemente pelo agente.
- A primeira tentativa no sandbox não conseguiu conectar ao daemon Gradle; a compilação passou com permissão para comunicação local.

As seções abaixo são registros históricos e suas limitações referem-se às versões indicadas.

## Player 0.2.0 — 2026-10-29

- Compilação desktop e APK Android concluída com sucesso.
- Quatro testes comuns passaram: URL HTTPS com query assinada, rejeição de entradas inválidas, limites de seek e formatação de duração.
- Teste nativo opt-in passou com libVLC 3.0.24: MP4 Sintel (52.209 ms) e HLS Big Buck Bunny (634.634 ms). Ambos avançaram, pausaram, buscaram a posição de 20 segundos, alteraram volume, retomaram e liberaram recursos sem aceitar comandos após o encerramento.
- Os endpoints iniciais Google/Bitdash responderam HTTP 403 nesta rede. Foram substituídos por exemplos W3C/Mux acessíveis; não foi alterada a validação de TLS.
- Inspeção visual no Windows: entrada Assistir, vídeo MP4 dentro do Compose, vídeo HLS e modo tela cheia. Corrigido contraste de título, posição, duração e volume. Retorno por clique em tela cheia não foi confirmado: a ferramenta de automação perdeu o mapeamento do monitor. Encerramento via atalho concluído, com processo de execução finalizado normalmente.
- Build final após correção visual passou; teste nativo ficou ignorado nesse último build padrão, pois só a cor do contêiner mudou. O teste nativo anterior havia passado com os mesmos adaptadores de reprodução.
- APK final 0.2.0: 13.840.361 bytes. Nenhum celular/emulador listado em `adb devices`; execução Android permanece pendente.
- Não se verificou áudio por escuta, seleção de legendas/faixas, reprodução DASH ou qualidade adaptativa em diferentes redes.

Os registros abaixo descrevem a base 0.1.0, antes do player.

## Comando executado

```powershell
. ./scripts/env.ps1
./gradlew.bat :composeApp:desktopJar :composeApp:assembleDebug --console=plain
```

Resultado: BUILD SUCCESSFUL; 41 tarefas executadas.

## Execução

Windows: `scripts/run-desktop.ps1` abriu a janela Anim. Tela inicial inspecionada e botão Abrir minha biblioteca levou à tela Sua biblioteca. Layout desktop sem cortes observados na janela padrão.

Android: APK debug gerado (10.312.234 bytes na primeira compilação). `adb devices` não encontrou dispositivos. Não foi instalado em celular nem emulador; compilação não equivale a teste de execução.

## Limitações

- Ainda sem player, catálogo, fontes ou persistência.
- Configurações é uma tela informativa inicial, não contém preferências editáveis.
- Layout compacto implementado, ainda sem inspeção visual em Android.
- Build emitiu aviso de que `libandroidx.graphics.path.so` foi empacotada sem remoção de símbolos. Não impediu a geração do APK.
- Gradle Wrapper foi gerado com avisos de APIs obsoletas para Gradle 9; o projeto permanece fixado no Gradle 8.13.
- CI ainda não executada em GitHub.


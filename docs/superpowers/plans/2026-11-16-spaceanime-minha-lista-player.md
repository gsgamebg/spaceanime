# Plano: Minha Lista e navegação do player

> **Execução:** seguir as tarefas na ordem, escrevendo o teste antes da implementação. A lista inclui mangás seguidos pelo `MangaFollowStore` do plano de catálogo.

**Objetivo:** substituir seções desconectadas por Minha Lista com filtros Tudo / Continuar / Favoritos e permitir avançar ou voltar episódios sem perder a fonte, áudio ou retomada local.

**Arquitetura:** derivar seções a partir dos stores existentes, sem mudar a persistência de anime; passar ao player uma sequência explícita de episódios e carregar o episódio vizinho pelo mesmo repositório/resolvedor já usado.

**Referência:** `docs/superpowers/specs/2026-11-16-spaceanime-discovery-library-player-notifications-design.md`.

## Restrições

- O progresso e o histórico de reprodução permanecem exclusivamente locais.
- `Tudo` reúne favoritos de anime, mangás seguidos e progresso de anime ainda não concluído; remover duplicatas somente pela identidade estável existente (incluindo origem e áudio).
- `Continuar` inclui somente progresso não concluído com posição de retomada. Itens concluídos permanecem no Histórico, não nessa seção.
- `Favoritos` inclui favoritos de anime e mangás seguidos.
- Não alterar o JSON da biblioteca para encaixar modelos de mangá; não apagar ou sobrescrever dados locais corrompidos.
- Anterior/próximo só aparece quando há episódio adjacente. No fim, oferecer ação explícita; não iniciar reprodução automática.
- Trocar episódio deve resolver streams novamente, manter o contexto de origem/variante e criar sessão nova do player, preservando a gravação local do progresso.

## Pontos de revisão

1. Os três filtros têm exatamente os membros definidos, sem entradas duplicadas? Testar união, precedência e identidade de anime por fonte/áudio.
2. Progresso concluído e progresso sem posição não aparecem em Continuar? Testar incompleto, concluído, posição zero e item favorito com progresso.
3. Os limites da fila ocultam Anterior/Próximo corretamente e não trocam áudio/fonte? Testar primeiro, meio e último episódio e sequência vazia.
4. Trocar episódio usa novamente o resolvedor, salva o progresso do item anterior e não faz autoplay? Testar callback, estado da sessão e persistência.
5. O armazenamento existente sobrevive a migrações/telas novas e arquivo corrompido? Rodar `FileLibraryStoreTest` sem alterar o formato/schema atual.

## Tarefa 1: modelo derivado e filtros de Minha Lista

**Arquivos principais:** `composeApp/src/commonMain/kotlin/app/anim/library/LibraryStore.kt`, `LibraryScreen.kt`, novo modelo puro de seções da biblioteca, implementação por perfil do `MangaFollowStore` do plano de catálogo e testes de biblioteca.

- [ ] Criar testes unitários puros para Tudo / Continuar / Favoritos, cobrindo favorito com progresso (uma só entrada), duas origens/áudios distintos, progresso concluído, posição zero, mangá seguido e remoção de duplicatas.
- [ ] Executar `.\gradlew.bat :composeApp:desktopTest --tests "app.anim.library.LibrarySectionsTest" --tests "app.anim.library.FileLibraryStoreTest"` e confirmar que os testes novos falham antes da implementação.
- [ ] Definir o tipo de filtro e uma projeção de visualização que combine referências de anime e mangá sem alterar o modelo persistido. Ao unir anime favorito e progresso, manter a identidade existente e o progresso mais recente ainda não concluído.
- [ ] Refazer `LibraryScreen` com seletor acessível Tudo / Continuar / Favoritos, estado vazio por filtro, cartões com origem/áudio e ações de retomar, abrir ou remover dos favoritos/seguidos.
- [ ] Carregar stores usando o perfil ativo, observar atualizações reativas e manter os estados de erro explícitos; não converter falha de leitura em lista vazia bem-sucedida.
- [ ] Rodar os testes de seções, perfil e store; confirmar que fixtures antigas continuam sendo lidas sem migração destrutiva.

## Tarefa 2: fila de episódios e controles de navegação

**Arquivos principais:** `composeApp/src/commonMain/kotlin/app/anim/player/PlayerScreen.kt`, `App.kt`, `SourcesScreen.kt`, `LibraryScreen.kt`, novo modelo comum de fila e testes de player.

- [ ] Escrever testes do modelo de fila para anterior/próximo no primeiro, meio e último episódio, episódio atual ausente, sequência vazia e preservação do item selecionado.
- [ ] Executar `.\gradlew.bat :composeApp:desktopTest --tests "app.anim.player.EpisodeQueueTest" --tests "app.anim.player.PlaybackTest" --tests "app.anim.player.PlayerInteractionsTest"` e confirmar falha dos novos casos.
- [ ] Criar `EpisodeQueue(anime, episodes, currentEpisodeId)` e uma operação tipada para obter episódio adjacente. Preservar a ordem entregue pela fonte e tratar ID atual inexistente sem selecionar episódio incorreto.
- [ ] Passar fila e callback de navegação para o player a partir da tela de episódios; fornecer contexto equivalente ao retomar pela Minha Lista quando a fonte consegue obter a sequência.
- [ ] Adicionar controles Anterior/Próximo com estado indisponível nos limites e ação explícita para o próximo episódio ao terminar. Não tocar automaticamente nem descartar o player atual ao falhar a resolução do seguinte.
- [ ] No callback, salvar o progresso local atual, solicitar streams do episódio selecionado através do repositório existente, e só então substituir fonte, episódio e sessão. Manter retry e seleção de servidor já existentes.
- [ ] Executar testes de fila, interação, playback e store; compilar Android/Desktop. Fazer smoke test manual dos dois backends de mídia e registrar que builds não substituem teste em aparelho.

## Verificação do plano

`.\gradlew.bat :composeApp:desktopTest --tests "app.anim.library.*" --tests "app.anim.player.*"`  
`.\gradlew.bat :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid`


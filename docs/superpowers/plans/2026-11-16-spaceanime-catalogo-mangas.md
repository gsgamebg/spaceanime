# Plano: catálogo de anime e mangá

> **Execução:** seguir as tarefas na ordem, escrevendo o teste antes da implementação. Não iniciar alterações de código até a aprovação explícita deste plano.

**Objetivo:** fazer Explorar e Mangás mostrarem conteúdo na primeira abertura e carregar páginas reais das fontes, mantendo atribuição, resultados parciais e alternativas de reprodução.

**Arquitetura:** estender os contratos existentes de fontes/repositório sem remover APIs usadas; manter anime e mangá como modelos separados; persistir mangás seguidos em armazenamento local por perfil, separado do JSON da biblioteca de anime.

**Referência:** `docs/superpowers/specs/2026-11-16-spaceanime-discovery-library-player-notifications-design.md`.

## Restrições

- Preservar os destinos de navegação existentes e as fontes/adaptadores já integrados.
- Usar páginas numeradas a partir de 1. Uma fonte sem paginação só pode informar `hasMore = false`; não simular páginas dividindo resultados locais.
- Erros de uma fonte não descartam dados das demais; expor falha parcial, estado vazio, carregamento e ação de tentar novamente.
- Deduplicar lançamentos somente pela chave estrita: título exibido normalizado por Unicode/caixa/pontuação/espaços, número do episódio interpretável e variante de áudio. Título ou número não interpretável nunca pode produzir mesclagem.
- Um lançamento agrupado mantém todos os candidatos de reprodução, inclusive fonte, URL e variante de áudio.
- MangaDex fornece a navegação inicial por um endpoint público suportado. Yomu permanece como busca até que uma operação de navegação seja comprovada por fixture e verificação limitada.
- Seguir mangá não grava progresso de leitura nem altera o formato da biblioteca de anime.

## Pontos de revisão

1. A página seguinte faz uma nova requisição à fonte e a exaustão encerra a paginação? Provar com fixtures que verificam URL/parâmetros e `hasMore`.
2. Uma falha parcial preserva os itens da fonte saudável, permite retry e não é apresentada como catálogo vazio? Cobrir ambos os resultados e erro por fonte.
3. Só títulos/episódios/áudios equivalentes são agrupados, e cada candidato continua selecionável? Cobrir Unicode, pontuação, títulos parecidos mas distintos, número ausente e dublado/legendado.
4. Explorar e Mangás exibem conteúdo sem consulta e mantêm estados vazios/erro úteis? Cobrir estado inicial, busca e paginação; conferir visualmente Android e Windows.
5. Seguir/desseguir persiste por perfil sem mudar o JSON de anime? Testar persistência, isolamento entre perfis e arquivo corrompido.

## Tarefa 1: paginação real de anime

**Arquivos principais:** `composeApp/src/commonMain/kotlin/app/anim/sources/SourceModels.kt`, `composeApp/src/jvmSharedMain/kotlin/app/anim/sources/PortugueseSources.kt`, `composeApp/src/jvmSharedMain/kotlin/app/anim/sources/CachedSources.kt`, `composeApp/src/jvmSharedMain/kotlin/app/anim/sources/UnifiedSearch.kt` e seus testes.

- [ ] Adicionar teste de contrato que prove página 1, página 2, exaustão, erro de uma fonte com sucesso da outra e erro total. Verificar que o adaptador rejeita links de continuação fora do host HTTPS esperado.
- [ ] Executar `.\gradlew.bat :composeApp:desktopTest --tests "app.anim.sources.UnifiedSearchTest" --tests "app.anim.sources.PortugueseSourcesTest"` e confirmar que os novos casos falham antes da implementação.
- [ ] Definir `SourcePage<T>(items, hasMore)` e um resultado agregado com itens, `hasMore` e falhas identificadas por fonte. Adicionar `searchPage(query, page)` e `genrePage(genre, page)` ao contrato do repositório, mantendo compatibilidade dos métodos existentes.
- [ ] Implementar paginação apenas onde o adaptador conhece e valida o formato remoto. Fontes sem suporte retornam a página inicial sem próxima página. Preservar os limites de requisição já usados pelos adaptadores.
- [ ] Atualizar a busca unificada para solicitar a página indicada a cada fonte/termo e agregar resultados sem ocultar falhas parciais. Verificar que o cache distingue consulta e página.
- [ ] Executar os testes de fontes, busca e cache; depois `.\gradlew.bat :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid`.

## Tarefa 2: Explorar, lançamentos e navegação de resultados

**Arquivos principais:** `composeApp/src/commonMain/kotlin/app/anim/sources/SourcesScreen.kt`, `composeApp/src/commonMain/kotlin/app/anim/HomeScreen.kt`, `composeApp/src/commonMain/kotlin/app/anim/discovery/Discovery.kt` e testes de fontes/discovery.

- [ ] Escrever testes para a chave de agrupamento: equivalência após normalização Unicode e de pontuação, preservação de títulos diferentes, episódio não interpretável e variantes de áudio distintas. Verificar que o resultado agrupado contém ambos os candidatos de servidor.
- [ ] Executar o teste novo e `ExploreSectionsTest`; confirmar o comportamento incorreto antes da alteração.
- [ ] Implementar um modelo puro para grupos de lançamentos, normalização determinística e extração conservadora do número. Não usar URL do servidor ou correspondência aproximada como identidade.
- [ ] Fazer Explorar renderizar seções de descoberta já disponíveis antes da busca, com título/origem e estados de carregamento, vazio, falha parcial e retry. Busca e navegação inicial devem ser estados distintos.
- [ ] Ao buscar ou escolher gênero, carregar a página 1; “Carregar mais” solicita a próxima página remota e acrescenta somente itens novos. Desabilitar/encerrar o controle quando todas as fontes reportarem exaustão; não apagar resultados já mostrados em uma falha parcial.
- [ ] Abrir um grupo de lançamento deve oferecer os candidatos disponíveis, sem fundir as variantes de áudio nem perder o contexto da fonte.
- [ ] Executar `.\gradlew.bat :composeApp:desktopTest --tests "app.anim.sources.ExploreSectionsTest" --tests "app.anim.discovery.DiscoveryTest" --tests "app.anim.sources.UnifiedSearchTest"` e compilar Android/Desktop.

## Tarefa 3: catálogo inicial e seguir mangá

**Arquivos principais:** `composeApp/src/jvmSharedMain/kotlin/app/anim/manga/MangaScreen.kt`, adaptadores MangaDex/Yomu, novo contrato comum de `MangaFollowStore`, implementações Android/Desktop e testes de manga/perfil.

- [ ] Escrever testes de fixture para o catálogo inicial MangaDex, página seguinte, exaustão, indisponibilidade independente de Yomu e busca atribuída por fonte. Acrescentar testes de seguir/desseguir, reinício, perfil separado e preservação de arquivo inválido.
- [ ] Executar `.\gradlew.bat :composeApp:desktopTest --tests "app.anim.manga.MangaCatalogTest" --tests "app.anim.manga.MangaChapterTest" --tests "app.anim.manga.YomuAdapterTest"`; confirmar os casos novos falhando.
- [ ] Adicionar um resultado paginado de mangá e uma operação explícita de catálogo MangaDex. Implementar apenas a ordenação pública confirmada pelo adaptador/fixture; manter Yomu em busca até que seu contrato de navegação seja demonstrado.
- [ ] Mostrar catálogo MangaDex sem consulta ao abrir Mangás; manter pesquisa e fonte selecionada. Paginar remotamente, indicar a fonte nos cartões e manter resultados MangaDex se Yomu falhar (e vice-versa na busca).
- [ ] Definir `MangaRef(sourceId, contentId, title, image)` e `MangaFollowStore`, com fluxo de estado e operações de seguir/desseguir. Implementar arquivo separado por perfil, escrita atômica e recuperação explícita de arquivo corrompido, seguindo os padrões de `FileLibraryStore`.
- [ ] Adicionar ação de seguir/desseguir em cartão e detalhe. Ao navegar entre perfis, carregar o armazenamento correspondente; não guardar capítulos lidos nem sincronizar dados.
- [ ] Executar testes de manga e biblioteca, `.\gradlew.bat :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid` e inspeção visual das telas nos dois destinos.

## Verificação do plano

`.\gradlew.bat :composeApp:desktopTest --tests "app.anim.sources.*" --tests "app.anim.discovery.*" --tests "app.anim.manga.*" --tests "app.anim.library.FileLibraryStoreTest"`  
`.\gradlew.bat :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid`


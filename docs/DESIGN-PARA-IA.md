# SpaceAnime — guia de design para IA e desenvolvimento

## Contexto e objetivo

O SpaceAnime é um aplicativo de anime para Android e Windows. Sua identidade une ficção espacial e anime com aparência de streaming premium: roxo, superfícies escuras, capas reais e tipografia clara. O projeto existente usa Kotlin Multiplatform e Compose Multiplatform, com Media3 no Android e VLC no Windows. Antes de alterar uma tela, leia o código atual e preserve busca, histórico, favoritos, episódios, perfis locais e o player. Não substitua a stack nem recrie o projeto por causa de uma referência visual.

## Fundamentos visuais

- Fundo #080611; sidebar #0D0919; cartões #151024.
- Roxo principal #9333EA, destaque #A855F7, claro #C084FC; rosa #E879F9 usado com parcimônia.
- Texto #F8F7FF; secundário #A7A1B8; bordas roxas translúcidas.
- Fonte Inter embarcada; hierarquia clara, espaçamento em múltiplos de 4 dp, cantos em geral 14–24 dp.
- Usar `SpaceColors`, `SpacePanel`, `AnimePosterCard`, `SpaceIcon` e outros componentes existentes antes de criar alternativas. Títulos devem manter contraste suficiente sobre imagens.
- A arte original `composeApp/src/commonMain/composeResources/drawable/space_hero_v1.png` é ambientação da marca, não representa o anime destacado. Não substituir o logotipo oficial do usuário sem solicitação.

## Layout e navegação

A home apresenta o carrossel de animes, categorias, continuar assistindo, destaques e lançamentos. A arte espacial da marca fica apenas como fundo do cabeçalho do perfil. A sidebar completa aparece a partir de 1280 dp, a compacta a partir de 1024 dp; telas menores usam navegação inferior. Categorias e tendências também são acessíveis na home em telas menores. Prever largura estreita, janela redimensionável, orientação do celular e fonte ampliada. Carrosséis usam LazyRow; cards de resultado têm proporção 2:3. Evite listas horizontais como única forma de acessar uma ação importante.

## Dados e comportamento

- Categorias consultam gêneros reais do catálogo Space 2 e são paginadas; não atribuir gêneros manualmente a obras desconhecidas.
- Em alta usa os dados de tendência da AniList e exibe somente correspondências exatas encontradas nos servidores. Não inventar ranking de usuários SpaceAnime, nota, sinopse, datas ou disponibilidade. Mostrar carregamento, erro e vazio.
- O cabeçalho do perfil usa a arte original da marca atrás do avatar, nome, biografia e estatísticas. Não repetir essa imagem nas demais telas. O carrossel usa capa e sinopse reais quando disponíveis.
- Perfis, favoritos, histórico e comentários são locais até existir integração verdadeira com um backend. Não anunciar login, assinatura ou sincronização inexistentes.
- Botões e filtros precisam funcionar; não inserir ações fictícias. Não expor nomes técnicos de provedores ao usuário: usar Servidor Space 1 e 2.

## Movimento e acessibilidade

As transições devem ser rápidas (cerca de 150–250 ms), leves e úteis. Respeite a preferência local `reduceMotion`; não inicie animações contínuas quando reduzida estiver ativa. Preserve foco visível, rótulos de acessibilidade, áreas de toque adequadas e navegação por teclado. Não dependa apenas de cor para indicar estado. Não cubra controles do player com a superfície nativa do VLC no Windows.

## Imagem original e prompt

Criada com a ferramenta imagegen integrada. Prompt: `Use case: stylized-concept. Create an original premium anime streaming application hero background, cinematic wide 16:9 landscape. A single original anime-style young adult space traveler in a tasteful astronaut jacket on the far right looking toward a luminous violet ringed planet, a deep starfield and subtle cosmic nebula, rich deep aubergine, violet and muted lavender palette, beautiful hand-painted anime art, polished but restrained. Left half mostly dark empty negative space for white interface text, no lettering, no logo, no watermark, no existing anime characters. The image should remain legible behind a dark gradient.`

Antes de adicionar imagens, use recursos originais ou fontes devidamente autorizadas; otimize tamanho e avalie recorte em múltiplas proporções. Nunca use imagens genéricas como se fossem a capa oficial de um anime.

## Verificação

Compile Android e desktop e rode testes adequados às alterações. Não declare fidelidade visual ou compatibilidade com aparelho real apenas por compilação. O usuário prefere realizar pessoalmente os testes visuais. Explique limitações e estados não conectados de maneira direta.

## Contexto técnico para a próxima IA

Antes de modificar a interface, leia `App.kt`, `design/SpaceTheme.kt`, `design/Artwork.kt`, `design/ExploreSections.kt` e as telas em `sources`, `library`, `profile` e `player`. As entidades reais são `AnimeResult`, `Episode` e `PlaybackSource`; o acesso passa por `SourceRepository` e `createSourceRepository()`. A AniList fornece nomes alternativos e tendências, não vídeos. Capas vêm dos resultados reais e passam pela política de rede de `ArtworkNetwork.kt`.

O estado da busca fica em `SourceBrowserState`; detalhes e episódios são consultados de forma assíncrona, e os lançamentos têm cache local em `FileDiscovery`. A seleção de uma opção de vídeo preserva cabeçalhos, contexto e progresso. Não troque esses dados por listas demonstrativas nem simule episódios, fontes, categorias ou estatísticas. `serverName` apresenta Space 1 e Space 2 para o usuário.

Os perfis e bibliotecas persistem localmente e não dependem de login. O canal de atualização tem URL vazia até a distribuição ser configurada; não apresente atualizações como ativas. O diretório `supabase` é uma preparação não aplicada e não representa um banco conectado.

Use os scripts do projeto para validar: `. ./scripts/env.ps1` seguido de `./gradlew.bat :composeApp:desktopTest :composeApp:assembleDebug --offline --console=plain --max-workers=2`. Preserve alterações existentes feitas por outras pessoas. Antes de declarar sucesso, relate o resultado real dos testes e não afirme que houve inspeção visual ou testes em celular se não foram realizados.

Na home atual, não há banner com a arte espacial; o carrossel usa capas e sinopses reais. Quando não houver sinopse, mostre um estado indisponível em vez de um carregamento infinito. Ao alterar a capa do carrossel, garanta altura mensurável e teste a adaptação para larguras pequenas.

## Atualização do carrossel e perfil — 0.7.2

O cabeçalho de perfil usa a arte espacial original `space_hero_v1.png` por trás das informações; não incluir a arte em faixa separada nem em outras páginas. A capa do carrossel é um pôster com proporção preservada, sem esticar para ocupar um painel largo. `SourceRepository.highResolutionCover` pode consultar a AniList; apenas uma correspondência inequívoca pelo título é aceita. `Artwork` tenta a capa do catálogo se o carregamento da imagem alternativa falhar. A política de rede de imagens aceita somente hosts HTTPS específicos, incluindo `s4.anilist.co`. Não adicionar domínios arbitrários nem usar uma capa de outra temporada para preencher espaço.

A proposta visual futura, incluindo novas ilustrações e paleta sugerida, está em `docs/PLANO-VISUAL-SPACEANIME.md`; ela não significa que as telas futuras já estejam implementadas.

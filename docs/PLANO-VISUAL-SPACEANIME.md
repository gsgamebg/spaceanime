# SpaceAnime — plano visual futuro

Este plano orienta a próxima etapa do produto e pode ser entregue a outra IA. A correção atual preserva a stack Kotlin/Compose, os dados e os recursos existentes. As propostas abaixo não devem ser tratadas como funcionalidades já implementadas.

## Direção criativa

Um observatório de histórias: espaço profundo, superfícies limpas e personagens originais de anime em momentos de exploração. A atmosfera deve ser acolhedora e cinematográfica. O brilho neon serve para guiar o olhar e indicar ações, com áreas de descanso para as capas e os textos reais. Manter a logo atual e evitar reproduzir personagens de obras existentes nas ilustrações próprias.

## Paleta proposta

| Token | Cor | Aplicação |
| --- | --- | --- |
| Fundo profundo | #080611 | Fundo geral |
| Superfície | #100B20 | Sidebar, header, painéis |
| Cartão | #19122A | Conteúdo sobre o fundo |
| Roxo principal | #9333EA | Ações principais |
| Neon violeta | #B45AFF | Foco, seleção e pequenos acentos |
| Lavanda | #D3B7FF | Links e detalhes |
| Rosa cósmico | #E879F9 | Pontos de destaque |
| Ciano estelar | #73E5F1 | Pequenos indicadores opcionais |
| Texto principal | #F8F7FF | Títulos e conteúdo |
| Texto secundário | #B8B0CA | Descrições e metadados |

Usar Inter já embarcada, contraste de texto conforme WCAG AA, alvos de toque de pelo menos 48 dp e foco visível. Validar cores efetivas sobre imagens e gradientes antes de usá-las. Neon nunca deve ser a única forma de indicar um estado.

## Telas e componentes

1. **Início:** carrossel com pôster vertical correto, sinopse real, ações claras, continuar assistindo, categorias, em alta e episódios recentes. Em telas pequenas, empilhar conteúdo e evitar rolagem horizontal da página inteira.
2. **Explorar:** busca unificada, legendado/dublado, estados de carregamento/erro/vazio, filtros reais, capas consistentes e paginação. Gêneros e tendências devem continuar ligados aos dados disponíveis.
3. **Detalhes do anime:** capa, sinopse em português quando disponível, favorito, filtros e lista de episódios, indicação de lacunas e opções de servidor. Não misturar temporadas ou inventar disponibilidade.
4. **Player:** vídeo em primeiro plano, controles acessíveis por mouse, toque e teclado, posição de reprodução e mensagens de erro claras. Respeitar os limites da superfície nativa do VLC no Windows; não prometer sobreposição enquanto ela não for implementada e validada.
5. **Perfil e biblioteca:** banner espacial da marca, avatar local, biografia, históricos e listas; perfil continua local até existir autenticação e sincronização reais. Não sugerir premium ou login inexistentes.
6. **Categorias e em alta:** capas reais, nome, idioma quando conhecido e origem da lista. Tendências AniList não são ranking próprio do SpaceAnime; manter erro e vazio honestos.

## Novas imagens propostas

- **Banner de perfil:** manter a arte cósmica original `space_hero_v1.png` atrás do avatar e dos dados do perfil; não reutilizar a imagem em outras páginas.
- **Carregamento:** uma personagem original caminhando, animação leve com estado estático quando reduzir movimento estiver ativo; reavaliar a ilustração atual em aparelhos reais.
- **Estados vazios:** pequenas ilustrações originais de uma janela espacial, livro de bordo e antena desconectada para biblioteca vazia, nenhuma busca e falha de rede. Usar só quando o estado existir; não empregar personagens protegidos.
- **Faixas de categoria:** paisagens abstratas originais coerentes com aventura, fantasia e romance, sem personagens que possam ser confundidos com capas oficiais. Gerar e integrar somente após validar custo, peso e legibilidade.
- **Lançamentos:** grafismos de órbitas e calendário em vetores Compose, preservando capas reais e datas apenas quando confirmadas.

Para cada imagem raster, manter o original no projeto, documentar o prompt/licença, otimizar o tamanho, conferir proporções para 16:9 e retrato e fornecer fundo legível. Usar degradê em código e não gravar texto na imagem. Não baixar material que não esteja autorizado para distribuição.

## Movimento e responsividade

Animações discretas de 150–250 ms para foco, hover e troca de slide; carrosséis por toque e setas no desktop. Respeitar a preferência local de reduzir movimento, sem transições contínuas obrigatórias. Planejar larguras compactas de 320–600 dp, tablet de 600–1023 dp, notebook de 1024–1279 dp e desktop a partir de 1280 dp; testar orientação e tamanho de fonte ampliado.

## Etapas propostas

1. Validar esta correção do carrossel e banner em Windows e Android.
2. Definir tokens de cores, tamanhos, foco e estados acessíveis numa biblioteca Compose compartilhada.
3. Renovar home, explorar e detalhes com componentes reutilizáveis e dados atuais.
4. Revisar perfil, biblioteca e player, preservando progresso e navegação.
5. Criar as ilustrações dos estados vazios e loading, depois medir tamanho e desempenho.
6. Revisar acessibilidade, rede lenta, navegação por teclado, modo reduzido e dispositivos reais; documentar resultados.

A IA responsável deve ler `docs/DESIGN-PARA-IA.md`, as instruções do repositório e a implementação atual antes de alterar arquivos. Não remover funcionalidades nem substituir dados reais por exemplos.

## Dependência de metadados de capas

O carrossel consulta a AniList pelo título exato e só utiliza a capa retornada quando houver uma correspondência sem ambiguidade. O carregamento de imagem aceita o domínio `s4.anilist.co` por HTTPS e mantém a capa do catálogo como alternativa. A disponibilidade e a resolução dependem do serviço externo.

A AniList estabelece condições para uso comercial e para coleta de dados; antes de monetizar ou ampliar a integração, revisar os termos atuais: https://docs.anilist.co/guide/terms-of-use.

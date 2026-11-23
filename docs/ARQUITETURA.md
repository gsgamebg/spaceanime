# Arquitetura inicial

Status: decisão inicial, sujeita à prova técnica de reprodução.
Data: 2026-10-28.

## Decisão

Construir uma base Kotlin Multiplatform com interface Compose Multiplatform para Android e Windows/JVM. Usar o Aniyomi como referência e adaptar código por módulo quando isso tiver benefício demonstrável. Não tentar compilar o aplicativo Android inteiro como desktop.

Essa escolha preserva a proximidade com Kotlin/Compose do projeto original e permite compartilhar modelos, regras, estado de tela e boa parte da interface. Dependências Android, serviços, permissões e reprodução permanecem em implementações específicas.

Compose oferece suporte estável a Android e desktop. A matriz de versões precisa ser fixada e validada na criação do projeto; não escolher independentemente as versões mais recentes de Kotlin, Compose, AGP e Gradle.

## Limites e organização proposta

| Área | Responsabilidade | Compartilhamento |
| --- | --- | --- |
| shared/domain | Anime, episódio, fonte, favoritos, progresso e casos de uso | Kotlin comum, sem tipos Android |
| shared/data | Repositórios, catálogo demonstrativo, fontes e persistência | Comum com drivers específicos |
| shared/ui | Tema, navegação, catálogo, detalhes e biblioteca | Compose comum, layouts adaptativos |
| apps/android | Activity, ciclo de vida, permissões e player | Android |
| apps/desktop | Janela, teclado, tela cheia, player e empacotamento | Windows/JVM |

Os nomes acima são a estrutura desejada, não módulos já implementados. Começar com poucos módulos Gradle e separar quando houver uma necessidade real.

## Reprodução: primeira decisão a validar

Definir um contrato PlayerController independente de Android com carregar, reproduzir, pausar, buscar posição e liberar recursos. Expor estado, duração, posição, buffering, faixas e erro. Callbacks devem identificar a sessão para impedir que eventos antigos atualizem outro episódio.

Android: candidato inicial Media3/ExoPlayer. Windows: avaliar libmpv e sua integração com a superfície de vídeo Compose. A escolha do binding desktop fica pendente de verificar manutenção, distribuição das bibliotecas nativas e funcionamento real. Media3 não garante paridade com todos os formatos atendidos pelo player do Aniyomi.

Atualização em 2026-10-29: a primeira implementação usa Media3 no Android e libVLC/vlcj no Windows. O binding Java documentado e a superfície AWT integrada ao Compose permitiram implementar a prova sem escrever um binding próprio de libmpv. libmpv continua sendo uma alternativa caso as limitações ou requisitos de distribuição justifiquem a troca. Detalhes em [PLAYER.md](PLAYER.md).

A prova técnica deve verificar MP4, HLS, legendas externas, áudio alternativo quando disponível, seek, retomada, troca de episódio e liberação de recursos. DASH, ASS e formatos adicionais entram na matriz antes de assumir compatibilidade. Falha no desktop exige revisar o adaptador antes de ampliar a interface.

## Fontes e dados

Contrato próprio AnimeSource: buscar, obter detalhes, listar episódios e resolver opções de reprodução. Cada reprodução pode incluir URL, cabeçalhos necessários, expiração e legendas. Não persistir URLs temporárias como identificador do episódio; resolver novamente ao retomar.

Começar com catálogo local demonstrativo e mídia de teste autorizada. Catálogo de metadados e provedor de vídeo são responsabilidades distintas. A fonte real será integrada após validar o fluxo completo.

Não prometer compatibilidade com extensões APK do Aniyomi no Windows: isso depende de código e runtime Android. A compatibilidade deve ser investigada separadamente; o MVP usa um adaptador próprio.

Persistência local de favoritos, episódios assistidos e progresso; SQLDelight é o candidato inicial. Chaves compostas por fonte e identificador do conteúdo. Escritas periódicas limitadas e salvamento ao pausar/sair, sem gravar a cada frame. Sem conta ou backend na primeira versão.

## Interface

Português como idioma inicial, tema escuro e navegação por Início, Buscar, Biblioteca e Configurações. Detalhes apresentam sinopse, episódios e ação de continuar assistindo.

Windows usa navegação lateral e suporte a teclado/mouse; celular usa navegação inferior e toque. Reutilizar componentes sem forçar dimensões de desktop no telefone. Prever foco visível, nomes acessíveis, fontes escaláveis e estados de carregamento, vazio e erro.

## Aproveitamento do Aniyomi

Examinar modelos, organização de fontes, biblioteca e regras de progresso. Evitar importar inicialmente leitor de mangás, torrents, rastreadores, instalador de extensões, downloads e serviços Android.

Antes de incorporar arquivos, verificar LICENSE e avisos do snapshot usado, registrar origem/commit, preservar atribuições e determinar os requisitos aplicáveis à distribuição. Nenhum código upstream foi copiado nesta etapa.

## Referências técnicas

- Aniyomi: https://github.com/aniyomiorg/aniyomi
- Kotlin Multiplatform FAQ: https://kotlinlang.org/docs/multiplatform/faq.html
- Compatibilidade Compose: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- Formatos Media3: https://developer.android.com/media/media3/exoplayer/supported-formats

## Alternativas consideradas

Manter um fork Android e desenvolver outro aplicativo Windows duplica interface e regras. Flutter atende ambas as plataformas, mas exigiria reescrever em Dart o código Kotlin que desejamos aproveitar. Kotlin/Compose é a preferência inicial pelo alinhamento com a base escolhida pelo usuário.

# SpaceAnime — interface 0.6.0

## Etapa 1 — auditoria e preservação

A stack existente é Kotlin Multiplatform/Compose, com libVLC no Windows e Media3 no Android. Foi preservada; React/Tailwind não foram introduzidos. As alterações do usuário em logo, navegação, perfis (nome de usuário e biografia), tipografia e velocidade do player foram analisadas e mantidas. Um checkpoint do código recebido está em `.tools/ui-user-checkpoint-*`.

## Etapa 2 — tema e componentes

Arquivos principais: `App.kt`, `design/SpaceTheme.kt`, `design/SpaceIcons.kt`, `design/Artwork.kt`, `design/ArtworkNetwork.kt`, `composeApp/build.gradle.kts` e recursos Compose.

Paleta: fundo #080611, sidebar #0D0919, cards #151024, roxo #9333EA/#A855F7, destaque #C084FC/#E879F9, texto #F8F7FF e secundário #A7A1B8. Fonte Inter embarcada e ícones Lucide vetoriais, com licenças no pacote. Logo fornecida aplicada na interface e ícones Windows/Android.

Sidebar de 240 dp a partir de 1280 dp; compacta de 92 dp entre 1024 e 1279 dp. Abaixo disso, navegação inferior. Grids variam de uma a cinco colunas conforme a largura disponível; filtros e ações quebram linha. Busca e botões possuem foco e descrições acessíveis. Hover de cards com deslocamento de 4 dp quando movimento está habilitado. Preferência local Reduzir movimento, ativada por padrão; não é uma integração automática com todas as configurações do sistema operacional.

## Etapa 3 — início

Arquivos: `App.kt`, módulos `discovery`, `sources/PortugueseSources.kt` e `sources/SourceModels.kt`.

Banner e carrosséis conectados a títulos reais, controles de navegação, continuar assistindo com posição persistida, destaques e episódios recentes. Sem notas, sinopses, anos ou rankings inventados. O catálogo não fornece métricas de audiência: a seção usa o termo Em destaque.

Cache persistente com atualização diária enquanto a Home estiver aberta; também consulta ao retornar à Home quando o cache estiver vencido. Botão de atualização manual. Falhas preservam os dados anteriores e aplicam intervalo de nova tentativa de 15 minutos. Não existe serviço que consulte com o aplicativo fechado. O horário exibido é o da atualização local, não a data de publicação do episódio.

Episódios recentes podem ter URL diferente da lista do anime: a seleção prioriza ID e depois número, preservando a identidade do índice e o histórico; se ainda não constarem na lista, usa o episódio recebido no feed.

## Etapa 4 — explorar

Arquivo: `sources/SourcesScreen.kt`.

Busca com debounce de 450 ms, cancelamento, timeout, skeleton, erro/repetição, vazio, grade paginada de 20 títulos e favoritos funcionais. Filtros reais: servidor, legendado/dublado, ordem alfabética e minha lista. Episódios em páginas de 30, filtro de nome/número, assistidos e ordenação. Metadados indisponíveis (gênero, ano, nota, pessoas, estúdios) não geram controles fictícios. Provedores aparecem na UI como Servidor Space 1/2; opções de vídeo mostram qualidade quando disponível.

## Etapa 5 — perfil, comentários e player

Arquivos: `profile/ProfileScreen.kt`, `profile/Profiles.kt`, `profile/FileProfiles.kt`, módulos `comments` e `player/PlayerScreen.kt`.

Perfil local com nome, usuário, bio e avatar por iniciais/cor; edição, troca de perfil, estatísticas reais, histórico, favoritos, listas existentes e configurações. Não há upload de foto, autenticação remota, planos ou coleções personalizadas. Migração de perfis anteriores preservada.

Comentários locais por perfil/anime, limite de 1000 caracteres, paginação, exclusão e spoiler oculto. Não são publicados ou sincronizados. Player com progresso, retomada, velocidade, volume, áudio/legendas e ações que se adaptam à largura. Controles fora da superfície nativa do vídeo evitam sobreposição com o Canvas do VLC no Windows.

## Segurança e desempenho

URLs de capas e consultas aceitam apenas HTTPS, sem credenciais, porta padrão e hosts conhecidos. Redirecionamentos são revalidados; downgrades para HTTP são recusados. Capas têm limite de 8 MiB inclusive em resposta sem Content-Length; consultas têm limite de 4 MiB e timeout. Erros visíveis não expõem URLs dos provedores. O player continua utilizando os hosts de mídia necessários à reprodução existente.

Imagens com cache Coil, listas paginadas e carrosséis lazy. Persistência local com gravação atômica, limites e isolamento por perfil. Esses controles não constituem autenticação, autorização administrativa, criptografia dos arquivos locais ou uma auditoria de segurança completa. Login, administração e pagamentos precisam de um backend próprio no futuro.

## Etapa 6 — validação

Compilação Windows/Android e testes automatizados de persistência, migração, busca/parser, política de URLs, atualização, comentários e cálculo das colunas. Consulta real validada nos dois servidores, incluindo vínculo do lançamento com a lista do anime. Sem automação visual, conforme orientação do usuário; aparência, fonte ampliada, toque e reprodução em aparelhos físicos continuam para validação manual.

Comandos:

```powershell
. ./scripts/env.ps1
./gradlew.bat :composeApp:desktopTest :composeApp:assembleDebug --offline --console=plain --max-workers=2
$env:ANIM_SOURCE_SMOKE='1'
./gradlew.bat :composeApp:desktopTest --tests '*DiscoveryTest*' --offline --console=plain --max-workers=2
```

Os testes que dependem de rede/player nativo são opt-in. Não confundir a verificação portátil de carregamento de Java/VLC com reprodução visual de um vídeo.

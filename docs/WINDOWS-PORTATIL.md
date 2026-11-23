# SpaceAnime para Windows x64 — pacote local de teste

Extraia o ZIP inteiro e abra `SpaceAnime/SpaceAnime.exe`. Mantenha as pastas app, runtime e vlc ao lado do executável. Não é necessário instalar Java, Gradle ou VLC separadamente. O acesso às fontes exige internet.

Favoritos e progresso continuam em `%LOCALAPPDATA%/Anim/library.json`. Para atualizar, feche o aplicativo e extraia a nova versão em outra pasta; os dados locais são reutilizados. Não abra duas versões simultaneamente.

O pacote inclui Java Temurin 17 e VLC 3.0.24. Avisos e licenças do Java ficam em runtime/legal; os do VLC permanecem na pasta vlc. O aplicativo também usa Compose, Kotlin, coroutines, OkHttp, Jsoup, kotlinx.serialization, JNA e vlcj. O projeto ainda não tem licença final nem pacote público de distribuição preparado; este artefato serve aos testes locais de desenvolvimento.

O script scripts/package-windows.ps1 gera o pacote, verifica o carregamento do Java, libVLC e plugins sem abrir janela, e grava o checksum SHA-256. Essa verificação não substitui o teste de reprodução nem a instalação em um Windows limpo. Testes visuais são realizados pelo usuário.

Empacotamento baseado na tarefa createDistributable do Compose: https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html

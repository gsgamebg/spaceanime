# SpaceAnime — Windows e Android

Aplicativo de streaming de animes em desenvolvimento. Nome: **SpaceAnime**.

Base arquitetural escolhida: Kotlin Multiplatform e Compose Multiplatform, usando o [Aniyomi](https://github.com/aniyomiorg/aniyomi) como referência funcional e fonte potencial de código adaptado.

Versão 0.5.0: interface compartilhada com Explorar, Buscar, Player, Biblioteca e Perfil. Busca em AniTube e Animes Digital, seleção de episódios e servidores, favoritos locais e retomada de reprodução. A lista de episódios permite filtrar por nome, ocultar assistidos e inverter a ordem.

- [Plano de desenvolvimento](docs/PLANO.md)
- [Decisão de arquitetura](docs/ARQUITETURA.md)
- [Player, dependências e testes](docs/PLAYER.md)

## Executar no Windows

Para gerar uma pasta portátil com executável, Java e VLC incluídos:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/package-windows.ps1
```

O pacote ZIP fica em `dist/`. Extraia tudo e abra `SpaceAnime/SpaceAnime.exe`. Favoritos e progresso permanecem no mesmo armazenamento local. Consulte [Windows portátil](docs/WINDOWS-PORTATIL.md). A geração usa o [empacotamento oficial do Compose](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html).

Com as ferramentas locais preparadas, dê dois cliques em `Iniciar-Anim.cmd` ou execute:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-desktop.ps1
```

## Gerar APK Android

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-android.ps1
```

Saída: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`. É um APK de desenvolvimento.

## Preparar outro computador Windows x64

Requer Python 3.11+ e acesso à internet:

```powershell
python scripts/bootstrap.py
powershell -ExecutionPolicy Bypass -File scripts/install-android-sdk.ps1
powershell -ExecutionPolicy Bypass -File scripts/install-player.ps1
```

O SDK solicita a aceitação de seus termos. Ferramentas e caches ficam em `.tools/`, ignorado pelo Git. Não é necessário instalar Gradle globalmente. Os scripts ajustam Java e SDK apenas no processo atual. Android Studio é opcional para editar o projeto, mas facilita o uso de emulador.

Versões iniciais: JDK 17, Gradle 8.13, Kotlin 2.2.20, Compose 1.9.1, AGP 8.9.3 e Android SDK 35; Android mínimo 26. A configuração de CI foi adicionada, mas só será executada quando o projeto estiver hospedado no GitHub.

Próximos objetivos: pacote Windows distribuível e ampliação gradual das fontes. Das 38 extensões importadas como metadados, duas possuem adaptadores nativos; as outras 36 ainda não estão disponíveis para reprodução. O app não executa o JavaScript do arquivo importado.

Favoritos e progresso ficam em `%LOCALAPPDATA%/Anim/library.json` no Windows e no armazenamento privado do app no Android. URLs temporárias de vídeo não são persistidas. Não há sincronização entre dispositivos.

Testes visuais ficam a cargo do usuário. Consulte [Validação](docs/VALIDACAO.md) para separar resultados automatizados de execução manual.

## Novidades 0.5.0

Identidade roxa e logo própria, início com dados reais da biblioteca, até seis perfis locais, cache de metadados, paginação e aviso de atualizações preparado para Render. O canal ainda está desativado até existir um endereço público. Login, administração, coleta e pagamentos não foram ativados.

- [Identidade visual](docs/SPACEANIME-DESIGN.md)
- [Atualizações e Render](docs/ATUALIZACOES.md)
- [Dados e evolução dos serviços](docs/DADOS-E-EVOLUCAO.md)

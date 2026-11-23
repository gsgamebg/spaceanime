# Plano de desenvolvimento

## SpaceAnime 0.5 — foco atual

Entregues identidade visual e ícones próprios, página inicial renovada, seis perfis locais com biblioteca independente, cache limitado de metadados e paginação. O perfil principal mantém os dados anteriores. Cliente de atualização e publicação estática no Render preparados; aguardam repositório/domínio para ativar o canal. O download é iniciado pelo usuário no navegador, sem instalação silenciosa.

Próximos passos: validação de interface pelo usuário; ativar e testar o canal público quando existir a URL; definir assinatura estável para distribuição Android/Windows. Contas, administração, coleta e pagamentos continuam posteriores, com decisões registradas em DADOS-E-EVOLUCAO.md. Não tratar perfis locais como autenticação.

## Entrega Windows portátil — 2026-10-30

Pacote local ZIP com executável, Java e VLC incluídos implementado em `scripts/package-windows.ps1`. O executável localiza o VLC junto ao runtime Java, e a verificação sem interface passou sem usar as variáveis do ambiente de desenvolvimento. Favoritos e progresso mantêm o armazenamento anterior. Faltam validação pelo usuário em execução normal, teste em Windows limpo e preparação de instalador/distribuição pública com avisos completos.

## Produto e escopo

Um aplicativo para encontrar animes, consultar episódios, assistir e continuar de onde parou no mesmo dispositivo, em Windows e Android. Nome Anim é provisório. Primeira versão sem cadastro e com dados locais.

Alvos iniciais propostos: Windows 10/11 x64 e Android 8 ou superior. Confirmar mínimos com as dependências fixadas. Sincronização entre dispositivos fica para uma fase posterior.

## Etapas e critérios de conclusão

### 0. Ambiente e base compilável

- Identificar JDK e Android SDK instalados, inclusive fora do PATH.
- Fixar uma combinação compatível de JDK, Kotlin, Compose, Gradle e AGP.
- Gerar Gradle Wrapper, estrutura compartilhada e entradas Android/desktop.
- Adicionar catálogo de versões, gitignore e instruções de execução.
- Configurar CI para testes compartilhados e build de ambas as plataformas.

Concluído quando uma janela Windows e uma Activity Android exibirem a mesma tela Compose, com comandos reproduzíveis. Registrar separadamente build e execução real em cada plataforma.

### 1. Prova de reprodução — prioridade máxima

- Criar contrato comum de player e adaptadores Android e Windows.
- Abrir mídia de teste com controles de play/pause, seek, volume e tela cheia.
- Validar HLS, MP4, legendas externas, buffering e mensagens de erro.
- Validar ciclo de vida, fechar/reabrir player e trocar vídeo sem áudio residual.
- Medir comportamento e documentar formatos e limitações por plataforma.

Concluído quando vídeo e áudio funcionarem nas duas plataformas e os recursos forem liberados ao sair. Não avançar para um catálogo completo com o player desktop ainda indefinido.

### 2. Primeiro fluxo utilizável

- Tema escuro e navegação adaptativa.
- Catálogo demonstrativo identificado como demonstração, busca e detalhes.
- Lista de episódios conectada ao player real.
- Estados de vazio, carregamento, falha e nova tentativa.

Concluído quando for possível buscar um título demonstrativo, abrir detalhes, escolher episódio, reproduzir e voltar, nas duas plataformas.

### 3. Biblioteca e progresso local

- Persistir favoritos, histórico e posição por episódio.
- Continuar assistindo e marcar/desmarcar episódio visto.
- Retomar após fechar completamente o aplicativo.
- Testar isolamento por fonte, alterações de duração e posição inválida.

Concluído quando biblioteca e progresso sobreviverem ao reinício e não se misturarem entre títulos/fontes.

### 4. Primeira fonte real

- Selecionar uma fonte e verificar seu contrato de acesso e formatos.
- Implementar busca, detalhes, episódios e resolução de streams.
- Tratar timeout, indisponibilidade e expiração de URL.
- Usar fixtures para testes do adaptador, sem depender do serviço ao testar regras locais.

Concluído quando o fluxo completo funcionar com a fonte selecionada nas duas plataformas. Instalação de extensões do Aniyomi não faz parte deste critério.

### 5. Versão de teste distribuível

- Gerar instalador Windows com dependências de reprodução e APK Android.
- Verificar instalação em ambiente limpo, atualização e retenção de dados.
- Conferir navegação por teclado, toque, redimensionamento e acessibilidade básica.
- Registrar versão, avisos de terceiros, limitações e instruções de instalação.

Concluído quando os dois pacotes puderem ser instalados e reproduzirem conteúdo de ponta a ponta. Assinatura de produção e publicação serão tratadas quando os pacotes de teste estiverem prontos.

## Fora do MVP

Downloads offline, sincronização em nuvem, contas, recomendações personalizadas, mangás, torrents, Android TV, plugins arbitrários e paridade completa com Aniyomi. Esses itens entram depois de validar reprodução, catálogo e persistência.

## Estratégia de validação

Testes unitários para regras de progresso e casos de uso; integração para persistência e contratos das fontes; execução real para reprodução e ciclo de vida. Build bem-sucedido sozinho não valida codecs, legendas ou superfície de vídeo.

## Estado inicial

- Planejamento e decisão de arquitetura registrados.
- Workspace inicialmente vazio, com apenas .git.
- Java, Gradle, Flutter e Dart não encontrados no PATH na inspeção inicial; JAVA_HOME e ANDROID_HOME também não foram encontrados. Isso não comprova ausência de instalações fora do PATH.
- Nenhum código de aplicativo implementado e nenhum build executado nesta etapa.
- Próxima tarefa: etapa 0, seguida imediatamente pela prova de reprodução.

Não fixar estimativa de entrega antes de concluir a prova do player Windows, que concentra a maior incerteza técnica.

## Atualização após preparação do ambiente

- JDK Temurin 17, Gradle 8.13, Android CLI 19, SDK 35, Build Tools 35 e platform-tools instalados localmente em `.tools/`.
- Gradle Wrapper gerado com verificação SHA-256 da distribuição.
- Módulo `composeApp` criado com código Compose comum e entradas Android e Windows/JVM.
- Tela inicial, biblioteca vazia e configurações implementadas; navegação lateral em janelas largas e inferior em telas estreitas.
- Compilação `:composeApp:desktopJar :composeApp:assembleDebug` concluída com sucesso.
- App Windows aberto e fluxo Início → Biblioteca verificado visualmente com a skill computer-use. Teste de Configurações interrompido porque a ferramenta detectou interação do usuário na janela; não foi retomado.
- APK de desenvolvimento gerado em `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.
- Nenhum dispositivo Android conectado. Execução Android e layout compacto ainda não foram validados em dispositivo/emulador.
- Workflow de CI criado, ainda não executado remotamente.
- Não há testes automatizados de comportamento nesta base de apresentação; os próximos testes devem cobrir player, progresso e fontes quando implementados.
- Próxima etapa: prova de reprodução e teste Android em dispositivo/emulador. A etapa 0 está parcialmente concluída até a execução Android.

## Player implementado — 2026-10-29

- Contrato comum de player e adaptadores Media3 (Android) e libVLC/vlcj (Windows).
- Nova entrada Assistir com demonstrações MP4/HLS e abertura de URLs HTTPS diretas.
- Controles compartilhados de pausa, retomada, avanço/retorno, posição, volume e tela cheia; erro com nova tentativa.
- Testes de regras comuns e teste nativo real MP4/HLS no Windows passaram. Inspeção visual confirmou vídeo integrado ao Compose e tela cheia.
- APK 0.2.0 compilado. Ainda sem dispositivo para validar Android.
- Etapa 1 parcialmente concluída: faltam validação Android, legendas externas, faixas e matriz adicional de formatos. Consultar [PLAYER.md](PLAYER.md) e [VALIDACAO.md](VALIDACAO.md).

## Estado atual — 2026-10-30

As seções anteriores registram a evolução histórica. Implementados: player Media3/libVLC, seleção de faixas e legendas externas, duas fontes nativas em português, favoritos e progresso persistentes, continuar assistindo e marcação de assistidos. A versão 0.4.1 acrescenta carregamento automático dos episódios de favoritos, filtros, ordenação e preservação da posição ao tentar novamente após erro.

O usuário informou sucesso nos testes e assumiu os testes visuais. A validação do agente continua por compilação e testes automatizados, sem controle da interface. Próximas entregas: pacote Windows distribuível e adaptação gradual de mais fontes. A execução Android não foi verificada independentemente pelo agente.

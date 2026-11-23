# SpaceAnime: player, atualizações obrigatórias e anúncios

## Intenção

Melhorar a experiência de reprodução no Android e Windows, garantir que usuários que já receberam uma atualização obrigatória não continuem usando uma versão antiga e permitir controlar anúncios Android sem publicar uma nova versão do app. A solução deve aproveitar o player, o manifesto do Render e o Supabase existentes; não criar um site público de downloads nem depender do MediaFire.

## Estado atual

- `PlayerScreen` já oferece pausa/retomada, busca, saltos de 10 segundos, velocidade de 0,5× a 2×, seleção de áudio/legendas, fila anterior/próximo e botão para assistir ao próximo episódio quando o atual termina.
- O botão de pular abertura/encerramento só aparece quando `PlaybackSource` contém marcações válidas. As fontes atuais normalmente não fornecem esses tempos; não se deve inventá-los.
- “Voltar” retorna à tela que abriu o player; não há uma ação explícita para ir diretamente ao Início.
- O app consulta `updates.json` ao iniciar e a cada seis horas. Uma versão superior à instalada abre a tela obrigatória. Se a consulta inicial falha, o app libera a entrada; a exigência não é persistida entre reinícios.
- O backend publica o manifesto e redireciona downloads a arquivos aprovados no GitHub Releases ou bucket público do Supabase. A configuração atual exige APK Android e ZIP Windows no mesmo lançamento. O Android abre o download no navegador e requer confirmação do usuário para instalar.
- Não há integração de anúncios. Os planos exibidos são apenas informativos e não representam assinaturas ou benefícios ativos.

## Objetivos

1. Deixar os controles existentes claros e acessíveis e completar navegação entre episódio, tela anterior e Início.
2. Oferecer avanço automático opcional para o próximo episódio, sem iniciar outro vídeo inesperadamente por padrão.
3. Exibir “Pular abertura”/“Pular encerramento” somente com intervalos confiáveis do episódio.
4. Persistir no dispositivo uma atualização obrigatória já conhecida e manter o bloqueio até a versão instalada alcançar o código exigido.
5. Se o Render estiver indisponível e o dispositivo ainda não conhecer uma atualização obrigatória, permitir o uso e tentar novamente depois.
6. Adicionar anúncios Android sob controle remoto, sem anúncios que interrompam ou cubram a reprodução.

## Fora de escopo

- Instalar APK silenciosamente ou atualizar um app antigo sem a confirmação do Android.
- Impedir que um cliente modificado contorne o bloqueio local. Isso exigiria versionamento mínimo também em cada serviço/API protegido.
- Criar painel web administrativo ou site público novo para downloads.
- Anúncios no Windows, anúncios em vídeo/intersticiais ou publicidade que imite controles do player.
- Ativar pagamentos, assinaturas ou benefícios Premium. O fluxo atual de planos ainda não concede direitos.
- Estimar tempos de abertura/encerramento sem dados confiáveis.

## Abordagens consideradas

### A — Reaproveitar Render, Supabase e os componentes existentes (escolhida)

Usar a UI e os motores Android/Windows existentes, o manifesto e redirecionamento de downloads do Render, arquivos públicos no GitHub Releases/Supabase e uma configuração remota pequena no Supabase para habilitar anúncios Android. Não há novo site para manter. A publicação continua exigindo APK e ZIP Windows conforme o contrato atual do backend.

### B — Criar um painel/site administrativo próprio

Criaria uma interface para gerenciar versões, marcações de episódios e anúncios sem entrar no painel do Supabase. Entrega mais conveniência, mas requer uma aplicação adicional, autenticação administrativa, implantação e manutenção. Não é necessário para a primeira entrega.

### C — Usar MediaFire e configurar anúncios diretamente no app

Seria uma tentativa de reduzir a configuração inicial, mas o MediaFire não passa pela lista de destinos permitidos pelo backend e pelas validações do app. Configurações embutidas também não permitem desligar anúncios remotamente. Abordagem descartada.

## Desenho escolhido

### Player (Android e Windows)

- Manter os controles comuns já existentes e ajustar o layout para que os alvos de toque Android tenham pelo menos 48 dp, espaçamento adequado e descrição acessível.
- Manter “Voltar” para retornar à tela anterior e adicionar uma ação “Início” explícita.
- Destacar anterior/próximo quando houver fila. No fim do episódio, oferecer “Assistir próximo”; avanço automático será uma preferência do usuário, inicialmente desligada.
- Continuar usando marcadores de intervalo já suportados por `EpisodeMarkers`. Para tornar o pulo utilizável, os intervalos serão mantidos como dados confiáveis por episódio no Supabase, com leitura pública limitada e escrita administrativa. Sem marcação válida, os botões não aparecem. A manutenção inicial poderá ser feita pelo painel do Supabase; não será criado um editor próprio nesta etapa.

### Atualização obrigatória

- Manter o manifesto do Render como fonte da versão e os artefatos nos destinos públicos já aprovados (GitHub Releases ou bucket público Supabase). O Render continua servindo metadados e redirecionamentos, não arquivos grandes.
- Continuar tratando cada versão superior anunciada como obrigatória, conforme o comportamento atual. A exigência recebida será persistida localmente com o código e os dados de download necessários; a tela bloqueia após reiniciar ou perder conexão até o app instalado alcançar esse código.
- Política de falha escolhida: se o app já conhece uma versão obrigatória, continua bloqueado offline; se ainda não conhece uma e não consegue contatar o Render, abre o app e tenta novamente depois.
- O lançamento continuará contendo Android e Windows no mesmo código de versão porque o backend atual exige ambos os artefatos e seus hashes.
- O botão de download abre o navegador. O usuário ainda precisa concluir o download e confirmar a instalação no Android. Versões anteriores à introdução do verificador precisam de uma instalação inicial manual para obter esse mecanismo.
- O endpoint/manifesto precisa estar realmente configurado no Render antes de anunciar uma atualização; caso contrário, ele não informa novas versões.

### Anúncios Android

- Integrar Google Mobile Ads/AdMob somente no Android.
- Criar uma configuração pública e mínima no Supabase, consultada por uma rota pública do backend. O painel do Supabase será o controle inicial; não haverá painel administrativo novo.
- A configuração terá uma chave geral e chaves por posição (por exemplo, Início e catálogo). Padrão seguro: anúncios desligados quando não houver configuração válida. O app pode usar a última configuração válida em cache; a mudança remota passa a valer na próxima sincronização.
- Exibir somente banners discretos em páginas de navegação/listagem. Nenhum anúncio durante o vídeo, sobre os controles, em tela cheia, ou intersticial entre episódios.
- Exibir apenas após a etapa de consentimento e seguir as escolhas de privacidade do usuário. Configurações e chaves secretas do Supabase nunca serão incluídas no APK; identificadores públicos do AdMob não são segredos.
- Os planos “sem anúncios” ainda não são compráveis nem verificados. A elegibilidade sem anúncios só poderá ser aplicada quando houver um entitlement confiável; não anunciar nem cobrar esse benefício neste projeto.

## Decisões já tomadas

- Escopo de anúncios: Android apenas.
- Atualizações offline: manter bloqueio se o dispositivo já conhece a versão obrigatória; permitir entrada se ainda não conseguiu descobri-la.
- Abordagem: reutilizar Render e Supabase, sem site/painel novo.
- Nenhum anúncio interrompe a reprodução.
- Avanço automático de episódios inicia desligado e pode ser ligado pelo usuário.
- Marcações de abertura/encerramento não serão adivinhadas.

## Critérios de aceitação

- Nos dois alvos, pausar, retomar, saltar 10 segundos, velocidade e navegação de episódios continuam funcionando; “Início” retorna diretamente à tela inicial e “Voltar” mantém o retorno anterior.
- A preferência de avanço automático respeita o valor desligado por padrão e nunca tenta avançar sem uma fila válida.
- Pular abertura/encerramento só fica disponível dentro de um intervalo válido, e o seek vai ao fim desse intervalo.
- Um manifesto superior à versão instalada bloqueia o app. A exigência sobrevive a reinício e indisponibilidade de rede; uma atualização já instalada libera o acesso. Falha de rede sem exigência conhecida não bloqueia a primeira entrada.
- Uma release publicada é acessível por HTTPS e contém APK/ZIP e hashes corretos; instalação real continua exigindo ação do usuário.
- O controle remoto consegue desligar todos os anúncios e cada posição; erro ou ausência de configuração não habilita anúncios. Nenhum anúncio aparece no player ou antes do consentimento.
- Nenhuma chave secreta ou credencial de administração é distribuída no app.

## Fases propostas

1. Completar e testar os controles/navegação do player, inclusive as marcações de episódio.
2. Implementar a persistência da exigência de atualização e exercitar os casos online, offline, reinício e recuperação após instalar a versão exigida.
3. Adicionar configuração remota e consentimento, integrar AdMob em ambiente de teste e validar as posições Android.
4. Preparar a publicação conjunta Android/Windows no fluxo Render + GitHub Releases/Supabase e testar em dispositivo Android e no Windows.

## Chamadas deixadas ao implementador

- Reutilizar os helpers/testes existentes e manter os motores de vídeo nativos.
- Validar os intervalos de marcação no app e no armazenamento; uma configuração incompleta não deve produzir botão de pulo.
- Limitar cache e tamanho/forma da resposta da configuração de anúncios; a rota pública não expõe configurações administrativas.
- Usar unidades de anúncio de teste até a aprovação e configuração das unidades reais.
- Testes automatizados não substituem validação manual do instalador, consentimento e reprodução em dispositivos.

## Referências do projeto

- `composeApp/src/commonMain/kotlin/app/anim/player/PlayerScreen.kt`
- `composeApp/src/commonMain/kotlin/app/anim/update/Updates.kt`
- `composeApp/src/commonMain/kotlin/app/anim/App.kt`
- `backend/update-feed.js`
- `backend/server.js`
- `docs/PLAYER-PREMIUM.md`
- `docs/ATUALIZACOES.md`

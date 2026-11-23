# Player experimental — 0.4.2

## Uso

Abra o Anim, entre em **Assistir** e escolha uma demonstração MP4/HLS ou informe uma URL HTTPS direta. URLs de páginas de streaming não são resolvidas automaticamente.

Controles: reproduzir/pausar, voltar/avançar dez segundos, linha do tempo, volume, tela cheia e voltar. Links com duração desconhecida ou sem suporte a seek desativam a linha do tempo. Uma falha oferece nova tentativa, recriando a sessão.

## Implementação

- Estado e controles em `commonMain`, através de `PlayerController`.
- Android: Media3 1.8.0 com módulos MP4, HLS e DASH. Player liberado ao sair e pausado quando o app vai para segundo plano.
- Windows: vlcj 4.8.3 e libVLC 3.0.24 x64, com superfície nativa integrada ao Compose por SwingPanel. Comandos serializados fora da thread da interface. Eventos antigos pertencem apenas à sessão que os originou; o controlador ignora comandos após liberação.
- Episódios abertos pelas fontes têm progresso persistido localmente. A Biblioteca resolve novamente o endereço do vídeo ao continuar. Vídeos por URL e demonstrações não entram no histórico. A nova tentativa após erro preserva a última posição válida da sessão.
- Menus de áudio e legendas mostram as faixas disponibilizadas pelo player. A abertura manual aceita legenda externa HTTPS em VTT ou SRT.
- Tela cheia usa a janela do desktop ou oculta as barras do sistema no Android. Não força rotação do celular.

## Ambiente

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-player.ps1
```

O script baixa o ZIP oficial do VLC, confere SHA-256 e extrai em `.tools/vlc`. `scripts/env.ps1` configura a descoberta das DLLs e plugins somente no processo atual. Ainda não existe instalador distribuível contendo o runtime.

## Testes

Testes comuns de URLs, formatação de tempo e limites de seek:

```powershell
. ./scripts/env.ps1
./gradlew.bat :composeApp:desktopTest :composeApp:assembleDebug
```

Teste nativo opcional, que abre uma janela própria e usa a internet para MP4 e HLS:

```powershell
. ./scripts/env.ps1
$env:ANIM_PLAYER_SMOKE = '1'
./gradlew.bat :composeApp:desktopTest --rerun-tasks
Remove-Item Env:ANIM_PLAYER_SMOKE
```

O teste nativo confere avanço do tempo, pausa, seek, volume, retomada e liberação. Na CI padrão, essa verificação é marcada como ignorada; os testes comuns continuam rodando.

## Pendências da prova técnica

Validar execução Android em aparelho/emulador; áudio percebido pelo usuário; formatos adicionais; DASH em execução; legendas externas e seleção de faixas; tratamento de interrupções; empacotamento Windows com dependências nativas. A versão atual não oferece DRM nem compatibilidade com extensões APK do Aniyomi.

## Referências e atribuições

- Media3: https://developer.android.com/media/media3/exoplayer/hello-world
- vlcj: https://github.com/caprica/vlcj — dependência GPL-3.0-or-later, sem código-fonte copiado.
- Runtime VLC: https://download.videolan.org/pub/videolan/vlc/3.0.24/win64/ — manter os avisos e licenças incluídos no arquivo oficial.
- Big Buck Bunny: https://peach.blender.org/ — Blender Foundation, CC BY 3.0; demonstração HLS hospedada em https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8.
- Sintel: https://durian.blender.org/ — Blender Foundation, CC BY 3.0; trailer MP4 hospedado em https://media.w3.org/2010/05/sintel/trailer.mp4.

Antes de produzir pacotes públicos, reunir licenças/avisos das dependências e os materiais correspondentes exigidos pela distribuição. A licença final do aplicativo ainda não foi definida. Nenhum código do Aniyomi foi incorporado nesta etapa.

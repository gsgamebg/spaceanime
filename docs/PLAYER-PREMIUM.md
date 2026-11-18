# Player SpaceAnime

Windows continua com VLC/vlcj em Canvas AWT; Android usa Media3/ExoPlayer e PlayerView. Os componentes nativos permanecem responsáveis pela reprodução e liberação de recursos. Os controles são áreas Compose separadas do vídeo porque o Canvas AWT do VLC fica acima das camadas Compose no Windows. Por esse motivo, o personagem, feedback e controles não são sobrepostos ao vídeo na edição desktop atual.

## Interações

- Controles aparecem com movimento do mouse ou interação; enquanto estiver reproduzindo, sem buffering, menu aberto ou arraste, desaparecem após 3 segundos. Permanecem visíveis pausado.
- No vídeo do Windows, o clique central alterna pausa/reprodução. Clique dá foco ao Canvas para atalhos. Teclas no contexto do player: Espaço/K, setas, M, F, L, C e Esc. O player não contém campos de texto e os atalhos não são registrados no aplicativo inteiro. Busca mantém Enter no campo de pesquisa.
- Android: toque revela controles ocultos; toque central com controles visíveis alterna pausa; toque fora pode ocultar enquanto reproduz; duplo toque em cada metade avança/retrocede 10 segundos quando seek estiver disponível.
- “Voltar” retorna à tela anterior e “Início” retorna diretamente à Home. Com uma fila válida, anterior/próximo ficam disponíveis. A reprodução automática do próximo episódio é opcional por perfil e desligada por padrão; quando desligada, o botão “Assistir próximo episódio” permanece no fim do vídeo.
- O mascote chibi é desenho original em Compose Canvas e anima apenas com Reduzir movimento desativado nas configurações do perfil. Durante buffering real há atraso de 450 ms para evitar flashes, com mensagem alternativa após 8 segundos. Erros permanecem visíveis e a nova tentativa preserva a posição conhecida.
- A velocidade inclui 1,75x, áudio e legendas usam faixas reais e volume/mute usa APIs dos motores.
- `EpisodeMarkers` e `Segment` aceitam intervalos explícitos em milissegundos; a abertura e encerramento aparecem somente dentro de intervalos válidos, com seek e duração disponíveis. O player consulta a RPC pública `spaceanime_public_episode_markers` por episódio e combina os intervalos recebidos com os metadados já presentes na fonte. A tabela é privada e a chave consultada é validada; sem marcações válidas, os botões não aparecem. Os tempos nunca são presumidos.

## Limites e validação

As marcações remotas dependem da migração `supabase/migrations/202611170003_playback_markers.sql`, que precisa ser aplicada e verificada em um projeto de desenvolvimento antes de habilitar o cadastro de tempos. Não há editor próprio: a manutenção inicial dos dados é feita pelo painel do Supabase. Não há ainda seletor de qualidade, miniaturas de posição ou personalização visual de legenda. O fullscreen Android oculta barras do sistema; não força orientação.

A suíte automatizada verifica regras puras de gestos, visibilidade, avanço automático e parsing/validação de marcações, além da compilação Android e dos testes desktop. Ela não substitui teste em dispositivo real de cliques, teclado, toque, fullscreen, buffering e VLC/Media3. A validação manual em Android e Windows ainda está pendente.

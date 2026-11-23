# Avaliação dos projetos e integração

Análise local dos arquivos fornecidos em 02/11/2026; os ZIPs são insumos de referência, não componentes executados ou copiados para o aplicativo.

- Yomotsu: aplicativo Android com base Mihon/Anikku, recursos de leitura, OCR, tradução e player; licença Apache 2.0. Seu runtime de extensões, OCR e dependências Android não são diretamente compatíveis com a aplicação multiplataforma. Inspiração útil: modos de leitura e tratamento de progresso. Não foram transplantados código nem modelos de tradução.
- GoAnime: aplicativo CLI escrito em Go, com busca em múltiplas fontes; licença MIT. Seus provedores e player mpv exigiriam adaptação para Kotlin/Android e análise individual das fontes. Não foi copiado código.
- ani-tupi: ferramenta CLI Python para fontes de anime; licença MPL 2.0. Dependências de terminal, navegador e mpv não são executáveis diretamente no app. Não foi copiado código.

Caso código seja incorporado futuramente, preservar avisos, licenças e obrigações aplicáveis aos arquivos utilizados.

O manifesto Stremio informado exige um token individual gerado pelo endpoint próprio de configuração. O manifesto configurado consultado expõe apenas recurso `stream` para `series`, sem catálogo. Uma consulta de exemplo retornou lista vazia. O aplicativo ainda não mapeia identificadores IMDb, temporadas e episódios para esse serviço e não possui cliente de torrent; portanto não anuncia esse servidor como funcional. Não salvar ou registrar tokens em logs.

Notícias: o feed público RSS de JBox forneceu título, descrição, imagem e link; mostrar apenas um resumo e levar ao original para matéria completa e vídeos. Não foi encontrado feed em animezone.com.br/feed; a rota testada na Crunchyroll respondeu HTML. A disponibilidade e a licença dos conteúdos devem ser verificadas antes de redistribuir artigos integrais.

Leitor: MangaDex já utiliza OkHttp e Kotlin serialization, além de Coil; Retrofit e Gson não são necessários para a funcionalidade implementada. A resposta at-home usa modo data-saver que mantém a ordem original das páginas. O leitor vertical usa LazyColumn limitada ao espaço do componente; páginas e URLs seguem as mesmas regras de segurança da rede.

## Ideias estudadas para o SpaceAnime

Os ZIPs foram consultados como material de estudo, sem copiar código ou recursos. Os caminhos abaixo descrevem ideias a projetar e validar, não recursos concluídos.

1. Leitor: estudar transição entre capítulos, barra de ferramentas, modos vertical/página e orientação; melhorar depois navegação entre capítulos, salvar posição e retorno ao ponto exato.
2. Player: projetar controles próprios sobre os motores nativos já utilizados, com legendas, áudio, velocidade e restauração de posição; não é necessário criar um decodificador multimídia do zero.
3. TV: avaliar Android TV (navegação por controle remoto e foco) e transmissão para dispositivos compatíveis como projetos diferentes. A primeira exige interface e testes de controle remoto; a segunda exige descoberta, protocolo/receptor e verificação de formatos, legendas e cabeçalhos. A presença de Chromecast em outro projeto não garante suporte neste app.
4. Fontes: analisar como o GoAnime separa provedores, capacidades e diagnóstico de indisponibilidade; criar contratos próprios, isolar falhas e testar cada fonte sem executar código externo arbitrário.
5. Prioridade sugerida: confiabilidade de busca e capítulos, progresso de leitura, acessibilidade do player, navegação de TV e só depois transmissão.

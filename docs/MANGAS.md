# Suporte a mangás

O repositório FireFlyDeveloper/Manga-API foi analisado em `.tools/Manga-API`: é um serviço Node/Express sob GPL-3.0 que consulta exclusivamente Mangakakalot. Ele não fornece indicação de idioma dos capítulos nem endereço público de serviço configurado. Por isso não foi incorporado ao aplicativo nem anunciado como fonte em português; o cliente não usa seu código.

A seção de mangás usa a API pública MangaDex. A busca usa o índice de títulos e nomes alternativos do serviço, filtrada por disponibilidade `pt-br`; a lista de capítulos também exige `pt-br`. Disponibilidade varia por obra e pode haver lacunas. As páginas são obtidas do serviço at-home, validando HTTPS e domínios da rede MangaDex. Conteúdo hospedado externamente não é aberto pelo leitor.

Animes e mangás têm navegação e busca distintas. A barra superior pesquisa mangás apenas quando a área Mangás estiver ativa. Não há sincronização de progresso de leitura nesta versão.

## Avaliação dos repositórios de extensões (novembro de 2026)

O índice bigExtensions recebido contém pacotes APK/JAR para Mihon, não APIs universais. Foram identificadas fontes marcadas como `pt-BR`, incluindo KuroMangas, Yomu Comics e MangaLivre.blog. O índice recebido é um retrato local e não comprova que as fontes estejam disponíveis atualmente. Executar APKs/JARs dessas extensões exige suporte à API do Mihon e dependências de plataforma; o SpaceAnime não possui esse mecanismo. Não instalar ou executar extensões baixadas apenas por constarem no índice.

O código do Project Nox (`https://github.com/Awerkori/manga-fonte-extensoes`) foi examinado, inclusive as implementações de MangaLivre.org, KuroMangas e Yomu Comics. KuroMangas usa sessão, credenciais e dependências Android. Yomu Comics referencia o serviço `https://yomu.tauruus.com`: em 31/10/2026 o endpoint de listagem retornou HTTP 502 nas tentativas feitas neste ambiente; o site yomu.com.br retornou HTTP 403. A API de MangaLivre.org também retornou HTTP 404 em uma consulta de teste. Uma fonte diferente, Yomu Mangás, retornou catálogo JSON, mas sua página de capítulo retornou HTTP 403; não foi possível confirmar a leitura. Isso não determina a disponibilidade em outros dispositivos ou momentos.

Próxima implementação: criar adaptadores isolados para fontes que permitam busca, capítulos e imagens de forma verificável; limitar acesso de rede a domínios necessários e validar URLs, isolar falhas por fonte e manter o catálogo de mangás separado do de animes. A busca unificada só deve incluir uma fonte depois de validar o fluxo completo e o tratamento de paginação e resultados parciais. Verificar a licença Apache-2.0, avisos e atribuições antes de reutilizar código; utilizar um adaptador próprio não instala automaticamente as atualizações dos repositórios de extensões.

## Adaptador Yomu Comics

A busca consulta MangaDex e Yomu Comics em paralelo e informa falhas parciais. O adaptador Yomu usa a estrutura pública da extensão Project Nox (Apache-2.0) para listagem, capítulos e páginas, sem executar APK/JAR nem copiar seu código. Resultados são identificados por servidor e não mesclados automaticamente por título. Capítulos são paginados localmente em grupos de 100. URLs de páginas são limitadas a domínios confiáveis; como o serviço retornou 502 no ambiente de desenvolvimento, ainda é necessário confirmar os domínios das imagens e o fluxo completo quando estiver disponível. A integração não instala atualizações de extensão automaticamente.

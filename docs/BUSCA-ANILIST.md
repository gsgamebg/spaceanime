# Busca com AniList

A busca envia o texto digitado à API pública GraphQL da AniList, sem conta ou token, para consultar títulos romanizados, ingleses e sinônimos. A consulta é somente de metadados e tem limite de tempo, tamanho de resposta, cache local em memória e tratamento para indisponibilidade. O endereço HTTPS é fixo e redirecionamentos não são seguidos.

Uma correspondência só fornece nomes adicionais quando exatamente uma obra retorna um título ou sinônimo igual à pesquisa após normalização de maiúsculas, acentos e pontuação. Resultados ambíguos não são unidos. O título original, os apelidos locais e até alguns nomes da AniList são pesquisados nos servidores do aplicativo; somente resultados realmente disponíveis nos servidores são exibidos. Se a AniList estiver indisponível, a busca local segue funcionando. A AniList recebe a consulta e os metadados normais da conexão, como endereço IP.

Referências: https://docs.anilist.co/guide/graphql/queries/media e https://docs.anilist.co/reference/object/media

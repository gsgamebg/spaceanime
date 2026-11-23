# Dados, desempenho e evolução dos serviços

## Estado implementado

Até seis perfis locais, com nome de até 24 caracteres e quatro cores. O perfil `default` lê o `library.json` existente. Novos perfis recebem um identificador aleatório e biblioteca em `profiles/<id>/library.json`. O arquivo `profiles.json` registra seleção e nomes, com escrita atômica fora da thread de interface. Dados inválidos são preservados e bloqueiam novas alterações do arquivo afetado. IDs aceitos não podem conter caminhos. Não há remoção destrutiva de perfis nesta etapa.

Perfis são organização local, não autenticação ou isolamento entre pessoas com acesso ao dispositivo. Os JSONs não são criptografados. Nenhuma senha, token de conta ou credencial de pagamento é armazenada. Android desativa tráfego HTTP em claro e backup automático da aplicação; o suporte a backup/exportação controlado pelo usuário permanece futuro.

Cache de metadados em memória: até 20 buscas e 12 listas de episódios, por 120 segundos. Busca distingue fonte, termo e idioma/modalidade. Streams não são armazenados no cache. Alterar perfil reinicia a navegação; o cache compartilhado contém apenas metadados públicos, sem biblioteca. O cache se perde ao encerrar o processo.

Biblioteca ignora gravação de progresso idêntico ao anterior; episódios usam índice de progresso em memória para evitar varrer todo o histórico para cada linha. Paginação limita composição de listas grandes. Limite anterior de 1.000 registros de progresso é mantido. Não foram medidos FPS, memória ou latência em aparelhos; os ganhos descritos são reduções de trabalho verificáveis no código e nos testes, sem percentuais prometidos.

Ainda não há sincronização ou controle de gravação entre dois processos Windows simultâneos. Não abrir duas cópias do aplicativo escrevendo na mesma pasta. Uma migração futura para banco transacional deve preservar o formato antigo e ser testada com interrupção e recuperação.

## Próximas etapas de serviços — ainda não implementadas

Contas: usar provedor OIDC e Authorization Code com PKCE, navegador do sistema, recuperação de conta e limites de tentativas. Nenhum segredo de servidor será colocado no APK/EXE. Sessões serão revogáveis; credenciais de sessão usarão o armazenamento seguro da plataforma. A adoção de conta deverá perguntar como associar o perfil local, sem enviar bibliotecas silenciosamente. Base: [OWASP OAuth2](https://cheatsheetseries.owasp.org/cheatsheets/OAuth2_Cheat_Sheet.html).

Administração: backend separado, MFA para administradores, permissões por ação/recurso verificadas no servidor e trilha de auditoria. Alterar o JSON local ou a interface nunca concede acesso administrativo. Negar acesso por padrão. Base: [OWASP Authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).

Informações e diagnósticos: definir a finalidade de cada evento, campos mínimos, retenção, acesso e exclusão antes de ativar coleta. Diagnóstico opcional deve omitir títulos assistidos, URLs assinadas, nomes e tokens. Nenhum SDK de analytics foi adicionado. Logs de infraestrutura serão tratados separadamente de métricas de produto.

Pagamentos: checkout hospedado por provedor; preço e permissão de acesso calculados no backend. Validar notificações assinadas, evitar processamento duplicado e reconciliar cancelamentos/estornos. Nunca confiar no aplicativo dizendo que o pagamento foi aprovado; não armazenar cartões no app. Base: [OWASP Payment Gateway Integration](https://cheatsheetseries.owasp.org/cheatsheets/Third_Party_Payment_Gateway_Integration_Cheat_Sheet.html).

Essas decisões são um plano de implementação, não uma certificação nem uma auditoria de segurança do produto. A ativação dos serviços exige backend real, testes de autorização e operação definida.

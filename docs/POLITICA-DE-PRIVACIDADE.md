# Política de Privacidade do SpaceAnime

Última atualização: 24 de novembro de 2026

Esta política explica quais dados o aplicativo SpaceAnime (Android e Windows) coleta, por que coleta e o que você pode fazer a respeito. Ela vale para o app e para o servidor `spaceanime.onrender.com`, que entrega as atualizações.

## O que fica só no seu dispositivo

- **Biblioteca, favoritos, histórico e progresso dos episódios.** Ficam salvos no próprio aparelho e não são enviados para os nossos servidores.
- **Preferências** (tema, reprodução, legendas, áudio). Também ficam no aparelho.
- **Sessão de login.** O token da sua sessão fica guardado de forma criptografada no aparelho, para você não precisar entrar toda vez.

Desinstalar o app ou limpar os dados dele apaga essas informações.

## O que coletamos

### Conta

Se você criar uma conta, guardamos o e-mail, o nome de exibição e a foto de perfil que você escolher. A conta é gerenciada pelo Supabase, nosso provedor de banco de dados e autenticação. A senha nunca é armazenada em texto puro.

Comentários que você publicar ficam associados à sua conta e são visíveis para outros usuários.

### Telemetria básica

Para saber quantas pessoas usam cada versão e em quais aparelhos, o app envia um registro ao abrir, no máximo uma vez a cada 6 horas. O registro contém:

- um identificador aleatório da instalação (não é ligado à sua conta);
- a plataforma (Android ou Windows), a versão do app e a versão do sistema;
- o modelo do aparelho, o idioma e o fuso horário;
- o endereço IP de onde o registro chegou.

Esses registros são apagados automaticamente depois de **90 dias** e só podem ser lidos pela equipe do projeto. Você pode desligar a telemetria a qualquer momento em **Perfil > Configurações > Privacidade**.

### Notificações (Android)

Se você ativar as notificações, o app registra um token do Firebase Cloud Messaging (Google) e os títulos que você escolheu acompanhar. Isso serve só para avisar sobre novos episódios e capítulos. Desligar as notificações no app remove essa inscrição.

### Anúncios (Android)

A versão Android pode exibir banners do **Google AdMob**. Antes de qualquer anúncio, o app mostra a mensagem de consentimento do Google (UMP), quando ela é exigida na sua região. Se você não der consentimento, nenhum anúncio é solicitado.

Quando os anúncios estão ativos, o Google pode coletar o identificador de publicidade do aparelho, o endereço IP e dados de interação com o anúncio, conforme a [política de privacidade do Google](https://policies.google.com/privacy) e a página [Como o Google usa informações de sites ou apps que usam nossos serviços](https://policies.google.com/technologies/partner-sites). Você pode rever sua escolha em **Opções de privacidade**, abaixo do banner, e redefinir o identificador de publicidade nas configurações do Android.

A versão Windows não exibe anúncios.

### Pagamentos

Se você assinar um plano VIP, o pagamento é processado pelo provedor de pagamento. O SpaceAnime não recebe nem armazena os dados do seu cartão.

## O que não fazemos

- Não vendemos seus dados.
- Não usamos sua localização precisa, contatos, câmera ou microfone.
- Não compartilhamos seus dados com terceiros além dos provedores citados aqui (Supabase, Render, Google/Firebase, Google AdMob e o provedor de pagamento), que os tratam apenas para prestar o serviço.

## Conteúdo de terceiros

O SpaceAnime mostra episódios, capítulos e notícias de sites de terceiros. Ao reproduzir um vídeo ou abrir uma página, o seu aparelho se conecta diretamente a esses sites, que podem registrar o seu endereço IP de acordo com as políticas deles.

## Crianças

O app não é direcionado a menores de 13 anos e não coleta dados deles de propósito.

## Seus direitos

De acordo com a LGPD, você pode pedir acesso, correção ou exclusão dos seus dados, e retirar o consentimento a qualquer momento. Para isso, abra uma solicitação em [github.com/gsgamebg/spaceanime/issues](https://github.com/gsgamebg/spaceanime/issues) com o título "Privacidade". **Não escreva seu e-mail nem outros dados pessoais na solicitação**, porque ela é pública. Responderemos por lá com um meio privado de confirmar que a conta é sua.

## Mudanças nesta política

Quando esta política mudar, a data no topo será atualizada e o histórico completo fica disponível neste repositório.

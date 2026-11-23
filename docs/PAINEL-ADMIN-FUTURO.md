# Base para o futuro painel administrativo

O aplicativo atual não possui contas, painel, coleta remota ou backend administrativo. Perfis são locais e não conferem privilégios.

## Arquitetura proposta

- Hospedar um painel separado do aplicativo; o aplicativo cliente nunca contém credenciais privilegiadas.
- Autenticar administradores com Supabase Auth e MFA. Autorizações e papéis devem ser verificados no servidor em cada operação, nunca pelo perfil local ou pela interface.
- Ativar RLS por padrão em todas as tabelas expostas, conceder o menor privilégio necessário e testar acessos anônimos, usuários comuns e administradores.
- Manter service role, chaves de assinatura e segredos apenas no servidor; publicar no app exclusivamente chaves públicas próprias do Supabase, com RLS adequada.
- Registrar alterações administrativas em auditoria protegida, com autor, horário e ação, sem senhas, tokens ou dados sensíveis desnecessários.
- Para notificações remotas futuras, solicitar consentimento, proteger tokens por usuário/dispositivo, permitir revogação, limitar envios e usar provedor push (FCM no Android). A notificação local atual não depende de backend.
- Para calendário editável, guardar correções com fonte, fuso e expiração, preservando distinção entre previsão e disponibilidade no catálogo.
- Antes da publicação, definir política de privacidade, retenção, backup, exclusão e plano de resposta a incidentes; pagamentos devem usar provedor especializado.

Estas são decisões de implementação futuras, não funcionalidades já ativas.

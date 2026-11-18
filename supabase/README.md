# Preparação para Supabase

A migração de exemplo cria somente perfis autenticados com RLS por `auth.uid()`. Não foi aplicada a nenhum servidor; precisa de testes SQL positivos e negativos antes de produção. Não existe autenticação, cadastro, migração de dados locais ou sincronização conectada nesta versão.

No aplicativo use somente a chave publicável do projeto, com RLS e permissões mínimas. Chaves secret e service_role só podem existir no servidor, nunca no APK ou executável. Configure confirmação de e-mail, políticas para cada tabela, limites de uso e armazenamento seguro de sessões quando a autenticação for implementada. Não inclua senhas nem tokens em logs.

Referência: https://supabase.com/docs/guides/database/secure-data

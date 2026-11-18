# Atualizações Android e Windows

A versão atual consulta `https://spaceanime.onrender.com/updates.json` ao iniciar e a cada seis horas. Qualquer manifesto válido com código superior ao instalado exige a atualização e bloqueia a navegação. A versão que inclui o armazenamento local mantém essa exigência após reiniciar e também sem rede; uma resposta nula, menor ou uma falha de rede não reduz uma exigência já salva. Se o Render falhar antes de o dispositivo conhecer uma exigência, a entrada é permitida e o app tenta novamente mais tarde. Falhas ao ler ou salvar o estado local são exibidas e não liberam a navegação.

O backend do Render serve `/updates.json` e `/downloads/<código>-<plataforma>.<extensão>`. Sem `SPACEANIME_RELEASE_JSON` válida, o manifesto responde 503 e não anuncia versão. A configuração só é válida quando contém APK e ZIP Windows, com SHA-256 correto em cada artefato. Os downloads só redirecionam a URLs HTTPS aprovadas do repositório público `gsgamebg/spaceanime-releases` ou do bucket público `spaceanime-releases` do Supabase deste projeto. O app instala pelo fluxo normal do sistema; não baixa nem instala silenciosamente.

## Preparar a primeira versão

1. Confirme que o backend existente no Render responde em `/updates.json` e que `/downloads/<código>-android.apk` e `/downloads/<código>-windows.zip` só redirecionam quando a configuração de release está completa.
2. Publique os pacotes Android e Windows no GitHub Releases público aprovado ou no bucket público Supabase. Confira que ambos podem ser baixados por HTTPS sem autenticação.
3. Configure `SPACEANIME_RELEASE_JSON` no Render com versão, código, notas, URLs e SHA-256 dos dois pacotes. Essa configuração é metadado público, não deve conter segredos.
4. Antes de anunciar uma atualização obrigatória, teste o manifesto e os dois redirecionamentos. O backend não publica o APK/ZIP e não aceita MediaFire.
5. A primeira instalação da versão com cache persistente precisa ser manual. Versões antigas sem verificador não conseguem descobrir o manifesto; versões com verificador, mas sem cache persistente, devem receber a versão de bootstrap enquanto ainda podem consultar o Render.

## Publicar uma versão

1. Confirme a versão semântica e o código; atualize `APP_VERSION` e `APP_VERSION_CODE` em `composeApp/src/commonMain/kotlin/app/anim/update/Updates.kt`. `RELEASE_VERSION` e `RELEASE_CODE` do build precisam corresponder a esses valores. Defina `MINIMUM_SUPPORTED_CODE` como o código mínimo que a nova versão substitui; `RELEASE_CODE` precisa ser maior.
2. Configure localmente, sem colocar em arquivos versionados, `SPACEANIME_KEYSTORE_FILE`, `SPACEANIME_KEYSTORE_PASSWORD`, `SPACEANIME_KEY_ALIAS` e `SPACEANIME_KEY_PASSWORD` com a mesma chave de assinatura Android já usada no canal instalado. Se algum valor faltar, o build release deve falhar; o build debug não depende deles.
3. Configure `RELEASE_VERSION` e `RELEASE_CODE` e compile o APK assinado com `.\gradlew.bat :composeApp:assembleRelease`. Para Windows, o projeto continua oferecendo MSI e passa a oferecer também o instalador EXE; gere o ZIP portátil por `.\gradlew.bat :composeApp:packageReleaseWindowsZip`. Não substitua o instalador MSI existente.
4. Verifique a assinatura do APK e confirme que o ZIP contém o executável Windows e seus arquivos de runtime. Teste o instalador EXE e faça instalação/execução manual em dispositivos de teste.
5. Calcule os SHA-256 dos dois pacotes. Configure `SPACEANIME_BASE_URL`, `MINIMUM_SUPPORTED_CODE`, `ANDROID_ARTIFACT_URL`, `WINDOWS_ARTIFACT_URL`, `ANDROID_SHA256` e `WINDOWS_SHA256`. O script de staging só aceita artefatos HTTPS nos hosts públicos aprovados do GitHub e só cria `updates.json` depois que ambos os downloads passaram pela validação de hash.
6. Rode `python -m unittest test_build` a partir de `distribution`, execute o staging e confira `distribution/public/updates.json`, os dois pacotes e seus hashes. O staging não publica por si só.
7. Publique ambos os artefatos em um dos locais públicos aprovados. Abra os URLs em uma janela anônima e valide HTTPS e os bytes/hash servidos.
8. Publique/sincronize o manifesto por último (`SPACEANIME_RELEASE_JSON` no backend existente, ou o destino de manifesto configurado para a hospedagem). Confirme `/updates.json` e teste o upgrade sem desinstalar o aplicativo.

Nunca gere uma chave substituta para contornar uma assinatura incompatível. O script de distribuição está preso ao host de manifesto aprovado `spaceanime.onrender.com`; use o ambiente de produção somente após confirmar o destino e os artefatos.

## Limitações importantes

O app abre os downloads no navegador: não instala sozinho nem verifica o SHA-256 do arquivo após o download. O hash informado identifica o artefato no staging, mas não garante a integridade no cliente; não há manifesto assinado. O bloqueio depende de rede e de uma consulta bem-sucedida; aplicações alteradas podem contorná-lo. O backend precisa impor sua própria versão mínima nos endpoints protegidos para negar clientes antigos. Preservar a mesma chave de assinatura é necessário para atualização sem desinstalar; uma versão release com chave diferente não substitui diretamente os APKs instalados.

Os dados locais permanecem no dispositivo. Não desinstale o app Android para atualizar, e feche o aplicativo Windows antes de trocar seus arquivos. Não exclua a biblioteca local. Os instaladores só estarão disponíveis após publicação e configuração reais; os testes locais do manifesto não substituem um teste em aparelhos.

# APK debug para instalação e testes

O GitHub Actions deste projeto gera um APK **debug assinado automaticamente pelo Gradle**. Esse fluxo não exige senha de keystore, alias ou secret privado. O arquivo produzido é `app-debug.apk` e é publicado como o artefato `ReplayX-Receiver-APK-debug`.

## Como baixar

Abra **Actions → Build APK - Receiver**, selecione uma execução concluída com sucesso e baixe o artefato **ReplayX-Receiver-APK-debug** na seção **Artifacts**. Extraia o ZIP baixado e instale o arquivo `app-debug.apk` no Android.

## Como instalar

No celular, habilite a instalação de aplicativos da fonte usada para abrir o APK, quando o Android solicitar, e toque no arquivo `app-debug.apk`. Esse APK é adequado para testes e instalação manual, mas não deve ser usado como versão final de distribuição.

## Limitações

O APK debug usa a chave automática de desenvolvimento do Gradle. Ele não é compatível para atualizar uma versão assinada com outro certificado e não deve ser publicado em lojas. O código permite a execução do modo debug sem exigir o certificado de release esperado; essa exceção existe somente para testes. Nos builds release, a verificação de certificado permanece ativa.

O keystore de release e seus arquivos Base64 continuam fora do repositório e não são necessários para este fluxo de instalação.

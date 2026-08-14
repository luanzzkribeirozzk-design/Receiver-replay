# APK debug do Sender

O workflow raiz do repositório gera um APK **debug assinado automaticamente pelo Gradle**, sem exigir senha de keystore, alias ou secret privado. O artefato produzido se chama `ReplayX-Sender-APK-debug` e contém o arquivo `app-debug.apk`.

## Como baixar e instalar

Abra **Actions → Build APK - Sender**, selecione uma execução concluída com sucesso e baixe o artefato `ReplayX-Sender-APK-debug` na seção **Artifacts**. Extraia o ZIP e instale `app-debug.apk` no Android. Se o sistema solicitar, permita a instalação pela fonte usada para abrir o arquivo.

## Limitações

O APK debug usa a chave automática de desenvolvimento do Gradle e serve para testes. Ele não deve ser publicado em loja nem usado para atualizar uma versão assinada com outro certificado. A verificação de certificado do Sender permanece ativa nos builds release; no modo debug ela é liberada apenas para permitir testes com a chave automática.

O keystore de release e os arquivos Base64 continuam fora do repositório e não são necessários para instalar o APK debug.

## Funcionalidades do Sender

O projeto inclui suporte a root com fallback para Shizuku, pareamento por código de seis caracteres de uso único com expiração em dez minutos e transferência do replay via Firestore em partes de texto.

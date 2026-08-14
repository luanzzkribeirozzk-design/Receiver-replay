# Configuração da assinatura de release

O projeto gera um APK `release` assinado pelo GitHub Actions. O arquivo `.jks` não deve ser commitado, publicado nem incluído no ZIP do projeto. O workflow restaura o keystore temporariamente no runner a partir de um secret protegido e o utiliza somente durante o build.

## Secrets obrigatórios

Cadastre os quatro valores em **Settings → Secrets and variables → Actions → New repository secret**:

| Nome | Valor |
|---|---|
| `RXR_KEYSTORE_B64` | Conteúdo integral de `replayx-receiver-release.jks.b64`, sem alterar o texto. |
| `RXR_KEYSTORE_PASSWORD` | Senha do keystore. |
| `RXR_KEY_ALIAS` | Alias real da chave dentro do keystore; confirme antes do primeiro build. |
| `RXR_KEY_PASSWORD` | Senha da chave associada ao alias. Pode ser igual à senha do keystore, mas não deve ser presumido sem confirmação. |

> Nunca coloque senhas, tokens, o arquivo `.jks` ou o arquivo `.jks.b64` em arquivos versionados, issues, logs ou mensagens públicas.

## Como validar o alias localmente

Com o arquivo `.jks` em uma máquina segura, execute `keytool -list -v -keystore replayx-receiver-release.jks` e informe a senha quando solicitado. Use o valor exibido em `Alias name` no secret `RXR_KEY_ALIAS`.

## Fluxo do GitHub Actions

O workflow verifica se os quatro secrets estão presentes, restaura o keystore em `app/replayx-receiver-release.jks`, executa `assembleRelease` com a configuração de assinatura e publica o APK como artefato por 30 dias. Se qualquer secret estiver ausente, o job falha imediatamente em vez de produzir um APK não assinado.

A cópia temporária é ignorada pelo `.gitignore`, que também bloqueia arquivos `*.jks.b64`. Ainda assim, a proteção principal é não fazer upload desses arquivos para o repositório.

## Segurança do aplicativo

O projeto já configura `debuggable=false` no build de release, aplica `FLAG_SECURE` e executa uma verificação de assinatura em runtime. A verificação só funcionará se o APK for assinado pelo certificado esperado pelo código do aplicativo.

# Configurar assinatura de release do Receptor

## Secrets (Settings → Secrets and variables → Actions)

| Nome                     | Valor                                     |
|---------------------------|---------------------------------------------|
| `RXR_KEYSTORE_B64`        | conteúdo de `replayx-receiver-release.jks.b64` |
| `RXR_KEYSTORE_PASSWORD`   | (te mandei junto)                           |
| `RXR_KEY_ALIAS`           | `replayxreceiver`                           |
| `RXR_KEY_PASSWORD`        | igual a `RXR_KEYSTORE_PASSWORD`             |

Guarda o `.jks` em local seguro, nunca sobe ele no repositório (já protegido
no `.gitignore`). Depois de cadastrar os 4 secrets, dá push e o Actions
builda `assembleRelease` automaticamente, assinado.

## O que já vem pronto nesse projeto
- Confere sozinho, ao abrir o app, se tem replay pendente pra esse aparelho
  (não precisa notificação, não precisa estar esperando na tela).
- Pergunta "Copiar replay?" → "FF MAX ou FF Normal?" → copia via
  Shizuku/root, igual o Combo Replay.
- Build release assinado, `debuggable=false`, verificação de assinatura
  em runtime, `FLAG_SECURE`.

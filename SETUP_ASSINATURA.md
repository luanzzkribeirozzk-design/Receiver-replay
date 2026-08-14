# Build release sem assinatura

O GitHub Actions deste projeto gera um APK `release` **sem assinatura**. O workflow não exige, lê ou restaura nenhum secret de keystore e publica o arquivo `app-release-unsigned.apk` como artefato.

## Fluxo do GitHub Actions

A cada push em `main` ou `master`, ou por execução manual, o workflow configura o JDK 17, instala o Gradle 8.4, executa `assembleRelease`, verifica a existência do arquivo sem assinatura e publica o APK por 30 dias.

O artefato pode ser baixado em **Actions → Build APK - Receiver → execução concluída → Artifacts → ReplayX-Receiver-APK-unsigned**.

## Observações importantes

Um APK sem assinatura não é adequado para distribuição final e pode não ser instalável em todos os dispositivos. Para distribuição, atualização ou publicação, o Android exige uma assinatura válida; nesse caso, será necessário restaurar uma configuração de assinatura protegida por secrets.

O projeto também contém uma verificação de assinatura em runtime. Como o APK sem assinatura não possui o certificado de release esperado, o aplicativo pode fechar ao iniciar ou bloquear essa verificação. Isso é esperado para este modo de build e não indica falha do workflow.

O arquivo `.jks` e sua versão `.b64` continuam bloqueados pelo `.gitignore` e não devem ser enviados ao repositório. Os secrets antigos de assinatura não são mais usados pelo workflow; eles podem ser removidos manualmente em **Settings → Secrets and variables → Actions** se não forem necessários para outro fluxo.

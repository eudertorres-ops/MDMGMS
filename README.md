# Controller — Agente Android (Device Owner)

App-agente que gerencia o aparelho e reporta ao Controller (Base44), **sem depender do Google / Android Management API**.

## O que ele faz

- Registra o aparelho no Controller usando um **código** gerado no painel.
- Reporta **localização, bateria, rede e versão do SO** no intervalo definido pela política.
- Executa **comandos remotos**: bloquear tela, reiniciar, localizar, liberar senha, aplicar política, apagar (wipe) e exibir mensagem.
- Como **Device Owner**: aplica **kiosk** (trava num app), **bloqueia apps**, concede a si mesmo as permissões de localização e **impede a própria desinstalação**.

## Endpoint

O app fala com: `https://controller-9f8beb13.base44.app/api/apps/692311b370fc40d19f8beb13/functions/mdmAgent`
(definido em `app/src/main/java/br/com/controller/agent/Constants.kt` — altere ali se a URL mudar).

## Como gerar o APK (Android Studio)

1. Instale o **Android Studio** (qualquer versão recente).
2. **File → Open** e selecione esta pasta (`controller-agent`). Aguarde o Gradle sincronizar (ele baixa o necessário sozinho).
3. **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
4. O APK sai em `app/build/outputs/apk/debug/app-debug.apk` (ou `release/` se buildar release).

> Para produção, gere um APK **release assinado** (Build → Generate Signed Bundle/APK). O debug serve para testar.

## Como transformar em Device Owner (provisionamento via ADB)

O Device Owner só pode ser definido num aparelho **sem contas** (recém-resetado de fábrica) e **antes** de adicionar qualquer conta Google.

1. Resete o aparelho de fábrica. Na configuração inicial, **pule** a adição de conta Google e **ative as Opções do desenvolvedor** (toque 7x em "Número da versão") e ligue a **Depuração USB**.
2. Conecte o aparelho ao computador por USB.
3. Instale o APK:
   ```
   adb install app-debug.apk
   ```
4. Defina como Device Owner:
   ```
   adb shell dpm set-device-owner br.com.controller.agent/.AgentDeviceAdminReceiver
   ```
   Deve responder `Success: Device owner set...`. Se der erro de "already has an account" ou "not allowed", o aparelho ainda tem conta/usuário — refaça o reset sem adicionar conta.
5. Abra o app **Controller** no aparelho, digite o **código de registro** (gerado no painel, em Detalhe do aparelho → Gerar código do agente) e toque em **Registrar**.
6. Confira no painel do Controller: o aparelho deve aparecer reportando em alguns minutos.

## Observações

- Requer Android 7+ (minSdk 24). Testado em estrutura; valide em cada modelo (Motorola/Samsung) antes de implantar na frota.
- Localização em segundo plano: como Device Owner, o app concede a si mesmo a permissão; não precisa aceitar prompt.
- Este é um MVP funcional. Alguns comandos (instalar/desinstalar app remoto) ficam como evolução.

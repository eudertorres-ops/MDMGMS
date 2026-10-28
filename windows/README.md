# Controller — Agente do Notebook (Windows)

Agente de desktop para notebooks Windows, no mesmo padrão do agente de celular:

- **Inicia com o Windows** (no login do usuário; registra-se na chave `Run` do HKCU, sem precisar de administrador).
- **Reporta a localização sempre** (serviço de localização do Windows via Wi-Fi; se indisponível, cai para localização aproximada por IP), além de nível de bateria e tipo de rede.
- **Termo de responsabilidade** no próprio notebook: identificação por CPF → busca nome/setor e a assinatura já cadastrada (ADL) → a pessoa confirma. Sem cadastro/assinatura, assina desenhando. A tela reabre sozinha até assinar e **não bloqueia** a máquina.
- **Comandos remotos** aplicáveis: bloquear a estação, exibir mensagem, reiniciar, localizar agora.

Tudo conversa com a mesma função `mdmAgent` do Controller; o notebook se autentica por um segredo próprio (sem login de usuário).

## Como usar (por notebook)

1. No Controller, abra o notebook em **Dispositivos → (o notebook) → Código do agente** e gere o código.
2. No notebook, baixe e execute `ControllerAgenteNotebook.exe` (link na tela do código).
3. Digite o código e clique **Registrar**. Pronto: o agente passa a rodar na bandeja e inicia com o Windows.
4. Na primeira sincronização, abre a tela do termo para o colaborador assinar (CPF).

## Build

O `.exe` é compilado na nuvem pelo GitHub Actions (`.github/workflows/build-windows.yml`, runner Windows) e publicado como Release `win-latest`. É um executável único, self-contained (não precisa instalar .NET no notebook).

Localmente (opcional):

```
dotnet publish windows/ControllerAgente.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o publish
```

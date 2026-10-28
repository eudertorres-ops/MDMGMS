using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text.Json;
using System.Windows.Forms;

namespace ControllerAgente;

/// <summary>
/// Execucao dos comandos remotos aplicaveis a um notebook. Comandos nao
/// aplicaveis (wipe, limpar_senha, etc.) sao reconhecidos e marcados como falhou,
/// com motivo, para aparecer no painel.
/// </summary>
public static class Commands
{
    [DllImport("user32.dll")]
    private static extern bool LockWorkStation();

    /// <summary>Retorna (status, resultado). status: "executado" ou "falhou".</summary>
    public static (string status, string resultado) Executar(string tipo, JsonElement parametros)
    {
        try
        {
            switch (tipo)
            {
                case "bloquear":
                    LockWorkStation();
                    return ("executado", "Estacao bloqueada.");

                case "localizar":
                    // A localizacao ja segue no reporte atual deste ciclo.
                    return ("executado", "Localizacao enviada no reporte.");

                case "mensagem":
                    MostrarMensagem(TextoParam(parametros));
                    return ("executado", "Mensagem exibida ao usuario.");

                case "reiniciar":
                    Process.Start(new ProcessStartInfo("shutdown", "/r /t 15 /c \"Reinicio solicitado pela empresa (Controller).\"")
                    { CreateNoWindow = true, UseShellExecute = false });
                    return ("executado", "Reinicio agendado em 15s.");

                default:
                    return ("falhou", $"Comando '{tipo}' nao se aplica a notebook.");
            }
        }
        catch (Exception ex)
        {
            return ("falhou", ex.Message);
        }
    }

    private static string TextoParam(JsonElement p)
    {
        if (p.ValueKind == JsonValueKind.Object)
        {
            if (p.TryGetProperty("mensagem", out var m) && m.ValueKind == JsonValueKind.String) return m.GetString();
            if (p.TryGetProperty("texto", out var t) && t.ValueKind == JsonValueKind.String) return t.GetString();
        }
        return "Mensagem da empresa.";
    }

    private static void MostrarMensagem(string msg)
    {
        var th = new Thread(() =>
            MessageBox.Show(msg, "Controller", MessageBoxButtons.OK, MessageBoxIcon.Information,
                MessageBoxDefaultButton.Button1, MessageBoxOptions.DefaultDesktopOnly));
        th.SetApartmentState(ApartmentState.STA);
        th.IsBackground = true;
        th.Start();
    }
}

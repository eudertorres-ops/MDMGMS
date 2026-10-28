using System.Text.Json;
using System.Text.Json.Serialization;

namespace ControllerAgente;

/// <summary>
/// Configuracao persistente do agente, guardada em
/// %APPDATA%\ControllerAgente\config.json (por usuario, sem necessidade de admin).
/// </summary>
public class Config
{
    public string EnrollmentId { get; set; } = "";
    public string DeviceSecret { get; set; } = "";
    public string DispositivoId { get; set; } = "";
    public int IntervaloMin { get; set; } = 15;
    public bool Rastreio { get; set; } = true;

    // Termo pendente de assinatura (quando houver).
    public string TermoId { get; set; } = "";
    public string TermoTexto { get; set; } = "";
    public string TermoModelo { get; set; } = "";

    [JsonIgnore]
    public bool Registrado =>
        !string.IsNullOrEmpty(EnrollmentId) && !string.IsNullOrEmpty(DeviceSecret);

    private static string Pasta =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ControllerAgente");

    private static string Arquivo => Path.Combine(Pasta, "config.json");

    public static Config Load()
    {
        try
        {
            if (File.Exists(Arquivo))
                return JsonSerializer.Deserialize<Config>(File.ReadAllText(Arquivo)) ?? new Config();
        }
        catch { /* ignora config corrompida */ }
        return new Config();
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Pasta);
            File.WriteAllText(Arquivo, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch { /* best effort */ }
    }

    public void LimparTermo()
    {
        TermoId = "";
        TermoTexto = "";
        TermoModelo = "";
        Save();
    }
}

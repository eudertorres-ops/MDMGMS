using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace ControllerAgente;

/// <summary>Helpers para ler campos de um JsonElement com seguranca.</summary>
public static class JsonExt
{
    public static string Str(this JsonElement e, string name) =>
        e.ValueKind == JsonValueKind.Object && e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String
            ? v.GetString() ?? "" : "";

    public static bool Bool(this JsonElement e, string name) =>
        e.ValueKind == JsonValueKind.Object && e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.True;
}

/// <summary>
/// Cliente HTTP para a funcao mdmAgent do Controller. O notebook se autentica
/// pelo device_secret (sem login de usuario), igual ao agente do celular.
/// </summary>
public static class ApiClient
{
    public const string AppId = "692311b370fc40d19f8beb13";
    public const string Endpoint =
        "https://controller-9f8beb13.base44.app/api/apps/692311b370fc40d19f8beb13/functions/mdmAgent";

    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(30) };

    private static async Task<JsonElement> PostAsync(IDictionary<string, object> payload)
    {
        var json = JsonSerializer.Serialize(payload);
        using var req = new HttpRequestMessage(HttpMethod.Post, Endpoint);
        req.Headers.Add("X-App-Id", AppId);
        req.Content = new StringContent(json, Encoding.UTF8, "application/json");
        using var resp = await Http.SendAsync(req);
        var body = await resp.Content.ReadAsStringAsync();
        if (string.IsNullOrWhiteSpace(body)) body = "{\"success\":false}";
        using var doc = JsonDocument.Parse(body);
        return doc.RootElement.Clone();
    }

    public static Task<JsonElement> EnrollAsync(string code, string modelo, string fabricante, string serie, string so) =>
        PostAsync(new Dictionary<string, object>
        {
            ["action"] = "enroll",
            ["code"] = code,
            ["tipo"] = "notebook",
            ["plataforma"] = "windows",
            ["modelo"] = modelo,
            ["fabricante"] = fabricante,
            ["numero_serie"] = serie,
            ["so_versao"] = so,
        });

    public static Task<JsonElement> ReportAsync(string eid, string secret, double? lat, double? lon,
        double? precisao, int? bateria, string rede, bool? carregando, string so)
    {
        var p = new Dictionary<string, object>
        {
            ["action"] = "report",
            ["enrollment_id"] = eid,
            ["device_secret"] = secret,
            ["so_versao"] = so,
        };
        if (lat.HasValue) p["latitude"] = lat.Value;
        if (lon.HasValue) p["longitude"] = lon.Value;
        if (precisao.HasValue) p["precisao_metros"] = precisao.Value;
        if (bateria.HasValue) p["nivel_bateria"] = bateria.Value;
        if (!string.IsNullOrEmpty(rede)) p["rede_tipo"] = rede;
        if (carregando.HasValue) p["status_carregamento"] = carregando.Value;
        return PostAsync(p);
    }

    public static Task<JsonElement> AckCommandAsync(string eid, string secret, string comandoId, string status, string resultado) =>
        PostAsync(new Dictionary<string, object>
        {
            ["action"] = "ackCommand",
            ["enrollment_id"] = eid,
            ["device_secret"] = secret,
            ["comando_id"] = comandoId,
            ["status"] = status,
            ["resultado"] = resultado,
        });

    public static Task<JsonElement> BuscarColaboradorAsync(string eid, string secret, string cpf) =>
        PostAsync(new Dictionary<string, object>
        {
            ["action"] = "buscarColaborador",
            ["enrollment_id"] = eid,
            ["device_secret"] = secret,
            ["cpf"] = cpf,
        });

    public static Task<JsonElement> AssinarTermoAsync(string eid, string secret, string termoId,
        string nome, string cpf, string assinatura, string texto, bool reaproveitada) =>
        PostAsync(new Dictionary<string, object>
        {
            ["action"] = "assinarTermo",
            ["enrollment_id"] = eid,
            ["device_secret"] = secret,
            ["termo_id"] = termoId,
            ["nome"] = nome,
            ["cpf"] = cpf,
            ["assinatura"] = assinatura,
            ["texto"] = texto,
            ["reaproveitada"] = reaproveitada,
        });

    public static async Task<byte[]> DownloadImageAsync(string url)
    {
        try { return await Http.GetByteArrayAsync(url); }
        catch { return null; }
    }
}

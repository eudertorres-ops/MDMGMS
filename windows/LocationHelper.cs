using System.Net.Http;
using System.Net.NetworkInformation;
using System.Text.Json;
using System.Windows.Forms;
using Windows.Devices.Geolocation;

namespace ControllerAgente;

/// <summary>
/// Localizacao (Wi-Fi via servico do Windows, caindo para IP), nivel de bateria
/// e tipo de rede. Tudo com fallback seguro para nao travar o reporte.
/// </summary>
public static class LocationHelper
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(15) };

    /// <summary>Retorna (latitude, longitude, precisao_m). Wi-Fi primeiro; depois IP.</summary>
    public static async Task<(double? lat, double? lon, double? acc)> ObterLocalizacaoAsync()
    {
        // 1) Servico de localizacao do Windows (Wi-Fi / mais preciso).
        try
        {
            var acesso = await Geolocator.RequestAccessAsync();
            if (acesso == GeolocationAccessStatus.Allowed)
            {
                var geo = new Geolocator { DesiredAccuracyInMeters = 100 };
                var pos = await geo.GetGeopositionAsync(TimeSpan.FromMinutes(5), TimeSpan.FromSeconds(15));
                var p = pos.Coordinate.Point.Position;
                return (p.Latitude, p.Longitude, pos.Coordinate.Accuracy);
            }
        }
        catch { /* localizacao desligada / sem permissao -> cai para IP */ }

        // 2) Fallback por IP (nivel cidade).
        return await PorIpAsync();
    }

    private static async Task<(double?, double?, double?)> PorIpAsync()
    {
        foreach (var url in new[] { "http://ip-api.com/json/?fields=status,lat,lon", "https://ipwho.is/" })
        {
            try
            {
                var s = await Http.GetStringAsync(url);
                using var doc = JsonDocument.Parse(s);
                var r = doc.RootElement;
                if (r.TryGetProperty("lat", out var la) && r.TryGetProperty("lon", out var lo)
                    && la.ValueKind == JsonValueKind.Number && lo.ValueKind == JsonValueKind.Number)
                    return (la.GetDouble(), lo.GetDouble(), 5000.0);
                if (r.TryGetProperty("latitude", out var la2) && r.TryGetProperty("longitude", out var lo2)
                    && la2.ValueKind == JsonValueKind.Number && lo2.ValueKind == JsonValueKind.Number)
                    return (la2.GetDouble(), lo2.GetDouble(), 5000.0);
            }
            catch { /* tenta o proximo provedor */ }
        }
        return (null, null, null);
    }

    /// <summary>Nivel da bateria (0-100) e se esta na tomada.</summary>
    public static (int? pct, bool? carregando) Bateria()
    {
        try
        {
            var ps = SystemInformation.PowerStatus;
            int? pct = null;
            if (!ps.BatteryChargeStatus.HasFlag(BatteryChargeStatus.NoSystemBattery)
                && ps.BatteryLifePercent >= 0 && ps.BatteryLifePercent <= 1)
                pct = (int)Math.Round(ps.BatteryLifePercent * 100);
            bool? carregando = ps.PowerLineStatus == PowerLineStatus.Online;
            return (pct, carregando);
        }
        catch { return (null, null); }
    }

    /// <summary>Tipo de conexao: "cabo" (ethernet), "wifi" ou "offline".</summary>
    public static string Rede()
    {
        try
        {
            bool wifi = false, cabo = false;
            foreach (var n in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (n.OperationalStatus != OperationalStatus.Up) continue;
                if (n.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel) continue;

                var desc = (n.Description + " " + n.Name).ToLowerInvariant();
                if (desc.Contains("virtual") || desc.Contains("vmware") || desc.Contains("hyper-v")
                    || desc.Contains("loopback") || desc.Contains("vethernet")) continue;

                if (n.NetworkInterfaceType == NetworkInterfaceType.Wireless80211) wifi = true;
                else if (n.NetworkInterfaceType == NetworkInterfaceType.Ethernet
                         || n.NetworkInterfaceType == NetworkInterfaceType.GigabitEthernet) cabo = true;
            }
            if (cabo) return "cabo";
            if (wifi) return "wifi";
            return "offline";
        }
        catch { return "offline"; }
    }
}

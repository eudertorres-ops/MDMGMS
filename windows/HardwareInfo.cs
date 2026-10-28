using System.Management;
using Microsoft.Win32;

namespace ControllerAgente;

/// <summary>Fabricante, modelo e numero de serie do notebook (via WMI, com fallback no registro).</summary>
public static class HardwareInfo
{
    public static (string fabricante, string modelo, string serie) Obter()
    {
        string fab = "", mod = "", ser = "";

        try
        {
            using var cs = new ManagementObjectSearcher("SELECT Manufacturer, Model FROM Win32_ComputerSystem");
            foreach (ManagementObject o in cs.Get())
            {
                fab = (o["Manufacturer"]?.ToString() ?? "").Trim();
                mod = (o["Model"]?.ToString() ?? "").Trim();
            }
        }
        catch { }

        try
        {
            using var bs = new ManagementObjectSearcher("SELECT SerialNumber FROM Win32_BIOS");
            foreach (ManagementObject o in bs.Get())
                ser = (o["SerialNumber"]?.ToString() ?? "").Trim();
        }
        catch { }

        if (string.IsNullOrEmpty(fab)) fab = RegBios("SystemManufacturer");
        if (string.IsNullOrEmpty(mod)) mod = RegBios("SystemProductName");
        if (string.IsNullOrEmpty(mod)) mod = Environment.MachineName;
        if (string.IsNullOrEmpty(fab)) fab = "Nao informado";
        if (string.IsNullOrEmpty(ser) || ser.Equals("To be filled by O.E.M.", StringComparison.OrdinalIgnoreCase))
            ser = Environment.MachineName;

        return (fab, mod, ser);
    }

    private static string RegBios(string name)
    {
        try
        {
            using var k = Registry.LocalMachine.OpenSubKey(@"HARDWARE\DESCRIPTION\System\BIOS");
            return k?.GetValue(name)?.ToString()?.Trim() ?? "";
        }
        catch { return ""; }
    }
}

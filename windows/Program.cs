using System.Windows.Forms;

namespace ControllerAgente;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        // Instancia unica: evita duas copias (autostart + abertura manual).
        using var mutex = new Mutex(true, "ControllerAgenteNotebook_SingleInstance", out bool novo);
        if (!novo) return;

        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.SetHighDpiMode(HighDpiMode.PerMonitorV2);

        Application.Run(new AgentContext());
    }
}

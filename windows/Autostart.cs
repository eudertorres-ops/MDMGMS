using Microsoft.Win32;
using System.Windows.Forms;

namespace ControllerAgente;

/// <summary>
/// Faz o agente iniciar junto com o Windows, no login do usuario, via a chave
/// Run do HKCU (nao exige privilegios de administrador).
/// </summary>
public static class Autostart
{
    private const string Chave = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string Nome = "ControllerAgenteNotebook";

    public static void Registrar()
    {
        try
        {
            var exe = Environment.ProcessPath ?? Application.ExecutablePath;
            using var k = Registry.CurrentUser.OpenSubKey(Chave, true)
                          ?? Registry.CurrentUser.CreateSubKey(Chave);
            k?.SetValue(Nome, $"\"{exe}\"");
        }
        catch { /* best effort */ }
    }

    public static void Remover()
    {
        try
        {
            using var k = Registry.CurrentUser.OpenSubKey(Chave, true);
            k?.DeleteValue(Nome, false);
        }
        catch { }
    }
}

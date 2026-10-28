using System.Drawing;
using System.Runtime.InteropServices;
using System.Text.Json;
using System.Windows.Forms;

namespace ControllerAgente;

/// <summary>
/// Nucleo do agente: roda em bandeja (sem janela principal), reporta telemetria
/// periodicamente, executa comandos remotos e abre a tela do termo enquanto
/// houver assinatura pendente — sem bloquear a maquina.
/// </summary>
public class AgentContext : ApplicationContext
{
    private readonly Config _cfg;
    private readonly Form _hidden;              // alvo para marshaling de UI
    private NotifyIcon _tray;
    private System.Windows.Forms.Timer _timerReport;
    private System.Windows.Forms.Timer _timerTermo;
    private ToolStripMenuItem _miTermo;
    private TermoForm _termoForm;
    private bool _ciclando;

    public AgentContext()
    {
        _cfg = Config.Load();

        _hidden = new Form
        {
            ShowInTaskbar = false,
            WindowState = FormWindowState.Minimized,
            FormBorderStyle = FormBorderStyle.None,
            Opacity = 0,
            Size = new Size(1, 1),
            StartPosition = FormStartPosition.Manual,
            Location = new Point(-2000, -2000),
        };
        _ = _hidden.Handle; // forca criacao do handle para BeginInvoke

        // Primeiro uso: registrar o notebook.
        if (!_cfg.Registrado)
        {
            using var ef = new EnrollForm(_cfg);
            if (ef.ShowDialog() != DialogResult.OK || !_cfg.Registrado)
            {
                ExitThread();
                return;
            }
        }

        ConfigurarBandeja();

        _timerReport = new System.Windows.Forms.Timer { Interval = IntervaloMs() };
        _timerReport.Tick += (s, e) => _ = CicloAsync();
        _timerReport.Start();

        // Reabre o termo periodicamente enquanto estiver pendente (sem travar nada).
        _timerTermo = new System.Windows.Forms.Timer { Interval = 60 * 1000 };
        _timerTermo.Tick += (s, e) => { if (!string.IsNullOrEmpty(_cfg.TermoId)) MostrarTermo(); };
        _timerTermo.Start();

        _ = CicloAsync(); // ciclo imediato ao iniciar
    }

    private int IntervaloMs() => Math.Max(1, _cfg.IntervaloMin) * 60 * 1000;

    private void ConfigurarBandeja()
    {
        var menu = new ContextMenuStrip();
        menu.Items.Add("Reportar agora", null, (s, e) => _ = CicloAsync());
        _miTermo = new ToolStripMenuItem("Assinar termo", null, (s, e) => MostrarTermo())
        {
            Enabled = !string.IsNullOrEmpty(_cfg.TermoId),
        };
        menu.Items.Add(_miTermo);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Sair", null, (s, e) => Sair());

        _tray = new NotifyIcon
        {
            Icon = SystemIcons.Application,
            Visible = true,
            Text = "Controller — Agente do notebook",
            ContextMenuStrip = menu,
        };
    }

    private async Task CicloAsync()
    {
        if (_ciclando) return;
        _ciclando = true;
        try
        {
            if (!_cfg.Registrado) return;

            var (pct, carregando) = LocationHelper.Bateria();
            var rede = LocationHelper.Rede();
            (double? lat, double? lon, double? acc) loc = (null, null, null);
            if (_cfg.Rastreio)
                loc = await LocationHelper.ObterLocalizacaoAsync();

            var so = RuntimeInformation.OSDescription;
            var resp = await ApiClient.ReportAsync(
                _cfg.EnrollmentId, _cfg.DeviceSecret, loc.lat, loc.lon, loc.acc, pct, rede, carregando, so);

            if (resp.ValueKind != JsonValueKind.Object) return;

            // Config (intervalo / rastreio)
            if (resp.TryGetProperty("config", out var cfgEl) && cfgEl.ValueKind == JsonValueKind.Object)
            {
                if (cfgEl.TryGetProperty("intervalo_telemetria_min", out var iv) && iv.TryGetInt32(out var mins) && mins > 0 && mins != _cfg.IntervaloMin)
                {
                    _cfg.IntervaloMin = mins;
                    _cfg.Save();
                    MarshalUI(() => { if (_timerReport != null) _timerReport.Interval = IntervaloMs(); });
                }
                if (cfgEl.TryGetProperty("rastreio_localizacao", out var rl))
                    _cfg.Rastreio = rl.ValueKind != JsonValueKind.False;
            }

            // Comandos pendentes
            if (resp.TryGetProperty("comandos", out var cmds) && cmds.ValueKind == JsonValueKind.Array)
            {
                foreach (var c in cmds.EnumerateArray())
                {
                    var id = c.Str("id");
                    var tipo = c.Str("tipo");
                    JsonElement par = c.TryGetProperty("parametros", out var pp) ? pp : default;
                    if (string.IsNullOrEmpty(id)) continue;
                    var (status, resultado) = Commands.Executar(tipo, par);
                    try { await ApiClient.AckCommandAsync(_cfg.EnrollmentId, _cfg.DeviceSecret, id, status, resultado); } catch { }
                }
            }

            // Termo pendente
            if (resp.TryGetProperty("termo_pendente", out var tp) && tp.ValueKind == JsonValueKind.Object)
            {
                _cfg.TermoId = tp.Str("id");
                _cfg.TermoTexto = tp.Str("texto");
                _cfg.TermoModelo = tp.Str("modelo");
                _cfg.Save();
                MarshalUI(() =>
                {
                    if (_miTermo != null) _miTermo.Enabled = true;
                    MostrarTermo();
                });
            }
            else if (!string.IsNullOrEmpty(_cfg.TermoId))
            {
                _cfg.LimparTermo();
                MarshalUI(() =>
                {
                    if (_miTermo != null) _miTermo.Enabled = false;
                    FecharTermo();
                });
            }
        }
        catch { /* nunca derruba o agente por um ciclo que falhou */ }
        finally { _ciclando = false; }
    }

    private void MarshalUI(Action acao)
    {
        try
        {
            if (_hidden.IsDisposed) return;
            if (_hidden.InvokeRequired) _hidden.BeginInvoke((MethodInvoker)(() => acao()));
            else acao();
        }
        catch { }
    }

    private void MostrarTermo()
    {
        if (string.IsNullOrEmpty(_cfg.TermoId)) return;
        if (_termoForm is { IsDisposed: false })
        {
            _termoForm.TopMost = true;
            _termoForm.WindowState = FormWindowState.Normal;
            _termoForm.BringToFront();
            _termoForm.Activate();
            return;
        }
        _termoForm = new TermoForm(_cfg, () =>
        {
            _cfg.LimparTermo();
            if (_miTermo != null) _miTermo.Enabled = false;
        });
        _termoForm.FormClosed += (s, e) => _termoForm = null;
        _termoForm.Show();
        _termoForm.Activate();
    }

    private void FecharTermo()
    {
        if (_termoForm is { IsDisposed: false }) _termoForm.Close();
        _termoForm = null;
    }

    private void Sair()
    {
        try
        {
            _timerReport?.Stop();
            _timerTermo?.Stop();
            if (_tray != null) _tray.Visible = false;
        }
        catch { }
        ExitThread();
    }
}

using System.Drawing;
using System.Runtime.InteropServices;
using System.Text.Json;
using System.Windows.Forms;

namespace ControllerAgente;

/// <summary>
/// Tela de primeiro uso: o colaborador/TI digita o codigo de registro gerado
/// no Controller. Ao registrar, o agente guarda o segredo e passa a iniciar
/// com o Windows.
/// </summary>
public class EnrollForm : Form
{
    private readonly Config _cfg;
    private readonly TextBox _txtCodigo = new();
    private readonly Button _btn = new();
    private readonly Label _status = new();

    public EnrollForm(Config cfg)
    {
        _cfg = cfg;

        Text = "Registrar notebook — Controller";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(440, 260);
        BackColor = Color.White;
        Font = new Font("Segoe UI", 9.75f);

        var titulo = new Label
        {
            Text = "Agente Controller",
            Font = new Font("Segoe UI", 15f, FontStyle.Bold),
            ForeColor = Color.FromArgb(30, 58, 138),
            AutoSize = true,
            Location = new Point(24, 20),
        };

        var sub = new Label
        {
            Text = "Digite o codigo de registro gerado no Controller\npara este notebook.",
            ForeColor = Color.FromArgb(100, 116, 139),
            AutoSize = true,
            Location = new Point(24, 56),
        };

        var lbl = new Label { Text = "Codigo de registro", AutoSize = true, Location = new Point(24, 104) };

        _txtCodigo.Location = new Point(24, 126);
        _txtCodigo.Width = 280;
        _txtCodigo.Font = new Font("Consolas", 16f, FontStyle.Bold);
        _txtCodigo.CharacterCasing = CharacterCasing.Upper;
        _txtCodigo.MaxLength = 12;

        _btn.Text = "Registrar";
        _btn.Location = new Point(314, 124);
        _btn.Size = new Size(100, 34);
        _btn.BackColor = Color.FromArgb(37, 99, 235);
        _btn.ForeColor = Color.White;
        _btn.FlatStyle = FlatStyle.Flat;
        _btn.Click += async (s, e) => await RegistrarAsync();

        _status.Location = new Point(24, 176);
        _status.AutoSize = false;
        _status.Size = new Size(392, 60);
        _status.ForeColor = Color.FromArgb(100, 116, 139);

        Controls.AddRange(new Control[] { titulo, sub, lbl, _txtCodigo, _btn, _status });
        AcceptButton = _btn;
    }

    private async Task RegistrarAsync()
    {
        var code = _txtCodigo.Text.Trim().ToUpperInvariant();
        if (string.IsNullOrEmpty(code))
        {
            _status.ForeColor = Color.FromArgb(185, 28, 28);
            _status.Text = "Informe o codigo de registro.";
            return;
        }

        _btn.Enabled = false;
        _txtCodigo.Enabled = false;
        _status.ForeColor = Color.FromArgb(100, 116, 139);
        _status.Text = "Registrando...";

        try
        {
            var so = RuntimeInformation.OSDescription;
            var (fab, mod, ser) = await Task.Run(HardwareInfo.Obter);
            var resp = await ApiClient.EnrollAsync(code, mod, fab, ser, so);

            var ok = resp.ValueKind == JsonValueKind.Object
                     && resp.TryGetProperty("success", out var sv) && sv.ValueKind == JsonValueKind.True;
            if (!ok)
            {
                _status.ForeColor = Color.FromArgb(185, 28, 28);
                _status.Text = resp.Str("error") is { Length: > 0 } err ? err : "Codigo invalido ou ja utilizado.";
                _btn.Enabled = true;
                _txtCodigo.Enabled = true;
                return;
            }

            _cfg.EnrollmentId = resp.Str("enrollment_id");
            _cfg.DeviceSecret = resp.Str("device_secret");
            _cfg.DispositivoId = resp.Str("dispositivo_id");
            if (resp.TryGetProperty("config", out var cfgEl) && cfgEl.ValueKind == JsonValueKind.Object)
            {
                if (cfgEl.TryGetProperty("intervalo_telemetria_min", out var iv) && iv.TryGetInt32(out var mins) && mins > 0)
                    _cfg.IntervaloMin = mins;
                if (cfgEl.TryGetProperty("rastreio_localizacao", out var rl))
                    _cfg.Rastreio = rl.ValueKind != JsonValueKind.False;
            }
            _cfg.Save();
            Autostart.Registrar();

            _status.ForeColor = Color.FromArgb(21, 128, 61);
            _status.Text = "Registrado! O agente ja esta ativo e inicia com o Windows.";
            DialogResult = DialogResult.OK;
            Close();
        }
        catch (Exception ex)
        {
            _status.ForeColor = Color.FromArgb(185, 28, 28);
            _status.Text = "Falha de conexao. Verifique a internet e tente de novo.\n" + ex.Message;
            _btn.Enabled = true;
            _txtCodigo.Enabled = true;
        }
    }
}

using System.Drawing;
using System.Text.Json;
using System.Windows.Forms;

namespace ControllerAgente;

/// <summary>
/// Tela do termo de responsabilidade, exibida no proprio notebook (sem login).
/// Fluxo igual ao do celular: a pessoa digita o CPF, o agente busca nome/setor
/// e a assinatura ja cadastrada (ADL) e ela apenas confirma. Sem cadastro ou
/// assinatura, cai no modo de assinar desenhando. Nao bloqueia a maquina.
/// </summary>
public class TermoForm : Form
{
    private readonly Config _cfg;
    private readonly Action _onAssinado;

    private readonly TextBox _txtCpf = new();
    private readonly Button _btnBuscar = new();
    private readonly Label _lblNome = new();
    private readonly Label _lblSetor = new();
    private readonly Panel _grupoResultado = new();
    private readonly PictureBox _imgAssinatura = new();
    private readonly Button _btnAssinarManual = new();
    private readonly Panel _grupoReuso = new();
    private readonly Label _lblNomeManual = new();
    private readonly TextBox _txtNome = new();
    private readonly SignaturePanel _assinatura = new();
    private readonly Button _btnLimpar = new();
    private readonly Panel _grupoManual = new();
    private readonly Button _btnConfirmar = new();

    private bool _encontrado;
    private string _nomeEncontrado = "";
    private string _storedSigUrl;
    private bool _modoReuso;

    public TermoForm(Config cfg, Action onAssinado)
    {
        _cfg = cfg;
        _onAssinado = onAssinado;

        Text = "Assinatura do termo — Controller";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = true;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(480, 680);
        BackColor = Color.White;
        TopMost = true;
        ShowInTaskbar = true;
        Font = new Font("Segoe UI", 9.75f);

        var flow = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            AutoScroll = true,
            Padding = new Padding(22, 18, 22, 18),
        };
        const int W = 418;

        var titulo = new Label
        {
            Text = "Assinatura obrigatoria do termo",
            Font = new Font("Segoe UI", 14f, FontStyle.Bold),
            ForeColor = Color.FromArgb(30, 58, 138),
            AutoSize = true,
        };
        var lblModelo = new Label
        {
            Text = string.IsNullOrEmpty(_cfg.TermoModelo) ? "" : _cfg.TermoModelo,
            ForeColor = Color.FromArgb(100, 116, 139),
            AutoSize = true,
            Margin = new Padding(3, 0, 3, 6),
        };
        var lblTermo = new Label
        {
            Text = string.IsNullOrEmpty(_cfg.TermoTexto)
                ? "Termo de responsabilidade do equipamento corporativo."
                : _cfg.TermoTexto,
            AutoSize = true,
            MaximumSize = new Size(W, 0),
            BackColor = Color.FromArgb(241, 245, 249),
            Padding = new Padding(12),
            Margin = new Padding(3, 3, 3, 12),
            ForeColor = Color.FromArgb(15, 23, 42),
        };

        var lblCpf = new Label { Text = "Digite seu CPF para identificar-se", Font = new Font("Segoe UI", 9.75f, FontStyle.Bold), AutoSize = true };
        _txtCpf.Width = W;
        _txtCpf.MaxLength = 14;
        _txtCpf.Margin = new Padding(3, 3, 3, 6);

        _btnBuscar.Text = "Buscar meus dados";
        _btnBuscar.Width = W;
        _btnBuscar.Height = 34;
        _btnBuscar.BackColor = Color.FromArgb(37, 99, 235);
        _btnBuscar.ForeColor = Color.White;
        _btnBuscar.FlatStyle = FlatStyle.Flat;
        _btnBuscar.Click += async (s, e) => await BuscarAsync();

        // Resultado
        _grupoResultado.Width = W;
        _grupoResultado.Height = 56;
        _grupoResultado.BackColor = Color.FromArgb(248, 250, 252);
        _grupoResultado.Visible = false;
        _grupoResultado.Margin = new Padding(3, 10, 3, 4);
        _lblNome.Font = new Font("Segoe UI", 11f, FontStyle.Bold);
        _lblNome.ForeColor = Color.FromArgb(15, 23, 42);
        _lblNome.AutoSize = true;
        _lblNome.Location = new Point(12, 8);
        _lblSetor.ForeColor = Color.FromArgb(100, 116, 139);
        _lblSetor.AutoSize = true;
        _lblSetor.Location = new Point(12, 30);
        _grupoResultado.Controls.AddRange(new Control[] { _lblNome, _lblSetor });

        // Reuso de assinatura
        _grupoReuso.Width = W;
        _grupoReuso.Height = 190;
        _grupoReuso.Visible = false;
        _grupoReuso.Margin = new Padding(3, 4, 3, 4);
        var lblReuso = new Label { Text = "Sua assinatura registrada:", Font = new Font("Segoe UI", 9.75f, FontStyle.Bold), AutoSize = true, Location = new Point(0, 0) };
        _imgAssinatura.Size = new Size(W, 140);
        _imgAssinatura.Location = new Point(0, 22);
        _imgAssinatura.BackColor = Color.FromArgb(248, 250, 252);
        _imgAssinatura.SizeMode = PictureBoxSizeMode.Zoom;
        _btnAssinarManual.Text = "Prefiro assinar agora";
        _btnAssinarManual.Location = new Point(0, 164);
        _btnAssinarManual.Width = 180;
        _btnAssinarManual.FlatStyle = FlatStyle.Flat;
        _btnAssinarManual.Click += (s, e) => UsarManual(nomeConhecido: true);
        _grupoReuso.Controls.AddRange(new Control[] { lblReuso, _imgAssinatura, _btnAssinarManual });

        // Assinatura manual
        _grupoManual.Width = W;
        _grupoManual.Height = 300;
        _grupoManual.Visible = false;
        _grupoManual.Margin = new Padding(3, 4, 3, 4);
        _lblNomeManual.Text = "Nome completo";
        _lblNomeManual.Font = new Font("Segoe UI", 9.75f, FontStyle.Bold);
        _lblNomeManual.AutoSize = true;
        _lblNomeManual.Location = new Point(0, 0);
        _lblNomeManual.Visible = false;
        _txtNome.Location = new Point(0, 22);
        _txtNome.Width = W;
        _txtNome.Visible = false;
        var lblAssine = new Label { Text = "Assine abaixo", Font = new Font("Segoe UI", 9.75f, FontStyle.Bold), AutoSize = true, Location = new Point(0, 54) };
        _assinatura.Location = new Point(0, 76);
        _assinatura.Size = new Size(W, 180);
        _btnLimpar.Text = "Limpar assinatura";
        _btnLimpar.Location = new Point(0, 262);
        _btnLimpar.Width = 160;
        _btnLimpar.FlatStyle = FlatStyle.Flat;
        _btnLimpar.Click += (s, e) => _assinatura.Limpar();
        _grupoManual.Controls.AddRange(new Control[] { _lblNomeManual, _txtNome, lblAssine, _assinatura, _btnLimpar });

        _btnConfirmar.Text = "Confirmar e assinar termo";
        _btnConfirmar.Width = W;
        _btnConfirmar.Height = 38;
        _btnConfirmar.BackColor = Color.FromArgb(22, 163, 74);
        _btnConfirmar.ForeColor = Color.White;
        _btnConfirmar.FlatStyle = FlatStyle.Flat;
        _btnConfirmar.Visible = false;
        _btnConfirmar.Margin = new Padding(3, 10, 3, 6);
        _btnConfirmar.Click += async (s, e) => await ConfirmarAsync();

        var rodape = new Label
        {
            Text = "Este equipamento e corporativo e gerenciado pela empresa. A assinatura do termo e obrigatoria.",
            ForeColor = Color.FromArgb(148, 163, 184),
            AutoSize = true,
            MaximumSize = new Size(W, 0),
            Font = new Font("Segoe UI", 8.25f),
        };

        flow.Controls.AddRange(new Control[]
        {
            titulo, lblModelo, lblTermo, lblCpf, _txtCpf, _btnBuscar,
            _grupoResultado, _grupoReuso, _grupoManual, _btnConfirmar, rodape
        });
        Controls.Add(flow);
        AcceptButton = _btnBuscar;
    }

    private static string SoDigitos(string s) => new string((s ?? "").Where(char.IsDigit).ToArray());

    private async Task BuscarAsync()
    {
        var cpf = SoDigitos(_txtCpf.Text);
        if (cpf.Length != 11) { MessageBox.Show("Digite um CPF valido (11 digitos)."); return; }

        _btnBuscar.Enabled = false;
        try
        {
            var resp = await ApiClient.BuscarColaboradorAsync(_cfg.EnrollmentId, _cfg.DeviceSecret, cpf);
            if (resp.ValueKind != JsonValueKind.Object || !(resp.TryGetProperty("success", out var sv) && sv.ValueKind == JsonValueKind.True))
            {
                MessageBox.Show("Falha na busca. Verifique a conexao.");
                return;
            }

            if (resp.Bool("encontrado"))
            {
                _encontrado = true;
                _nomeEncontrado = resp.Str("nome");
                var setor = resp.Str("setor");
                _lblNome.Text = _nomeEncontrado;
                _lblSetor.Text = string.IsNullOrEmpty(setor) ? "" : "Setor: " + setor;
                _grupoResultado.Visible = true;
                _btnConfirmar.Visible = true;

                var sigUrl = resp.Str("assinatura_url");
                if (resp.Bool("tem_assinatura") && !string.IsNullOrEmpty(sigUrl))
                {
                    _storedSigUrl = sigUrl;
                    _modoReuso = true;
                    _grupoReuso.Visible = true;
                    _grupoManual.Visible = false;
                    await CarregarAssinaturaAsync(sigUrl);
                }
                else
                {
                    _grupoReuso.Visible = false;
                    UsarManual(nomeConhecido: true);
                }
            }
            else
            {
                _encontrado = false;
                MessageBox.Show("CPF nao encontrado no cadastro. Preencha manualmente.");
                _grupoResultado.Visible = false;
                _grupoReuso.Visible = false;
                UsarManual(nomeConhecido: false);
                _btnConfirmar.Visible = true;
            }
        }
        catch
        {
            MessageBox.Show("Falha na busca. Verifique a conexao.");
        }
        finally
        {
            _btnBuscar.Enabled = true;
        }
    }

    private void UsarManual(bool nomeConhecido)
    {
        _modoReuso = false;
        _grupoReuso.Visible = false;
        _grupoManual.Visible = true;
        _lblNomeManual.Visible = !nomeConhecido;
        _txtNome.Visible = !nomeConhecido;
        _btnConfirmar.Visible = true;
    }

    private async Task CarregarAssinaturaAsync(string url)
    {
        try
        {
            byte[] bytes;
            if (url.StartsWith("data:", StringComparison.OrdinalIgnoreCase))
            {
                var b64 = url.Contains(',') ? url[(url.IndexOf(',') + 1)..] : "";
                bytes = Convert.FromBase64String(b64);
            }
            else
            {
                bytes = await ApiClient.DownloadImageAsync(url);
            }
            if (bytes != null)
            {
                using var ms = new MemoryStream(bytes);
                using var tmp = Image.FromStream(ms);
                _imgAssinatura.Image = new Bitmap(tmp); // copia independente do stream
                return;
            }
        }
        catch { }
        MessageBox.Show("Nao foi possivel carregar sua assinatura. Assine abaixo.");
        UsarManual(nomeConhecido: true);
    }

    private async Task ConfirmarAsync()
    {
        var cpf = SoDigitos(_txtCpf.Text);
        var nome = _encontrado ? _nomeEncontrado : _txtNome.Text.Trim();
        if (cpf.Length != 11) { MessageBox.Show("CPF invalido."); return; }
        if (nome.Length < 3) { MessageBox.Show("Informe o nome completo."); return; }
        if (string.IsNullOrEmpty(_cfg.TermoId)) { Close(); return; }

        string assinatura;
        if (_modoReuso)
        {
            assinatura = _storedSigUrl ?? "";
        }
        else
        {
            if (_assinatura.Vazio) { MessageBox.Show("Assine no quadro."); return; }
            assinatura = _assinatura.ExportarDataUrl();
        }
        if (string.IsNullOrEmpty(assinatura)) { MessageBox.Show("Assinatura ausente."); return; }

        _btnConfirmar.Enabled = false;
        try
        {
            var resp = await ApiClient.AssinarTermoAsync(
                _cfg.EnrollmentId, _cfg.DeviceSecret, _cfg.TermoId,
                nome, cpf, assinatura, _cfg.TermoTexto ?? "", _modoReuso);

            var ok = resp.ValueKind == JsonValueKind.Object
                     && resp.TryGetProperty("success", out var sv) && sv.ValueKind == JsonValueKind.True;
            if (ok)
            {
                _onAssinado?.Invoke();
                MessageBox.Show("Termo assinado. Obrigado!", "Controller", MessageBoxButtons.OK, MessageBoxIcon.Information);
                Close();
            }
            else
            {
                MessageBox.Show("Falha ao enviar. Verifique a conexao e tente de novo.");
                _btnConfirmar.Enabled = true;
            }
        }
        catch
        {
            MessageBox.Show("Falha ao enviar. Verifique a conexao e tente de novo.");
            _btnConfirmar.Enabled = true;
        }
    }
}

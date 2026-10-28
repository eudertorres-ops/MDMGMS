using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Windows.Forms;

namespace ControllerAgente;

/// <summary>Quadro de assinatura: desenha com o mouse/touch e exporta PNG (data URL).</summary>
public class SignaturePanel : Panel
{
    private Bitmap _bmp;
    private Point _ultimo;
    private bool _desenhando;
    private bool _temTraco;

    public SignaturePanel()
    {
        DoubleBuffered = true;
        BackColor = Color.White;
        BorderStyle = BorderStyle.FixedSingle;
    }

    private void Garantir()
    {
        if (_bmp == null || _bmp.Width != Math.Max(1, Width) || _bmp.Height != Math.Max(1, Height))
        {
            var antigo = _bmp;
            _bmp = new Bitmap(Math.Max(1, Width), Math.Max(1, Height));
            using (var g = Graphics.FromImage(_bmp)) g.Clear(Color.White);
            antigo?.Dispose();
        }
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        Garantir();
        e.Graphics.DrawImage(_bmp, 0, 0);
    }

    protected override void OnMouseDown(MouseEventArgs e)
    {
        Garantir();
        _desenhando = true;
        _ultimo = e.Location;
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        if (!_desenhando) return;
        using (var g = Graphics.FromImage(_bmp))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            using var pen = new Pen(Color.Black, 2.5f) { StartCap = LineCap.Round, EndCap = LineCap.Round };
            g.DrawLine(pen, _ultimo, e.Location);
        }
        _ultimo = e.Location;
        _temTraco = true;
        Invalidate();
    }

    protected override void OnMouseUp(MouseEventArgs e) => _desenhando = false;

    public bool Vazio => !_temTraco;

    public void Limpar()
    {
        _bmp?.Dispose();
        _bmp = null;
        _temTraco = false;
        Invalidate();
    }

    public string ExportarDataUrl()
    {
        Garantir();
        using var ms = new MemoryStream();
        _bmp.Save(ms, ImageFormat.Png);
        return "data:image/png;base64," + Convert.ToBase64String(ms.ToArray());
    }
}

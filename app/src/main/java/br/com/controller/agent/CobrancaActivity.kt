package br.com.controller.agent

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.com.controller.agent.databinding.ActivityCobrancaBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela que cobra a assinatura do termo. Abre sozinha e reabre ate ser assinada.
 * Fluxo: a pessoa digita o CPF -> o app busca nome/setor e a assinatura ja
 * cadastrada (ADL) -> a pessoa confirma. Sem login. Se nao houver cadastro ou
 * assinatura, cai no modo de assinar desenhando.
 */
class CobrancaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCobrancaBinding
    private lateinit var storage: Storage
    private val scope = CoroutineScope(Dispatchers.Main)

    private var encontrado = false
    private var nomeEncontrado = ""
    private var storedSigUrl: String? = null
    private var modoReuso = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCobrancaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        storage = Storage(this)

        binding.txtModelo.text = storage.termoModelo ?: ""
        binding.txtTermo.text = storage.termoTexto ?: "Termo de responsabilidade do aparelho corporativo."

        binding.btnBuscar.setOnClickListener { buscar() }
        binding.btnLimpar.setOnClickListener { binding.signaturePad.limpar() }
        binding.btnConfirmar.setOnClickListener { confirmar() }
        binding.btnAssinarManual.setOnClickListener { usarAssinaturaManual(nomeConhecido = true) }
    }

    override fun onResume() {
        super.onResume()
        visivel = true
        if (storage.termoPendenteId.isNullOrEmpty()) finish()
    }

    override fun onPause() {
        super.onPause()
        visivel = false
    }

    private fun buscar() {
        val cpf = binding.editCpf.text.toString().replace(Regex("\\D"), "")
        if (cpf.length != 11) { toast("Digite um CPF válido (11 dígitos)"); return }
        val enrollmentId = storage.enrollmentId
        val secret = storage.deviceSecret
        if (enrollmentId.isNullOrEmpty() || secret.isNullOrEmpty()) { toast("Aparelho não registrado"); return }

        binding.btnBuscar.isEnabled = false
        scope.launch {
            val resp = withContext(Dispatchers.IO) {
                try { ApiClient.buscarColaborador(enrollmentId, secret, cpf) } catch (_: Exception) { null }
            }
            binding.btnBuscar.isEnabled = true
            if (resp == null || !resp.optBoolean("success", false)) {
                toast("Falha na busca. Verifique a conexão.")
                return@launch
            }
            if (resp.optBoolean("encontrado", false)) {
                encontrado = true
                nomeEncontrado = resp.optString("nome")
                val setor = resp.optString("setor")
                binding.grupoResultado.visibility = View.VISIBLE
                binding.txtNomeInfo.text = nomeEncontrado
                binding.txtSetorInfo.text = if (setor.isNotEmpty()) "Setor: $setor" else ""
                binding.btnConfirmar.visibility = View.VISIBLE

                val sigUrl = resp.optString("assinatura_url")
                if (resp.optBoolean("tem_assinatura", false) && sigUrl.isNotEmpty()) {
                    storedSigUrl = sigUrl
                    modoReuso = true
                    binding.grupoReuso.visibility = View.VISIBLE
                    binding.grupoManual.visibility = View.GONE
                    carregarAssinatura(sigUrl)
                } else {
                    usarAssinaturaManual(nomeConhecido = true)
                    binding.grupoReuso.visibility = View.GONE
                }
            } else {
                encontrado = false
                toast("CPF não encontrado no cadastro. Preencha manualmente.")
                binding.grupoResultado.visibility = View.GONE
                binding.grupoReuso.visibility = View.GONE
                usarAssinaturaManual(nomeConhecido = false)
                binding.btnConfirmar.visibility = View.VISIBLE
            }
        }
    }

    /** Mostra a area de desenho. Se o nome ja e conhecido, esconde o campo de nome. */
    private fun usarAssinaturaManual(nomeConhecido: Boolean) {
        modoReuso = false
        binding.grupoReuso.visibility = View.GONE
        binding.grupoManual.visibility = View.VISIBLE
        binding.lblNomeManual.visibility = if (nomeConhecido) View.GONE else View.VISIBLE
        binding.editNome.visibility = if (nomeConhecido) View.GONE else View.VISIBLE
        binding.btnConfirmar.visibility = View.VISIBLE
    }

    private fun carregarAssinatura(url: String) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    if (url.startsWith("data:")) {
                        val b64 = url.substringAfter(",", "")
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else {
                        val bytes = ApiClient.baixarImagem(url)
                        if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
                    }
                } catch (_: Exception) { null }
            }
            if (bmp != null) binding.imgAssinatura.setImageBitmap(bmp)
            else {
                // Nao conseguiu carregar a assinatura armazenada: cai no manual.
                toast("Não foi possível carregar sua assinatura. Assine abaixo.")
                usarAssinaturaManual(nomeConhecido = true)
            }
        }
    }

    private fun confirmar() {
        val cpf = binding.editCpf.text.toString().replace(Regex("\\D"), "")
        val nome = if (encontrado) nomeEncontrado else binding.editNome.text.toString().trim()
        val termoId = storage.termoPendenteId
        if (cpf.length != 11) { toast("CPF inválido"); return }
        if (nome.length < 3) { toast("Informe o nome completo"); return }
        if (termoId.isNullOrEmpty()) { finish(); return }

        val assinatura: String = if (modoReuso) {
            storedSigUrl ?: ""
        } else {
            if (binding.signaturePad.vazio()) { toast("Assine no quadro"); return }
            binding.signaturePad.exportarDataUrl()
        }
        if (assinatura.isEmpty()) { toast("Assinatura ausente"); return }

        val enrollmentId = storage.enrollmentId ?: return
        val secret = storage.deviceSecret ?: return
        val texto = storage.termoTexto ?: ""

        binding.btnConfirmar.isEnabled = false
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val r = ApiClient.assinarTermo(enrollmentId, secret, termoId, nome, cpf, assinatura, texto, modoReuso)
                    r.optBoolean("success", false)
                } catch (_: Exception) { false }
            }
            binding.btnConfirmar.isEnabled = true
            if (ok) {
                storage.limparTermo()
                Toast.makeText(this@CobrancaActivity, "Termo assinado. Obrigado!", Toast.LENGTH_LONG).show()
                finish()
            } else {
                toast("Falha ao enviar. Verifique a conexão e tente de novo.")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        @Volatile var visivel = false
    }
}

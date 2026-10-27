package br.com.controller.agent

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.com.controller.agent.databinding.ActivityCobrancaBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tela que cobra a assinatura do termo. Abre sozinha e reabre ate ser assinada. */
class CobrancaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCobrancaBinding
    private lateinit var storage: Storage
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCobrancaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        storage = Storage(this)

        binding.txtModelo.text = storage.termoModelo ?: ""
        binding.txtTermo.text = storage.termoTexto ?: "Termo de responsabilidade do aparelho corporativo."

        binding.btnLimpar.setOnClickListener { binding.signaturePad.limpar() }
        binding.btnAssinar.setOnClickListener { assinar() }
    }

    override fun onResume() {
        super.onResume()
        visivel = true
        // Se o termo ja nao esta mais pendente (assinou em outro ciclo), fecha.
        if (storage.termoPendenteId.isNullOrEmpty()) finish()
    }

    override fun onPause() {
        super.onPause()
        visivel = false
    }

    private fun assinar() {
        val nome = binding.editNome.text.toString().trim()
        val cpf = binding.editCpf.text.toString().replace(Regex("\\D"), "")
        val termoId = storage.termoPendenteId
        if (nome.length < 3) { toast("Digite seu nome completo"); return }
        if (cpf.length != 11) { toast("CPF deve ter 11 dígitos"); return }
        if (binding.signaturePad.vazio()) { toast("Assine no quadro"); return }
        if (termoId.isNullOrEmpty()) { finish(); return }

        val enrollmentId = storage.enrollmentId
        val secret = storage.deviceSecret
        if (enrollmentId.isNullOrEmpty() || secret.isNullOrEmpty()) { toast("Aparelho não registrado"); return }

        val assinatura = binding.signaturePad.exportarDataUrl()
        val texto = storage.termoTexto ?: ""
        binding.btnAssinar.isEnabled = false
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val r = ApiClient.assinarTermo(enrollmentId, secret, termoId, nome, cpf, assinatura, texto)
                    r.optBoolean("success", false)
                } catch (_: Exception) { false }
            }
            binding.btnAssinar.isEnabled = true
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

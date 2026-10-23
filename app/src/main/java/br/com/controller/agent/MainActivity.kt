package br.com.controller.agent

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.com.controller.agent.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: Storage
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        storage = Storage(this)

        binding.btnRegistrar.setOnClickListener { registrar() }
        binding.btnForcar.setOnClickListener {
            TelemetryWorker.reportarAgora(this)
            Toast.makeText(this, "Reporte solicitado", Toast.LENGTH_SHORT).show()
        }
        atualizarTela()
    }

    private fun atualizarTela() {
        val executor = CommandExecutor(this)
        val owner = executor.isDeviceOwner()
        binding.txtOwner.text = if (owner) "✓ Device Owner ativo" else "✗ NÃO é Device Owner (provisionar via ADB)"

        if (storage.registrado) {
            binding.grupoRegistro.visibility = android.view.View.GONE
            binding.grupoStatus.visibility = android.view.View.VISIBLE
            binding.txtStatus.text = "Registrado neste Controller.\nDispositivo: ${storage.dispositivoId ?: "-"}\nIntervalo: ${storage.intervaloMin} min"
        } else {
            binding.grupoRegistro.visibility = android.view.View.VISIBLE
            binding.grupoStatus.visibility = android.view.View.GONE
        }
    }

    private fun registrar() {
        val code = binding.editCodigo.text.toString().trim().uppercase()
        if (code.isEmpty()) {
            Toast.makeText(this, "Digite o código de registro", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnRegistrar.isEnabled = false
        val modelo = "${Build.MANUFACTURER} ${Build.MODEL}"
        val soVersao = Build.VERSION.RELEASE ?: ""
        scope.launch {
            val resp = withContext(Dispatchers.IO) {
                try { ApiClient.enroll(code, modelo, soVersao) } catch (e: Exception) { null }
            }
            binding.btnRegistrar.isEnabled = true
            if (resp == null || !resp.optBoolean("success", false)) {
                Toast.makeText(this@MainActivity, "Falha: ${resp?.optString("error") ?: "sem conexão"}", Toast.LENGTH_LONG).show()
                return@launch
            }
            storage.enrollmentId = resp.optString("enrollment_id")
            storage.deviceSecret = resp.optString("device_secret")
            storage.dispositivoId = resp.optString("dispositivo_id")
            resp.optJSONObject("config")?.let { ReportCycle.aplicarConfigNaStorage(it, storage) }

            CommandExecutor(this@MainActivity).aplicarConfig(storage)
            TelemetryWorker.agendarPeriodico(this@MainActivity)
            AgentService.iniciar(this@MainActivity)
            TelemetryWorker.reportarAgora(this@MainActivity)

            Toast.makeText(this@MainActivity, "Registrado com sucesso!", Toast.LENGTH_LONG).show()
            atualizarTela()
        }
    }
}

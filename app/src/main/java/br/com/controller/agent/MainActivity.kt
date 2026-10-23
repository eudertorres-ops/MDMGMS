package br.com.controller.agent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import br.com.controller.agent.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: Storage
    private val scope = CoroutineScope(Dispatchers.Main)

    /** Pede localizacao + notificacoes quando o app NAO e Device Owner (instalacao simples). */
    private fun pedirPermissoes() {
        val faltando = mutableListOf<String>()
        val precisa = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) precisa.add(Manifest.permission.POST_NOTIFICATIONS)
        for (p in precisa) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) faltando.add(p)
        }
        if (faltando.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltando.toTypedArray(), 101)
        } else {
            pedirLocalizacaoSegundoPlano()
        }
    }

    private fun pedirLocalizacaoSegundoPlano() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            try { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 102) } catch (_: Exception) {}
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) pedirLocalizacaoSegundoPlano()
    }

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

        // Instalacao simples (sem Device Owner): precisamos pedir as permissoes
        // ao usuario. Como Device Owner, o app ja se autoconcede.
        if (!CommandExecutor(this).isDeviceOwner()) pedirPermissoes()
    }

    private fun atualizarTela() {
        val executor = CommandExecutor(this)
        val owner = executor.isDeviceOwner()
        binding.txtOwner.text = if (owner) "✓ Device Owner (controle total)" else "Modo simples (rastreio ativo). Para controle total, provisione via ADB."

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

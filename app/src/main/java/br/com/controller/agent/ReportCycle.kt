package br.com.controller.agent

import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject

/** Um ciclo completo: coleta telemetria, reporta, aplica config e executa comandos. */
object ReportCycle {

    @Synchronized
    fun executar(context: Context): Boolean {
        val storage = Storage(context)
        if (!storage.registrado) return false

        val enrollmentId = storage.enrollmentId!!
        val secret = storage.deviceSecret!!
        val executor = CommandExecutor(context)

        val loc = if (storage.rastreioLocalizacao) LocationHelper.ultimaLocalizacao(context) else null
        val bateria = LocationHelper.nivelBateria(context)
        val rede = LocationHelper.tipoRede(context)
        val carregando = LocationHelper.carregando(context)
        val soVersao = Build.VERSION.RELEASE ?: ""

        val resp: JSONObject = try {
            ApiClient.report(
                enrollmentId, secret,
                loc?.latitude, loc?.longitude, loc?.accuracy,
                bateria, rede, carregando, soVersao
            )
        } catch (_: Exception) {
            return false
        }

        if (!resp.optBoolean("success", false)) return false

        // Atualiza config local
        resp.optJSONObject("config")?.let { aplicarConfigNaStorage(it, storage) }
        executor.aplicarConfig(storage)

        // Executa comandos
        val comandos = ApiClient.comandosFrom(resp)
        for (i in 0 until comandos.length()) {
            val c = comandos.optJSONObject(i) ?: continue
            val id = c.optString("id")
            val tipo = c.optString("tipo")
            val params = c.optJSONObject("parametros") ?: JSONObject()
            val (status, resultado) = executor.executar(tipo, params)
            try { ApiClient.ackCommand(enrollmentId, secret, id, status, resultado) } catch (_: Exception) {}
        }

        // Termo pendente -> guarda e abre a tela de cobranca (reabre ate assinar).
        val termo = ApiClient.termoFrom(resp)
        if (termo != null && !termo.optString("id").isNullOrEmpty()) {
            storage.termoPendenteId = termo.optString("id")
            storage.termoTexto = termo.optString("texto")
            storage.termoModelo = termo.optString("modelo")
            abrirCobranca(context)
        } else {
            storage.limparTermo()
        }
        return true
    }

    /** Abre a tela de cobranca, se ainda nao estiver na frente. */
    fun abrirCobranca(context: Context) {
        if (CobrancaActivity.visivel) return
        try {
            val i = Intent(context, CobrancaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (_: Exception) {}
    }

    fun aplicarConfigNaStorage(config: JSONObject, storage: Storage) {
        storage.intervaloMin = config.optInt("intervalo_telemetria_min", storage.intervaloMin)
        storage.modo = config.optString("modo", storage.modo)
        storage.kioskPackage = config.optString("kiosk_app_package", null)
        storage.rastreioLocalizacao = config.optBoolean("rastreio_localizacao", true)
        val bloq = config.optJSONArray("apps_bloqueados")
        if (bloq != null) {
            val set = mutableSetOf<String>()
            for (i in 0 until bloq.length()) bloq.optString(i)?.let { if (it.isNotEmpty()) set.add(it) }
            storage.appsBloqueados = set
        }
    }
}

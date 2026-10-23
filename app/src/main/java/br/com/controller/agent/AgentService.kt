package br.com.controller.agent

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano: mantém o ciclo de reporte rodando no intervalo
 * definido pela política (pode ser < 15 min, diferente do WorkManager).
 */
class AgentService : Service() {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startForeground(Constants.NOTIF_ID, Notifier.notificacaoServico(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job?.isActive != true) {
            job = scope.launch {
                while (isActive) {
                    try { ReportCycle.executar(applicationContext) } catch (_: Exception) {}
                    val min = Storage(applicationContext).intervaloMin.coerceAtLeast(1)
                    delay(min * 60_000L)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun iniciar(context: Context) {
            val i = Intent(context, AgentService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else
                context.startService(i)
        }
    }
}

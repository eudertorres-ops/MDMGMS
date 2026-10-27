package br.com.controller.agent

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    // Ao desbloquear/ligar a tela, reabre a cobranca se houver termo pendente.
    private val telaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!Storage(context).termoPendenteId.isNullOrEmpty()) {
                ReportCycle.abrirCobranca(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(Constants.NOTIF_ID, Notifier.notificacaoServico(this))
        val filtro = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try { registerReceiver(telaReceiver, filtro) } catch (_: Exception) {}
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
        try { unregisterReceiver(telaReceiver) } catch (_: Exception) {}
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

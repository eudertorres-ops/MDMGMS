package br.com.controller.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Religa o agente após o boot do aparelho. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val storage = Storage(context)
            if (storage.registrado) {
                TelemetryWorker.agendarPeriodico(context)
                AgentService.iniciar(context)
            }
        }
    }
}

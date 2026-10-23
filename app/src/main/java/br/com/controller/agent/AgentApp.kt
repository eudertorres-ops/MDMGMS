package br.com.controller.agent

import android.app.Application

class AgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.criarCanal(this)
        val storage = Storage(this)
        if (storage.registrado) {
            TelemetryWorker.agendarPeriodico(this)
            AgentService.iniciar(this)
        }
    }
}

package br.com.controller.agent

object Constants {
    // Endpoint publico do agente no Controller (Base44).
    // Se o app/URL mudar, altere apenas esta linha.
    const val AGENT_ENDPOINT =
        "https://controller-9f8beb13.base44.app/api/apps/692311b370fc40d19f8beb13/functions/mdmAgent"

    const val APP_ID = "692311b370fc40d19f8beb13"

    const val PREFS = "controller_agent_prefs"
    const val WORK_TELEMETRIA = "controller_telemetria"
    const val NOTIF_CHANNEL = "controller_agent"
    const val NOTIF_ID = 4201

    // Intervalo minimo de telemetria em minutos (o servidor pode sobrescrever).
    const val INTERVALO_PADRAO_MIN = 15
}

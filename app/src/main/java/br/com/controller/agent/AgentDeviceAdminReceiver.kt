package br.com.controller.agent

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AgentDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Controller Agent ativado", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Desativar o Controller removerá o gerenciamento corporativo deste aparelho."
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, AgentDeviceAdminReceiver::class.java)
    }
}

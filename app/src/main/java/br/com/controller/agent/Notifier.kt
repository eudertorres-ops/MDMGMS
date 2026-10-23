package br.com.controller.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object Notifier {

    fun criarCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val canal = NotificationChannel(
                Constants.NOTIF_CHANNEL,
                "Controller Agent",
                NotificationManager.IMPORTANCE_LOW
            )
            canal.description = "Gerenciamento corporativo do aparelho"
            nm.createNotificationChannel(canal)
        }
    }

    fun notificacaoServico(context: Context): Notification {
        criarCanal(context)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(context, Constants.NOTIF_CHANNEL)
        else
            @Suppress("DEPRECATION") Notification.Builder(context)
        return builder
            .setContentTitle("Controller")
            .setContentText("Gerenciamento corporativo ativo")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    fun mostrarMensagem(context: Context, texto: String) {
        criarCanal(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(context, Constants.NOTIF_CHANNEL)
        else
            @Suppress("DEPRECATION") Notification.Builder(context)
        val n = builder
            .setContentTitle("Mensagem da empresa")
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
    }
}

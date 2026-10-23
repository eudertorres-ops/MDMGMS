package br.com.controller.agent

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import org.json.JSONObject

/**
 * Aplica config (politica) e executa comandos usando o DevicePolicyManager.
 * Requer que o app seja Device Owner (provisionado via ADB: dpm set-device-owner).
 */
class CommandExecutor(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin: ComponentName = AgentDeviceAdminReceiver.component(context)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    /** Concede a si mesmo as permissoes de localizacao (poder de Device Owner). */
    fun autoConcederPermissoes() {
        if (!isDeviceOwner()) return
        val perms = listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.POST_NOTIFICATIONS"
        )
        for (p in perms) {
            try {
                dpm.setPermissionGrantState(admin, context.packageName, p, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            } catch (_: Exception) {}
        }
    }

    /** Impede que o proprio agente seja desinstalado. */
    fun bloquearDesinstalacao() {
        if (!isDeviceOwner()) return
        try { dpm.setUninstallBlocked(admin, context.packageName, true) } catch (_: Exception) {}
    }

    /** Aplica a politica recebida do servidor. */
    fun aplicarConfig(storage: Storage) {
        if (!isDeviceOwner()) return
        autoConcederPermissoes()
        bloquearDesinstalacao()

        // Camera
        // (permite_camera e tratado no servidor; aqui so aplicamos bloqueios explicitos)

        // Apps bloqueados -> suspende/oculta
        val bloqueados = storage.appsBloqueados
        for (pkg in bloqueados) {
            try {
                dpm.setApplicationHidden(admin, pkg, true)
            } catch (_: Exception) {}
        }

        // Kiosk: trava num app unico (lock task)
        if (storage.modo == "kiosk" && !storage.kioskPackage.isNullOrEmpty()) {
            val pkgs = arrayOf(storage.kioskPackage!!, context.packageName)
            try {
                dpm.setLockTaskPackages(admin, pkgs)
            } catch (_: Exception) {}
        } else {
            try { dpm.setLockTaskPackages(admin, arrayOf(context.packageName)) } catch (_: Exception) {}
        }
    }

    /**
     * Executa um comando. Retorna um par (status, resultado).
     * status: "executado" ou "falhou".
     */
    fun executar(tipo: String, parametros: JSONObject): Pair<String, String> {
        if (!isDeviceOwner()) return "falhou" to "App não é Device Owner."
        return try {
            when (tipo) {
                "bloquear" -> { dpm.lockNow(); "executado" to "Tela bloqueada." }
                "reiniciar" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { dpm.reboot(admin); "executado" to "Reiniciando." }
                    else "falhou" to "Reinicio requer Android 7+."
                }
                "limpar_senha" -> {
                    // Remove restricoes de senha (nao expoe senha). Em Device Owner,
                    // zera a politica de qualidade de senha.
                    try { dpm.setPasswordQuality(admin, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) } catch (_: Exception) {}
                    "executado" to "Política de senha liberada."
                }
                "localizar" -> {
                    // A localizacao e enviada no proximo report; forcamos um agora.
                    TelemetryWorker.reportarAgora(context)
                    "executado" to "Localização solicitada."
                }
                "desbloquear" -> { "executado" to "Sem ação (tela é desbloqueada pelo usuário)." }
                "aplicar_politica" -> { "executado" to "Política reaplicada no próximo ciclo." }
                "wipe" -> {
                    val flags = DevicePolicyManager.WIPE_EXTERNAL_STORAGE
                    dpm.wipeData(flags)
                    "executado" to "Aparelho sendo apagado."
                }
                "instalar_app", "desinstalar_app" -> {
                    "falhou" to "Instalação/remoção de apps não suportada nesta versão do agente."
                }
                "mensagem" -> {
                    val texto = parametros.optString("mensagem", "Mensagem da empresa")
                    Notifier.mostrarMensagem(context, texto)
                    "executado" to "Mensagem exibida."
                }
                else -> "falhou" to "Tipo de comando desconhecido: $tipo"
            }
        } catch (e: Exception) {
            "falhou" to (e.message ?: "Erro ao executar.")
        }
    }
}

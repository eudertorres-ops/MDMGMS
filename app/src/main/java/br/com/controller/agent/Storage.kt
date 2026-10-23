package br.com.controller.agent

import android.content.Context

/** Guarda o estado do registro (enrollment_id, device_secret) e a config. */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE)

    var enrollmentId: String?
        get() = prefs.getString("enrollment_id", null)
        set(v) = prefs.edit().putString("enrollment_id", v).apply()

    var deviceSecret: String?
        get() = prefs.getString("device_secret", null)
        set(v) = prefs.edit().putString("device_secret", v).apply()

    var dispositivoId: String?
        get() = prefs.getString("dispositivo_id", null)
        set(v) = prefs.edit().putString("dispositivo_id", v).apply()

    var intervaloMin: Int
        get() = prefs.getInt("intervalo_min", Constants.INTERVALO_PADRAO_MIN)
        set(v) = prefs.edit().putInt("intervalo_min", v).apply()

    var modo: String
        get() = prefs.getString("modo", "totalmente_gerenciado") ?: "totalmente_gerenciado"
        set(v) = prefs.edit().putString("modo", v).apply()

    var kioskPackage: String?
        get() = prefs.getString("kiosk_package", null)
        set(v) = prefs.edit().putString("kiosk_package", v).apply()

    var appsBloqueados: Set<String>
        get() = prefs.getStringSet("apps_bloqueados", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("apps_bloqueados", v).apply()

    var rastreioLocalizacao: Boolean
        get() = prefs.getBoolean("rastreio_localizacao", true)
        set(v) = prefs.edit().putBoolean("rastreio_localizacao", v).apply()

    val registrado: Boolean
        get() = !enrollmentId.isNullOrEmpty() && !deviceSecret.isNullOrEmpty()

    fun limpar() = prefs.edit().clear().apply()
}

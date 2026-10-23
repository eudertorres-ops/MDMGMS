package br.com.controller.agent

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Coleta localizacao (via LocationManager, sem Play Services), bateria e rede. */
object LocationHelper {

    @SuppressLint("MissingPermission")
    fun ultimaLocalizacao(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provedores = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var melhor: Location? = null
        for (p in provedores) {
            try {
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (melhor == null || loc.time > melhor!!.time) melhor = loc
            } catch (_: Exception) {}
        }
        return melhor
    }

    fun nivelBateria(context: Context): Int? {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        } catch (_: Exception) { null }
    }

    fun carregando(context: Context): Boolean? {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.isCharging
        } catch (_: Exception) { null }
    }

    fun tipoRede(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return "offline"
            val caps = cm.getNetworkCapabilities(net) ?: return "offline"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "movel"
                else -> "offline"
            }
        } catch (_: Exception) { "offline" }
    }
}

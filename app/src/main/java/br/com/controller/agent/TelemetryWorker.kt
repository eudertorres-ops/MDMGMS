package br.com.controller.agent

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Backup periodico (minimo 15 min pelo WorkManager) do ciclo de reporte. */
class TelemetryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            ReportCycle.executar(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun agendarPeriodico(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<TelemetryWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.WORK_TELEMETRIA,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        /** Dispara um ciclo imediato (ex.: comando "localizar"). */
        fun reportarAgora(context: Context) {
            val req = OneTimeWorkRequestBuilder<TelemetryWorker>().build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}

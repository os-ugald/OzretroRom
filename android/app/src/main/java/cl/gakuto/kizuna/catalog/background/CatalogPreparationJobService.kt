package cl.gakuto.kizuna.catalog.background

import android.app.NotificationManager
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import androidx.annotation.RequiresApi
import cl.gakuto.kizuna.OzApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class CatalogPreparationJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val systemIds = params.extras.getStringArray(CatalogPreparationScheduler.EXTRA_SYSTEM_IDS)?.toList().orEmpty()
        if (systemIds.isEmpty()) return false

        val completeCatalog = params.extras.getBoolean(CatalogPreparationScheduler.EXTRA_COMPLETE_CATALOG, false)
        val store = CatalogPreparationTaskStore(this)
        val initial = store.get() ?: store.create(systemIds, completeCatalog)
        val running = store.markRunning() ?: initial
        if (!running.isActive) return false
        setNotification(
            params,
            CatalogPreparationNotification.NOTIFICATION_ID,
            CatalogPreparationNotification.progress(this, running),
            JobService.JOB_END_NOTIFICATION_POLICY_DETACH,
        )

        activeJob?.cancel()
        activeJob = scope.launch {
            val notificationManager = getSystemService(NotificationManager::class.java)
            try {
                val app = application as OzApplication
                val summary = CatalogPreparationManager(applicationContext, app.database).prepare(systemIds) { progress ->
                    store.updateProgress(progress, running.attempt)
                        ?.takeIf { it.isActive && it.attempt == running.attempt }
                        ?.let { updated ->
                        notificationManager?.notify(
                            CatalogPreparationNotification.NOTIFICATION_ID,
                            CatalogPreparationNotification.progress(this@CatalogPreparationJobService, updated),
                        )
                    }
                }
                val completed = store.markResult(summary, running.attempt) ?: running
                if (completed.attempt != running.attempt || completed.isActive) {
                    jobFinished(params, false)
                    return@launch
                }
                val notification = if (completed.status == CatalogPreparationStatus.FAILED) {
                    CatalogPreparationNotification.failed(this@CatalogPreparationJobService, completed)
                } else {
                    CatalogPreparationNotification.completed(this@CatalogPreparationJobService, completed)
                }
                notificationManager?.notify(CatalogPreparationNotification.NOTIFICATION_ID, notification)
                jobFinished(params, false)
            } catch (_: CancellationException) {
                // JobScheduler decides whether a system-stopped UIDT job must be rescheduled.
            } catch (error: Throwable) {
                val failed = store.markFailed(
                    error.message ?: error.javaClass.simpleName,
                    running.attempt,
                ) ?: running
                if (failed.attempt == running.attempt && failed.status == CatalogPreparationStatus.FAILED) {
                    notificationManager?.notify(
                        CatalogPreparationNotification.NOTIFICATION_ID,
                        CatalogPreparationNotification.failed(this@CatalogPreparationJobService, failed),
                    )
                }
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        activeJob?.cancel()
        activeJob = null
        val stopReason = params.stopReason
        val shouldRetry = catalogPreparationShouldRetry(stopReason)
        val suspended = CatalogPreparationTaskStore(this).markSuspended(
            stopReason = stopReason,
            message = catalogPreparationSuspendedMessage(stopReason, shouldRetry),
        )
        if (suspended?.status == CatalogPreparationStatus.SUSPENDED) {
            getSystemService(NotificationManager::class.java)?.notify(
                CatalogPreparationNotification.NOTIFICATION_ID,
                CatalogPreparationNotification.suspended(this, suspended),
            )
        }
        return shouldRetry && suspended?.status == CatalogPreparationStatus.SUSPENDED
    }

    override fun onDestroy() {
        activeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}

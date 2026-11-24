package cl.gakuto.kizuna.catalog.background

import android.app.NotificationManager
import android.app.Service
import android.app.job.JobParameters
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import cl.gakuto.kizuna.OzApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CatalogPreparationForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val systemIds = intent.getStringArrayExtra(CatalogPreparationScheduler.EXTRA_SYSTEM_IDS)?.toList().orEmpty()
        if (systemIds.isEmpty()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val completeCatalog = intent.getBooleanExtra(CatalogPreparationScheduler.EXTRA_COMPLETE_CATALOG, false)
        val store = CatalogPreparationTaskStore(this)
        val initial = store.get() ?: store.create(systemIds, completeCatalog)
        val running = store.markRunning() ?: initial
        if (!running.isActive) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            CatalogPreparationNotification.NOTIFICATION_ID,
            CatalogPreparationNotification.progress(this, running),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
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
                            CatalogPreparationNotification.progress(this@CatalogPreparationForegroundService, updated),
                        )
                    }
                }
                val completed = store.markResult(summary, running.attempt) ?: running
                if (completed.attempt != running.attempt || completed.isActive) return@launch
                val notification = if (completed.status == CatalogPreparationStatus.FAILED) {
                    CatalogPreparationNotification.failed(this@CatalogPreparationForegroundService, completed)
                } else {
                    CatalogPreparationNotification.completed(this@CatalogPreparationForegroundService, completed)
                }
                notificationManager?.notify(CatalogPreparationNotification.NOTIFICATION_ID, notification)
            } catch (_: CancellationException) {
                // The scheduler records explicit pauses before stopping this service.
            } catch (error: Throwable) {
                val failed = store.markFailed(
                    error.message ?: error.javaClass.simpleName,
                    running.attempt,
                ) ?: running
                if (failed.attempt == running.attempt && failed.status == CatalogPreparationStatus.FAILED) {
                    notificationManager?.notify(
                        CatalogPreparationNotification.NOTIFICATION_ID,
                        CatalogPreparationNotification.failed(this@CatalogPreparationForegroundService, failed),
                    )
                }
            } finally {
                if (store.get()?.attempt == running.attempt) {
                    ServiceCompat.stopForeground(
                        this@CatalogPreparationForegroundService,
                        ServiceCompat.STOP_FOREGROUND_DETACH,
                    )
                    stopSelf(startId)
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        activeJob?.cancel()
        val suspended = CatalogPreparationTaskStore(this).markSuspended(
            stopReason = JobParameters.STOP_REASON_TIMEOUT,
            message = catalogPreparationSuspendedMessage(
                JobParameters.STOP_REASON_TIMEOUT,
                willRetry = false,
            ),
        )
        if (suspended?.status == CatalogPreparationStatus.SUSPENDED) {
            getSystemService(NotificationManager::class.java)?.notify(
                CatalogPreparationNotification.NOTIFICATION_ID,
                CatalogPreparationNotification.suspended(this, suspended),
            )
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf(startId)
    }

    override fun onDestroy() {
        activeJob?.cancel()
        val suspended = CatalogPreparationTaskStore(this).markSuspended(
            stopReason = JobParameters.STOP_REASON_UNDEFINED,
            message = catalogPreparationSuspendedMessage(
                JobParameters.STOP_REASON_UNDEFINED,
                willRetry = false,
            ),
        )
        if (suspended?.status == CatalogPreparationStatus.SUSPENDED) {
            getSystemService(NotificationManager::class.java)?.notify(
                CatalogPreparationNotification.NOTIFICATION_ID,
                CatalogPreparationNotification.suspended(this, suspended),
            )
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "cl.gakuto.kizuna.action.START_CATALOG_PREPARATION"
    }
}

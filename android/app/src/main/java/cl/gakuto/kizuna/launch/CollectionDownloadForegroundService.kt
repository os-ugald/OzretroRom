package cl.gakuto.kizuna.launch

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.data.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CollectionDownloadForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeCollectionId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val collectionId = intent?.getStringExtra(CollectionDownloadScheduler.EXTRA_COLLECTION_ID) ?: return START_NOT_STICKY
        val name = intent.getStringExtra(CollectionDownloadScheduler.EXTRA_COLLECTION_NAME) ?: "Colección"
        val store = CollectionBatchTaskStore(this)
        val initial = store.get(collectionId) ?: store.create(collectionId, name, 0)
        val running = store.markRunning(collectionId) ?: initial
        activeCollectionId = collectionId
        ServiceCompat.startForeground(
            this,
            CollectionDownloadNotification.notificationId(collectionId),
            CollectionDownloadNotification.progress(this, running),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )

        activeJob?.cancel()
        activeJob = scope.launch {
            val notificationManager = getSystemService(NotificationManager::class.java)
            try {
                val app = application as OzApplication
                val result = CollectionDownloadManager(
                    context = applicationContext,
                    preferences = AppPreferences(applicationContext),
                    database = app.database,
                ).download(collectionId) { progress ->
                    val updated = store.updateProgress(collectionId, progress)
                    if (updated != null) notificationManager?.notify(
                        CollectionDownloadNotification.notificationId(collectionId),
                        CollectionDownloadNotification.progress(this@CollectionDownloadForegroundService, updated),
                    )
                }
                val completed = store.markResult(collectionId, result) ?: running
                notificationManager?.notify(
                    CollectionDownloadNotification.notificationId(collectionId),
                    CollectionDownloadNotification.completed(this@CollectionDownloadForegroundService, completed),
                )
            } catch (_: CancellationException) {
                // Keep resumable per-ROM staging if Android stops the foreground service.
            } catch (error: Throwable) {
                val failed = store.markFailed(collectionId, error.message ?: error.javaClass.simpleName) ?: running
                notificationManager?.notify(
                    CollectionDownloadNotification.notificationId(collectionId),
                    CollectionDownloadNotification.failed(this@CollectionDownloadForegroundService, failed),
                )
            } finally {
                ServiceCompat.stopForeground(this@CollectionDownloadForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        activeJob?.cancel()
        activeCollectionId?.let {
            CollectionBatchTaskStore(this).markFailed(
                it,
                "Android alcanzó el límite de dataSync. Abre Oz y vuelve a pulsar Descargar colección; las partes de ROM ya descargadas se reanudarán.",
            )
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        activeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "cl.gakuto.kizuna.action.START_COLLECTION_DOWNLOAD"
    }
}

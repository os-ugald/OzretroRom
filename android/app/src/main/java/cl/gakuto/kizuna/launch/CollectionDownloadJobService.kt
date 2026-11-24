package cl.gakuto.kizuna.launch

import android.app.NotificationManager
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import androidx.annotation.RequiresApi
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.data.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class CollectionDownloadJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val collectionId = params.extras.getString(CollectionDownloadScheduler.EXTRA_COLLECTION_ID) ?: return false
        val name = params.extras.getString(CollectionDownloadScheduler.EXTRA_COLLECTION_NAME) ?: "Colección"
        val store = CollectionBatchTaskStore(this)
        val initial = store.get(collectionId) ?: store.create(collectionId, name, 0)
        val running = store.markRunning(collectionId) ?: initial
        setNotification(
            params,
            CollectionDownloadNotification.notificationId(collectionId),
            CollectionDownloadNotification.progress(this, running),
            JobService.JOB_END_NOTIFICATION_POLICY_REMOVE,
        )

        activeJob?.cancel()
        activeJob = scope.launch {
            val manager = getSystemService(NotificationManager::class.java)
            try {
                val app = application as OzApplication
                val result = CollectionDownloadManager(
                    context = applicationContext,
                    preferences = AppPreferences(applicationContext),
                    database = app.database,
                    network = params.network,
                ).download(collectionId) { progress ->
                    val updated = store.updateProgress(collectionId, progress)
                    if (updated != null) manager?.notify(
                        CollectionDownloadNotification.notificationId(collectionId),
                        CollectionDownloadNotification.progress(this@CollectionDownloadJobService, updated),
                    )
                }
                val completed = store.markResult(collectionId, result) ?: running
                manager?.notify(
                    CollectionDownloadNotification.notificationId(collectionId),
                    CollectionDownloadNotification.completed(this@CollectionDownloadJobService, completed),
                )
                jobFinished(params, false)
            } catch (_: CancellationException) {
                // JobScheduler can restart the same UIDT job. Per-ROM staging remains resumable.
            } catch (error: Throwable) {
                val failed = store.markFailed(collectionId, error.message ?: error.javaClass.simpleName) ?: running
                manager?.notify(
                    CollectionDownloadNotification.notificationId(collectionId),
                    CollectionDownloadNotification.failed(this@CollectionDownloadJobService, failed),
                )
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        activeJob?.cancel()
        activeJob = null
        val collectionId = params.extras.getString(CollectionDownloadScheduler.EXTRA_COLLECTION_ID)
        return collectionId?.let { CollectionBatchTaskStore(this).get(it)?.isActive } == true
    }

    override fun onDestroy() {
        activeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}

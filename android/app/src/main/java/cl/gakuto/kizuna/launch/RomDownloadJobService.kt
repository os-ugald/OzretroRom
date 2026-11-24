package cl.gakuto.kizuna.launch

import android.app.NotificationManager
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class RomDownloadJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeTaskId: String? = null
    private var activeEngine: BackgroundRomDownloadEngine? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val taskId = params.extras.getString(RomDownloadScheduler.EXTRA_TASK_ID) ?: return false
        val store = RomDownloadTaskStore(this)
        val snapshot = store.findByTaskId(taskId) ?: return false
        val initialTitle = snapshot.fileName.ifBlank { "ROM" }

        val initial = store.markRunning(snapshot.gameId) ?: snapshot
        setNotification(
            params,
            RomDownloadNotification.progressId(initial),
            RomDownloadNotification.progress(this, initial, initialTitle),
            JobService.JOB_END_NOTIFICATION_POLICY_REMOVE,
        )
        activeTaskId = taskId
        activeJob?.cancel()
        activeJob = scope.launch {
            val manager = getSystemService(NotificationManager::class.java)
            try {
                val engine = BackgroundRomDownloadEngine(applicationContext, params.network)
                activeEngine = engine
                val result = engine.run(initial) { progress ->
                    val updated = store.updateProgress(initial.gameId, progress)
                    if (updated != null) {
                        manager?.notify(
                            RomDownloadNotification.progressId(updated),
                            RomDownloadNotification.progress(this@RomDownloadJobService, updated, resultTitle(initialTitle, updated)),
                        )
                    }
                }
                val completed = store.markSuccess(
                    initial.gameId,
                    result.stored.uri.toString(),
                    result.stored.displayName,
                    result.stored.bytes,
                ) ?: initial
                manager?.notify(
                    RomDownloadNotification.resultId(completed),
                    RomDownloadNotification.completed(this@RomDownloadJobService, completed, result.game.title),
                )
                jobFinished(params, false)
            } catch (_: CancellationException) {
                // JobScheduler may stop and later restart the user-initiated transfer. The partial
                // file and task state stay intact so HTTP Range can resume from the last byte.
            } catch (error: Throwable) {
                val failed = store.markFailed(initial.gameId, error.message ?: error.javaClass.simpleName) ?: initial
                manager?.notify(
                    RomDownloadNotification.resultId(failed),
                    RomDownloadNotification.failed(this@RomDownloadJobService, failed, initialTitle),
                )
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onNetworkChanged(params: JobParameters) {
        activeEngine?.updateNetwork(params.network)
    }

    override fun onStopJob(params: JobParameters): Boolean {
        activeJob?.cancel()
        activeEngine = null
        activeJob = null
        val taskId = params.extras.getString(RomDownloadScheduler.EXTRA_TASK_ID)
        val snapshot = taskId?.let { RomDownloadTaskStore(this).findByTaskId(it) }
        return snapshot?.isActive == true
    }

    override fun onDestroy() {
        activeJob?.cancel()
        activeEngine = null
        scope.cancel()
        super.onDestroy()
    }

    private fun resultTitle(fallback: String, snapshot: RomDownloadTaskSnapshot): String =
        snapshot.storedDisplayName?.takeIf { it.isNotBlank() } ?: fallback
}

package cl.gakuto.kizuna.launch

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RomDownloadForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeGameId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(RomDownloadScheduler.EXTRA_TASK_ID)
        if (intent?.action == ACTION_CANCEL) {
            activeJob?.cancel()
            activeGameId?.let { RomDownloadTaskStore(this).markCancelled(it) }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (taskId.isNullOrBlank()) return START_NOT_STICKY
        val store = RomDownloadTaskStore(this)
        val snapshot = store.findByTaskId(taskId) ?: return START_NOT_STICKY
        val gameTitle = snapshot.fileName.ifBlank { "ROM" }
        val initial = store.markRunning(snapshot.gameId) ?: snapshot
        RomDownloadNotification.ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            RomDownloadNotification.progressId(initial),
            RomDownloadNotification.progress(this, initial, gameTitle),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        activeGameId = initial.gameId
        activeJob?.cancel()
        activeJob = scope.launch {
            val manager = getSystemService(NotificationManager::class.java)
            try {
                val result = BackgroundRomDownloadEngine(applicationContext).run(initial) { progress ->
                    val updated = store.updateProgress(initial.gameId, progress)
                    if (updated != null) {
                        manager?.notify(
                            RomDownloadNotification.progressId(updated),
                            RomDownloadNotification.progress(this@RomDownloadForegroundService, updated, gameTitle),
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
                    RomDownloadNotification.completed(this@RomDownloadForegroundService, completed, result.game.title),
                )
            } catch (_: CancellationException) {
                // User cancellation or service shutdown. Keep partial data unless explicitly deleted.
            } catch (error: Throwable) {
                val failed = store.markFailed(initial.gameId, error.message ?: error.javaClass.simpleName) ?: initial
                manager?.notify(
                    RomDownloadNotification.resultId(failed),
                    RomDownloadNotification.failed(this@RomDownloadForegroundService, failed, gameTitle),
                )
            } finally {
                ServiceCompat.stopForeground(this@RomDownloadForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        activeJob?.cancel()
        activeGameId?.let {
            RomDownloadTaskStore(this).markFailed(
                it,
                "Android alcanzó el límite de ejecución en segundo plano. Abre Oz y reintenta; la parte descargada se conserva para reanudar.",
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
        const val ACTION_START = "cl.gakuto.kizuna.action.START_ROM_DOWNLOAD"
        const val ACTION_CANCEL = "cl.gakuto.kizuna.action.CANCEL_ROM_DOWNLOAD_SERVICE"
    }
}

package cl.gakuto.kizuna.launch

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.ContextCompat
import cl.gakuto.kizuna.data.AuthorizedSource
import cl.gakuto.kizuna.data.GameEntity

object RomDownloadScheduler {
    data class ScheduleResult(
        val snapshot: RomDownloadTaskSnapshot,
        val mode: Mode,
    )

    enum class Mode { USER_INITIATED_JOB, FOREGROUND_SERVICE }

    fun schedule(
        context: Context,
        game: GameEntity,
        source: AuthorizedSource,
        biosSources: List<Pair<String, AuthorizedSource>>,
        resolvedRegion: String?,
        externalLauncher: Boolean = false,
        downloadSet: RomDownloadSet? = null,
        sourcePlan: DownloadSourcePlan? = null,
    ): ScheduleResult {
        val appContext = context.applicationContext
        val store = RomDownloadTaskStore(appContext)
        val snapshot = store.createOrReuse(
            game.id,
            source,
            biosSources,
            resolvedRegion,
            externalLauncher = externalLauncher,
            downloadSet = downloadSet,
            sourcePlan = sourcePlan,
        )
        RomDownloadNotification.ensureChannel(appContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val scheduler = appContext.getSystemService(JobScheduler::class.java)
            val extras = PersistableBundle().apply { putString(EXTRA_TASK_ID, snapshot.taskId) }
            val builder = JobInfo.Builder(jobId(snapshot), ComponentName(appContext, RomDownloadJobService::class.java))
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras)
            val expectedBytes = snapshot.downloadSet?.totalSize ?: snapshot.source.expectedSize
            if (expectedBytes > 0L) {
                val remainingBytes = (expectedBytes - store.partialBytes(snapshot)).coerceAtLeast(0L)
                builder.setEstimatedNetworkBytes(remainingBytes, 0L)
                if (remainingBytes > 0L) builder.setMinimumNetworkChunkBytes(MIN_RESUMABLE_CHUNK_BYTES.coerceAtMost(remainingBytes))
            }
            val result = scheduler?.schedule(builder.build()) ?: JobScheduler.RESULT_FAILURE
            if (result == JobScheduler.RESULT_SUCCESS) {
                return ScheduleResult(snapshot, Mode.USER_INITIATED_JOB)
            }
        }

        val intent = Intent(appContext, RomDownloadForegroundService::class.java)
            .setAction(RomDownloadForegroundService.ACTION_START)
            .putExtra(EXTRA_TASK_ID, snapshot.taskId)
        ContextCompat.startForegroundService(appContext, intent)
        return ScheduleResult(snapshot, Mode.FOREGROUND_SERVICE)
    }

    fun cancel(context: Context, gameId: String, keepPartial: Boolean) {
        val appContext = context.applicationContext
        val store = RomDownloadTaskStore(appContext)
        val snapshot = store.get(gameId) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            appContext.getSystemService(JobScheduler::class.java)?.cancel(jobId(snapshot))
        }
        appContext.stopService(Intent(appContext, RomDownloadForegroundService::class.java))
        store.markCancelled(gameId)
        if (!keepPartial) store.deletePartial(gameId)
    }

    fun jobId(snapshot: RomDownloadTaskSnapshot): Int = JOB_ID_BASE + ((snapshot.taskId.hashCode() and Int.MAX_VALUE) % JOB_ID_RANGE)

    const val EXTRA_TASK_ID = "oz.download.taskId"
    private const val JOB_ID_BASE = 52_000
    private const val JOB_ID_RANGE = 8_000
    private const val MIN_RESUMABLE_CHUNK_BYTES = 256L * 1024L
}

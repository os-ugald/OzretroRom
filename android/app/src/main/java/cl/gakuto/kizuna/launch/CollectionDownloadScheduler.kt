package cl.gakuto.kizuna.launch

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.ContextCompat

object CollectionDownloadScheduler {
    enum class Mode { USER_INITIATED_JOB, FOREGROUND_SERVICE }
    data class ScheduleResult(val snapshot: CollectionBatchSnapshot, val mode: Mode)

    fun schedule(context: Context, collectionId: String, collectionName: String, totalHint: Int): ScheduleResult {
        val appContext = context.applicationContext
        val store = CollectionBatchTaskStore(appContext)
        val snapshot = store.create(collectionId, collectionName, totalHint)
        if (snapshot.status == CollectionBatchStatus.RUNNING) {
            return ScheduleResult(snapshot, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Mode.USER_INITIATED_JOB else Mode.FOREGROUND_SERVICE)
        }
        RomDownloadNotification.ensureChannel(appContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val extras = PersistableBundle().apply {
                putString(EXTRA_COLLECTION_ID, collectionId)
                putString(EXTRA_COLLECTION_NAME, collectionName)
            }
            val builder = JobInfo.Builder(jobId(collectionId), ComponentName(appContext, CollectionDownloadJobService::class.java))
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras)
            val scheduler = appContext.getSystemService(JobScheduler::class.java)
            if (scheduler?.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS) {
                return ScheduleResult(snapshot, Mode.USER_INITIATED_JOB)
            }
        }

        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, CollectionDownloadForegroundService::class.java)
                .setAction(CollectionDownloadForegroundService.ACTION_START)
                .putExtra(EXTRA_COLLECTION_ID, collectionId)
                .putExtra(EXTRA_COLLECTION_NAME, collectionName),
        )
        return ScheduleResult(snapshot, Mode.FOREGROUND_SERVICE)
    }

    fun jobId(collectionId: String): Int = JOB_ID_BASE + ((collectionId.hashCode() and Int.MAX_VALUE) % JOB_ID_RANGE)

    const val EXTRA_COLLECTION_ID = "oz.batch.collectionId"
    const val EXTRA_COLLECTION_NAME = "oz.batch.collectionName"
    private const val JOB_ID_BASE = 64_000
    private const val JOB_ID_RANGE = 4_000
}

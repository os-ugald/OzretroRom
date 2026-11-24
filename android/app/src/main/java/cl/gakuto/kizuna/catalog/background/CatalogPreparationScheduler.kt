package cl.gakuto.kizuna.catalog.background

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.ContextCompat

object CatalogPreparationScheduler {
    enum class Mode { USER_INITIATED_JOB, FOREGROUND_SERVICE }

    data class ScheduleResult(
        val snapshot: CatalogPreparationSnapshot,
        val mode: Mode,
    )

    fun schedule(
        context: Context,
        systemIds: Collection<String>,
        completeCatalog: Boolean,
    ): ScheduleResult {
        val targets = systemIds.distinct()
        require(targets.isNotEmpty()) { "No hay sistemas para preparar." }

        val appContext = context.applicationContext
        val store = CatalogPreparationTaskStore(appContext)
        store.get()?.takeIf { it.isActive }?.let { active ->
            return ScheduleResult(active, preferredMode())
        }

        val created = store.create(targets, completeCatalog)
        val snapshot = store.markQueued() ?: created
        try {
            CatalogPreparationNotification.ensureChannel(appContext)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val extras = PersistableBundle().apply {
                    putStringArray(EXTRA_SYSTEM_IDS, targets.toTypedArray())
                    putBoolean(EXTRA_COMPLETE_CATALOG, completeCatalog)
                }
                val job = JobInfo.Builder(
                    JOB_ID,
                    ComponentName(appContext, CatalogPreparationJobService::class.java),
                )
                    .setUserInitiated(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setExtras(extras)
                    .build()
                val scheduler = appContext.getSystemService(JobScheduler::class.java)
                val result = runCatching { scheduler?.schedule(job) }.getOrNull()
                if (result == JobScheduler.RESULT_SUCCESS) {
                    return ScheduleResult(snapshot, Mode.USER_INITIATED_JOB)
                }
            }

            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, CatalogPreparationForegroundService::class.java)
                    .setAction(CatalogPreparationForegroundService.ACTION_START)
                    .putExtra(EXTRA_SYSTEM_IDS, targets.toTypedArray())
                    .putExtra(EXTRA_COMPLETE_CATALOG, completeCatalog),
            )
            return ScheduleResult(snapshot, Mode.FOREGROUND_SERVICE)
        } catch (error: Throwable) {
            store.markFailed(error.message ?: error.javaClass.simpleName)
            throw error
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        CatalogPreparationTaskStore(appContext).markCancelled()
        appContext.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        appContext.stopService(Intent(appContext, CatalogPreparationForegroundService::class.java))
        CatalogPreparationNotification.cancel(appContext)
    }

    private fun preferredMode(): Mode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Mode.USER_INITIATED_JOB
        } else {
            Mode.FOREGROUND_SERVICE
        }

    const val EXTRA_SYSTEM_IDS = "oz.catalog.systemIds"
    const val EXTRA_COMPLETE_CATALOG = "oz.catalog.complete"
    private const val JOB_ID = 70_100
}

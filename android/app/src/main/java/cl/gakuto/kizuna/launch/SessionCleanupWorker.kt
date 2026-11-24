package cl.gakuto.kizuna.launch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork() = withContext(Dispatchers.IO) {
        val root=applicationContext.getExternalFilesDir(null)?.resolve("sessions") ?: return@withContext Result.success()
        val cutoff=System.currentTimeMillis()-24*60*60*1000L
        root.listFiles()?.filter { it.lastModified()<cutoff }?.forEach { it.deleteRecursively() }
        Result.success()
    }
}

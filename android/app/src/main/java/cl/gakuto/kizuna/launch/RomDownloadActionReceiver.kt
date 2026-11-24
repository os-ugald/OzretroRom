package cl.gakuto.kizuna.launch

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RomDownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val gameId = intent.getStringExtra(EXTRA_GAME_ID)?.takeIf { it.isNotBlank() } ?: return
        val store = RomDownloadTaskStore(context)
        val snapshot = store.get(gameId)
        when (intent.action) {
            ACTION_CANCEL -> RomDownloadScheduler.cancel(context, gameId, keepPartial = true)
            ACTION_DELETE_PARTIAL -> RomDownloadScheduler.cancel(context, gameId, keepPartial = false)
            else -> return
        }
        snapshot?.let {
            context.getSystemService(NotificationManager::class.java)?.apply {
                cancel(RomDownloadNotification.progressId(it))
                cancel(RomDownloadNotification.resultId(it))
            }
        }
    }

    companion object {
        const val ACTION_CANCEL = "cl.gakuto.kizuna.action.CANCEL_ROM_DOWNLOAD"
        const val ACTION_DELETE_PARTIAL = "cl.gakuto.kizuna.action.DELETE_PARTIAL_ROM_DOWNLOAD"
        const val EXTRA_GAME_ID = "oz.download.gameId"
    }
}

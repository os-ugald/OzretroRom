package cl.gakuto.kizuna.catalog.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CatalogPreparationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_PAUSE) CatalogPreparationScheduler.cancel(context)
    }

    companion object {
        const val ACTION_PAUSE = "cl.gakuto.kizuna.action.PAUSE_CATALOG_PREPARATION"
    }
}

package cl.gakuto.kizuna.launch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.ui.MainActivity
import kotlin.math.roundToInt

object RomDownloadNotification {
    const val CHANNEL_ID = "rom_downloads"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Descargas de ROM",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progreso de las descargas iniciadas desde Oz RetroRom"
                setShowBadge(false)
            },
        )
    }

    fun progress(context: Context, snapshot: RomDownloadTaskSnapshot, gameTitle: String): Notification {
        ensureChannel(context)
        val percent = percentage(snapshot.downloaded, snapshot.total)
        val text = buildString {
            append(snapshot.stage.ifBlank { "Descargando ROM…" })
            if (snapshot.downloaded > 0L) {
                append(" · ").append(formatBytes(snapshot.downloaded))
                if (snapshot.total > 0L) append(" / ").append(formatBytes(snapshot.total))
            }
            if (snapshot.bytesPerSecond > 0L) append(" · ").append(formatBytes(snapshot.bytesPerSecond)).append("/s")
        }
        return base(context, gameTitle)
            .setContentText(text)
            .setSubText("Oz RetroRom · descarga en segundo plano")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, percent ?: 0, percent == null)
            .setContentIntent(openSessionIntent(context, snapshot, requestCode(snapshot, 1), autoLaunch = false))
            .addAction(
                0,
                "Cancelar",
                actionIntent(context, RomDownloadActionReceiver.ACTION_CANCEL, snapshot.gameId, requestCode(snapshot, 2)),
            )
            .build()
    }

    fun completed(context: Context, snapshot: RomDownloadTaskSnapshot, gameTitle: String): Notification {
        ensureChannel(context)
        return base(context, "Descarga completa · $gameTitle")
            .setContentText("La ROM está guardada en tu biblioteca. ¿Quieres jugar ahora?")
            .setSubText("Oz RetroRom")
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setProgress(0, 0, false)
            .setContentIntent(openSessionIntent(context, snapshot, requestCode(snapshot, 3), autoLaunch = false))
            .addAction(0, "Jugar ahora", openSessionIntent(context, snapshot, requestCode(snapshot, 4), autoLaunch = true))
            .addAction(0, "Abrir Oz", openMainIntent(context, requestCode(snapshot, 5)))
            .build()
    }

    fun failed(context: Context, snapshot: RomDownloadTaskSnapshot, gameTitle: String): Notification {
        ensureChannel(context)
        val partial = RomDownloadTaskStore(context).partialBytes(snapshot)
        val text = buildString {
            append(snapshot.error ?: "La descarga se interrumpió.")
            if (partial > 0L) append(" · ").append(formatBytes(partial)).append(" conservados para reanudar")
        }
        return base(context, "Descarga interrumpida · $gameTitle")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText("Oz RetroRom")
            .setAutoCancel(false)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setProgress(0, 0, false)
            .setContentIntent(openSessionIntent(context, snapshot, requestCode(snapshot, 6), autoLaunch = false))
            .addAction(0, "Reintentar", openSessionIntent(context, snapshot, requestCode(snapshot, 7), autoLaunch = false))
            .addAction(
                0,
                "Borrar parcial",
                actionIntent(context, RomDownloadActionReceiver.ACTION_DELETE_PARTIAL, snapshot.gameId, requestCode(snapshot, 8)),
            )
            .build()
    }

    fun progressId(snapshot: RomDownloadTaskSnapshot): Int = 31_000 + positiveHash(snapshot.gameId) % 8_000
    fun resultId(snapshot: RomDownloadTaskSnapshot): Int = 41_000 + positiveHash(snapshot.gameId) % 8_000

    private fun base(context: Context, title: String): NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_download)
        .setContentTitle(title)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun openSessionIntent(context: Context, snapshot: RomDownloadTaskSnapshot, requestCode: Int, autoLaunch: Boolean): PendingIntent {
        val intent = Intent(context, SessionActivity::class.java).apply {
            putExtra(SessionActivity.EXTRA_GAME_ID, snapshot.gameId)
            putExtra(SessionActivity.EXTRA_BACKGROUND_DOWNLOAD, autoLaunch)
            putExtra(SessionActivity.EXTRA_EXTERNAL_LAUNCHER, snapshot.externalLauncher)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openMainIntent(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(context: Context, action: String, gameId: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, RomDownloadActionReceiver::class.java).apply {
                this.action = action
                putExtra(RomDownloadActionReceiver.EXTRA_GAME_ID, gameId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun requestCode(snapshot: RomDownloadTaskSnapshot, salt: Int): Int =
        positiveHash("${snapshot.taskId}:$salt")

    private fun positiveHash(value: String): Int = value.hashCode() and Int.MAX_VALUE

    private fun percentage(downloaded: Long, total: Long): Int? {
        if (total <= 0L) return null
        return ((downloaded.coerceIn(0L, total).toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

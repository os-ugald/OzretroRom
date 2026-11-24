package cl.gakuto.kizuna.launch

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.ui.MainActivity
import kotlin.math.roundToInt

object CollectionDownloadNotification {
    private const val GROUP = "oz_collection_batch_downloads"

    fun progress(context: Context, snapshot: CollectionBatchSnapshot): Notification {
        RomDownloadNotification.ensureChannel(context)
        val current = buildString {
            if (snapshot.total > 0) append("${snapshot.completed}/${snapshot.total}")
            if (snapshot.gameTitle.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(snapshot.gameTitle)
            }
            if (snapshot.currentDownloaded > 0L) {
                append(" · ").append(formatBytes(snapshot.currentDownloaded))
                if (snapshot.currentTotal > 0L) append(" / ").append(formatBytes(snapshot.currentTotal))
            }
        }
        return base(context, "Descargando colección · ${snapshot.collectionName}")
            .setContentText(snapshot.stage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(snapshot.stage, current).filter { it.isNotBlank() }.joinToString("\n")))
            .setSubText("Oz RetroRom · batch download")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, percent(snapshot) ?: 0, percent(snapshot) == null)
            .setContentIntent(openMain(context, snapshot.collectionId.hashCode()))
            .build()
    }

    fun completed(context: Context, snapshot: CollectionBatchSnapshot): Notification {
        RomDownloadNotification.ensureChannel(context)
        val text = buildString {
            append("${snapshot.downloadedGames} descargados")
            if (snapshot.alreadyAvailable > 0) append(" · ${snapshot.alreadyAvailable} ya locales")
            if (snapshot.failedCount > 0) append(" · ${snapshot.failedCount} fallos")
            if (snapshot.needsConfirmationCount > 0) append(" · ${snapshot.needsConfirmationCount} por revisar")
        }
        return base(context, "Colección finalizada · ${snapshot.collectionName}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText("Oz RetroRom")
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setProgress(0, 0, false)
            .setContentIntent(openMain(context, snapshot.collectionId.hashCode()))
            .build()
    }

    fun failed(context: Context, snapshot: CollectionBatchSnapshot): Notification {
        RomDownloadNotification.ensureChannel(context)
        val text = snapshot.error ?: snapshot.stage
        return base(context, "Colección interrumpida · ${snapshot.collectionName}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText("Oz RetroRom")
            .setAutoCancel(false)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(openMain(context, snapshot.collectionId.hashCode()))
            .build()
    }

    fun notificationId(collectionId: String): Int = 61_000 + ((collectionId.hashCode() and Int.MAX_VALUE) % 3_000)

    private fun base(context: Context, title: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, RomDownloadNotification.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(GROUP)

    private fun openMain(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode and Int.MAX_VALUE,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun percent(snapshot: CollectionBatchSnapshot): Int? {
        if (snapshot.total <= 0) return null
        val base = snapshot.completed.coerceIn(0, snapshot.total).toDouble()
        val fraction = if (snapshot.currentTotal > 0L && snapshot.currentDownloaded > 0L) {
            (snapshot.currentDownloaded.coerceAtMost(snapshot.currentTotal).toDouble() / snapshot.currentTotal.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0
        return (((base + fraction) / snapshot.total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

package cl.gakuto.kizuna.catalog.background

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

object CatalogPreparationNotification {
    const val NOTIFICATION_ID = 67_100
    private const val CHANNEL_ID = "catalog_preparation"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Preparación del catálogo",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progreso de la preparación del catálogo global de Oz RetroRom"
                setShowBadge(false)
            },
        )
    }

    fun progress(context: Context, snapshot: CatalogPreparationSnapshot): Notification {
        ensureChannel(context)
        val detail = buildString {
            append(snapshot.stage)
            if (snapshot.currentSystem.isNotBlank() && !snapshot.stage.contains(snapshot.currentSystem)) {
                append(" · ").append(snapshot.currentSystem)
            }
        }
        val elapsed = formatCatalogPreparationElapsed(
            catalogPreparationElapsedMillis(snapshot, System.currentTimeMillis()),
        )
        val prepared = snapshot.currentPreparedGames.takeIf { it > 0 }
            ?.let { " · $it juegos en el sistema actual" }
            .orEmpty()
        return base(context, "Preparando catálogo de Oz")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setSubText("${snapshot.processedSystems}/${snapshot.totalTargets} sistemas$prepared · $elapsed")
            .setWhen(snapshot.startedAt)
            .setUsesChronometer(snapshot.startedAt > 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            // El tamaño de los DAT no se conoce antes de descargarlos. Una barra determinada
            // por sistema aparentaba quedar congelada durante índices grandes aunque hubiera
            // escrituras activas; el contador exacto permanece visible en el subtítulo.
            .setProgress(0, 0, true)
            .setContentIntent(openMain(context))
            .addAction(0, "Pausar", pauseIntent(context))
            .build()
    }

    fun suspended(context: Context, snapshot: CatalogPreparationSnapshot): Notification {
        ensureChannel(context)
        val elapsed = formatCatalogPreparationElapsed(
            catalogPreparationElapsedMillis(snapshot, System.currentTimeMillis()),
        )
        val text = "${snapshot.stage} · ${snapshot.processedSystems}/${snapshot.totalTargets} sistemas · Tiempo $elapsed"
        return base(context, "Preparación de catálogo suspendida")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(false)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setProgress(0, 0, false)
            .setContentIntent(openMain(context))
            .build()
    }

    fun completed(context: Context, snapshot: CatalogPreparationSnapshot): Notification {
        ensureChannel(context)
        val elapsed = formatCatalogPreparationElapsed(
            catalogPreparationElapsedMillis(snapshot, System.currentTimeMillis()),
        )
        val text = buildString {
            append(snapshot.stage)
            if (snapshot.failedCount > 0) append(" · ${snapshot.failedCount} fallos")
            append(" · Tiempo $elapsed")
        }
        return base(context, "Catálogo de Oz actualizado")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setProgress(0, 0, false)
            .setContentIntent(openMain(context))
            .build()
    }

    fun failed(context: Context, snapshot: CatalogPreparationSnapshot): Notification {
        ensureChannel(context)
        val elapsed = formatCatalogPreparationElapsed(
            catalogPreparationElapsedMillis(snapshot, System.currentTimeMillis()),
        )
        val text = "${snapshot.error ?: snapshot.stage} · Tiempo $elapsed"
        return base(context, "Catálogo pendiente")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(false)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setProgress(0, 0, false)
            .setContentIntent(openMain(context))
            .build()
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun base(context: Context, title: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun openMain(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pauseIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID + 1,
        Intent(context, CatalogPreparationActionReceiver::class.java).setAction(
            CatalogPreparationActionReceiver.ACTION_PAUSE,
        ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

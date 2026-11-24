package cl.gakuto.kizuna.launch

import java.util.Locale

/**
 * Convierte métricas técnicas de descarga en textos breves para la pantalla de sesión.
 * No depende de Android para que pueda probarse con tests JVM.
 */
object SessionProgressFormatter {
    data class UiState(
        val percentage: Int?,
        val percentageText: String,
        val elapsedText: String,
        val transferredText: String,
        val speedText: String,
        val peersText: String,
        val etaText: String,
        val helperText: String,
    )

    fun build(progress: AuthorizedSourceDownloader.Progress): UiState {
        val percentage = percentage(progress.downloaded, progress.total)
        return UiState(
            percentage = percentage,
            percentageText = percentage?.let { "$it%" } ?: "Calculando…",
            elapsedText = formatDurationMillis(progress.elapsedMillis),
            transferredText = when {
                progress.total > 0L -> "${formatBytes(progress.downloaded)} de ${formatBytes(progress.total)}"
                progress.downloaded > 0L -> formatBytes(progress.downloaded)
                else -> "Tamaño pendiente"
            },
            speedText = if (progress.bytesPerSecond > 0L) "${formatBytes(progress.bytesPerSecond)}/s" else "Esperando datos",
            peersText = when (progress.peers) {
                0 -> "Sin pares conectados"
                1 -> "1 par conectado"
                else -> "${progress.peers} pares conectados"
            },
            etaText = when {
                progress.etaSeconds == 0L -> "Completado"
                progress.etaSeconds != null -> formatEta(progress.etaSeconds)
                progress.total <= 0L -> "Disponible al conocer el tamaño"
                progress.bytesPerSecond <= 0L -> "Calculando…"
                else -> "Calculando…"
            },
            helperText = helperFor(progress),
        )
    }

    fun percentage(downloaded: Long, total: Long): Int? {
        if (total <= 0L) return null
        return ((downloaded.coerceIn(0L, total) * 100L) / total).toInt().coerceIn(0, 100)
    }

    fun formatDurationMillis(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatEta(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        return when {
            safe < 60L -> "menos de 1 min"
            safe < 3_600L -> "${(safe + 59L) / 60L} min aprox."
            else -> {
                val hours = safe / 3_600L
                val minutes = (safe % 3_600L + 59L) / 60L
                "${hours} h ${minutes} min aprox."
            }
        }
    }

    fun formatBytes(value: Long): String = when {
        value >= 1024L * 1024L * 1024L -> String.format(Locale.ROOT, "%.2f GB", value / (1024.0 * 1024.0 * 1024.0))
        value >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", value / (1024.0 * 1024.0))
        value >= 1024L -> String.format(Locale.ROOT, "%.0f KB", value / 1024.0)
        value > 0L -> "$value B"
        else -> "0 B"
    }

    private fun helperFor(progress: AuthorizedSourceDownloader.Progress): String = when (progress.phase) {
        AuthorizedSourceDownloader.Phase.CONNECTING ->
            "Inicializando la red y contactando fuentes disponibles."
        AuthorizedSourceDownloader.Phase.METADATA ->
            "El torrent todavía no informa el tamaño del juego. El porcentaje aparecerá cuando lleguen los metadatos."
        AuthorizedSourceDownloader.Phase.SELECTING_FILE ->
            "Los metadatos llegaron. Oz está identificando el archivo exacto que corresponde al juego."
        AuthorizedSourceDownloader.Phase.DOWNLOADING ->
            "La ROM se descarga de forma selectiva; los demás archivos del torrent se ignoran."
        AuthorizedSourceDownloader.Phase.VERIFYING ->
            "La descarga terminó y Oz está comprobando que el archivo esté completo."
        AuthorizedSourceDownloader.Phase.COPYING ->
            "Preparando una copia temporal compatible con el emulador."
        AuthorizedSourceDownloader.Phase.READY ->
            "El archivo está listo para abrirse en el emulador configurado."
        AuthorizedSourceDownloader.Phase.PREPARING ->
            "Preparando la sesión de juego."
    }
}

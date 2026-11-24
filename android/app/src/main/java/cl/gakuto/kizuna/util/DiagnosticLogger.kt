package cl.gakuto.kizuna.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import cl.gakuto.kizuna.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Small persistent diagnostic logger designed for field testing on Android.
 *
 * Logs live in the app-private files directory and can only be exported through the
 * explicit Android share sheet. It deliberately avoids writing ROM contents, cookies,
 * authorization headers or complete magnet/HTTP URLs.
 */
object DiagnosticLogger {
    private const val MAX_LOGS = 12
    private const val MAX_LOG_BYTES = 2L * 1024L * 1024L
    private val fileLock = Any()

    class Session internal constructor(
        private val context: Context,
        val file: File,
    ) {
        fun info(stage: String, detail: String = "") = write("INFO", stage, detail)
        fun warn(stage: String, detail: String = "") = write("WARN", stage, detail)

        fun error(stage: String, throwable: Throwable) {
            val summary = buildString {
                append(throwable.javaClass.name)
                throwable.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                append('\n')
                append(throwable.stackTraceToString())
            }
            write("ERROR", stage, summary)
        }

        fun error(stage: String, detail: String) = write("ERROR", stage, detail)

        private fun write(level: String, stage: String, detail: String) {
            val safeStage = sanitize(stage)
            val safeDetail = sanitize(detail)
            val line = "${timestamp()} | $level | ${Thread.currentThread().name} | $safeStage" +
                if (safeDetail.isBlank()) "\n" else " | $safeDetail\n"
            synchronized(fileLock) {
                runCatching {
                    if (file.length() > MAX_LOG_BYTES) {
                        file.appendText("${timestamp()} | WARN | logger | LOG_TRUNCATED | Maximum size reached\n")
                        return
                    }
                    file.appendText(line)
                }
            }
        }
    }

    fun startSession(context: Context, sessionId: String): Session {
        val directory = logsDirectory(context)
        prune(directory)
        val safeId = sessionId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
        val file = File(directory, "session-${fileTimestamp()}-$safeId.log")
        val session = Session(context.applicationContext, file)
        synchronized(fileLock) {
            file.writeText(
                buildString {
                    appendLine("Oz RetroRom diagnostic log")
                    appendLine("app_version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
                    appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("abi=${Build.SUPPORTED_ABIS.joinToString()}")
                    appendLine("session_id=${sanitize(sessionId)}")
                    appendLine("started_utc=${timestamp()}")
                    appendLine("privacy=No ROM contents, credentials, cookies or complete source URLs are recorded.")
                    appendLine("---")
                },
            )
        }
        session.info("SESSION_CREATED")
        return session
    }

    fun latest(context: Context): File? = logsDirectory(context)
        .listFiles { file -> file.isFile && file.extension.equals("log", ignoreCase = true) }
        ?.maxByOrNull { it.lastModified() }

    fun share(context: Context, file: File, chooserTitle: String = "Compartir diagnóstico") {
        require(file.isFile) { "No existe un registro para compartir." }
        val shareDirectory = File(context.cacheDir, "diagnostic-share").apply { mkdirs() }
        val exported = File(shareDirectory, file.name)
        file.copyTo(exported, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", exported)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Oz RetroRom diagnostic ${file.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newRawUri("diagnostic", uri)
                },
                chooserTitle,
            ),
        )
    }

    fun shareLatest(context: Context) {
        val file = latest(context) ?: error("Todavía no existe ningún registro de sesión.")
        share(context, file)
    }

    private fun logsDirectory(context: Context): File =
        File(context.filesDir, "diagnostics").apply { mkdirs() }

    private fun prune(directory: File) {
        val logs = directory.listFiles { file -> file.isFile && file.extension == "log" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        logs.drop(MAX_LOGS - 1).forEach { runCatching { it.delete() } }
    }

    private fun sanitize(value: String): String {
        var result = value.replace('\r', ' ').trim()
        val sensitive = Regex(
            "(?i)(token|api[_-]?key|authorization|auth|cookie|password|signature|sig)=([^&\\s]+)",
        )
        result = result.replace(sensitive) { match -> "${match.groupValues[1]}=<redacted>" }
        result = result.replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*"), "Bearer <redacted>")
        // Avoid dumping complete magnets, URLs and personal storage paths.
        if (result.contains("magnet:?", ignoreCase = true)) {
            result = result.replace(Regex("""(?i)magnet:\?[^\s]+"""), "magnet:<redacted>")
        }
        result = result.replace(Regex("""(?i)https?://[^\s]+""")) { match ->
            runCatching {
                val uri = java.net.URI(match.value)
                "${uri.scheme}://${uri.host ?: "host"}/<redacted>"
            }.getOrDefault("https://<redacted>")
        }
        result = result.replace(
            Regex("""(?i)(?:file://)?(?:/storage/emulated/\d+|/sdcard|/data/user/\d+|/data/data)/[^\s]+"""),
        ) { match ->
            val name = match.value.substringBefore('?').substringAfterLast('/').take(160)
            "<private-path>/${name.ifBlank { "file" }}"
        }
        return result.take(12_000)
    }

    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    private fun fileTimestamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())
}

package cl.gakuto.kizuna.ftp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.launch.PersistentRomLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Pure-Kotlin RFC 959 FTP background transfer manager.
 *
 * Designed to stay out of the main game-launch path: sends local or patched ROMs to PS4/PS3/Switch
 * asynchronously in the background while RetroArch quick-launch remains primary.
 */
object FtpBackgroundManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transferMutex = Mutex()
    private val _jobs = MutableStateFlow<List<FtpTransferJob>>(emptyList())
    val jobs: StateFlow<List<FtpTransferJob>> = _jobs

    private const val CHANNEL_ID = "gamevault_ftp"

    suspend fun testTarget(target: FtpTargetConfig): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (target.host.isBlank()) return@withContext false to "Host vacío."
        runCatching {
            FtpClientSession(target.host.trim(), target.port).use { ftp ->
                ftp.connectAndLogin(target.username.ifBlank { "anonymous" }, target.password)
                val pwd = ftp.pwd()
                true to "Conexión FTP OK ($pwd)"
            }
        }.getOrElse { err ->
            false to (err.message ?: "No se pudo conectar a ${target.host}:${target.port}")
        }
    }

    fun enqueueGameTransfer(
        context: Context,
        preferences: AppPreferences,
        game: GameEntity,
        target: FtpTargetConfig,
        patchedLocalFile: File? = null,
    ): FtpTransferJob {
        val appContext = context.applicationContext
        val jobId = "ftp-${UUID.randomUUID().toString().take(8)}"
        val remoteDir = target.resolveRemoteDir(game.systemId)
        val initialName = patchedLocalFile?.name ?: "${game.title}.${game.virtualExtension.ifBlank { "zip" }}"
        val initialJob = FtpTransferJob(
            jobId = jobId,
            gameId = game.id,
            title = game.title,
            targetName = target.name,
            remotePath = "$remoteDir/$initialName",
            totalBytes = patchedLocalFile?.length() ?: -1L,
            state = FtpTransferState.QUEUED,
            message = "En cola para ${target.name}",
        )
        _jobs.update { (listOf(initialJob) + it).take(25) }

        scope.launch {
            transferMutex.withLock {
                runTransfer(appContext, preferences, game, target, jobId, patchedLocalFile)
            }
        }
        return initialJob
    }

    private suspend fun runTransfer(
        context: Context,
        preferences: AppPreferences,
        game: GameEntity,
        target: FtpTargetConfig,
        jobId: String,
        patchedLocalFile: File?,
    ) {
        val remoteDir = target.resolveRemoteDir(game.systemId)
        try {
            val fileSpec: Triple<String, Long, () -> InputStream> = if (patchedLocalFile != null && patchedLocalFile.isFile) {
                Triple(patchedLocalFile.name, patchedLocalFile.length()) { patchedLocalFile.inputStream() }
            } else {
                val uriStr = game.contentUri?.takeIf { it.isNotBlank() }
                    ?: PersistentRomLibrary(context, preferences, OzDatabase.get(context))
                        .resolve(game)?.members?.firstOrNull()?.uri?.toString()
                    ?: error("Esta ROM no está descargada localmente en Android.")
                val uri = Uri.parse(uriStr)
                val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                val filename = doc?.name?.takeIf { it.isNotBlank() }
                    ?: "${game.title}.${game.virtualExtension.ifBlank { "zip" }}"
                val size = doc?.length()?.takeIf { it > 0L } ?: -1L
                Triple(filename, size) {
                    context.contentResolver.openInputStream(uri)
                        ?: throw IOException("No se pudo abrir la ROM local.")
                }
            }

            val (filename, totalBytes, streamOpener) = fileSpec
            val fullRemote = "$remoteDir/$filename"
            updateJob(jobId) {
                it.copy(
                    remotePath = fullRemote,
                    totalBytes = totalBytes,
                    state = FtpTransferState.UPLOADING,
                    message = "Conectando a ${target.host}:${target.port}…",
                )
            }
            notifyProgress(context, jobId, "Enviando ${game.title}", "Conectando con ${target.name}…", 5, ongoing = true)

            FtpClientSession(target.host.trim(), target.port).use { ftp ->
                ftp.connectAndLogin(target.username.ifBlank { "anonymous" }, target.password)
                val resolvedDir = ftp.ensureRemoteDir(remoteDir)
                val finalRemote = "${resolvedDir.trimEnd('/')}/$filename"
                var lastNotifyAt = 0L
                streamOpener().buffered().use { input ->
                    ftp.stor(finalRemote, input) { sent ->
                        updateJob(jobId) {
                            it.copy(
                                remotePath = finalRemote,
                                transferredBytes = sent,
                                message = "Subiendo a ${target.name}…",
                            )
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyAt > 900L) {
                            lastNotifyAt = now
                            val pct = if (totalBytes > 0L) ((sent * 100L) / totalBytes).toInt().coerceIn(0, 99) else 0
                            notifyProgress(context, jobId, "Enviando ${game.title}", "${target.name} · $pct%", pct, ongoing = true)
                        }
                    }
                }
                updateJob(jobId) {
                    it.copy(
                        remotePath = finalRemote,
                        totalBytes = if (totalBytes > 0L) totalBytes else it.transferredBytes,
                        transferredBytes = if (totalBytes > 0L) totalBytes else it.transferredBytes,
                        state = FtpTransferState.COMPLETE,
                        message = "Completado en ${target.name}",
                    )
                }
                notifyProgress(context, jobId, "${game.title} enviado", "Disponible en $finalRemote", 100, ongoing = false)
            }
        } catch (err: Throwable) {
            val msg = err.message ?: "Error en transferencia FTP"
            updateJob(jobId) { it.copy(state = FtpTransferState.ERROR, message = msg) }
            notifyProgress(context, jobId, "Fallo FTP · ${game.title}", msg, 0, ongoing = false)
        }
    }

    private fun updateJob(jobId: String, transform: (FtpTransferJob) -> FtpTransferJob) {
        _jobs.update { list -> list.map { if (it.jobId == jobId) transform(it) else it } }
    }

    private fun notifyProgress(
        context: Context,
        jobId: String,
        title: String,
        text: String,
        pct: Int,
        ongoing: Boolean,
    ) {
        runCatching {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val existing = mgr.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    mgr.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "Transferencias FTP (Consolas)", NotificationManager.IMPORTANCE_LOW),
                    )
                }
            }
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setProgress(100, pct, ongoing && pct == 0)
                .build()
            mgr.notify(jobId.hashCode(), notif)
        }
    }

    /** Minimal pure-Kotlin RFC 959 FTP client with GoldHEN read-only `/` fallback. */
    internal class FtpClientSession(
        private val host: String,
        private val port: Int,
        private val timeoutMs: Int = 12_000,
    ) : AutoCloseable {
        private var socket: Socket? = null
        private var reader: BufferedReader? = null
        private var writer: BufferedWriter? = null

        fun connectAndLogin(username: String, password: String) {
            val sock = Socket()
            sock.connect(InetSocketAddress(host, port), timeoutMs)
            sock.soTimeout = 30_000
            socket = sock
            reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8))
            val greet = readReply()
            if (greet.first >= 400) throw IOException("FTP rechazó conexión (${greet.second})")

            val uReply = sendCommand("USER $username")
            if (uReply.first == 331) {
                val pReply = sendCommand("PASS $password")
                if (pReply.first >= 400) throw IOException("FTP credenciales rechazadas (${pReply.second})")
            } else if (uReply.first >= 400) {
                throw IOException("FTP USER rechazado (${uReply.second})")
            }
            sendCommand("TYPE I")
        }

        fun pwd(): String {
            val r = sendCommand("PWD")
            val m = Regex("\"([^\"]+)\"").find(r.second)
            return m?.groupValues?.getOrNull(1) ?: "/"
        }

        fun ensureRemoteDir(rawDir: String): String {
            val cleaned = rawDir.trim().replace('\\', '/')
            if (cleaned.isBlank() || cleaned == "/") return "/"
            val candidates = mutableListOf<String>()
            if (cleaned.startsWith("/")) {
                candidates += cleaned
            } else {
                candidates += cleaned
                candidates += "/data/EmuSuNi/${cleaned.trimStart('/')}"
            }

            var lastErr: String = ""
            for (cand in candidates.distinct()) {
                val res = tryWalkOrCreate(cand)
                if (res.isSuccess) return cand
                lastErr = res.exceptionOrNull()?.message.orEmpty()
            }
            throw IOException(lastErr.ifBlank { "No se pudo abrir ni crear $cleaned en FTP." })
        }

        private fun tryWalkOrCreate(dir: String): Result<Unit> = runCatching {
            val fullCwd = sendCommand("CWD $dir")
            if (fullCwd.first in 200..299) return@runCatching
            val isAbs = dir.startsWith("/")
            if (isAbs) sendCommand("CWD /")
            val parts = dir.trim('/').split('/').filter { it.isNotBlank() }
            var current = if (isAbs) "" else ""
            for (part in parts) {
                current = if (current.isEmpty() && !isAbs) part else "$current/$part"
                val step = sendCommand("CWD $part")
                if (step.first in 200..299) continue
                val mkd = sendCommand("MKD $part")
                if (mkd.first >= 500) {
                    val retry = sendCommand("CWD $part")
                    if (retry.first >= 400) error("FTP ${mkd.second} en '$part'")
                } else {
                    val enter = sendCommand("CWD $part")
                    if (enter.first >= 400) error("FTP ${enter.second} en '$part'")
                }
            }
        }

        fun stor(remotePath: String, input: InputStream, onProgress: (Long) -> Unit) {
            val (dataHost, dataPort) = enterPasv()
            val dataSock = Socket()
            dataSock.connect(InetSocketAddress(dataHost, dataPort), timeoutMs)
            dataSock.soTimeout = 30_000
            try {
                val leaf = remotePath.substringAfterLast('/')
                val storCmd = sendCommand("STOR $leaf")
                if (storCmd.first !in 100..199) {
                    throw IOException("FTP STOR rechazado (${storCmd.second})")
                }
                dataSock.getOutputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        total += n
                        onProgress(total)
                    }
                    out.flush()
                }
            } finally {
                runCatching { dataSock.close() }
            }
            val done = readReply()
            if (done.first >= 400) throw IOException("FTP transferencia incompleta (${done.second})")
        }

        private fun enterPasv(): Pair<String, Int> {
            val res = sendCommand("PASV")
            if (res.first != 227) throw IOException("FTP PASV falló (${res.second})")
            val m = Regex("""\((\d+),(\d+),(\d+),(\d+),(\d+),(\d+)\)""").find(res.second)
                ?: throw IOException("Respuesta PASV inválida: ${res.second}")
            val g = m.groupValues
            // Use control host when PASV advertises 0.0.0.0 or internal NAT
            val advertised = "${g[1]}.${g[2]}.${g[3]}.${g[4]}"
            val targetHost = if (advertised == "0.0.0.0" || advertised.startsWith("127.")) host else advertised
            val targetPort = (g[5].toInt() shl 8) or g[6].toInt()
            return targetHost to targetPort
        }

        private fun sendCommand(cmd: String): Pair<Int, String> {
            val w = writer ?: throw IOException("FTP no conectado")
            w.write(cmd)
            w.write("\r\n")
            w.flush()
            return readReply()
        }

        private fun readReply(): Pair<Int, String> {
            val r = BufferedInputStream(socket?.getInputStream() ?: throw IOException("FTP cerrado"))
            val sb = StringBuilder()
            var code = 0
            while (true) {
                val line = reader?.readLine() ?: throw IOException("Conexión FTP cerrada inesperadamente")
                sb.appendLine(line)
                if (line.length >= 3 && line.take(3).all(Char::isDigit)) {
                    code = line.take(3).toInt()
                    if (line.length == 3 || line[3] == ' ') break
                }
            }
            return code to sb.toString().trim()
        }

        override fun close() {
            runCatching { sendCommand("QUIT") }
            runCatching { reader?.close() }
            runCatching { writer?.close() }
            runCatching { socket?.close() }
        }
    }
}

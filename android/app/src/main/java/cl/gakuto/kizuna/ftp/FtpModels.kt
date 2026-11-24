package cl.gakuto.kizuna.ftp

import org.json.JSONArray
import org.json.JSONObject

data class FtpTargetConfig(
    val id: String,
    val name: String,
    val console: String,
    val host: String,
    val port: Int = 21,
    val username: String = "anonymous",
    val password: String = "",
    val basePath: String = "Roms",
    val pathOverrides: Map<String, String> = emptyMap(),
) {
    fun resolveRemoteDir(systemId: String): String {
        val ck = systemId.trim().lowercase()
        val mapped = pathOverrides.entries.firstOrNull { it.key.equals(ck, ignoreCase = true) }?.value
        if (!mapped.isNullOrBlank()) return mapped.trimEnd('/')
        val base = basePath.trimEnd('/')
        return if (base.isBlank()) ck else "$base/$ck"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("console", console)
        put("host", host)
        put("port", port)
        put("username", username)
        put("password", password)
        put("basePath", basePath)
        put("pathOverrides", JSONObject(pathOverrides))
    }

    companion object {
        fun defaultTargets(): List<FtpTargetConfig> = listOf(
            FtpTargetConfig(
                id = "ps4-emusuni",
                name = "PS4 (EmuSuNi / GoldHEN)",
                console = "PS4",
                host = "192.168.100.141",
                port = 2121,
                username = "anonymous",
                password = "anonymous@oz.local",
                basePath = "/data/EmuSuNi/Roms",
                pathOverrides = mapOf(
                    "arcade" to "/data/EmuSuNi/Roms/arcade",
                    "mame" to "/data/EmuSuNi/Roms/arcade",
                    "snes" to "/data/EmuSuNi/Roms/snes",
                    "nes" to "/data/EmuSuNi/Roms/nes",
                    "genesis" to "/data/EmuSuNi/Roms/md",
                    "gba" to "/data/EmuSuNi/Roms/gba",
                    "ps1" to "/data/EmuSuNi/Roms/ps1",
                ),
            ),
            FtpTargetConfig(
                id = "ps3-webman",
                name = "PS3 (webMAN MOD)",
                console = "PS3",
                host = "192.168.100.142",
                port = 21,
                username = "anonymous",
                password = "anonymous@oz.local",
                basePath = "/dev_hdd0/ROMS",
                pathOverrides = mapOf(
                    "ps1" to "/dev_hdd0/PSXISO",
                    "ps2" to "/dev_hdd0/PS2ISO",
                    "ps3" to "/dev_hdd0/PS3ISO",
                    "psp" to "/dev_hdd0/PSPISO",
                ),
            ),
            FtpTargetConfig(
                id = "switch-ftp",
                name = "Nintendo Switch (ftpd / DBI)",
                console = "Switch",
                host = "192.168.100.143",
                port = 5000,
                username = "anonymous",
                password = "",
                basePath = "/roms",
                pathOverrides = mapOf("switch" to "/switch/nsp"),
            ),
        )

        fun listFromJson(raw: String?): List<FtpTargetConfig> {
            if (raw.isNullOrBlank()) return defaultTargets()
            return runCatching {
                val arr = JSONArray(raw)
                val list = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val ovObj = obj.optJSONObject("pathOverrides")
                        val overrides = buildMap {
                            if (ovObj != null) {
                                ovObj.keys().forEach { k ->
                                    val v = ovObj.optString(k)
                                    if (v.isNotBlank()) put(k, v)
                                }
                            }
                        }
                        add(
                            FtpTargetConfig(
                                id = obj.optString("id", "target-${i + 1}"),
                                name = obj.optString("name", "Consola FTP"),
                                console = obj.optString("console", "Custom"),
                                host = obj.optString("host", ""),
                                port = obj.optInt("port", 21).coerceIn(1, 65535),
                                username = obj.optString("username", "anonymous"),
                                password = obj.optString("password", ""),
                                basePath = obj.optString("basePath", "Roms"),
                                pathOverrides = overrides,
                            ),
                        )
                    }
                }
                if (list.isEmpty()) defaultTargets() else list
            }.getOrElse { defaultTargets() }
        }

        fun listToJson(targets: List<FtpTargetConfig>): String =
            JSONArray().apply { targets.forEach { put(it.toJson()) } }.toString()
    }
}

enum class FtpTransferState { QUEUED, UPLOADING, COMPLETE, ERROR }

data class FtpTransferJob(
    val jobId: String,
    val gameId: String,
    val title: String,
    val targetName: String,
    val remotePath: String,
    val totalBytes: Long,
    val transferredBytes: Long = 0L,
    val state: FtpTransferState = FtpTransferState.QUEUED,
    val message: String = "En cola (segundo plano)",
) {
    val progressPct: Int
        get() = if (totalBytes > 0L) ((transferredBytes * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0
}

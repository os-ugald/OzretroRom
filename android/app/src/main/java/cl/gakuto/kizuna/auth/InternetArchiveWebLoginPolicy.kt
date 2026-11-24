package cl.gakuto.kizuna.auth

import java.net.URI

/** Pure URL policy shared by WebView navigation, DOM extraction and JVM tests. */
internal object InternetArchiveWebLoginPolicy {
    private const val ARCHIVE_HOST = "archive.org"
    private const val S3_KEYS_PATH = "/account/s3.php"

    fun isTrustedDownloadUrl(value: String): Boolean = parsedHttps(value, allowDefaultHttpsPortOnly = false)?.let { uri ->
        val host = uri.host.lowercase()
        host == ARCHIVE_HOST || host.endsWith(".$ARCHIVE_HOST")
    } == true

    fun isAllowedWebUrl(value: String): Boolean = parsedHttps(value)?.let { uri ->
        val host = uri.host.lowercase()
        host == ARCHIVE_HOST || host.endsWith(".$ARCHIVE_HOST")
    } == true

    /** DOM credential extraction is restricted to one official origin and one exact path. */
    fun isS3KeysPage(value: String): Boolean = parsedHttps(value)?.let { uri ->
        uri.host.equals(ARCHIVE_HOST, ignoreCase = true) && uri.path == S3_KEYS_PATH
    } == true

    private fun parsedHttps(
        value: String,
        allowDefaultHttpsPortOnly: Boolean = true,
    ): URI? = runCatching { URI(value) }.getOrNull()?.takeIf { uri ->
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            (!allowDefaultHttpsPortOnly || uri.port == -1 || uri.port == 443)
    }
}

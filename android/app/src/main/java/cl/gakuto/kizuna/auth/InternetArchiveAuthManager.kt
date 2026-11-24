package cl.gakuto.kizuna.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Internet Archive Auth V2.
 *
 * Mirrors ROMgi's persistent IA-S3 model while deliberately separating a downstream 401/403
 * from proof that the whole account session is invalid. Secrets and cookies are encrypted with
 * a non-exportable Android Keystore key and are never exposed through diagnostics.
 */
class InternetArchiveAuthManager(context: Context) {
    enum class State { DISCONNECTED, CONNECTED, STALE, INVALID }
    enum class Validation { OK, STALE, INVALID, SIGNED_OUT }

    data class Session(
        val accessKey: String,
        val secretKey: String,
        val cookies: String,
        val createdAt: Long,
        val lastValidatedAt: Long,
        val state: State,
        val failureCount: Int,
        val lastValidationResult: String,
    )

    data class FailureResult(val state: State, val failureCount: Int)

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun currentSession(): Session? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { decodeSession(decrypt(raw)) }.getOrNull()
    }

    fun state(): State = currentSession()?.state ?: State.DISCONNECTED

    /** Kept for legacy SettingsActivity compatibility; Auth V2 deliberately stores no account identifier. */
    fun username(): String? = null

    fun isLoggedIn(): Boolean = currentSession()?.let {
        it.accessKey.isNotBlank() && it.secretKey.isNotBlank() &&
            it.state != State.INVALID && it.failureCount < MAX_AUTH_FAILURES
    } == true

    fun isStale(now: Long = System.currentTimeMillis()): Boolean = currentSession()?.let {
        it.state == State.STALE || it.lastValidatedAt <= 0L || now - it.lastValidatedAt >= STALE_AFTER_MS
    } ?: false

    /** Explicit disconnect is the only operation that intentionally destroys the whole session. */
    fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    /**
     * ROMgi uses the captured archive.org cookies for archive downloads when present and LOW
     * access:secret as the API/S3 fallback. Headers are never returned to non-IA HTTPS hosts.
     */
    fun headersFor(url: String, preferLow: Boolean = false): Map<String, String> {
        if (!isTrustedArchiveUrl(url)) return emptyMap()
        val session = currentSession() ?: return emptyMap()
        if (session.state == State.INVALID) return emptyMap()
        val host = runCatching { URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
        if (preferLow || host == "s3.us.archive.org" || session.cookies.isBlank()) {
            return mapOf("Authorization" to "LOW ${session.accessKey}:${session.secretKey}")
        }
        return mapOf(
            "Cookie" to session.cookies,
            "Referer" to "https://archive.org/",
        )
    }

    /** Exchange a legitimate WebView session for the existing IA-S3 keys. */
    suspend fun completeLoginFromCookies(cookieHeader: String): Session? = withContext(Dispatchers.IO) {
        val cookies = cleanCookieHeader(cookieHeader)
        if (cookies.isBlank()) return@withContext null
        val connection = open(S3_KEYS_URL, "GET").apply {
            setRequestProperty("Cookie", cookies)
            setRequestProperty("Referer", "https://archive.org/")
        }
        val keys = try {
            val code = connection.responseCode
            if (code !in 200..299) return@withContext null
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            InternetArchiveS3PageParser.parse(html)
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
        keys?.let { verifyAndSave(it.access, it.secret, cookies) }
    }

    /** Validates keys read from the live official DOM before persisting them. */
    suspend fun completeLoginFromPageKeys(
        accessKey: String,
        secretKey: String,
        cookieHeader: String,
    ): Session? = withContext(Dispatchers.IO) {
        verifyAndSave(accessKey, secretKey, cleanCookieHeader(cookieHeader))
    }

    /** Manual IA-S3 import remains a safe recovery path; user/password are never stored. */
    suspend fun importS3Credentials(accessKey: String, secretKey: String): Session? =
        completeLoginFromPageKeys(accessKey, secretKey, "")

    /**
     * Real IA-S3 validation. A transport/server failure becomes STALE; an explicit auth rejection
     * increments the persistent counter and only becomes INVALID after repeated evidence.
     */
    suspend fun validate(countAuthFailure: Boolean = true): Validation = withContext(Dispatchers.IO) {
        val session = currentSession() ?: return@withContext Validation.SIGNED_OUT
        if (session.accessKey.isBlank() || session.secretKey.isBlank()) {
            persist(session.copy(state = State.STALE, lastValidationResult = "missing_keys"))
            return@withContext Validation.STALE
        }
        when (val code = probeCredentials(session.accessKey, session.secretKey)) {
            null -> {
                persist(session.copy(state = State.STALE, lastValidationResult = "transport_error"))
                Validation.STALE
            }
                in 200..299 -> {
                    persist(
                        session.copy(
                            lastValidatedAt = System.currentTimeMillis(),
                            state = State.CONNECTED,
                            failureCount = 0,
                            lastValidationResult = "http_$code",
                        ),
                    )
                    Validation.OK
                }
                401, 403 -> {
                    val next = if (countAuthFailure) {
                        incrementAuthFailure(session, "probe_http_$code")
                    } else {
                        session.copy(
                            state = if (session.failureCount >= MAX_AUTH_FAILURES) State.INVALID else State.STALE,
                            lastValidationResult = "probe_http_$code",
                        ).also(::persist)
                    }
                    if (next.state == State.INVALID) Validation.INVALID else Validation.STALE
                }
                else -> {
                    persist(session.copy(state = State.STALE, lastValidationResult = "probe_http_$code"))
                    Validation.STALE
                }
        }
    }

    /** Records only an authentication-shaped downstream failure; never clears secrets. */
    fun recordAuthFailure(statusCode: Int, requestUrl: String): FailureResult {
        val session = currentSession() ?: return FailureResult(State.DISCONNECTED, 0)
        if (statusCode !in setOf(401, 403) || !isTrustedArchiveUrl(requestUrl)) {
            persist(session.copy(state = State.STALE, lastValidationResult = "source_http_$statusCode"))
            return FailureResult(State.STALE, session.failureCount)
        }
        val next = incrementAuthFailure(session, "download_http_$statusCode")
        return FailureResult(next.state, next.failureCount)
    }

    fun recordSourceFailure(statusCode: Int) {
        val session = currentSession() ?: return
        persist(session.copy(state = State.STALE, lastValidationResult = "source_http_$statusCode"))
    }

    fun isTrustedArchiveUrl(value: String): Boolean = InternetArchiveWebLoginPolicy.isTrustedDownloadUrl(value)

    fun isExpectedWebLoginUrl(value: String): Boolean = InternetArchiveWebLoginPolicy.isAllowedWebUrl(value)

    fun isS3KeysPageUrl(value: String): Boolean = InternetArchiveWebLoginPolicy.isS3KeysPage(value)

    private fun incrementAuthFailure(session: Session, result: String): Session {
        val failures = (session.failureCount + 1).coerceAtMost(MAX_AUTH_FAILURES)
        val next = session.copy(
            state = if (failures >= MAX_AUTH_FAILURES) State.INVALID else State.STALE,
            failureCount = failures,
            lastValidationResult = result,
        )
        persist(next)
        return next
    }

    private fun saveVerifiedSession(access: String, secret: String, cookies: String): Session {
        val now = System.currentTimeMillis()
        val prior = currentSession()
        val session = Session(
            accessKey = access,
            secretKey = secret,
            cookies = cookies,
            createdAt = prior?.createdAt?.takeIf { it > 0L } ?: now,
            lastValidatedAt = now,
            state = State.CONNECTED,
            failureCount = 0,
            lastValidationResult = "login_verified",
        )
        persist(session)
        return session
    }

    private fun verifyAndSave(accessKey: String, secretKey: String, cookies: String): Session? {
        val keys = InternetArchiveS3PageParser.normalized(accessKey, secretKey) ?: return null
        val code = probeCredentials(keys.access, keys.secret)
        return if (code != null && code in 200..299) {
            saveVerifiedSession(keys.access, keys.secret, cookies)
        } else {
            null
        }
    }

    private fun open(url: String, method: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 7_000
        readTimeout = 7_000
        requestMethod = method
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.5")
    }

    /** Some Android/proxy combinations reject HEAD even though the authenticated GET works. */
    private fun probeCredentials(access: String, secret: String): Int? {
        var lastCode: Int? = null
        for (method in listOf("HEAD", "GET")) {
            val connection = open(S3_PROBE_URL, method).apply {
                instanceFollowRedirects = false
                setRequestProperty("Authorization", "LOW $access:$secret")
            }
            try {
                val code = connection.responseCode
                lastCode = code
                if (code in 200..299) return code
            } catch (_: Throwable) {
                // GET remains the compatibility fallback after a HEAD transport failure.
            } finally {
                connection.disconnect()
            }
        }
        return lastCode
    }

    private fun cleanCookieHeader(value: String): String = value.trim()
        .takeIf { '\r' !in it && '\n' !in it }
        .orEmpty()

    private fun persist(session: Session) {
        prefs.edit().putString(KEY_SESSION, encrypt(encodeSession(session))).apply()
    }

    private fun encodeSession(session: Session): ByteArray = JSONObject().apply {
        put("schema", 2)
        put("accessKey", session.accessKey)
        put("secretKey", session.secretKey)
        put("cookies", session.cookies)
        put("createdAt", session.createdAt)
        put("lastValidatedAt", session.lastValidatedAt)
        put("state", session.state.name)
        put("failureCount", session.failureCount)
        put("lastValidationResult", session.lastValidationResult)
    }.toString().toByteArray(StandardCharsets.UTF_8)

    /** Decodes both Fix5 schema 1 and Auth V2 schema 2 without deleting working credentials. */
    private fun decodeSession(bytes: ByteArray): Session {
        val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
        val access = json.optString("accessKey")
        val secret = json.optString("secretKey")
        val lastValidated = json.optLong("lastValidatedAt", 0L)
        val legacyConnected = access.isNotBlank() && secret.isNotBlank()
        return Session(
            accessKey = access,
            secretKey = secret,
            cookies = json.optString("cookies"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            lastValidatedAt = lastValidated,
            state = runCatching { State.valueOf(json.optString("state")) }.getOrDefault(
                if (legacyConnected) State.STALE else State.DISCONNECTED,
            ),
            failureCount = json.optInt("failureCount", 0).coerceIn(0, MAX_AUTH_FAILURES),
            lastValidationResult = json.optString("lastValidationResult", if (legacyConnected) "migrated_fix5" else "none"),
        )
    }

    private fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain)
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): ByteArray {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_BYTES) { "Sesión cifrada inválida" }
        val iv = payload.copyOfRange(0, IV_BYTES)
        val encrypted = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "oz_internet_archive_auth"
        private const val KEY_SESSION = "session.v1"
        private const val KEY_ALIAS = "oz_retrorom_ia_session_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val MAX_AUTH_FAILURES = 3
        private const val STALE_AFTER_MS = 24L * 60L * 60L * 1000L
        private const val USER_AGENT = "OzRetroRom/0.6.0-beta.4 (InternetArchiveAuth)"
        const val S3_KEYS_URL = "https://archive.org/account/s3.php"
        private const val S3_PROBE_URL = "https://s3.us.archive.org/?check_auth=1"
    }
}

package cl.gakuto.kizuna.launch

import android.content.Context
import org.json.JSONObject
import java.net.URI
import java.util.Locale

/**
 * Immutable allow-list packaged inside the APK.
 *
 * A release build installs a core only when the exact archive and extracted
 * library hashes are present here. Update app/src/main/assets/trusted_cores.json
 * with scripts/update_trusted_cores.py before publishing a release.
 */
class TrustedCoreCatalog(context: Context) {
    data class Entry(
        val abi: String,
        val coreName: String,
        val url: String,
        val archiveSha256: String,
        val librarySha256: String,
    )

    private val entries: Map<String, Entry> = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
            val root = JSONObject(reader.readText())
            require(root.optInt("schema") == 1) { "Versión de catálogo de cores no compatible." }
            val array = root.optJSONArray("cores") ?: return@use emptyMap()
            buildMap {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val entry = Entry(
                        abi = item.getString("abi").trim(),
                        coreName = item.getString("coreName").trim(),
                        url = item.getString("url").trim(),
                        archiveSha256 = normalizeHash(item.getString("archiveSha256")),
                        librarySha256 = normalizeHash(item.getString("librarySha256")),
                    )
                    validate(entry)
                    put(key(entry.abi, entry.coreName), entry)
                }
            }
        }
    }.getOrElse { emptyMap() }

    fun find(abi: String, coreName: String): Entry? = entries[key(abi, coreName)]

    fun contains(abi: String, coreName: String): Boolean = find(abi, coreName) != null

    private fun validate(entry: Entry) {
        require(entry.abi in SUPPORTED_ABIS) { "ABI de core no permitida: ${entry.abi}" }
        require(entry.coreName.matches(Regex("[A-Za-z0-9._-]+_libretro_android\\.so"))) {
            "Nombre de core no permitido: ${entry.coreName}"
        }
        val uri = URI(entry.url)
        require(uri.scheme.equals("https", true)) { "Los cores deben descargarse mediante HTTPS." }
        require(uri.host.equals(TRUSTED_HOST, true)) { "Servidor de core no permitido: ${uri.host}" }
        require(entry.url.endsWith("/${entry.coreName}.zip")) { "La URL no corresponde al core declarado." }
    }

    companion object {
        private const val ASSET_NAME = "trusted_cores.json"
        private const val TRUSTED_HOST = "buildbot.libretro.com"
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a")

        private fun key(abi: String, coreName: String): String =
            "${abi.trim().lowercase(Locale.ROOT)}|${coreName.trim().lowercase(Locale.ROOT)}"

        private fun normalizeHash(value: String): String {
            val normalized = value.trim().lowercase(Locale.ROOT)
            require(normalized.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 inválido en el catálogo de cores." }
            return normalized
        }
    }
}

package cl.gakuto.kizuna.sagas

import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.hub.GameVaultHubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

data class SagaAiAuditReport(
    val sagaId: String,
    val sagaName: String,
    val score: Int,
    val sourceLabel: String,
    val chronologyOk: Boolean,
    val summary: String,
    val issues: List<String> = emptyList(),
    val eraSuggestions: List<String> = emptyList(),
    val missingHighlights: List<String> = emptyList(),
)

object SagaAiAuditEngine {

    fun localAudit(saga: SagaSnapshot): SagaAiAuditReport {
        val entries = saga.entries
        val mainEntries = entries.filter { !it.isExtra }
        val extras = entries.filter { it.isExtra }
        val years = mainEntries.mapNotNull { it.year }
        val chronologyOk = years == years.sorted()

        val eraCounts = linkedMapOf<String, Int>()
        val seenNorms = hashSetOf<String>()
        val duplicates = mutableListOf<String>()
        for (entry in entries) {
            val era = entry.era.ifBlank { "Sin era" }
            eraCounts[era] = (eraCounts[era] ?: 0) + 1
            val norm = entry.title.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
            if (norm.isNotEmpty() && !seenNorms.add(norm)) {
                duplicates += entry.title
            }
        }

        val fragmentedEras = eraCounts.entries
            .filter { (era, count) -> count == 1 && "extra" !in era.lowercase(Locale.ROOT) }
            .map { it.key }

        val issues = mutableListOf<String>()
        val eraSuggestions = mutableListOf<String>()
        if (!chronologyOk) {
            issues += "Orden cronológico con años intercalados en entregas principales."
        }
        if (duplicates.isNotEmpty()) {
            issues += "Posibles entregas duplicadas: ${duplicates.distinct().take(3).joinToString(", ")}"
        }
        if (fragmentedEras.size >= 3) {
            eraSuggestions += "Conviene consolidar eras de un solo título (${fragmentedEras.take(3).joinToString(", ")}) en bloques generacionales."
        }
        if (extras.size > mainEntries.size && extras.size > 3) {
            eraSuggestions += "Hay ${extras.size} extras/spin-offs detectados fuera de las eras principales."
        }

        val missingHighlights = mainEntries
            .filter { !it.localOwned }
            .take(4)
            .map { "${it.title} (${it.consoleKey.uppercase(Locale.ROOT)})" }

        var score = 96
        if (!chronologyOk) score -= 22
        if (duplicates.isNotEmpty()) score -= 10
        if (fragmentedEras.size >= 3) score -= 8
        if (extras.size > mainEntries.size + 3) score -= 6
        score = score.coerceIn(45, 100)

        val summary = buildString {
            append("${saga.name}: ${mainEntries.size} entregas principales en ${eraCounts.size} grupos de era")
            if (chronologyOk) {
                append(" con cronología verificada.")
            } else {
                append(" (revisar orden de años).")
            }
        }

        return SagaAiAuditReport(
            sagaId = saga.id,
            sagaName = saga.name,
            score = score,
            sourceLabel = "Auditoría curada",
            chronologyOk = chronologyOk,
            summary = summary,
            issues = issues,
            eraSuggestions = eraSuggestions,
            missingHighlights = missingHighlights,
        )
    }

    suspend fun audit(saga: SagaSnapshot, preferences: AppPreferences): SagaAiAuditReport = withContext(Dispatchers.IO) {
        val baseline = localAudit(saga)

        val geminiKey = preferences.geminiApiKey.trim()
        val geminiModel = preferences.geminiModel.trim().ifBlank { "gemini-2.5-flash" }
        if (geminiKey.isNotBlank()) {
            runCatching {
                queryGeminiAudit(saga, baseline, geminiKey, geminiModel)
            }.getOrNull()?.let { return@withContext it }
        }

        val hubUrl = preferences.pcHubUrl.trim()
        if (hubUrl.isNotBlank()) {
            runCatching {
                val payload = JSONObject()
                    .put("id", saga.id)
                    .put("name", saga.name)
                    .put(
                        "entries",
                        JSONArray().apply {
                            saga.entries.take(35).forEach { e ->
                                put(
                                    JSONObject()
                                        .put("title", e.title)
                                        .put("year", e.year)
                                        .put("era", e.era)
                                        .put("consoleKey", e.consoleKey)
                                        .put("isExtra", e.isExtra)
                                        .put("owned", e.localOwned),
                                )
                            }
                        },
                    )
                    .toString()
                val raw = GameVaultHubClient.requestSagaAiAudit(hubUrl, saga.id, payload)
                parseAuditJson(raw, saga, baseline, fallbackSource = "PC Hub IA")
            }.getOrNull()?.let { return@withContext it }
        }

        baseline
    }

    private fun queryGeminiAudit(
        saga: SagaSnapshot,
        baseline: SagaAiAuditReport,
        apiKey: String,
        model: String,
    ): SagaAiAuditReport? {
        val modelEnc = URLEncoder.encode(model, "UTF-8")
        val keyEnc = URLEncoder.encode(apiKey, "UTF-8")
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelEnc:generateContent?key=$keyEnc"

        val entriesSummary = JSONArray().apply {
            saga.entries.take(35).forEach { e ->
                put(
                    JSONObject()
                        .put("title", e.title)
                        .put("year", e.year)
                        .put("era", e.era)
                        .put("console", e.consoleKey)
                        .put("extra", e.isExtra),
                )
            }
        }
        val prompt = buildString {
            appendLine("Eres un historiador experto en sagas de videojuegos retro. Evalúa cronología, coherencia de agrupación por eras y duplicados.")
            appendLine("Responde SOLO JSON válido con esta estructura exacta:")
            appendLine("""{"score": 0, "summary": "diagnóstico breve en español", "issues": ["problema o lista vacía"], "eraSuggestions": ["sugerencia concreta"], "missingHighlights": ["título clásico ausente"]}""")
            appendLine("Saga: ${saga.name}")
            appendLine("Entregas: $entriesSummary")
        }

        val reqBody = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("responseMimeType", "application/json"),
            )
            .toString()

        val conn = URI(endpoint).toURL().openConnection() as HttpURLConnection
        val rawText = try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 9000
            conn.readTimeout = 11000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.outputStream.use { it.write(reqBody.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }

        val root = JSONObject(rawText)
        val text = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.trim()
            .orEmpty()
        if (text.isBlank()) return null

        val cleanedJson = extractFirstJsonObject(text) ?: return null
        return parseAuditJson(cleanedJson, saga, baseline, fallbackSource = "Gemini IA ($model)")
    }

    internal fun parseAuditJson(
        rawJson: String,
        saga: SagaSnapshot,
        baseline: SagaAiAuditReport = localAudit(saga),
        fallbackSource: String = "Gemini IA",
    ): SagaAiAuditReport? {
        val obj = runCatching { JSONObject(extractFirstJsonObject(rawJson) ?: rawJson) }.getOrNull() ?: return null
        val score = obj.optInt("score", baseline.score).coerceIn(10, 100)
        val summary = obj.optString("summary").trim().ifBlank { baseline.summary }
        val sourceLabel = obj.optString("sourceLabel").trim().ifBlank { fallbackSource }
        val issues = obj.optJSONArray("issues").toStringList(limit = 4).ifEmpty { baseline.issues }
        val eraSuggestions = obj.optJSONArray("eraSuggestions").toStringList(limit = 4).ifEmpty { baseline.eraSuggestions }
        val missingHighlights = obj.optJSONArray("missingHighlights").toStringList(limit = 4).ifEmpty { baseline.missingHighlights }
        return baseline.copy(
            score = score,
            sourceLabel = sourceLabel,
            summary = summary,
            issues = issues,
            eraSuggestions = eraSuggestions,
            missingHighlights = missingHighlights,
        )
    }

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    private fun JSONArray?.toStringList(limit: Int): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(minOf(length(), limit))
        for (i in 0 until length()) {
            val s = optString(i).trim()
            if (s.isNotBlank()) {
                out += s
                if (out.size >= limit) break
            }
        }
        return out
    }
}

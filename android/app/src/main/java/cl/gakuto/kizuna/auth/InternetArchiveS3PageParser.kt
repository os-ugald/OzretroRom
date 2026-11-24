package cl.gakuto.kizuna.auth

/**
 * Extracts the two existing IA-S3 keys without depending on Internet Archive's historical
 * `name="access"` / `name="secret"` attributes.
 *
 * The JavaScript is evaluated only after [InternetArchiveWebLoginPolicy.isS3KeysPage] accepts the
 * current top-level page. It reads visible inputs and returns a short value to Android; it never
 * submits forms, clicks controls, creates keys, rotates keys or installs a JavaScript bridge.
 */
internal object InternetArchiveS3PageParser {
    data class Keys(val access: String, val secret: String)

    private data class Candidate(val value: String, val tag: String, val offset: Int)

    private val keyPattern = Regex("^[A-Za-z0-9]{16}$")
    private val inputPattern = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ignoredInputTypes = setOf("button", "checkbox", "file", "hidden", "image", "radio", "reset", "submit")

    /**
     * Live-DOM extractor for the official IA-S3 page. The exact-two fallback is enabled only when
     * the page contains both official field labels, which avoids treating unrelated tokens as keys.
     */
    val DOM_EXTRACTION_SCRIPT: String = """
        (() => {
          const normalize = (value) => (value || '').trim();
          const keyPattern = /^[A-Za-z0-9]{16}$/;
          const ignoredTypes = new Set(['button', 'checkbox', 'file', 'hidden', 'image', 'radio', 'reset', 'submit']);
          const fields = Array.from(document.querySelectorAll('input')).filter((input) => {
            const type = (input.getAttribute('type') || 'text').toLowerCase();
            return !ignoredTypes.has(type) && input.getClientRects().length > 0 && keyPattern.test(normalize(input.value));
          });
          const unique = [];
          const seen = new Set();
          for (const input of fields) {
            const value = normalize(input.value);
            if (!seen.has(value)) {
              seen.add(value);
              unique.push(input);
            }
          }
          const identity = (input) => [
            input.name,
            input.id,
            input.getAttribute('aria-label'),
            input.getAttribute('placeholder'),
            input.getAttribute('data-name'),
            input.getAttribute('data-key')
          ].filter(Boolean).join(' ').toLowerCase();
          const nearbyText = (input) => {
            const parts = [];
            if (input.labels) {
              for (const label of input.labels) parts.push(label.textContent || '');
            }
            let node = input;
            for (let level = 0; node && level < 4; level += 1) {
              if (node.previousElementSibling) parts.push(node.previousElementSibling.textContent || '');
              node = node.parentElement;
            }
            return parts.join(' ').replace(/\s+/g, ' ').toLowerCase();
          };
          const contextKind = (input) => {
            const context = nearbyText(input);
            const accessAt = context.lastIndexOf('access key');
            const secretAt = context.lastIndexOf('secret key');
            if (accessAt < 0 && secretAt < 0) return '';
            return accessAt > secretAt ? 'access' : 'secret';
          };
          let accessInput = unique.find((input) => /(^|[^a-z])access([^a-z]|$)/.test(identity(input)));
          let secretInput = unique.find((input) => /(^|[^a-z])secret([^a-z]|$)/.test(identity(input)));
          if (!accessInput) accessInput = unique.find((input) => contextKind(input) === 'access');
          if (!secretInput) secretInput = unique.find((input) => contextKind(input) === 'secret');
          const pageText = (document.body && document.body.innerText ? document.body.innerText : '').replace(/\s+/g, ' ').toLowerCase();
          const hasOfficialLabels = /s3[\s-]*access key/.test(pageText) && /s3[\s-]*secret key/.test(pageText);
          if ((!accessInput || !secretInput) && hasOfficialLabels && unique.length === 2) {
            accessInput = unique[0];
            secretInput = unique[1];
          }
          const access = normalize(accessInput && accessInput.value);
          const secret = normalize(secretInput && secretInput.value);
          return keyPattern.test(access) && keyPattern.test(secret) && access !== secret
            ? access + '|' + secret
            : '';
        })()
    """.trimIndent()

    /** Parses the JSON-quoted string returned by WebView.evaluateJavascript. */
    fun parseJavascriptResult(raw: String?): Keys? {
        val value = raw.orEmpty().trim().let { encoded ->
            if (encoded.length >= 2 && encoded.first() == '"' && encoded.last() == '"') {
                encoded.substring(1, encoded.length - 1)
            } else {
                encoded
            }
        }
        val pieces = value.split('|')
        return if (pieces.size == 2) normalized(pieces[0], pieces[1]) else null
    }

    /** Parses the authenticated server response used by the cookie-exchange fallback. */
    fun parse(html: String): Keys? {
        val candidates = inputPattern.findAll(html).mapNotNull { match ->
            val tag = match.value
            val type = attributeValue(tag, "type")?.lowercase()
            if (type in ignoredInputTypes) return@mapNotNull null
            val value = attributeValue(tag, "value")?.let(::decodeEntities)?.trim().orEmpty()
            value.takeIf(::isValidKey)?.let { Candidate(it, tag, match.range.first) }
        }.distinctBy(Candidate::value).toList()

        if (candidates.size < 2) return null

        val identifiedAccess = candidates.firstOrNull { identifies(it.tag, "access") }
        val identifiedSecret = candidates.firstOrNull { identifies(it.tag, "secret") }
        normalized(identifiedAccess?.value.orEmpty(), identifiedSecret?.value.orEmpty())?.let { return it }

        val contextualAccess = candidates.firstOrNull { contextKind(html, it.offset) == "access" }
        val contextualSecret = candidates.firstOrNull { contextKind(html, it.offset) == "secret" }
        normalized(contextualAccess?.value.orEmpty(), contextualSecret?.value.orEmpty())?.let { return it }

        val pageText = normalizedText(html)
        val hasOfficialLabels = pageText.contains("s3 access key") && pageText.contains("s3 secret key")
        return if (hasOfficialLabels && candidates.size == 2) {
            normalized(candidates[0].value, candidates[1].value)
        } else {
            null
        }
    }

    fun normalized(access: String, secret: String): Keys? {
        val cleanAccess = access.trim()
        val cleanSecret = secret.trim()
        return if (isValidKey(cleanAccess) && isValidKey(cleanSecret) && cleanAccess != cleanSecret) {
            Keys(cleanAccess, cleanSecret)
        } else {
            null
        }
    }

    private fun isValidKey(value: String): Boolean = keyPattern.matches(value)

    private fun identifies(tag: String, identity: String): Boolean {
        val attributes = listOf("name", "id", "aria-label", "placeholder", "data-name", "data-key")
        return attributes.any { attribute ->
            val value = attributeValue(tag, attribute)?.lowercase().orEmpty()
            Regex("(^|[^a-z])${Regex.escape(identity)}([^a-z]|$)").containsMatchIn(value)
        }
    }

    private fun contextKind(html: String, inputOffset: Int): String? {
        val start = (inputOffset - 600).coerceAtLeast(0)
        val context = normalizedText(html.substring(start, inputOffset))
        val accessAt = context.lastIndexOf("access key")
        val secretAt = context.lastIndexOf("secret key")
        if (accessAt < 0 && secretAt < 0) return null
        return if (accessAt > secretAt) "access" else "secret"
    }

    private fun normalizedText(html: String): String = decodeEntities(html)
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()

    private fun attributeValue(tag: String, attribute: String): String? {
        val escaped = Regex.escape(attribute)
        val quoted = Regex("\\b$escaped\\s*=\\s*([\"'])(.*?)\\1", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        quoted.find(tag)?.groupValues?.getOrNull(2)?.let { return it }
        return Regex("\\b$escaped\\s*=\\s*([^\\s>]+)", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.getOrNull(1)
    }

    private fun decodeEntities(value: String): String {
        val named = value
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace("&apos;", "'", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
        return Regex("&#(x[0-9a-f]+|[0-9]+);", RegexOption.IGNORE_CASE).replace(named) { entity ->
            val raw = entity.groupValues[1]
            val codePoint = if (raw.startsWith("x", true)) raw.drop(1).toIntOrNull(16) else raw.toIntOrNull()
            codePoint?.let { String(Character.toChars(it)) } ?: entity.value
        }
    }
}

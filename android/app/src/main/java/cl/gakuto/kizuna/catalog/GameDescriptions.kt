package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.sagas.SagasRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

enum class GameMood(
    val key: String,
    val label: String,
    val durationHint: String,
    val badge: String,
) {
    QUICK("quick", "Partida rápida", "15–30 min", "⚡"),
    ADVENTURE("adventure", "Aventura larga", "Historia profunda", "🗺"),
    ACTION("action", "Acción pura", "Adrenalina directa", "🔥"),
    HIDDEN_GEM("hidden_gem", "Joya oculta", "Para descubrir", "💎"),
}

data class GameSynopsis(
    val description: String,
    val source: String,
)

object GameDescriptions {
    private val CURATED: Map<String, String> = mapOf(
        "sonic the hedgehog" to "Corre a toda velocidad por Green Hill Zone y cinco zonas clásicas para liberar a los animales capturados por el Dr. Robotnik y reunir las seis Esmeraldas del Caos.",
        "super mario rpg" to "Mario une fuerzas con Bowser, Peach, Mallow y Geno en una aventura RPG por turnos con ataques sincronizados, secretos por todas partes y el humor clásico de Square y Nintendo.",
        "chrono trigger" to "Viaja entre épocas desde la prehistoria hasta un futuro apocalíptico con Crono y compañía en combates activos con técnicas combinadas y más de diez finales distintos.",
        "final fantasy ix" to "Yitán, Vivi y la princesa Garnet emprenden un viaje épico de fantasía clásica por Gaia con aprendizaje de habilidades por equipo y una historia inolvidable.",
        "final fantasy vii" to "Cloud Strife y Avalancha enfrentan a Shinra y a Sefirot usando el legendario sistema de Materias para personalizar magias, invocaciones y comandos.",
        "resident evil" to "Sobrevive en la mansión Spencer manejando a Jill Valentine o Chris Redfield mientras administras munición limitada, resuelves acertijos y descubres los experimentos de Umbrella.",
        "resident evil 2" to "Leon S. Kennedy y Claire Redfield intentan escapar de una Raccoon City invadida mediante dos campañas entrelazadas.",
        "resident evil 3" to "Jill Valentine protagoniza una huida desesperada por las calles en llamas de Raccoon City perseguida sin tregua por el imparable Nemesis T-Type.",
        "alundra" to "Explora la aldea de Inoa e introduce tu espada en las pesadillas de sus habitantes en una exigente aventura de acción cenital llena de calabozos.",
        "kirby & the amazing mirror" to "Cuatro Kirbys de colores cooperan en un gigantesco mundo espejo interconectado estilo Metroidvania con habilidades copiables y llamadas por comunicador.",
        "007 - from russia with love" to "Encarna a James Bond con la apariencia clásica de Sean Connery entre tiroteos tácticos, gadgets secretos y secuencias contra SPECTRE.",
        "007 shitou - the duel" to "Acción arcade lateral protagonizada por Timothy Dalton como James Bond enfrentando a icónicos villanos de la saga.",
        "mortal kombat" to "El torneo que revolucionó los salones recreativos con actores digitalizados, combates viscerales y los míticos remates Fatality.",
        "mortal kombat ii" to "Secuela legendaria en Outworld con el doble de personajes, secretos ocultos, Friendships, Babalities y ritmo arcade impecable.",
        "metal slug" to "Acción pixel-art dibujada a mano cuadro por cuadro pilotando el tanque Super Vehicle-001 frente a jefes mecánicos gigantes.",
        "castlevania: symphony of the night" to "Alucard explora un castillo de Drácula no lineal lleno de equipamiento RPG, transformaciones y un giro épico tras los créditos.",
        "super metroid" to "Samus Aran regresa al planeta Zebes en una obra maestra de atmósfera solitaria, mejoras progresivas y libertad total de exploración.",
        "the legend of zelda: a link to the past" to "Explora los mundos paralelos de la Luz y las Tinieblas en Hyrule superando calabozos llenos de ingenio para derrotar a Ganon.",
        "streets of rage 2" to "Axel, Blaze, Skate y Max limpian la ciudad del sindicato de Mr. X al ritmo de una de las mejores bandas sonoras electrónicas de 16 bits.",
        "super mario world" to "Explora Dinosaur Land junto a Mario y Yoshi en más de noventa salidas ocultas, capas voladoras y fortalezas llenas de secretos.",
        "golden sun" to "Domina la Psicoenergía elemental y colecciona espíritus Djinn para desatar invocaciones espectaculares por Weyard.",
        "earthbound" to "Ness y sus amigos recorren un mundo surrealista enfrentando invasores y hordas estrafalarias con humor inolvidable.",
        "shenmue" to "Ryo Hazuki investiga el asesinato de su padre en una Yokosuka viva con ciclo día-noche, minijuegos arcade y artes marciales.",
    )

    private val NORMALIZED_CURATED: Map<String, String> = CURATED.entries.associate { (k, v) ->
        SagasRepository.norm(k) to v
    }

    fun lookupCurated(title: String): String? {
        val clean = SagasRepository.norm(SagasRepository.cleanTitle(title))
        if (clean.isBlank()) return null
        NORMALIZED_CURATED[clean]?.let { return it }
        return NORMALIZED_CURATED.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { (k, _) ->
                k.length >= 6 && Regex("""\b${Regex.escape(k)}\b""").containsMatchIn(clean)
            }?.value
    }

    fun synthesizeFallback(game: GameEntity): String {
        val yearPart = game.year?.let { " ($it)" }.orEmpty()
        val regionPart = game.region.takeIf { it.isNotBlank() && !it.equals("Sin región", true) }?.let { " · Región $it" }.orEmpty()
        val genrePart = game.genre.takeIf { it.isNotBlank() && !it.equals("Sin clasificar", true) } ?: "clásico retro"
        return "Título de $genrePart para ${game.platform}$yearPart$regionPart. Listo en Game Vault para descargar, parchear al español o ejecutar en RetroArch."
    }

    suspend fun resolve(
        game: GameEntity,
        preferences: AppPreferences,
    ): GameSynopsis = withContext(Dispatchers.IO) {
        preferences.cachedDescription(game.id)?.let { cached ->
            return@withContext GameSynopsis(cached, "cache")
        }
        val cleanTitle = SagasRepository.cleanTitle(game.title)
        lookupCurated(cleanTitle)?.let { desc ->
            preferences.setCachedDescription(game.id, desc)
            return@withContext GameSynopsis(desc, "curated-es")
        }

        // Optional remote Wikipedia summary (HTTPS only)
        val wiki = fetchWikipediaSummary("es", cleanTitle) ?: fetchWikipediaSummary("en", cleanTitle)
        if (!wiki.isNullOrBlank()) {
            preferences.setCachedDescription(game.id, wiki)
            return@withContext GameSynopsis(wiki, "wikipedia")
        }

        val fallback = synthesizeFallback(game)
        GameSynopsis(fallback, "local")
    }

    private fun fetchWikipediaSummary(lang: String, cleanTitle: String): String? = runCatching {
        if (cleanTitle.length < 3) return@runCatching null
        val q = URLEncoder.encode(if (lang == "es") "$cleanTitle videojuego" else "$cleanTitle video game", "UTF-8")
        val searchUrl = URL("https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q&format=json&srlimit=2")
        val searchConn = (searchUrl.openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 4_000
            setRequestProperty("User-Agent", "GameVaultAndroid/0.6.0")
        }
        val searchBody = try {
            if (searchConn.responseCode !in 200..299) return@runCatching null
            searchConn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            searchConn.disconnect()
        }
        val hits = JSONObject(searchBody).optJSONObject("query")?.optJSONArray("search") ?: return@runCatching null
        if (hits.length() == 0) return@runCatching null
        val normQ = SagasRepository.norm(cleanTitle).split(' ').filter { it.isNotBlank() }.toSet()
        var chosen = ""
        for (i in 0 until hits.length()) {
            val h = hits.optJSONObject(i) ?: continue
            val t = h.optString("title")
            val snippet = h.optString("snippet").lowercase(Locale.ROOT)
            val looksGame = listOf("videojuego", "video game", "arcade", "nintendo", "sega", "playstation", "super nintendo", "konami", "capcom")
                .any { it in snippet || it in t.lowercase(Locale.ROOT) }
            val nt = SagasRepository.norm(t).split(' ').toSet()
            if (looksGame && (normQ intersect nt).isNotEmpty()) {
                chosen = t
                break
            }
        }
        if (chosen.isBlank()) return@runCatching null

        val slug = URLEncoder.encode(chosen.replace(' ', '_'), "UTF-8").replace("+", "%20")
        val sumConn = (URL("https://$lang.wikipedia.org/api/rest_v1/page/summary/$slug").openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 4_000
            setRequestProperty("User-Agent", "GameVaultAndroid/0.6.0")
        }
        val sumBody = try {
            if (sumConn.responseCode !in 200..299) return@runCatching null
            sumConn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            sumConn.disconnect()
        }
        val extract = JSONObject(sumBody).optString("extract").trim()
        if (extract.length < 40) return@runCatching null
        if (extract.length <= 340) extract else extract.take(336).substringBeforeLast(' ') + "…"
    }.getOrNull()
}

object MoodPickerEngine {
    fun pick(
        games: List<GameEntity>,
        mood: GameMood,
        offset: Int = 0,
        limit: Int = 3,
    ): List<GameEntity> {
        if (games.isEmpty()) return emptyList()
        val quickSystems = setOf("arcade", "fbneo", "mame", "nes", "snes", "genesis", "md", "gb", "gbc", "gba")
        val longSystems = setOf("snes", "gba", "ps1", "ps2", "gc", "n64", "nds", "dc", "saturn")
        val actionGenres = listOf("acción", "lucha", "plataforma", "beat", "run", "shoot", "arcade", "carreras")
        val rpgGenres = listOf("rpg", "jrpg", "aventura", "estrategia", "táctico", "rol", "exploración")

        val scored = games.sortedWith(
            compareByDescending<GameEntity> { game ->
                val g = game.genre.lowercase(Locale.ROOT)
                val sys = game.systemId.lowercase(Locale.ROOT)
                val hasLocal = if (!game.contentUri.isNullOrBlank()) 4 else 0
                val curatedDesc = if (GameDescriptions.lookupCurated(game.title) != null) 2 else 0
                val moodFit = when (mood) {
                    GameMood.QUICK -> (if (sys in quickSystems) 4 else 0) + if (actionGenres.any { it in g }) 4 else 0
                    GameMood.ADVENTURE -> (if (sys in longSystems) 3 else 0) + if (rpgGenres.any { it in g }) 6 else 0
                    GameMood.ACTION -> (if (actionGenres.any { it in g }) 6 else 0) + if (sys in setOf("arcade", "genesis", "snes", "ps1", "dc")) 2 else 0
                    GameMood.HIDDEN_GEM -> (if ((game.lastPlayedAt ?: 0L) == 0L) 4 else -2) + curatedDesc
                }
                moodFit + hasLocal + curatedDesc
            }.thenBy { it.title },
        )

        val window = scored.take((limit * 6).coerceAtLeast(limit))
        if (window.size <= limit) return window
        val start = ((offset * limit) % window.size).coerceAtLeast(0)
        return List(limit) { i -> window[(start + i) % window.size] }.distinctBy { it.id }
    }
}

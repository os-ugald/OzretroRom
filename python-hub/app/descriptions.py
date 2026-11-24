from __future__ import annotations

import re
from typing import Any
from urllib.parse import quote

import httpx

from .catalog import RomRecord
from .config import CONSOLES
from .db import HubDatabase
from .recommendations import GENRE_LABELS, infer_item_genres

CURATED_DESCRIPTIONS: dict[str, dict[str, str]] = {
    "sonic the hedgehog": {
        "releaseYear": "1991",
        "genre": "Plataformas",
        "developer": "Sonic Team / Sega",
        "description": "Corre a toda velocidad por Green Hill Zone y cinco zonas clásicas más para liberar a los animales capturados por el Dr. Robotnik y reunir las seis Esmeraldas del Caos.",
    },
    "super mario rpg": {
        "releaseYear": "1996",
        "genre": "RPG / JRPG",
        "developer": "Square / Nintendo",
        "description": "Mario une fuerzas con Bowser, Peach, Mallow y Geno en una aventura RPG por turnos con ataques sincronizados, secretos por todas partes y carisma único de Square y Nintendo.",
    },
    "chrono trigger": {
        "releaseYear": "1995",
        "genre": "RPG / JRPG",
        "developer": "Square",
        "description": "Viaja entre épocas desde la prehistoria hasta un futuro apocalíptico con Crono y compañía en combates activos con técnicas combinadas y más de diez finales distintos.",
    },
    "final fantasy ix": {
        "releaseYear": "2000",
        "genre": "RPG / JRPG",
        "developer": "Square",
        "description": "Yitán, Vivi y la princesa Garnet emprenden un viaje épico de fantasía clásica por Gaia con aprendizaje de habilidades por equipo, chocografías y una historia inolvidable.",
    },
    "final fantasy vii": {
        "releaseYear": "1997",
        "genre": "RPG / JRPG",
        "developer": "Square",
        "description": "Cloud Strife y el grupo Avalancha enfrentan a la corporación Shinra y a Sefirot usando el legendario sistema de Materias para personalizar magias, invocaciones y comandos.",
    },
    "resident evil": {
        "releaseYear": "1996",
        "genre": "Survival Horror",
        "developer": "Capcom",
        "description": "Sobrevive en la mansión Spencer manejando a Jill Valentine o Chris Redfield mientras administras munición limitada, resuelves acertijos y descubres los experimentos de Umbrella.",
    },
    "resident evil 2": {
        "releaseYear": "1998",
        "genre": "Survival Horror",
        "developer": "Capcom",
        "description": "Leon S. Kennedy y Claire Redfield intentan escapar de una Raccoon City invadida por muertos vivientes y criaturas B.O.W. mediante dos campañas entrelazadas.",
    },
    "resident evil 3": {
        "releaseYear": "1999",
        "genre": "Survival Horror",
        "developer": "Capcom",
        "description": "Jill Valentine protagoniza una huida desesperada por las calles en llamas de Raccoon City perseguida sin tregua por el imparable Nemesis T-Type.",
    },
    "alundra": {
        "releaseYear": "1997",
        "genre": "Acción / RPG",
        "developer": "Matrix Software",
        "description": "Explora la aldea de Inoa e introduce tu espada en las pesadillas de sus habitantes en una exigente aventura de acción cenital llena de calabozos y acertijos.",
    },
    "kirby & the amazing mirror": {
        "releaseYear": "2004",
        "genre": "Plataformas / Exploración",
        "developer": "HAL Laboratory / Flagship",
        "description": "Cuatro Kirbys de colores cooperan en un gigantesco mundo espejo interconectado estilo Metroidvania con habilidades copiables y llamadas por comunicador.",
    },
    "007 - from russia with love": {
        "releaseYear": "2005",
        "genre": "Acción / Aventura",
        "developer": "EA Redwood Shores",
        "description": "Encarna a James Bond con la voz y apariencia clásica de Sean Connery entre tiroteos tácticos, gadgets secretos y secuencias con jetpack contra SPECTRE.",
    },
    "007 shitou - the duel": {
        "releaseYear": "1993",
        "genre": "Acción / Plataformas",
        "developer": "The Kremlin / Tengen",
        "description": "Acción arcade lateral protagonizada por Timothy Dalton como James Bond enfrentando a icónicos villanos de la saga en instalaciones secretas.",
    },
    "marvel super heroes vs. street fighter": {
        "releaseYear": "1997",
        "genre": "Lucha / Arcade",
        "developer": "Capcom",
        "description": "Duelos arcade 2 contra 2 llenos de combos aéreos y asistencias explosivas cruzando a los luchadores de Street Fighter con los héroes de Marvel.",
    },
    "x-men vs. street fighter": {
        "releaseYear": "1996",
        "genre": "Lucha / Arcade",
        "developer": "Capcom",
        "description": "El histórico primer crossover tag-team de Capcom: arma parejas con Ryu, Chun-Li, Wolverine o Cíclope con relevos en vivo y Hyper Combos dobles.",
    },
    "marvel super heroes": {
        "releaseYear": "1995",
        "genre": "Lucha / Arcade",
        "developer": "Capcom",
        "description": "Los campeones y villanos de Marvel luchan por las Gemas del Infinito contra Thanos, usando poderes cósmicos temporales en pleno combate 2D.",
    },
    "super gem fighter": {
        "releaseYear": "1997",
        "genre": "Lucha / Arcade",
        "developer": "Capcom",
        "description": "Luchadores chibi de Street Fighter y Darkstalkers combaten recolectando gemas elementales que suben el nivel de sus ataques especiales entre guiños cómicos.",
    },
    "mortal kombat": {
        "releaseYear": "1992",
        "genre": "Lucha / Arcade",
        "developer": "Midway",
        "description": "El torneo que revolucionó los salones recreativos con actores digitalizados, combates viscerales y los míticos remates Fatality.",
    },
    "mortal kombat ii": {
        "releaseYear": "1993",
        "genre": "Lucha / Arcade",
        "developer": "Midway",
        "description": "Secuela legendaria ambientada en Outworld con el doble de personajes, escenario con secretos ocultos, Friendships, Babalities y ritmo arcade impecable.",
    },
    "mortal kombat 3": {
        "releaseYear": "1995",
        "genre": "Lucha / Arcade",
        "developer": "Midway",
        "description": "Shao Kahn invade la Tierra introduciendo el botón de correr, Dial-a-Combos encadenados y múltiples escenarios conectados en vertical.",
    },
    "killer instinct": {
        "releaseYear": "1994",
        "genre": "Lucha / Arcade",
        "developer": "Rare / Midway",
        "description": "Combates ultraveloces basados en auto-combos, Ultra Combos interminables y C-C-C-Combo Breakers con gráficos pre-renderizados.",
    },
    "metal slug": {
        "releaseYear": "1996",
        "genre": "Run & Gun / Arcade",
        "developer": "Nazca / SNK",
        "description": "Acción pixel-art dibujada a mano cuadro por cuadro pilotando el tanque Super Vehicle-001 frente a oleadas humorísticas y jefes mecánicos gigantes.",
    },
    "castlevania: symphony of the night": {
        "releaseYear": "1997",
        "genre": "Acción / Exploración",
        "developer": "Konami",
        "description": "Alucard explora un castillo de Drácula no lineal lleno de equipamiento RPG, transformaciones en lobo o murciélago y un giro épico tras los créditos.",
    },
    "super metroid": {
        "releaseYear": "1994",
        "genre": "Acción / Exploración",
        "developer": "Nintendo R&D1 / Intelligent Systems",
        "description": "Samus Aran regresa al planeta Zebes en una obra maestra de atmósfera solitaria, mejoras progresivas y libertad total de exploración.",
    },
    "the legend of zelda: a link to the past": {
        "releaseYear": "1991",
        "genre": "Acción / Aventura",
        "developer": "Nintendo EAD",
        "description": "Explora los mundos paralelos de la Luz y las Tinieblas en Hyrule superando calabozos llenos de ingenio para rescatar a las siete doncellas y derrotar a Ganon.",
    },
    "strets of rage 2": {
        "releaseYear": "1992",
        "genre": "Beat 'em Up",
        "developer": "Ancient / Sega",
        "description": "Axel, Blaze, Skate y Max limpian la ciudad del sindicato de Mr. X al ritmo de una de las mejores bandas sonoras electrónicas de 16 bits.",
    },
    "streets of rage 2": {
        "releaseYear": "1992",
        "genre": "Beat 'em Up",
        "developer": "Ancient / Sega",
        "description": "Axel, Blaze, Skate y Max limpian la ciudad del sindicato de Mr. X al ritmo de una de las mejores bandas sonoras electrónicas de 16 bits.",
    },
    "4x4 evo": {
        "releaseYear": "2000",
        "genre": "Carreras Off-Road",
        "developer": "Terminal Reality",
        "description": "Competencias todo terreno campo a través con camionetas SUV licenciadas, mejoras mecánicas detalladas y circuitos llenos de barro y atajos.",
    },
    "2020 super baseball": {
        "releaseYear": "1991",
        "genre": "Deportes / Arcade",
        "developer": "SNK / Pallas",
        "description": "Béisbol futurista de estilo arcade protagonizado por jugadores robóticos mejorables, minas sobre el campo y estadios de alta tecnología.",
    },
    "3 ninjas kick back": {
        "releaseYear": "1994",
        "genre": "Acción / Plataformas",
        "developer": "Malibu Interactive",
        "description": "Controla a Rocky, Colt o Tum-Tum a través de templos, cuevas y fortalezas ninja para recuperar una daga ceremonial robada.",
    },
    "10 super jogos": {
        "releaseYear": "2002",
        "genre": "Compilación",
        "developer": "Tectoy / Sega",
        "description": "Cartucho oficial brasileño de Mega Drive con diez clásicos arcade y familiares listos para jugar al instante en un solo paquete.",
    },
    "golden sun": {
        "releaseYear": "2001",
        "genre": "RPG / JRPG",
        "developer": "Camelot / Nintendo",
        "description": "Domina la Psicoenergía elemental y colecciona espíritus Djinn para desatar invocaciones espectaculares y resolver ingeniosos acertijos por Weyard.",
    },
    "earthbound": {
        "releaseYear": "1994",
        "genre": "RPG / JRPG",
        "developer": "Ape Inc. / HAL Laboratory",
        "description": "Ness y sus amigos recorren un mundo contemporáneo surrealista enfrentando alienígenas, ovnis y hordas estrafalarias con humor inolvidable.",
    },
    "secret of mana": {
        "releaseYear": "1993",
        "genre": "Action RPG",
        "developer": "Square",
        "description": "Aventura de acción en tiempo real para hasta tres jugadores combinando armas forjables, hechizos elementales y viajes sobre Flammie.",
    },
    "shenmue": {
        "releaseYear": "1999",
        "genre": "Aventura / Mundo Abierto",
        "developer": "Sega AM2",
        "description": "Ryo Hazuki investiga el asesinato de su padre en una Yokosuka viva con ciclo día-noche, minijuegos arcade jugables y combates de artes marciales.",
    },
}


def _norm_key(title: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", (title or "").lower()).strip()


_NORMALIZED_CURATED: dict[str, dict[str, str]] = {_norm_key(k): v for k, v in CURATED_DESCRIPTIONS.items()}


def _trim_summary(text: str, max_len: int = 380) -> str:
    cleaned = re.sub(r"\s+", " ", (text or "")).strip()
    if len(cleaned) <= max_len:
        return cleaned
    cut = cleaned[: max_len - 1]
    last_dot = max(cut.rfind("."), cut.rfind(" "))
    if last_dot > 160:
        cut = cut[:last_dot]
    return cut.rstrip(" ,;:") + "…"


async def _fetch_wikipedia_summary(lang: str, clean_title: str) -> dict[str, str] | None:
    if not clean_title or len(clean_title) < 3:
        return None
    headers = {"User-Agent": "GameVaultRetroHub/2.0 (desktop organizer)"}
    search_url = f"https://{lang}.wikipedia.org/w/api.php"
    try:
        async with httpx.AsyncClient(timeout=4.5, follow_redirects=True) as client:
            sr = await client.get(
                search_url,
                params={
                    "action": "query",
                    "list": "search",
                    "srsearch": f"{clean_title} videojuego" if lang == "es" else f"{clean_title} video game",
                    "format": "json",
                    "srlimit": 3,
                },
                headers=headers,
            )
            if sr.status_code != 200:
                return None
            hits = sr.json().get("query", {}).get("search", [])
            if not hits:
                return None

            norm_q = _norm_key(clean_title)
            chosen_title = ""
            for h in hits:
                snippet = re.sub(r"<[^>]+>", " ", h.get("snippet") or "").lower()
                t = str(h.get("title") or "")
                nt = _norm_key(t)
                looks_game = any(
                    w in snippet or w in t.lower()
                    for w in (
                        "videojuego", "video game", "arcade", "nintendo", "playstation",
                        "sega", "game boy", "snes", "famicom", "super nintendo", "konami", "capcom",
                    )
                )
                if looks_game and (norm_q in nt or nt in norm_q or len(set(norm_q.split()) & set(nt.split())) >= 1):
                    chosen_title = t
                    break
            if not chosen_title:
                return None

            sum_resp = await client.get(
                f"https://{lang}.wikipedia.org/api/rest_v1/page/summary/{quote(chosen_title.replace(' ', '_'))}",
                headers=headers,
            )
            if sum_resp.status_code != 200:
                return None
            payload = sum_resp.json()
            extract = str(payload.get("extract") or "").strip()
            if len(extract) < 40:
                return None
            year_match = re.search(r"\b(19[7-9]\d|20[0-2]\d)\b", extract)
            return {
                "description": _trim_summary(extract),
                "releaseYear": year_match.group(1) if year_match else "",
                "source": f"wikipedia-{lang}",
            }
    except Exception:
        return None


def _synthesize_fallback_description(rec: RomRecord) -> dict[str, str]:
    c_meta = CONSOLES.get(rec.console_key, {})
    console_name = c_meta.get("full") or rec.console_key.upper()
    genres = infer_item_genres({"cleanTitle": rec.clean_title, "title": rec.title, "consoleKey": rec.console_key, "relativePath": rec.relative_path})
    genre_label = GENRE_LABELS.get(genres[0], "Clásico Retro") if genres else "Clásico Retro"
    year_match = re.search(r"\b(19[8-9]\d|20[0-2]\d)\b", rec.title)
    rel_year = year_match.group(1) if year_match else ""
    extra_arcade = f" Romset {rec.romset} ({rec.arcade_core or 'Arcade'})." if rec.romset else ""
    reg = f" · Edición {rec.region}" if rec.region and rec.region != "Sin región" else ""
    desc = (
        f"Título de {genre_label} para {console_name}{reg}.{extra_arcade} "
        f"Listo en tu bóveda local para jugar, verificar SHA-256 o enviar por FTP."
    ).strip()
    return {
        "description": desc,
        "releaseYear": rel_year,
        "genre": genre_label,
        "developer": rec.arcade_core or console_name,
        "source": "catalog-local",
    }


async def resolve_game_description(
    rec: RomRecord,
    db: HubDatabase,
    force: bool = False,
) -> dict[str, Any]:
    existing = db.get_rom_meta(rec.entry_id)
    if not force and existing and (existing.get("description") or "").strip():
        return {
            "entryId": rec.entry_id,
            "description": existing["description"],
            "releaseYear": existing.get("release_year") or "",
            "genre": existing.get("genre") or "",
            "developer": existing.get("developer") or "",
            "source": existing.get("description_source") or "cache",
        }

    norm_clean = _norm_key(rec.clean_title or rec.title)
    curated_match = _NORMALIZED_CURATED.get(norm_clean)
    if not curated_match and norm_clean:
        for key in sorted(_NORMALIZED_CURATED.keys(), key=len, reverse=True):
            if len(key) >= 6 and re.search(rf"\b{re.escape(key)}\b", norm_clean):
                curated_match = _NORMALIZED_CURATED[key]
                break

    genres = infer_item_genres({"cleanTitle": rec.clean_title, "title": rec.title, "consoleKey": rec.console_key, "relativePath": rec.relative_path})
    inferred_genre = GENRE_LABELS.get(genres[0], "Clásico Retro") if genres else "Clásico Retro"

    if curated_match:
        return db.set_game_description(
            rec.entry_id,
            description=curated_match["description"],
            release_year=curated_match.get("releaseYear", ""),
            genre=curated_match.get("genre", inferred_genre),
            developer=curated_match.get("developer", ""),
            source="curated-es",
        )

    wiki = await _fetch_wikipedia_summary("es", rec.clean_title)
    if not wiki:
        wiki = await _fetch_wikipedia_summary("en", rec.clean_title)
    if wiki:
        return db.set_game_description(
            rec.entry_id,
            description=wiki["description"],
            release_year=wiki.get("releaseYear", ""),
            genre=inferred_genre,
            developer="",
            source=wiki["source"],
        )

    fb = _synthesize_fallback_description(rec)
    return db.set_game_description(
        rec.entry_id,
        description=fb["description"],
        release_year=fb["releaseYear"],
        genre=fb["genre"],
        developer=fb["developer"],
        source=fb["source"],
    )

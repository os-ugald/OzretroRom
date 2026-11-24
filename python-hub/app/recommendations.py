from __future__ import annotations

import datetime as dt
import hashlib
import json
import re
from typing import Any

import httpx

from .config import CONSOLES

GENRE_RULES: list[tuple[str, str, tuple[str, ...]]] = [
    (
        "rpg",
        "RPG / JRPG",
        (
            "rpg", "final fantasy", "chrono", "dragon quest", "dragon warrior", "breath of fire",
            "earthbound", "mother 2", "mother 3", "secret of mana", "trials of mana", "sword of mana",
            "seiken", "suikoden", "xenogears", "terranigma", "illusion of gaia", "golden sun",
            "pokemon", "alundra", "grandia", "phantasy star", "shining force", "lufia",
            "wild arms", "vagrant story", "persona", "tales of", "star ocean", "lunar",
            "valkyrie profile", "paper mario", "mario & luigi", "kingdom hearts", "xenoblade",
            "baldurs", "diablo", "fire emblem", "tactics ogre", "front mission", "shin megami",
            "super mario rpg", "boktai", "live a live", "bahamut",
        ),
    ),
    (
        "fighting",
        "Lucha / Arcade",
        (
            "street fighter", "marvel vs", "marvel super heroes", "x-men vs",
            "children of the atom", "mortal kombat", "killer instinct",
            "king of fighters", "kof", "tekken", "soul calibur", "soulcalibur",
            "soul edge", "soul blade", "darkstalkers", "vampire savior", "vampire hunter",
            "fatal fury", "real bout", "garou", "samurai shodown", "samurai spirits",
            "guilty gear", "gem fighter", "pocket fighter", "rival schools", "virtua fighter",
            "dead or alive", "smash bros", "super smash", "art of fighting", "world heroes",
            "last blade", "bloody roar", "primal rage", "red earth", "power stone",
            "svc chaos", "capcom vs", "snk vs", "tatsunoko", "eternal champions", "clayfighter",
        ),
    ),
    (
        "platform",
        "Plataformas",
        (
            "mario", "sonic", "kirby", "donkey kong", "mega man", "rockman",
            "rayman", "crash bandicoot", "spyro", "castlevania", "metroid", "yoshi", "klonoa",
            "banjo", "wario", "earthworm jim", "bubsy", "gex", "ninja gaiden", "shinobi",
            "ghosts 'n goblins", "ghouls 'n ghosts", "super meat", "celeste", "shantae",
            "ducktales", "duck tales", "tiny toon", "aladdin", "lion king", "drill dozer",
            "rondo of blood", "aria of sorrow", "harmony of dissonance", "circle of the moon",
            "zero mission", "fusion", "strider", "bionic commando", "rocket knight", "sparkster",
            "ristar", "dynamite headdy", "tomba", "ape escape", "jak", "ratchet", "sly",
            "wonder boy", "monster world", "cave story", "joe & mac", "bonk",
        ),
    ),
    (
        "action",
        "Acción / Aventura",
        (
            "zelda", "link to the past", "ocarina of time", "majora's mask", "minish cap",
            "wind waker", "twilight princess", "link's awakening", "metal gear", "resident evil",
            "biohazard", "silent hill", "007", "goldeneye", "perfect dark", "tomb raider",
            "god of war", "devil may cry", "okami", "shadow of the colossus", "ico",
            "legacy of kain", "soul reaver", "beyond good", "prince of persia",
            "dino crisis", "parasite eve", "grand theft", "gta", "shenmue",
            "hitman", "max payne", "splinter cell", "medievil", "onimusha",
            "syphon filter", "tenchu", "clock tower", "fatal frame",
        ),
    ),
    (
        "racing",
        "Carreras y Deportes",
        (
            "gran turismo", "mario kart", "f-zero", "wipeout", "burnout",
            "ridge racer", "outrun", "out run", "crazy taxi", "daytona", "sega rally",
            "need for speed", "midnight club", "wave race", "excitebike", "road rash",
            "top gear", "cruis'n", "baseball", "soccer", "fifa", "pes", "nba", "nfl",
            "tennis", "golf", "tony hawk", "ssx", "boxing", "wrestle", "windjammers",
            "super sidekicks", "neo turf", "hang-on",
        ),
    ),
    (
        "beatemup",
        "Beat 'em Up / Run & Gun",
        (
            "metal slug", "contra", "super c", "final fight", "streets of rage",
            "bare knuckle", "tmnt", "turtles in time", "hyperstone heist", "cadillacs",
            "punisher", "sunset riders", "double dragon", "golden axe", "alien vs",
            "battletoads", "knights of the round", "dungeons & dragons", "captain commando",
            "gunstar heroes", "sengoku", "night slashers", "armored warriors", "cyberbots",
            "alien storm", "three wonders", "king of dragons", "river city", "kunio",
            "guardian heroes", "comix zone", "fighting force", "viewtiful joe",
            "maximum carnage", "power rangers", "metamorphic force", "vendetta",
            "violent storm", "ninja baseball",
        ),
    ),
    (
        "shooter",
        "Shoot 'em Up / Arcade",
        (
            "gradius", "r-type", "ikaruga", "radiant silvergun", "dodonpachi", "donpachi",
            "raiden", "galaga", "1941", "1942", "1943", "1944", "19xx", "star fox",
            "starfox", "panzer dragoon", "parodius", "metal black", "twinbee",
            "strikers 1945", "gunbird", "giga wing", "mars matrix", "progear",
            "esp.ra.de", "esprade", "esp galuda", "ketsui", "mushihimesama",
            "blazing star", "pulstar", "thunder force", "axelay", "space harrier",
            "after burner", "salamander", "life force", "lifeforce", "aero fighters",
            "sonic wings", "darius", "einhander", "area 88", "u.n. squadron",
        ),
    ),
    (
        "puzzle",
        "Estrategia y Puzzle",
        (
            "tetris", "puyo", "puzzle", "bomberman", "lemmings", "bust-a-move",
            "puzzle bobble", "magical drop", "columns", "dr. mario", "advance wars",
            "ogre battle", "simcity", "civilization", "command & conquer", "warcraft",
            "starcraft", "pikmin", "lumines", "mr. driller", "picross", "meteos",
            "panel de pon", "pac-man", "ms. pac-man", "dig dug", "bubble bobble",
            "snow bros", "pang", "buster bros", "kururin",
        ),
    ),
]

GENRE_LABELS: dict[str, str] = {key: label for key, label, _ in GENRE_RULES}

_SHORT_WORD_BOUNDARY_KWS = {"rpg", "kof", "gta", "pes", "nba", "nfl", "ssx", "007", "ico", "gex", "jak", "sly", "pang", "1941", "1942", "1943", "1944"}


def _kw_pattern(kw: str) -> str:
    escaped = re.escape(kw)
    if kw in _SHORT_WORD_BOUNDARY_KWS or len(kw) <= 4:
        return rf"\b{escaped}\b"
    return escaped


_COMPILED_GENRE_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    (
        genre_key,
        re.compile(r"(?:" + "|".join(_kw_pattern(kw) for kw in keywords) + r")", re.IGNORECASE),
    )
    for genre_key, _label, keywords in GENRE_RULES
]

_FRANCHISE_ROOTS: tuple[str, ...] = (
    "kirby", "super mario", "mario kart", "mario & luigi", "paper mario", "dr. mario", "mario",
    "metroid", "castlevania", "zelda", "sonic", "mega man", "rockman", "donkey kong", "yoshi",
    "wario", "pokemon", "final fantasy", "chrono", "dragon quest", "golden sun", "fire emblem",
    "advance wars", "street fighter", "mortal kombat", "king of fighters", "killer instinct",
    "fatal fury", "samurai shodown", "darkstalkers", "tekken", "metal slug", "contra",
    "streets of rage", "final fight", "double dragon", "sengoku", "resident evil", "silent hill",
    "metal gear", "tomb raider", "gran turismo", "f-zero", "crash bandicoot", "spyro",
    "rayman", "shantae", "bomberman", "puyo", "gradius", "r-type",
)

_FRANCHISE_RE = re.compile(
    r"\b(" + "|".join(re.escape(root) for root in sorted(_FRANCHISE_ROOTS, key=len, reverse=True)) + r")\b",
    re.IGNORECASE,
)
_DEMO_PENALTY_RE = re.compile(r"\b(proto|prototype|beta|demo|sample|sampler|interactive cd|playstation underground|hack|bootleg)\b", re.IGNORECASE)

_METADATA_GENRE_HINTS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("rpg", ("rpg", "jrpg", "rol", "turnos")),
    ("fighting", ("lucha", "fighting", "versus")),
    ("platform", ("plataform", "metroidvania", "exploración", "exploracion")),
    ("beatemup", ("beat 'em up", "beat em up", "run & gun", "yo contra el barrio")),
    ("shooter", ("shoot 'em up", "shmup", "naves", "matamarcianos")),
    ("racing", ("carreras", "conducción", "conduccion", "deportes", "racing")),
    ("puzzle", ("puzzle", "rompecabezas", "estrategia", "táctic", "tactic")),
    ("action", ("acción", "accion", "aventura", "sigilo", "survival")),
)

_CONSOLE_FAMILIES: dict[str, str] = {
    "gb": "nintendo-handheld",
    "gbc": "nintendo-handheld",
    "gba": "nintendo-handheld",
    "nds": "nintendo-handheld",
    "3ds": "nintendo-handheld",
    "nes": "nintendo-home",
    "snes": "nintendo-home",
    "n64": "nintendo-home",
    "gc": "nintendo-home",
    "wii": "nintendo-home",
    "wiiu": "nintendo-home",
    "switch": "nintendo-home",
    "sms": "sega",
    "genesis": "sega",
    "sega32x": "sega",
    "gg": "sega",
    "saturn": "sega",
    "dc": "sega",
    "ps1": "playstation",
    "ps2": "playstation",
    "ps3": "playstation",
    "psp": "playstation",
    "arcade": "arcade",
    "mame": "arcade",
}

CURATED_CLASSICS: dict[str, list[dict[str, str]]] = {
    "rpg": [
        {"title": "Final Fantasy IX", "consoleKey": "ps1", "reason": "JRPG clásico con gran historia, combates por turnos y exploración inolvidable."},
        {"title": "Chrono Trigger DS", "consoleKey": "nds", "reason": "Edición definitiva en Nintendo DS con extras, cinemáticas y múltiples finales."},
        {"title": "Golden Sun", "consoleKey": "gba", "reason": "Puzzles ingeniosos, invocaciones espectaculares y progresión clásica en portátil."},
        {"title": "Crisis Core: Final Fantasy VII", "consoleKey": "psp", "reason": "JRPG de acción vibrante en PSP expandiendo el universo de Midgar."},
        {"title": "Skies of Arcadia Legends", "consoleKey": "gc", "reason": "Aventura aérea inolvidable con barcos celestes y combates épicos en GameCube."},
        {"title": "Super Mario RPG: Legend of the Seven Stars", "consoleKey": "snes", "reason": "Humor, combates activos por turnos y mundos vibrantes de Square y Nintendo."},
        {"title": "Xenoblade Chronicles", "consoleKey": "wii", "reason": "Mundo colosal, combates tácticos en tiempo real y banda sonora legendaria en Wii."},
        {"title": "Phantasy Star IV", "consoleKey": "genesis", "reason": "El gran referente JRPG de 16-bit en Mega Drive / Genesis."},
    ],
    "fighting": [
        {"title": "Street Fighter III: 3rd Strike", "consoleKey": "arcade", "reason": "La cima técnica del combate 2D con parries y animación pixel-art impecable."},
        {"title": "Soulcalibur", "consoleKey": "dc", "reason": "Referente absoluto de la lucha 3D con armas y fluidez arcade en Dreamcast."},
        {"title": "Super Smash Bros. Melee", "consoleKey": "gc", "reason": "Velocidad competitiva y crossovers icónicos de Nintendo en GameCube."},
        {"title": "Marvel vs. Capcom 2", "consoleKey": "dc", "reason": "Batallas frenéticas 3v3 con el roster más emblemático en Dreamcast."},
        {"title": "Garou: Mark of the Wolves", "consoleKey": "arcade", "reason": "Fluidez arcade de SNK con duelos intensos y sistema Just Defend."},
        {"title": "Tekken 3", "consoleKey": "ps1", "reason": "Un clásico eterno de la lucha 3D con modos arcade y personajes icónicos."},
    ],
    "platform": [
        {"title": "Castlevania: Order of Ecclesia", "consoleKey": "nds", "reason": "Aventura gótica exigente en Nintendo DS con glifos mágicos y gran ritmo."},
        {"title": "Super Mario Galaxy", "consoleKey": "wii", "reason": "Diseño de niveles con gravedad creativa e imaginación desbordante en Wii."},
        {"title": "Sonic Adventure 2", "consoleKey": "dc", "reason": "Velocidad doble héroe/villano y Chao Garden en el clásico de Dreamcast."},
        {"title": "Metroid Fusion", "consoleKey": "gba", "reason": "Exploración atmosférica por zonas conectadas y mejora progresiva de habilidades en GBA."},
        {"title": "Castlevania: Aria of Sorrow", "consoleKey": "gba", "reason": "Aventura gótica no lineal con sistema de almas y exploración constante."},
        {"title": "Donkey Kong Country Returns", "consoleKey": "wii", "reason": "Plataformas intensas y precisas rescatando la esencia clásica en Wii."},
        {"title": "Super Mario 64 DS", "consoleKey": "nds", "reason": "Redescubre el salto al 3D en portátil con mini-juegos y cuatro héroes."},
        {"title": "Super Metroid", "consoleKey": "snes", "reason": "Atmósfera inolvidable y progresión por habilidades que definió todo un género."},
    ],
    "action": [
        {"title": "Metroid Prime", "consoleKey": "gc", "reason": "Exploración en primera persona con atmósfera insuperable en Nintendo GameCube."},
        {"title": "The Legend of Zelda: The Wind Waker", "consoleKey": "gc", "reason": "Alta mar celeste y estilo cel-shading atemporal en GameCube."},
        {"title": "God of War: Chains of Olympus", "consoleKey": "psp", "reason": "Acción mitológica a escala cinematográfica diseñada especialmente para PSP."},
        {"title": "Jet Set Radio", "consoleKey": "dc", "reason": "Patines, grafiti, cel-shading y funk futurista único en Dreamcast."},
        {"title": "The Legend of Zelda: The Minish Cap", "consoleKey": "gba", "reason": "Aventura colorida en GBA llena de mazmorras ingeniosas y secretos."},
        {"title": "Resident Evil 4", "consoleKey": "gc", "reason": "Tensión constante y acción táctica que reinventó los juegos en tercera persona."},
        {"title": "Metal Gear Solid: Peace Walker", "consoleKey": "psp", "reason": "Sigilo táctico profundo y gestión de base diseñado para misiones en PSP."},
    ],
    "racing": [
        {"title": "Mario Kart DS", "consoleKey": "nds", "reason": "Circuitos brillantes, derrapes rápidos y diversión instantánea en Nintendo DS."},
        {"title": "F-Zero GX", "consoleKey": "gc", "reason": "Velocidad extrema a 60 FPS sin concesiones y pistas imposibles en GameCube."},
        {"title": "Wipeout Pulse", "consoleKey": "psp", "reason": "Carreras antigravedad electrónicas a toda velocidad en PSP."},
        {"title": "Crazy Taxi", "consoleKey": "dc", "reason": "Pura adrenalina arcade directa de recreativa a Dreamcast."},
        {"title": "Mario Kart Wii", "consoleKey": "wii", "reason": "Caos multijugador, motos y copas icónicas listas para cualquier partida."},
    ],
    "beatemup": [
        {"title": "Metal Slug X", "consoleKey": "arcade", "reason": "Acción explosiva cuadro por cuadro con humor y jefes gigantescos."},
        {"title": "Streets of Rage 2", "consoleKey": "genesis", "reason": "Peleas callejeras contundentes acompañadas de una banda sonora legendaria."},
        {"title": "Cadillacs and Dinosaurs", "consoleKey": "arcade", "reason": "Clásico arcade cooperativo con ritmo imparable y armas contundentes."},
        {"title": "Viewtiful Joe", "consoleKey": "gc", "reason": "Estética cómic y cámara lenta estilizada en el beat 'em up de GameCube."},
    ],
    "shooter": [
        {"title": "Ikaruga", "consoleKey": "dc", "reason": "Obra maestra de polaridad y precisión arcade con diseño inolvidable en Dreamcast."},
        {"title": "DoDonPachi", "consoleKey": "arcade", "reason": "Bullet hell icónico de Cave con cadenas de puntuación y acción frenética."},
        {"title": "Star Fox: Assault", "consoleKey": "gc", "reason": "Combates espaciales arcade con el escuadrón Star Fox en GameCube."},
        {"title": "Gradius Collection", "consoleKey": "psp", "reason": "Antología shoot 'em up impecable de Konami lista para jugar en PSP."},
    ],
    "puzzle": [
        {"title": "Lumines", "consoleKey": "psp", "reason": "Puzzles musicales hipnóticos sincronizados con luz y ritmo en PSP."},
        {"title": "Professor Layton", "consoleKey": "nds", "reason": "Acertijos ingeniosos y misterio ilustrado hecho a medida para Nintendo DS."},
        {"title": "Advance Wars: Dual Strike", "consoleKey": "nds", "reason": "Estrategia táctica doble pantalla tan accesible como adictiva."},
        {"title": "Pikmin 2", "consoleKey": "gc", "reason": "Estrategia encantadora en tiempo real explorando jardines en GameCube."},
    ],
}


def _norm_title(val: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", (val or "").lower()).strip()


def _extract_franchise_key(title: str) -> str:
    m = _FRANCHISE_RE.search(_norm_title(title))
    return m.group(1).lower() if m else ""


def infer_item_genres(item: dict[str, Any]) -> list[str]:
    matched: list[str] = []
    seen: set[str] = set()

    def _add(g: str) -> None:
        if g in GENRE_LABELS and g not in seen:
            seen.add(g)
            matched.append(g)

    meta_genre = str(item.get("genre") or "").lower()
    meta_desc = str(item.get("description") or "").lower()
    meta_blob = f"{meta_genre} {meta_desc}".strip()
    if meta_genre:
        for genre_key, hints in _METADATA_GENRE_HINTS:
            if any(h in meta_genre for h in hints):
                _add(genre_key)

    title_text = f"{item.get('cleanTitle') or ''} {item.get('title') or ''} {item.get('relativePath') or ''}"
    for genre_key, pattern in _COMPILED_GENRE_PATTERNS:
        if pattern.search(title_text):
            _add(genre_key)

    if not matched and meta_blob:
        for genre_key, hints in _METADATA_GENRE_HINTS:
            if any(h in meta_blob for h in hints):
                _add(genre_key)

    return matched


def _console_label(console_key: str) -> str:
    ck = str(console_key or "").lower()
    if ck in CONSOLES:
        return CONSOLES[ck]["full"]
    return ck.upper() or "Retro"


def _select_game_of_the_week(base: list[dict[str, Any]]) -> dict[str, Any] | None:
    if not base:
        return None
    covered = [x for x in base if x.get("hasCover")]
    pool = covered if len(covered) >= 3 else base
    pool_sorted = sorted(pool, key=lambda x: str(x.get("entryId") or ""))
    iso_year, iso_week, _ = dt.date.today().isocalendar()
    digest = hashlib.sha256(f"{iso_year}-W{iso_week}".encode("utf-8")).digest()
    idx = int.from_bytes(digest[:4], "big") % len(pool_sorted)
    pick = pool_sorted[idx]
    genres = infer_item_genres(pick)
    genre_label = GENRE_LABELS[genres[0]] if genres else _console_label(str(pick.get("consoleKey") or ""))
    return {
        "entryId": pick["entryId"],
        "title": pick.get("cleanTitle") or pick.get("title") or "",
        "consoleKey": pick.get("consoleKey") or "other",
        "reason": f"Selección de la semana ({iso_year}-W{iso_week:02d}) · {genre_label}",
        "weekLabel": f"Semana {iso_week}",
    }


def _recency_bonus(last_played_at: Any) -> float:
    raw = str(last_played_at or "").strip()
    if not raw:
        return 0.0
    try:
        parsed = dt.datetime.fromisoformat(raw.replace("Z", "+00:00"))
        now = dt.datetime.now(parsed.tzinfo or dt.timezone.utc)
        hours = max(0.0, (now - parsed).total_seconds() / 3600.0)
        if hours <= 6:
            return 6.0
        if hours <= 48:
            return 3.5
        if hours <= 168:
            return 1.5
    except Exception:
        pass
    return 0.5


def _diversify_pool(
    items: list[dict[str, Any]],
    *,
    max_per_console: int = 2,
    max_per_franchise: int = 1,
    batch_size: int = 6,
) -> list[dict[str, Any]]:
    """Order ranked top candidates into diverse batches using O(N) precomputed metadata."""
    max_diversify = min(len(items), 300)
    head = [
        (
            it,
            str(it.get("consoleKey") or "").lower(),
            _extract_franchise_key(str(it.get("cleanTitle") or it.get("title") or "")),
        )
        for it in items[:max_diversify]
    ]
    ordered: list[dict[str, Any]] = []
    remaining = head
    lookahead = max(24, batch_size * 10)
    while remaining:
        batch_consoles: dict[str, int] = {}
        batch_franchises: dict[str, int] = {}
        picked_indices: set[int] = set()
        scan_cap = min(len(remaining), lookahead)
        for idx in range(scan_cap):
            item, ck, fr = remaining[idx]
            if batch_consoles.get(ck, 0) >= max_per_console:
                continue
            if fr and batch_franchises.get(fr, 0) >= max_per_franchise:
                continue
            picked_indices.add(idx)
            ordered.append(item)
            batch_consoles[ck] = batch_consoles.get(ck, 0) + 1
            if fr:
                batch_franchises[fr] = batch_franchises.get(fr, 0) + 1
            if len(picked_indices) >= batch_size:
                break
        if len(picked_indices) < min(batch_size, len(remaining)):
            for idx in range(min(len(remaining), batch_size * 2)):
                if idx in picked_indices:
                    continue
                picked_indices.add(idx)
                ordered.append(remaining[idx][0])
                if len(picked_indices) >= batch_size:
                    break
        remaining = [row for idx, row in enumerate(remaining) if idx not in picked_indices]
    if max_diversify < len(items):
        ordered.extend(items[max_diversify:])
    return ordered


def _slice_with_exclusions(
    pool: list[dict[str, Any]],
    *,
    offset: int = 0,
    limit: int = 6,
    exclude_ids: set[str] | None = None,
) -> tuple[list[dict[str, Any]], int, bool]:
    """Return (items, remaining_unseen_after_slice, cycled) without duplicating pagination logic."""
    if not pool:
        return [], 0, False
    excl = exclude_ids or set()
    unseen = [it for it in pool if str(it.get("entryId") or "") not in excl]
    if excl:
        if len(unseen) >= limit:
            chosen = unseen[:limit]
            return chosen, max(0, len(unseen) - len(chosen)), False
        chosen = list(unseen)
        seen_now = {str(it.get("entryId") or "") for it in chosen}
        for it in pool:
            eid = str(it.get("entryId") or "")
            if eid in seen_now:
                continue
            chosen.append(it)
            seen_now.add(eid)
            if len(chosen) >= min(limit, len(pool)):
                break
        return chosen, 0, True

    start = (max(0, int(offset)) * max(1, limit)) % len(pool)
    rotated = pool[start:] + pool[:start]
    chosen = rotated[:limit]
    remaining = max(0, len(pool) - ((max(0, int(offset)) + 1) * max(1, limit)))
    cycled = len(pool) > 0 and ((max(0, int(offset)) * max(1, limit)) >= len(pool))
    return chosen, remaining, cycled


def compute_local_recommendations(
    library_items: list[dict[str, Any]],
    limit: int = 6,
    offset: int = 0,
    exclude_ids: set[str] | None = None,
    seed: int = 0,
    discover_limit: int = 8,
) -> dict[str, Any]:
    base = [it for it in library_items if not it.get("isSys")]
    if not base:
        return {
            "engine": "local",
            "engineLabel": "Recomendación Inteligente",
            "tasteProfile": [],
            "anchorTitle": "",
            "summary": "Agrega ROMs o marca partidas para activar recomendaciones a tu medida.",
            "gameOfTheWeek": None,
            "fromLibrary": [],
            "discoverCatalog": [],
            "hasMore": False,
            "poolTotal": 0,
            "remainingCount": 0,
            "cycled": False,
        }

    seeds: list[tuple[dict[str, Any], float]] = []
    for it in base:
        plays = int(it.get("playCount") or 0)
        fav = bool(it.get("favorite"))
        if plays > 0 or fav:
            weight = (plays * 2.4) + (5.0 if fav else 0.0) + _recency_bonus(it.get("lastPlayedAt")) + (1.2 if it.get("hasCover") else 0.0)
            seeds.append((it, weight))
    seeds.sort(key=lambda pair: pair[1], reverse=True)

    anchor_title = ""
    if seeds:
        anchor_title = str(seeds[0][0].get("cleanTitle") or seeds[0][0].get("title") or "")
    else:
        covered_seed = next((x for x in base if x.get("hasCover")), base[0])
        seeds = [(covered_seed, 1.0)]

    seed_profiles: list[dict[str, Any]] = []
    genre_scores: dict[str, float] = {}
    console_scores: dict[str, float] = {}
    top_seeds = seeds[:10]
    for rank_idx, (seed_item, raw_weight) in enumerate(top_seeds):
        rank_factor = 1.0 / (1.0 + 0.32 * rank_idx)
        effective_weight = raw_weight * rank_factor
        ck = str(seed_item.get("consoleKey") or "").lower()
        s_genres = infer_item_genres(seed_item)
        s_title = str(seed_item.get("cleanTitle") or seed_item.get("title") or "")
        s_franchise = _extract_franchise_key(s_title)
        seed_profiles.append(
            {
                "item": seed_item,
                "title": s_title,
                "consoleKey": ck,
                "family": _CONSOLE_FAMILIES.get(ck, ""),
                "genres": s_genres,
                "franchise": s_franchise,
                "weight": effective_weight,
                "isPrimary": rank_idx == 0,
            }
        )
        if ck:
            console_scores[ck] = console_scores.get(ck, 0.0) + effective_weight
        for g in s_genres:
            genre_scores[g] = genre_scores.get(g, 0.0) + effective_weight

    if not genre_scores:
        for it in base[:80]:
            for g in infer_item_genres(it):
                genre_scores[g] = genre_scores.get(g, 0.0) + 0.5
        if not genre_scores:
            genre_scores["platform"] = 1.0
            genre_scores["rpg"] = 0.8

    ranked_genres = [g for g, _ in sorted(genre_scores.items(), key=lambda kv: kv[1], reverse=True)]
    primary_genres = seed_profiles[0]["genres"] if seed_profiles else []
    ordered_taste_keys = list(dict.fromkeys(primary_genres + ranked_genres))
    taste_profile = [GENRE_LABELS[g] for g in ordered_taste_keys[:3] if g in GENRE_LABELS]

    seed_ids = {s["item"]["entryId"] for s in seed_profiles[:6]}
    seed_norms = {_norm_title(s["title"]) for s in seed_profiles[:6]}
    library_has_covers = any(bool(it.get("hasCover")) for it in base)

    scored_candidates: list[tuple[float, dict[str, Any], str, list[str]]] = []
    for it in base:
        eid = it["entryId"]
        plays = int(it.get("playCount") or 0)
        raw_title = str(it.get("cleanTitle") or it.get("title") or "")
        it_norm = _norm_title(raw_title)
        if (eid in seed_ids and plays > 0) or (it_norm and it_norm in seed_norms and int(seeds[0][0].get("playCount") or 0) > 0):
            continue

        it_genres = infer_item_genres(it)
        ck = str(it.get("consoleKey") or "").lower()
        fam = _CONSOLE_FAMILIES.get(ck, "")
        it_franchise = _extract_franchise_key(raw_title)
        low_full_title = str(it.get("title") or "").lower()

        best_pair_score = -1.0
        best_seed: dict[str, Any] | None = None
        best_shared_genre = ""
        best_shared_franchise = False
        total_score = 0.0

        for sp in seed_profiles:
            shared_genres = [g for g in it_genres if g in sp["genres"]]
            same_franchise = bool(it_franchise and sp["franchise"] and it_franchise == sp["franchise"])
            same_console = bool(ck and ck == sp["consoleKey"])
            same_family = bool(fam and fam == sp["family"])

            pair_score = 0.0
            if shared_genres:
                pair_score += sp["weight"] * (4.0 * len(shared_genres))
            if same_franchise:
                pair_score += sp["weight"] * 5.0 + 18.0
            if same_console:
                pair_score += sp["weight"] * 1.4 + (3.0 if shared_genres else 0.8)
            elif same_family and shared_genres:
                pair_score += sp["weight"] * 0.7

            if sp["isPrimary"] and (shared_genres or same_franchise):
                pair_score *= 1.35

            if pair_score > 0:
                total_score += pair_score * 0.65
            if pair_score > best_pair_score:
                best_pair_score = pair_score
                best_seed = sp
                best_shared_genre = shared_genres[0] if shared_genres else ""
                best_shared_franchise = same_franchise

        total_score += best_pair_score
        if it.get("hasCover"):
            total_score += 14.0
        elif library_has_covers:
            total_score -= 3.5

        if it.get("hasMetadata"):
            total_score += 2.0
        if it.get("description") or it.get("releaseYear"):
            total_score += 1.5
        if plays == 0:
            total_score += 2.2
        if low_full_title.count("+") >= 2 or _DEMO_PENALTY_RE.search(low_full_title):
            total_score -= 12.0

        if total_score <= 0:
            continue

        if best_seed and best_shared_franchise and best_shared_genre:
            reason = f"Misma saga que {best_seed['title']} · {GENRE_LABELS.get(best_shared_genre, _console_label(ck))}"
        elif best_seed and best_shared_genre:
            reason = f"Similar a {best_seed['title']} · {GENRE_LABELS.get(best_shared_genre, _console_label(ck))}"
        elif best_seed and best_seed["consoleKey"] == ck and it_genres:
            reason = f"Afinidad en {_console_label(ck)} · {GENRE_LABELS.get(it_genres[0], '')}".rstrip(" ·")
        elif it_genres:
            reason = f"Destacado en {GENRE_LABELS.get(it_genres[0], _console_label(ck))}"
        else:
            reason = f"Clásico disponible en {_console_label(ck)}"

        genre_labels = [GENRE_LABELS[g] for g in it_genres if g in GENRE_LABELS]
        scored_candidates.append((total_score, it, reason, genre_labels))

    scored_candidates.sort(
        key=lambda tup: (tup[0], bool(tup[1].get("hasCover")), bool(tup[1].get("hasMetadata"))),
        reverse=True,
    )

    deduped_pool: list[dict[str, Any]] = []
    seen_norms: set[str] = set()
    for _score, it, reason, genre_labels in scored_candidates:
        norm = _norm_title(it.get("cleanTitle") or it.get("title") or "")
        if norm in seen_norms:
            continue
        seen_norms.add(norm)
        deduped_pool.append({**it, "recReason": reason, "recGenres": genre_labels})

    if len(deduped_pool) < min(limit, len(base)):
        used_ids = {p["entryId"] for p in deduped_pool}
        fallback_pool = sorted(base, key=lambda x: (not bool(x.get("hasCover")), int(x.get("playCount") or 0)))
        for it in fallback_pool:
            if it["entryId"] in used_ids or it["entryId"] in seed_ids:
                continue
            genres = [GENRE_LABELS[g] for g in infer_item_genres(it) if g in GENRE_LABELS]
            deduped_pool.append(
                {
                    **it,
                    "recReason": f"Joyas por descubrir en {_console_label(str(it.get('consoleKey') or ''))}",
                    "recGenres": genres,
                }
            )
            used_ids.add(it["entryId"])

    diversified_pool = _diversify_pool(deduped_pool, max_per_console=3, max_per_franchise=2, batch_size=max(1, limit))
    picked, remaining_count, cycled = _slice_with_exclusions(
        diversified_pool,
        offset=offset,
        limit=limit,
        exclude_ids=exclude_ids,
    )

    # Cross-console discovery suggestions not currently in local library
    local_norms = [_norm_title(it.get("cleanTitle") or it.get("title") or "") for it in base]
    all_discover: list[dict[str, Any]] = []
    seen_discover: set[str] = set()
    target_genres = ordered_taste_keys[:3] + [g for g in CURATED_CLASSICS if g not in ordered_taste_keys[:3]]
    for g in target_genres:
        matching_seed_title = next((sp["title"] for sp in seed_profiles if g in sp["genres"]), anchor_title)
        for classic in CURATED_CLASSICS.get(g, []):
            cnorm = _norm_title(classic["title"])
            if cnorm in seen_discover:
                continue
            if any(cnorm and (cnorm in ln or ln in cnorm) for ln in local_norms if len(ln) >= 5):
                continue
            seen_discover.add(cnorm)
            has_seed_match = any(g in sp["genres"] for sp in seed_profiles)
            prefix = (
                f"Porque te gusta {matching_seed_title} ({GENRE_LABELS.get(g, 'retro')}): "
                if matching_seed_title and has_seed_match
                else f"{GENRE_LABELS.get(g, 'Clásico')}: "
            )
            all_discover.append(
                {
                    "title": classic["title"],
                    "consoleKey": classic["consoleKey"],
                    "consoleName": _console_label(classic["consoleKey"]),
                    "genre": GENRE_LABELS.get(g, "Retro"),
                    "reason": prefix + classic["reason"],
                    "minervaQuery": classic["title"].split(":")[0],
                }
            )
    if all_discover:
        # Interleave genres + apply launch seed + offset so each launch rotates fresh cross-console ideas
        d_target = max(1, min(12, int(discover_limit or 8)))
        if seed:
            all_discover.sort(
                key=lambda d: hashlib.sha1(f"{seed}:{d['title']}".encode("utf-8")).digest()[0]
            )
        disc_start = (max(0, int(offset)) * d_target + max(0, int(seed))) % len(all_discover)
        disc_rot = all_discover[disc_start:] + all_discover[:disc_start]
        # Avoid repeating identical consoles adjacent when more consoles exist
        discover = _diversify_pool(
            [{**d, "entryId": d["title"]} for d in disc_rot],
            max_per_console=2,
            max_per_franchise=1,
            batch_size=d_target,
        )[:d_target]
        for d in discover:
            d.pop("entryId", None)
    else:
        discover = []

    if anchor_title and taste_profile:
        summary = f"Basado en tus partidas en {anchor_title} y afinidad por {' · '.join(taste_profile)}."
    elif taste_profile:
        summary = f"Afinidad detectada en {' · '.join(taste_profile)}."
    else:
        summary = "Selección curada de tu colección lista para jugar."

    return {
        "engine": "local",
        "engineLabel": "Recomendación Inteligente",
        "tasteProfile": taste_profile,
        "anchorTitle": anchor_title,
        "summary": summary,
        "gameOfTheWeek": _select_game_of_the_week(base),
        "fromLibrary": picked,
        "discoverCatalog": discover,
        "hasMore": len(diversified_pool) > limit,
        "poolTotal": len(diversified_pool),
        "remainingCount": remaining_count,
        "cycled": cycled,
    }


def _extract_json_block(raw: str) -> dict[str, Any]:
    text = (raw or "").strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text, flags=re.IGNORECASE | re.DOTALL).strip()
    return json.loads(text)


async def compute_ai_recommendations(
    library_items: list[dict[str, Any]],
    settings: dict[str, Any],
    limit: int = 6,
    offset: int = 0,
    exclude_ids: set[str] | None = None,
    seed: int = 0,
    discover_limit: int = 8,
) -> dict[str, Any]:
    local_res = compute_local_recommendations(
        library_items,
        limit=limit,
        offset=offset,
        exclude_ids=exclude_ids,
        seed=seed,
        discover_limit=discover_limit,
    )
    provider = str(settings.get("ai_provider") or "local").strip().lower()
    api_key = str(settings.get("ai_api_key") or "").strip()
    model = str(settings.get("ai_model") or "").strip()
    base_url = str(settings.get("ai_base_url") or "").strip()

    if provider == "local":
        return local_res
    if provider == "gemini" and not api_key:
        return {**local_res, "aiWarning": "Configura tu API Key de Gemini en Configuración > IA para recomendaciones con IA."}
    if provider == "openai" and not api_key and not base_url:
        return {**local_res, "aiWarning": "Configura tu API Key o endpoint OpenAI/Ollama en Configuración > IA."}

    base = [it for it in library_items if not it.get("isSys")]
    if not base:
        return local_res

    excl = exclude_ids or set()
    played_or_fav = sorted(
        [x for x in base if int(x.get("playCount") or 0) > 0 or x.get("favorite")],
        key=lambda x: (int(x.get("playCount") or 0), bool(x.get("favorite")), _recency_bonus(x.get("lastPlayedAt"))),
        reverse=True,
    )[:10]
    by_id = {x["entryId"]: x for x in base}
    played_ids = {x["entryId"] for x in played_or_fav}
    unseen_candidates = [
        x
        for x in sorted(base, key=lambda r: (not bool(r.get("hasCover")), int(r.get("playCount") or 0)))
        if x["entryId"] not in played_ids and x["entryId"] not in excl
    ]
    if len(unseen_candidates) < limit:
        unseen_candidates = [
            x
            for x in sorted(base, key=lambda r: (not bool(r.get("hasCover")), int(r.get("playCount") or 0)))
            if x["entryId"] not in played_ids
        ]
    candidates = [
        {
            "id": x["entryId"],
            "title": x.get("cleanTitle") or x.get("title"),
            "console": x.get("consoleKey"),
        }
        for x in unseen_candidates[:45]
    ]

    prompt_payload = {
        "jugados_y_favoritos": [
            {
                "title": x.get("cleanTitle") or x.get("title"),
                "console": x.get("consoleKey"),
                "plays": int(x.get("playCount") or 0),
                "favorite": bool(x.get("favorite")),
            }
            for x in played_or_fav
        ],
        "ronda": offset + 1,
        "biblioteca_candidatos": candidates,
    }
    system_prompt = (
        "Eres un curador experto en videojuegos retro en español. "
        "Analiza lo que el usuario juega y recomienda exactamente en JSON válido con esta estructura: "
        '{"summary": "frase breve del gusto del usuario", '
        '"tasteProfile": ["Género 1", "Género 2"], '
        '"libraryPicks": [{"entryId": "id exacto de biblioteca_candidatos", "reason": "por qué le va a encantar"}], '
        '"discoverPicks": [{"title": "clásico retro externo que no tenga", "consoleKey": "ps1|snes|arcade|gba|genesis|n64|dc", "reason": "motivo concreto en español"}]}. '
        "Elige hasta 6 libraryPicks y 4 discoverPicks evitando atribuir juegos de lucha a sagas de plataformas."
    )

    try:
        raw_content = ""
        async with httpx.AsyncClient(timeout=12.0) as client:
            if provider == "gemini":
                target_model = model or "gemini-2.5-flash"
                url = f"https://generativelanguage.googleapis.com/v1beta/models/{target_model}:generateContent?key={api_key}"
                resp = await client.post(
                    url,
                    json={
                        "system_instruction": {"parts": [{"text": system_prompt}]},
                        "contents": [{"parts": [{"text": json.dumps(prompt_payload, ensure_ascii=False)}]}],
                        "generationConfig": {"responseMimeType": "application/json", "temperature": 0.7},
                    },
                )
                resp.raise_for_status()
                data = resp.json()
                raw_content = data["candidates"][0]["content"]["parts"][0]["text"]
            else:
                endpoint = (base_url or "https://api.openai.com/v1").rstrip("/") + "/chat/completions"
                headers = {"Content-Type": "application/json"}
                if api_key:
                    headers["Authorization"] = f"Bearer {api_key}"
                resp = await client.post(
                    endpoint,
                    headers=headers,
                    json={
                        "model": model or "gpt-4o-mini",
                        "temperature": 0.7,
                        "messages": [
                            {"role": "system", "content": system_prompt},
                            {"role": "user", "content": json.dumps(prompt_payload, ensure_ascii=False)},
                        ],
                    },
                )
                resp.raise_for_status()
                data = resp.json()
                raw_content = data["choices"][0]["message"]["content"]

        parsed = _extract_json_block(raw_content)
        ai_lib: list[dict[str, Any]] = []
        used_ids: set[str] = set()
        for pick in parsed.get("libraryPicks") or []:
            eid = str(pick.get("entryId") or "").strip()
            if eid in by_id and eid not in used_ids:
                used_ids.add(eid)
                ai_lib.append({**by_id[eid], "recReason": str(pick.get("reason") or "Recomendado para tu estilo"), "recGenres": []})
            if len(ai_lib) >= limit:
                break
        if len(ai_lib) < limit:
            for it in local_res["fromLibrary"]:
                if it["entryId"] not in used_ids:
                    ai_lib.append(it)
                    used_ids.add(it["entryId"])
                if len(ai_lib) >= limit:
                    break

        ai_disc: list[dict[str, Any]] = []
        for dp in parsed.get("discoverPicks") or []:
            title = str(dp.get("title") or "").strip()
            if not title:
                continue
            ck = str(dp.get("consoleKey") or "arcade").strip().lower()
            ai_disc.append(
                {
                    "title": title,
                    "consoleKey": ck,
                    "consoleName": _console_label(ck),
                    "genre": "Selección curada",
                    "reason": str(dp.get("reason") or "Sugerido por tu historial"),
                    "minervaQuery": title.split(":")[0],
                }
            )
            if len(ai_disc) >= 4:
                break

        return {
            **local_res,
            "engine": provider,
            "engineLabel": "IA Gemini" if provider == "gemini" else "IA Personalizada",
            "summary": str(parsed.get("summary") or local_res["summary"]),
            "tasteProfile": [str(t) for t in (parsed.get("tasteProfile") or local_res["tasteProfile"])][:3],
            "fromLibrary": ai_lib,
            "discoverCatalog": ai_disc or local_res["discoverCatalog"],
        }
    except Exception as exc:
        return {**local_res, "aiWarning": f"Usando motor local (IA no respondió: {exc})"}


async def test_ai_provider(provider: str, api_key: str, model: str, base_url: str = "") -> dict[str, Any]:
    prov = (provider or "local").strip().lower()
    if prov == "local":
        return {"ok": True, "message": "Motor inteligente local activo (instantáneo y sin API Key)."}
    key = (api_key or "").strip()
    mdl = (model or "").strip()
    burl = (base_url or "").strip()
    if prov == "gemini":
        if not key:
            return {"ok": False, "message": "Ingresa una API Key de Google Gemini."}
        target_model = mdl or "gemini-2.5-flash"
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{target_model}:generateContent?key={key}"
        try:
            async with httpx.AsyncClient(timeout=8.0) as client:
                resp = await client.post(
                    url,
                    json={"contents": [{"parts": [{"text": "Responde solo: OK"}]}]},
                )
            if resp.status_code == 200:
                return {"ok": True, "message": f"Gemini conectado correctamente ({target_model})."}
            return {"ok": False, "message": f"Gemini devolvió HTTP {resp.status_code}."}
        except Exception as exc:
            return {"ok": False, "message": f"Error conectando a Gemini: {exc}"}

    if not key and not burl:
        return {"ok": False, "message": "Ingresa API Key o URL base compatible con OpenAI / Ollama."}
    endpoint = (burl or "https://api.openai.com/v1").rstrip("/") + "/chat/completions"
    headers = {"Content-Type": "application/json"}
    if key:
        headers["Authorization"] = f"Bearer {key}"
    try:
        async with httpx.AsyncClient(timeout=8.0) as client:
            resp = await client.post(
                endpoint,
                headers=headers,
                json={"model": mdl or "gpt-4o-mini", "messages": [{"role": "user", "content": "OK"}], "max_tokens": 5},
            )
        if resp.status_code == 200:
            return {"ok": True, "message": f"Proveedor IA conectado ({mdl or 'predeterminado'})."}
        return {"ok": False, "message": f"Endpoint IA devolvió HTTP {resp.status_code}."}
    except Exception as exc:
        return {"ok": False, "message": f"Error de conexión IA: {exc}"}


def compute_mood_picks(
    library_items: list[dict[str, Any]],
    mood: str = "quick",
    offset: int = 0,
    limit: int = 3,
    exclude_ids: set[str] | None = None,
) -> dict[str, Any]:
    base = [it for it in library_items if not it.get("isSys")]
    mood_key = (mood or "quick").strip().lower()
    mood_meta = {
        "quick": ("Partida rápida (15–30 min)", "15–30 min", {"fighting", "beatemup", "shooter", "racing", "puzzle"}),
        "story": ("Historia y RPG", "15–40 h", {"rpg", "action"}),
        "action": ("Plataformas y Acción", "2–8 h", {"platform", "beatemup", "shooter", "action"}),
        "surprise": ("Sorpréndeme", "Variable", set()),
    }.get(mood_key, ("Partida rápida (15–30 min)", "15–30 min", {"fighting", "beatemup", "shooter", "racing", "puzzle"}))

    mood_label, duration_hint, target_genres = mood_meta
    if not base:
        return {
            "mood": mood_key,
            "moodLabel": mood_label,
            "durationHint": duration_hint,
            "picks": [],
            "totalMatches": 0,
            "remainingCount": 0,
            "cycled": False,
        }

    scored: list[tuple[float, dict[str, Any], str]] = []
    for it in base:
        genres = infer_item_genres(it)
        genre_set = set(genres)
        ck = str(it.get("consoleKey") or "").lower()
        low_full_title = str(it.get("title") or "").lower()
        score = 0.0
        if mood_key == "surprise":
            # Deterministic yet varied per title hash so surprise crosses consoles cleanly
            h_byte = hashlib.sha1(str(it.get("entryId") or "").encode("utf-8")).digest()[0] / 255.0
            score += (6.0 if it.get("hasCover") else 0.8) + h_byte * 2.5
            if int(it.get("playCount") or 0) == 0:
                score += 1.8
        else:
            matches = genre_set & target_genres
            score += min(len(matches), 2) * 6.0
            if mood_key == "quick" and ck in ("arcade", "mame", "gba", "gb", "gbc"):
                score += 1.6 if matches else 1.2
            elif mood_key == "story" and ck in ("snes", "ps1", "ps2", "gba", "nds", "gc"):
                score += 1.4 if matches else 0.6
            elif mood_key == "action" and ck in ("snes", "genesis", "gba", "ps1", "n64"):
                score += 1.2 if matches else 0.5
            if it.get("hasCover"):
                score += 5.0
            if int(it.get("playCount") or 0) == 0:
                score += 1.0
            if score <= 0:
                score = 0.3 + (2.0 if it.get("hasCover") else 0.0)
        if low_full_title.count("+") >= 2 or _DEMO_PENALTY_RE.search(low_full_title):
            score -= 12.0

        g_label = next((GENRE_LABELS[g] for g in genres if g in GENRE_LABELS), _console_label(ck))
        reason = f"{g_label} · listo en {_console_label(ck)}"
        scored.append((score, it, reason))

    scored.sort(key=lambda tup: (tup[0], bool(tup[1].get("hasCover")), tup[1].get("title", "")), reverse=True)

    deduped: list[dict[str, Any]] = []
    seen_norms: set[str] = set()
    max_pool = min(len(scored), 400)
    for _score, it, reason in scored:
        norm = _norm_title(it.get("cleanTitle") or it.get("title") or "")
        if norm in seen_norms:
            continue
        seen_norms.add(norm)
        deduped.append({**it, "durationHint": duration_hint, "moodReason": reason})
        if len(deduped) >= max_pool:
            break

    if not deduped:
        return {
            "mood": mood_key,
            "moodLabel": mood_label,
            "durationHint": duration_hint,
            "picks": [],
            "totalMatches": 0,
            "remainingCount": 0,
            "cycled": False,
        }

    diversified = _diversify_pool(deduped, max_per_console=1, max_per_franchise=1, batch_size=max(3, limit))
    picks, remaining_count, cycled = _slice_with_exclusions(
        diversified,
        offset=offset,
        limit=limit,
        exclude_ids=exclude_ids,
    )
    return {
        "mood": mood_key,
        "moodLabel": mood_label,
        "durationHint": duration_hint,
        "picks": picks,
        "totalMatches": len(diversified),
        "remainingCount": remaining_count,
        "cycled": cycled,
    }

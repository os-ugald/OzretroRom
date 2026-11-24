from __future__ import annotations

import hashlib
import re
import time
from pathlib import Path
from typing import Any

from .config import CONSOLES


def _norm(val: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", (val or "").lower()).strip()


def _ext_cover_id(key: str) -> str:
    return "ext-" + hashlib.sha256(key.encode("utf-8")).hexdigest()[:20]


def _cover_url_if_exists(path: Path) -> str | None:
    if not path.is_file():
        return None
    try:
        ver = int(path.stat().st_mtime)
    except OSError:
        ver = int(time.time())
    return f"/api/v1/covers/{path.name}?v={ver}"


def _clean_match_text(item: dict[str, Any]) -> tuple[str, str]:
    raw_title = str(item.get("title") or "")
    clean = str(item.get("cleanTitle") or raw_title)
    # Strip trailing parenthetical region/rev tags & arcade/SNES hash markers while keeping subtitles
    stripped = re.sub(r"\s*#.*$", "", clean).strip()
    norm_clean = _norm(stripped)
    norm_raw = _norm(raw_title)
    return norm_clean, norm_raw


def _matches_any(patterns: tuple[str, ...], *texts: str) -> bool:
    for pat in patterns:
        rx = re.compile(pat, re.I)
        if any(rx.search(t) for t in texts if t):
            return True
    return False


CURATED_SAGAS: list[dict[str, Any]] = [
    {
        "id": "super-mario",
        "name": "Super Mario",
        "tagline": "Plataformas doradas, mundos RPG y el salto al 3D del Reino Champiñón.",
        "foundedYear": 1985,
        "creator": "Nintendo / Shigeru Miyamoto",
        "accent": "#F87171",
        "history": (
            "Nacida en 1985 en NES tras la aparición original de Jumpman en Donkey Kong, Super Mario definió para siempre "
            "el diseño de plataformas laterales con desplazamiento suave y secretos ocultos. Desde el salto maestro de "
            "Super Mario Bros. 3 y Super Mario World en SNES hasta la revolución tridimensional de Super Mario 64, "
            "la franquicia ha reinventado el género en cada generación sin perder su precisión milimétrica."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Super Mario World (USA)",
            "boxartName": "Super Mario World (USA)",
            "sgdbQuery": "Super Mario World",
        },
        "includeRegex": (r"\b(super mario|mario rpg|paper mario|yoshi s island|mario kart|mario galaxy)\b",),
        "excludeRegex": (r"\b(mario andretti|mario lemieux|mario is missing|mario s time machine)\b",),
        "timeline": [
            {
                "title": "Super Mario Bros.",
                "year": 1985,
                "consoleKey": "nes",
                "era": "Era 8-bit",
                "matchRegex": (r"\bsuper mario bros\b",),
                "excludeMatchRegex": (r"\b(new super|all stars|deluxe|lost levels|wii|ds|2|3)\b",),
                "preferConsoles": ("nes",),
                "minervaQuery": "Super Mario Bros",
            },
            {
                "title": "Super Mario Bros. 2",
                "year": 1988,
                "consoleKey": "nes",
                "era": "Era 8-bit",
                "matchRegex": (r"\bsuper mario bros 2\b",),
                "preferConsoles": ("nes", "gba"),
                "minervaQuery": "Super Mario Bros 2",
            },
            {
                "title": "Super Mario Bros. 3",
                "year": 1988,
                "consoleKey": "nes",
                "era": "Era 8-bit",
                "matchRegex": (r"\bsuper mario bros 3\b", r"\bsuper mario advance 4\b"),
                "preferConsoles": ("nes", "gba"),
                "minervaQuery": "Super Mario Bros 3",
            },
            {
                "title": "Super Mario World",
                "year": 1990,
                "consoleKey": "snes",
                "era": "Era 16-bit",
                "matchRegex": (r"\bsuper mario world\b", r"\bsuper mario advance 2\b"),
                "excludeMatchRegex": (r"\b(yoshi|2)\b",),
                "preferConsoles": ("snes", "gba"),
                "minervaQuery": "Super Mario World",
            },
            {
                "title": "Super Mario Kart",
                "year": 1992,
                "consoleKey": "snes",
                "era": "Spin-off Arcade",
                "matchRegex": (r"\bsuper mario kart\b",),
                "preferConsoles": ("snes",),
                "minervaQuery": "Super Mario Kart",
            },
            {
                "title": "Super Mario All-Stars",
                "year": 1993,
                "consoleKey": "snes",
                "era": "Compilación 16-bit",
                "matchRegex": (r"\bsuper mario all stars\b",),
                "preferConsoles": ("snes",),
                "allowCombo": True,
                "minervaQuery": "Super Mario All-Stars",
            },
            {
                "title": "Super Mario World 2: Yoshi's Island",
                "year": 1995,
                "consoleKey": "snes",
                "era": "Era 16-bit",
                "matchRegex": (r"\byoshi s? ?island\b",),
                "preferConsoles": ("snes", "gba"),
                "minervaQuery": "Yoshi's Island",
            },
            {
                "title": "Super Mario RPG: Legend of the Seven Stars",
                "year": 1996,
                "consoleKey": "snes",
                "era": "Sub-serie RPG",
                "matchRegex": (r"\bsuper mario rpg\b",),
                "preferConsoles": ("snes",),
                "minervaQuery": "Super Mario RPG",
            },
            {
                "title": "Super Mario 64",
                "year": 1996,
                "consoleKey": "n64",
                "era": "Revolución 3D",
                "matchRegex": (r"\bsuper mario 64\b",),
                "preferConsoles": ("n64", "nds"),
                "minervaQuery": "Super Mario 64",
            },
            {
                "title": "Paper Mario",
                "year": 2000,
                "consoleKey": "n64",
                "era": "Sub-serie RPG",
                "matchRegex": (r"^paper mario\b",),
                "excludeMatchRegex": (r"\b(thousand|ttyd|super|color|origami|sticker)\b",),
                "preferConsoles": ("n64",),
                "minervaQuery": "Paper Mario",
            },
            {
                "title": "Super Mario Sunshine",
                "year": 2002,
                "consoleKey": "gc",
                "era": "Era 128-bit",
                "matchRegex": (r"\bsuper mario sunshine\b",),
                "preferConsoles": ("gc",),
                "minervaQuery": "Super Mario Sunshine",
            },
            {
                "title": "New Super Mario Bros.",
                "year": 2006,
                "consoleKey": "nds",
                "era": "Renacimiento 2D",
                "matchRegex": (r"\bnew super mario bros\b",),
                "excludeMatchRegex": (r"\b(wii|2|u)\b",),
                "preferConsoles": ("nds",),
                "minervaQuery": "New Super Mario Bros",
            },
            {
                "title": "Super Mario Galaxy",
                "year": 2007,
                "consoleKey": "wii",
                "era": "Era Wii",
                "matchRegex": (r"\bsuper mario galaxy\b",),
                "excludeMatchRegex": (r"\b2\b",),
                "preferConsoles": ("wii",),
                "minervaQuery": "Super Mario Galaxy",
            },
            {
                "title": "New Super Mario Bros. Wii",
                "year": 2009,
                "consoleKey": "wii",
                "era": "Era Wii",
                "matchRegex": (r"\bnew super mario bros wii\b",),
                "preferConsoles": ("wii",),
                "minervaQuery": "New Super Mario Bros Wii",
            },
            {
                "title": "Mario Kart 64",
                "year": 1996,
                "consoleKey": "n64",
                "era": "Spin-off Arcade",
                "matchRegex": (r"\bmario kart 64\b",),
                "preferConsoles": ("n64",),
                "minervaQuery": "Mario Kart 64",
            },
            {
                "title": "Mario Kart: Super Circuit",
                "year": 2001,
                "consoleKey": "gba",
                "era": "Portátil GBA",
                "matchRegex": (r"\bmario kart.*super circuit\b",),
                "preferConsoles": ("gba",),
                "minervaQuery": "Mario Kart Super Circuit",
            },
            {
                "title": "Mario Kart Wii",
                "year": 2008,
                "consoleKey": "wii",
                "era": "Era Wii",
                "matchRegex": (r"\bmario kart wii\b",),
                "preferConsoles": ("wii",),
                "minervaQuery": "Mario Kart Wii",
            },
        ],
    },
    {
        "id": "the-legend-of-zelda",
        "name": "The Legend of Zelda",
        "tagline": "Calabozos, la Trifuerza y aventuras eternas en Hyrule y más allá.",
        "foundedYear": 1986,
        "creator": "Nintendo / Shigeru Miyamoto & Eiji Aonuma",
        "accent": "#39FF88",
        "history": (
            "Creada en 1986 para Famicom y NES, The Legend of Zelda sentó las bases de la acción y aventura no lineal con "
            "batería de guardado en cartucho. A Link to the Past (1991) consolidó la estructura de mundos duales, mientras "
            "Ocarina of Time (1998) y Majora's Mask (2000) inventaron la cámara Z-Targeting y el combate en 3D moderno. "
            "Entre portátiles y consolas de sobremesa, cada entrega combina exploración, música inmortal y rompecabezas."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Legend of Zelda, The - A Link to the Past (USA)",
            "boxartName": "Legend of Zelda, The - A Link to the Past (USA)",
            "sgdbQuery": "The Legend of Zelda: A Link to the Past",
        },
        "includeRegex": (r"\bzelda\b", r"\blink s awakening\b", r"\bocarina of time\b", r"\bmajora s mask\b", r"\bwind waker\b", r"\bminish cap\b", r"\btwilight princess\b"),
        "excludeRegex": (r"\b(cd i|wand of gamelon|faces of evil|zelda s adventure)\b",),
        "timeline": [
            {
                "title": "The Legend of Zelda",
                "year": 1986,
                "consoleKey": "nes",
                "era": "Era 8-bit",
                "matchRegex": (r"^(the )?legend of zelda( nes classics)?$", r"^legend of zelda the$"),
                "preferConsoles": ("nes",),
                "minervaQuery": "The Legend of Zelda",
            },
            {
                "title": "Zelda II: The Adventure of Link",
                "year": 1987,
                "consoleKey": "nes",
                "era": "Era 8-bit",
                "matchRegex": (r"\bzelda ii\b", r"\badventure of link\b"),
                "preferConsoles": ("nes",),
                "minervaQuery": "Adventure of Link",
            },
            {
                "title": "The Legend of Zelda: A Link to the Past",
                "year": 1991,
                "consoleKey": "snes",
                "era": "Era 16-bit",
                "matchRegex": (r"\blink to the past\b",),
                "preferConsoles": ("snes", "gba"),
                "minervaQuery": "Link to the Past",
            },
            {
                "title": "The Legend of Zelda: Link's Awakening",
                "year": 1993,
                "consoleKey": "gbc",
                "era": "Portátil Clásico",
                "matchRegex": (r"\blink s? awakening\b",),
                "preferConsoles": ("gbc", "gb"),
                "minervaQuery": "Link's Awakening",
            },
            {
                "title": "The Legend of Zelda: Ocarina of Time",
                "year": 1998,
                "consoleKey": "n64",
                "era": "Era 64-bit",
                "matchRegex": (r"\bocarina of time\b",),
                "preferConsoles": ("n64", "gc", "3ds"),
                "minervaQuery": "Ocarina of Time",
            },
            {
                "title": "The Legend of Zelda: Majora's Mask",
                "year": 2000,
                "consoleKey": "n64",
                "era": "Era 64-bit",
                "matchRegex": (r"\bmajora s? mask\b",),
                "preferConsoles": ("n64", "3ds"),
                "minervaQuery": "Majora's Mask",
            },
            {
                "title": "The Legend of Zelda: Oracle of Seasons / Ages",
                "year": 2001,
                "consoleKey": "gbc",
                "era": "Portátil GBC",
                "matchRegex": (r"\boracle of (seasons|ages)\b",),
                "preferConsoles": ("gbc",),
                "minervaQuery": "Oracle of Seasons",
            },
            {
                "title": "The Legend of Zelda: The Wind Waker",
                "year": 2002,
                "consoleKey": "gc",
                "era": "Era GameCube",
                "matchRegex": (r"\bwind waker\b",),
                "preferConsoles": ("gc", "wiiu"),
                "minervaQuery": "Wind Waker",
            },
            {
                "title": "The Legend of Zelda: The Minish Cap",
                "year": 2004,
                "consoleKey": "gba",
                "era": "Portátil GBA",
                "matchRegex": (r"\bminish cap\b",),
                "preferConsoles": ("gba",),
                "minervaQuery": "Minish Cap",
            },
            {
                "title": "The Legend of Zelda: Twilight Princess",
                "year": 2006,
                "consoleKey": "gc",
                "era": "Era GameCube / Wii",
                "matchRegex": (r"\btwilight princess\b",),
                "preferConsoles": ("gc", "wii"),
                "minervaQuery": "Twilight Princess",
            },
        ],
    },
    {
        "id": "sonic",
        "name": "Sonic the Hedgehog",
        "tagline": "Velocidad supersónica, física de momentum y loops icónicos en Sega.",
        "foundedYear": 1991,
        "creator": "Sonic Team / Yuji Naka & Naoto Ohshima",
        "accent": "#60A5FA",
        "history": (
            "Debutando en 1991 como el arma secreta de Sega Mega Drive para competir cara a cara con Nintendo, Sonic "
            "conquistó al mundo con físicas de pendiente, loops vertiginosos y música inolvidable. Su época dorada "
            "en 16 bits culminó con la tecnología Lock-On de Sonic 3 & Knuckles y la ambición animada de Sonic CD, "
            "dando paso luego al salto 3D de Dreamcast con Sonic Adventure y al retorno 2D portátil en Game Boy Advance."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Sonic The Hedgehog (USA, Europe)",
            "boxartName": "Sonic The Hedgehog (USA, Europe)",
            "sgdbQuery": "Sonic the Hedgehog",
        },
        "includeRegex": (r"\bsonic\b",),
        "excludeRegex": (r"\b(aerobiz|supersonic|dragon ball|sonic blast man|sonic wings|aero fighters|sonic crackers|sonic eraser)\b",),
        "timeline": [
            {
                "title": "Sonic the Hedgehog",
                "year": 1991,
                "consoleKey": "genesis",
                "era": "Mega Drive 16-bit",
                "matchRegex": (r"^sonic the hedgehog$",),
                "preferConsoles": ("genesis", "sms"),
                "minervaQuery": "Sonic the Hedgehog",
            },
            {
                "title": "Sonic the Hedgehog 2",
                "year": 1992,
                "consoleKey": "genesis",
                "era": "Mega Drive 16-bit",
                "matchRegex": (r"^sonic the hedgehog 2$", r"^sonic 2$"),
                "preferConsoles": ("genesis", "sms"),
                "minervaQuery": "Sonic the Hedgehog 2",
            },
            {
                "title": "Sonic CD",
                "year": 1993,
                "consoleKey": "segacd",
                "era": "Sega CD",
                "matchRegex": (r"\bsonic cd\b",),
                "preferConsoles": ("segacd",),
                "minervaQuery": "Sonic CD",
            },
            {
                "title": "Sonic Spinball",
                "year": 1993,
                "consoleKey": "genesis",
                "era": "Spin-off Pinball",
                "matchRegex": (r"\bsonic( the hedgehog)? spinball\b",),
                "preferConsoles": ("genesis", "gg"),
                "minervaQuery": "Sonic Spinball",
            },
            {
                "title": "Sonic the Hedgehog 3",
                "year": 1994,
                "consoleKey": "genesis",
                "era": "Mega Drive 16-bit",
                "matchRegex": (r"\bsonic( the hedgehog)? 3\b",),
                "excludeMatchRegex": (r"\b3d|advance\b",),
                "preferConsoles": ("genesis",),
                "minervaQuery": "Sonic the Hedgehog 3",
            },
            {
                "title": "Sonic & Knuckles",
                "year": 1994,
                "consoleKey": "genesis",
                "era": "Mega Drive Lock-On",
                "matchRegex": (r"^sonic (and )?knuckles$",),
                "excludeMatchRegex": (r"\b(hedgehog|2|3)\b",),
                "preferConsoles": ("genesis",),
                "minervaQuery": "Sonic & Knuckles",
            },
            {
                "title": "Sonic Compilation / Classics",
                "year": 1995,
                "consoleKey": "genesis",
                "era": "Compilación Clásica",
                "matchRegex": (r"\bsonic compilation\b", r"\bsonic classics\b"),
                "preferConsoles": ("genesis",),
                "allowCombo": True,
                "minervaQuery": "Sonic Compilation",
            },
            {
                "title": "Sonic 3D Blast",
                "year": 1996,
                "consoleKey": "genesis",
                "era": "Isométrico",
                "matchRegex": (r"\bsonic 3d( blast| flickies)?\b",),
                "preferConsoles": ("genesis", "saturn"),
                "minervaQuery": "Sonic 3D Blast",
            },
            {
                "title": "Sonic Adventure",
                "year": 1998,
                "consoleKey": "dc",
                "era": "Dreamcast 3D",
                "matchRegex": (r"\bsonic adventure\b",),
                "excludeMatchRegex": (r"\b2\b",),
                "preferConsoles": ("dc", "gc"),
                "minervaQuery": "Sonic Adventure",
            },
            {
                "title": "Sonic Adventure 2",
                "year": 2001,
                "consoleKey": "dc",
                "era": "Dreamcast / GC",
                "matchRegex": (r"\bsonic adventure 2\b",),
                "preferConsoles": ("dc", "gc"),
                "minervaQuery": "Sonic Adventure 2",
            },
            {
                "title": "Sonic Advance",
                "year": 2001,
                "consoleKey": "gba",
                "era": "Portátil GBA",
                "matchRegex": (r"\bsonic advance\b",),
                "excludeMatchRegex": (r"\b(2|3)\b",),
                "preferConsoles": ("gba",),
                "minervaQuery": "Sonic Advance",
            },
            {
                "title": "Sonic Advance 2",
                "year": 2002,
                "consoleKey": "gba",
                "era": "Portátil GBA",
                "matchRegex": (r"\bsonic advance 2\b",),
                "preferConsoles": ("gba",),
                "minervaQuery": "Sonic Advance 2",
            },
            {
                "title": "Sonic Battle",
                "year": 2003,
                "consoleKey": "gba",
                "era": "Portátil GBA",
                "matchRegex": (r"\bsonic battle\b",),
                "preferConsoles": ("gba",),
                "minervaQuery": "Sonic Battle",
            },
            {
                "title": "Sonic Heroes",
                "year": 2003,
                "consoleKey": "gc",
                "era": "Era 128-bit",
                "matchRegex": (r"\bsonic heroes\b",),
                "preferConsoles": ("gc", "ps2"),
                "minervaQuery": "Sonic Heroes",
            },
            {
                "title": "Sonic Advance 3",
                "year": 2004,
                "consoleKey": "gba",
                "era": "Portátil GBA",
                "matchRegex": (r"\bsonic advance 3\b",),
                "preferConsoles": ("gba",),
                "minervaQuery": "Sonic Advance 3",
            },
        ],
    },
    {
        "id": "mega-man",
        "name": "Mega Man / X / Zero",
        "tagline": "Jefes robóticos, armas copiables y acción de precisión desde 1987.",
        "foundedYear": 1987,
        "creator": "Capcom / Akira Kitamura & Keiji Inafune",
        "accent": "#38BDF8",
        "history": (
            "Mega Man (Rockman en Japón) debutó en NES en 1987 imponiendo un diseño revolucionario: elegir el orden de los "
            "Robot Masters para robar sus armas y descubrir debilidades elementales. En 1993 nació Mega Man X en SNES con "
            "dash en paredes, armaduras ocultas y un tono más oscuro, seguido en 2002 por la exigente subsaga Mega Man Zero "
            "en Game Boy Advance desarrollada junto a Inti Creates."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Mega Man X (USA)",
            "boxartName": "Mega Man X (USA)",
            "sgdbQuery": "Mega Man X",
        },
        "includeRegex": (r"\b(mega ?man|rockman)\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Mega Man", "year": 1987, "consoleKey": "nes", "era": "Serie Clásica", "matchRegex": (r"^(mega ?man|rockman)( 1| i)?$",), "excludeMatchRegex": (r"\b(x|zero|ii|iii|iv|v|vi|vii|2|3|4|5|6|7|8|wily|soccer|battle)\b",), "preferConsoles": ("nes",), "minervaQuery": "Mega Man"},
            {"title": "Mega Man 2", "year": 1988, "consoleKey": "nes", "era": "Serie Clásica", "matchRegex": (r"^(mega ?man|rockman) (2|ii)$",), "preferConsoles": ("nes",), "minervaQuery": "Mega Man 2"},
            {"title": "Mega Man 3", "year": 1990, "consoleKey": "nes", "era": "Serie Clásica", "matchRegex": (r"^(mega ?man|rockman) (3|iii)$",), "preferConsoles": ("nes",), "minervaQuery": "Mega Man 3"},
            {"title": "Mega Man 4", "year": 1991, "consoleKey": "nes", "era": "Serie Clásica", "matchRegex": (r"^(mega ?man|rockman) (4|iv)$",), "preferConsoles": ("nes",), "minervaQuery": "Mega Man 4"},
            {"title": "Mega Man 5", "year": 1992, "consoleKey": "nes", "era": "Serie Clásica", "matchRegex": (r"^(mega ?man|rockman) (5|v)$",), "preferConsoles": ("nes",), "minervaQuery": "Mega Man 5"},
            {"title": "Mega Man 6", "year": 1993, "consoleKey": "nes", "era": "Serie Clásica", "matchRegex": (r"^(mega ?man|rockman) (6|vi)$",), "preferConsoles": ("nes",), "minervaQuery": "Mega Man 6"},
            {"title": "Mega Man X", "year": 1993, "consoleKey": "snes", "era": "Serie X", "matchRegex": (r"^(mega ?man|rockman) x( 1)?$",), "excludeMatchRegex": (r"\b(2|3|4|5|6|7|8|command|maverick)\b",), "preferConsoles": ("snes", "psp"), "minervaQuery": "Mega Man X"},
            {"title": "Mega Man: The Wily Wars", "year": 1994, "consoleKey": "genesis", "era": "Remake 16-bit", "matchRegex": (r"\b(wily wars|mega world)\b",), "preferConsoles": ("genesis",), "allowCombo": True, "minervaQuery": "Mega Man Wily Wars"},
            {"title": "Mega Man's Soccer", "year": 1994, "consoleKey": "snes", "era": "Spin-off 16-bit", "matchRegex": (r"\bmega ?man s? soccer\b",), "preferConsoles": ("snes",), "minervaQuery": "Mega Man Soccer"},
            {"title": "Mega Man X2", "year": 1994, "consoleKey": "snes", "era": "Serie X", "matchRegex": (r"\b(mega ?man|rockman) x ?2\b",), "preferConsoles": ("snes",), "minervaQuery": "Mega Man X2"},
            {"title": "Mega Man 7", "year": 1995, "consoleKey": "snes", "era": "Serie Clásica", "matchRegex": (r"^(mega ?man|rockman) (7|vii)$",), "preferConsoles": ("snes",), "minervaQuery": "Mega Man 7"},
            {"title": "Mega Man X3", "year": 1995, "consoleKey": "snes", "era": "Serie X", "matchRegex": (r"\b(mega ?man|rockman) x ?3\b",), "preferConsoles": ("snes", "ps1"), "minervaQuery": "Mega Man X3"},
            {"title": "Mega Man 8", "year": 1996, "consoleKey": "ps1", "era": "Serie Clásica", "matchRegex": (r"^(mega ?man|rockman) (8|viii)\b",), "preferConsoles": ("ps1", "saturn"), "minervaQuery": "Mega Man 8"},
            {"title": "Mega Man X4", "year": 1997, "consoleKey": "ps1", "era": "Serie X", "matchRegex": (r"\b(mega ?man|rockman) x ?4\b",), "preferConsoles": ("ps1", "saturn"), "minervaQuery": "Mega Man X4"},
            {"title": "Mega Man X5", "year": 2000, "consoleKey": "ps1", "era": "Serie X", "matchRegex": (r"\b(mega ?man|rockman) x ?5\b",), "preferConsoles": ("ps1",), "minervaQuery": "Mega Man X5"},
            {"title": "Mega Man X6", "year": 2001, "consoleKey": "ps1", "era": "Serie X", "matchRegex": (r"\b(mega ?man|rockman) x ?6\b",), "preferConsoles": ("ps1",), "minervaQuery": "Mega Man X6"},
            {"title": "Mega Man Zero", "year": 2002, "consoleKey": "gba", "era": "Serie Zero", "matchRegex": (r"^(mega ?man|rockman) zero( 1)?$",), "excludeMatchRegex": (r"\b(2|3|4|collection|zx)\b",), "preferConsoles": ("gba",), "minervaQuery": "Mega Man Zero"},
            {"title": "Mega Man Zero 2", "year": 2003, "consoleKey": "gba", "era": "Serie Zero", "matchRegex": (r"\b(mega ?man|rockman) zero 2\b",), "preferConsoles": ("gba",), "minervaQuery": "Mega Man Zero 2"},
            {"title": "Mega Man Zero 3", "year": 2004, "consoleKey": "gba", "era": "Serie Zero", "matchRegex": (r"\b(mega ?man|rockman) zero 3\b",), "preferConsoles": ("gba",), "minervaQuery": "Mega Man Zero 3"},
            {"title": "Mega Man Zero 4", "year": 2005, "consoleKey": "gba", "era": "Serie Zero", "matchRegex": (r"\b(mega ?man|rockman) zero 4\b",), "preferConsoles": ("gba",), "minervaQuery": "Mega Man Zero 4"},
        ],
    },
    {
        "id": "castlevania",
        "name": "Castlevania",
        "tagline": "Cazadores de vampiros del clan Belmont y exploraciones góticas en Castlevania.",
        "foundedYear": 1986,
        "creator": "Konami / Hitoshi Akamatsu & Koji Igarashi",
        "accent": "#C4B5FD",
        "history": (
            "Akumajō Dracula nació en 1986 en Famicom Disk System y NES como una exigente cruzada gótica contra el Conde "
            "Drácula armada con el látigo Vampire Killer. Tras obras cumbres lineales como Castlevania III, Super Castlevania IV "
            "y Rondo of Blood, Koji Igarashi reinventó la saga en 1997 con Symphony of the Night en PS1, fusionando rol y "
            "mapas interconectados para dar nombre al subgénero Metroidvania continuado en GBA y Nintendo DS."
        ),
        "heroArt": {
            "repo": "Sony_-_PlayStation",
            "titleName": "Castlevania - Symphony of the Night (USA)",
            "boxartName": "Castlevania - Symphony of the Night (USA)",
            "sgdbQuery": "Castlevania: Symphony of the Night",
        },
        "includeRegex": (r"\b(castlevania|akumajou dracula|akumajo dracula|dracula x)\b",),
        "excludeRegex": (r"\b(bram stoker|kid dracula|hotel transylvania)\b",),
        "timeline": [
            {"title": "Castlevania", "year": 1986, "consoleKey": "nes", "era": "Clásico Lineal", "matchRegex": (r"^castlevania( chronicle)?$", r"^akumajou? dracula nes$"), "excludeMatchRegex": (r"\b(ii|iii|iv|2|3|4|64|chronicles|bloodlines|dracula x|symphony|circle|harmony|aria|dawn|portrait|order|new generation)\b",), "preferConsoles": ("nes",), "minervaQuery": "Castlevania"},
            {"title": "Castlevania II: Simon's Quest", "year": 1987, "consoleKey": "nes", "era": "Clásico Lineal", "matchRegex": (r"\bsimon s? quest\b", r"\bcastlevania ii\b"), "preferConsoles": ("nes",), "minervaQuery": "Simon's Quest"},
            {"title": "Castlevania III: Dracula's Curse", "year": 1989, "consoleKey": "nes", "era": "Clásico Lineal", "matchRegex": (r"\bdracula s? curse\b", r"\bcastlevania iii\b"), "preferConsoles": ("nes",), "minervaQuery": "Dracula's Curse"},
            {"title": "Super Castlevania IV", "year": 1991, "consoleKey": "snes", "era": "Era 16-bit", "matchRegex": (r"\bsuper castlevania iv\b", r"\bcastlevania iv\b", r"^akumajou? dracula$"), "preferConsoles": ("snes",), "minervaQuery": "Super Castlevania IV"},
            {"title": "Castlevania: Rondo of Blood", "year": 1993, "consoleKey": "pce", "era": "PC Engine CD", "matchRegex": (r"\brondo of blood\b", r"\bchi no rondo\b"), "preferConsoles": ("pce", "psp"), "minervaQuery": "Rondo of Blood"},
            {"title": "Castlevania: Bloodlines", "year": 1994, "consoleKey": "genesis", "era": "Mega Drive 16-bit", "matchRegex": (r"\bbloodlines\b", r"\bnew generation\b", r"\bvampire killer\b"), "preferConsoles": ("genesis",), "minervaQuery": "Bloodlines"},
            {"title": "Castlevania: Dracula X", "year": 1995, "consoleKey": "snes", "era": "Era 16-bit", "matchRegex": (r"\bdracula x\b", r"\bvampire s? kiss\b"), "excludeMatchRegex": (r"\bsymphony\b",), "preferConsoles": ("snes",), "minervaQuery": "Dracula X"},
            {"title": "Castlevania: Symphony of the Night", "year": 1997, "consoleKey": "ps1", "era": "Metroidvania", "matchRegex": (r"\bsymphony of the night\b", r"\bnocturne in the moonlight\b"), "preferConsoles": ("ps1", "saturn"), "minervaQuery": "Symphony of the Night"},
            {"title": "Castlevania: Circle of the Moon", "year": 2001, "consoleKey": "gba", "era": "Metroidvania GBA", "matchRegex": (r"\bcircle of the moon\b",), "preferConsoles": ("gba",), "minervaQuery": "Circle of the Moon"},
            {"title": "Castlevania: Harmony of Dissonance", "year": 2002, "consoleKey": "gba", "era": "Metroidvania GBA", "matchRegex": (r"\bharmony of dissonance\b",), "preferConsoles": ("gba",), "minervaQuery": "Harmony of Dissonance"},
            {"title": "Castlevania: Aria of Sorrow", "year": 2003, "consoleKey": "gba", "era": "Metroidvania GBA", "matchRegex": (r"\baria of sorrow\b",), "preferConsoles": ("gba",), "minervaQuery": "Aria of Sorrow"},
            {"title": "Castlevania: Dawn of Sorrow", "year": 2005, "consoleKey": "nds", "era": "Metroidvania DS", "matchRegex": (r"\bdawn of sorrow\b",), "preferConsoles": ("nds",), "minervaQuery": "Dawn of Sorrow"},
            {"title": "Castlevania: Portrait of Ruin", "year": 2006, "consoleKey": "nds", "era": "Metroidvania DS", "matchRegex": (r"\bportrait of ruin\b",), "preferConsoles": ("nds",), "minervaQuery": "Portrait of Ruin"},
            {"title": "Castlevania: Order of Ecclesia", "year": 2008, "consoleKey": "nds", "era": "Metroidvania DS", "matchRegex": (r"\border of ecclesia\b",), "preferConsoles": ("nds",), "minervaQuery": "Order of Ecclesia"},
        ],
    },
    {
        "id": "resident-evil",
        "name": "Resident Evil",
        "tagline": "Survival horror biológico desde la mansión Spencer hasta Raccoon City y Europa.",
        "foundedYear": 1996,
        "creator": "Capcom / Shinji Mikami & Hideki Kamiya",
        "accent": "#F87171",
        "history": (
            "Lanzado como Biohazard en Japón en 1996, Resident Evil popularizó mundialmente el término 'Survival Horror' "
            "con cámaras fijas cinematográficas, gestión estricta de munición y los oscuros experimentos de Umbrella. "
            "Tras la trilogía clásica de Raccoon City y Code: Veronica en Dreamcast, Resident Evil 4 (2005) transformó "
            "para siempre los juegos de acción con su perspectiva sobre el hombro y ritmo implacable."
        ),
        "heroArt": {
            "repo": "Sony_-_PlayStation",
            "titleName": "Resident Evil 2 (USA) (Disc 1)",
            "boxartName": "Resident Evil 2 (USA) (Disc 1)",
            "sgdbQuery": "Resident Evil 2",
        },
        "includeRegex": (r"\b(resident evil|biohazard)\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Resident Evil", "year": 1996, "consoleKey": "ps1", "era": "Trilogía PS1", "matchRegex": (r"^(resident evil|biohazard)( director s? cut)?( dual shock.*)?$",), "excludeMatchRegex": (r"\b(2|3|4|5|code|veronica|zero|survivor|outbreak)\b",), "preferConsoles": ("ps1", "gc"), "minervaQuery": "Resident Evil"},
            {"title": "Resident Evil 2", "year": 1998, "consoleKey": "ps1", "era": "Trilogía PS1", "matchRegex": (r"\b(resident evil|biohazard) 2\b",), "preferConsoles": ("ps1", "dc", "gc", "n64"), "minervaQuery": "Resident Evil 2"},
            {"title": "Resident Evil 3: Nemesis", "year": 1999, "consoleKey": "ps1", "era": "Trilogía PS1", "matchRegex": (r"\b(resident evil|biohazard) 3\b", r"\bnemesis\b"), "preferConsoles": ("ps1", "dc", "gc"), "minervaQuery": "Resident Evil 3"},
            {"title": "Resident Evil Code: Veronica", "year": 2000, "consoleKey": "dc", "era": "Dreamcast / PS2", "matchRegex": (r"\bcode ?veronica\b",), "preferConsoles": ("dc", "ps2", "gc"), "minervaQuery": "Code Veronica"},
            {"title": "Resident Evil Zero", "year": 2002, "consoleKey": "gc", "era": "Era GameCube", "matchRegex": (r"\b(resident evil|biohazard) (0|zero)\b",), "preferConsoles": ("gc", "wii"), "minervaQuery": "Resident Evil Zero"},
            {"title": "Resident Evil (Remake)", "year": 2002, "consoleKey": "gc", "era": "Era GameCube", "matchRegex": (r"\b(resident evil|biohazard) (remake|rebirth)\b",), "preferConsoles": ("gc",), "minervaQuery": "Resident Evil"},
            {"title": "Resident Evil Outbreak", "year": 2003, "consoleKey": "ps2", "era": "Era PS2", "matchRegex": (r"\boutbreak\b",), "preferConsoles": ("ps2",), "minervaQuery": "Resident Evil Outbreak"},
            {"title": "Resident Evil 4", "year": 2005, "consoleKey": "gc", "era": "Acción Moderna", "matchRegex": (r"\b(resident evil|biohazard) 4\b",), "preferConsoles": ("gc", "ps2", "wii"), "minervaQuery": "Resident Evil 4"},
        ],
    },
    {
        "id": "final-fantasy",
        "name": "Final Fantasy",
        "tagline": "Cristales, chocobos, invocaciones y las grandes epopeyas JRPG de Square.",
        "foundedYear": 1987,
        "creator": "Square / Hironobu Sakaguchi & Nobuo Uematsu",
        "accent": "#67E8F9",
        "history": (
            "Cuando Hironobu Sakaguchi dirigió el primer Final Fantasy en 1987 para NES junto a la música de Nobuo Uematsu "
            "y el arte de Yoshitaka Amano, inauguró la saga insignia del JRPG. Cada entrega numérica construye un universo "
            "independiente: desde los picos dramáticos de 16 bits en FF IV y FF VI hasta el fenómeno cinematográfico de "
            "PlayStation con Final Fantasy VII, VIII, IX y el salto de voz y emoción de Final Fantasy X."
        ),
        "heroArt": {
            "repo": "Sony_-_PlayStation",
            "titleName": "Final Fantasy VII (USA) (Disc 1)",
            "boxartName": "Final Fantasy VII (USA) (Disc 1)",
            "sgdbQuery": "Final Fantasy VII",
        },
        "includeRegex": (r"\bfinal fantasy\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Final Fantasy", "year": 1987, "consoleKey": "nes", "era": "Era 8-bit", "matchRegex": (r"^final fantasy( origins| anniversary| i)?$",), "excludeMatchRegex": (r"\b(ii|iii|iv|v|vi|vii|viii|ix|x|tactics|mystic|crystal|adventure|2|3|4|5|6|7|8|9|10)\b",), "preferConsoles": ("nes", "psp", "ps1"), "minervaQuery": "Final Fantasy"},
            {"title": "Final Fantasy II (JP)", "year": 1988, "consoleKey": "nes", "era": "Era 8-bit", "matchRegex": (r"^final fantasy (ii|2)$",), "excludeConsoles": ("snes",), "preferConsoles": ("nes", "psp"), "minervaQuery": "Final Fantasy II"},
            {"title": "Final Fantasy III (JP)", "year": 1990, "consoleKey": "nes", "era": "Era 8-bit", "matchRegex": (r"^final fantasy (iii|3)$",), "excludeConsoles": ("snes",), "preferConsoles": ("nes", "nds"), "minervaQuery": "Final Fantasy III"},
            {"title": "Final Fantasy IV (US: FF II)", "year": 1991, "consoleKey": "snes", "era": "Era 16-bit", "matchRegex": (r"\bfinal fantasy (iv|4)\b",), "snesAliasRegex": (r"^final fantasy (ii|2)$",), "preferConsoles": ("snes", "gba", "nds", "psp"), "minervaQuery": "Final Fantasy IV"},
            {"title": "Final Fantasy: Mystic Quest", "year": 1992, "consoleKey": "snes", "era": "Spin-off SNES", "matchRegex": (r"\bmyth?ic quest\b", r"\bfinal fantasy.*mystic quest\b"), "preferConsoles": ("snes",), "minervaQuery": "Final Fantasy Mystic Quest"},
            {"title": "Final Fantasy V", "year": 1992, "consoleKey": "snes", "era": "Era 16-bit", "matchRegex": (r"\bfinal fantasy (v|5)\b",), "preferConsoles": ("snes", "gba", "ps1"), "minervaQuery": "Final Fantasy V"},
            {"title": "Final Fantasy VI (US: FF III)", "year": 1994, "consoleKey": "snes", "era": "Era 16-bit", "matchRegex": (r"\bfinal fantasy (vi|6)\b",), "snesAliasRegex": (r"^final fantasy (iii|3)$",), "preferConsoles": ("snes", "gba", "ps1"), "minervaQuery": "Final Fantasy VI"},
            {"title": "Final Fantasy VII", "year": 1997, "consoleKey": "ps1", "era": "Trilogía PS1", "matchRegex": (r"\bfinal fantasy (vii|7)\b",), "preferConsoles": ("ps1",), "minervaQuery": "Final Fantasy VII"},
            {"title": "Final Fantasy Tactics", "year": 1997, "consoleKey": "ps1", "era": "Táctico", "matchRegex": (r"\bfinal fantasy tactics\b",), "preferConsoles": ("ps1", "psp", "gba"), "minervaQuery": "Final Fantasy Tactics"},
            {"title": "Final Fantasy VIII", "year": 1999, "consoleKey": "ps1", "era": "Trilogía PS1", "matchRegex": (r"\bfinal fantasy (viii|8)\b",), "preferConsoles": ("ps1",), "minervaQuery": "Final Fantasy VIII"},
            {"title": "Final Fantasy IX", "year": 2000, "consoleKey": "ps1", "era": "Trilogía PS1", "matchRegex": (r"\bfinal fantasy (ix|9)\b",), "preferConsoles": ("ps1",), "minervaQuery": "Final Fantasy IX"},
            {"title": "Final Fantasy X", "year": 2001, "consoleKey": "ps2", "era": "Era PS2", "matchRegex": (r"\bfinal fantasy (x|10)\b",), "excludeMatchRegex": (r"\bx ?2\b",), "preferConsoles": ("ps2",), "minervaQuery": "Final Fantasy X"},
            {"title": "Final Fantasy XII", "year": 2006, "consoleKey": "ps2", "era": "Era PS2", "matchRegex": (r"\bfinal fantasy (xii|12)\b",), "preferConsoles": ("ps2",), "minervaQuery": "Final Fantasy XII"},
        ],
    },
    {
        "id": "metroid",
        "name": "Metroid",
        "tagline": "Exploración atmosférica, secretos alienígenas y cazarrecompensas junto a Samus Aran.",
        "foundedYear": 1986,
        "creator": "Nintendo R&D1 / Yoshio Sakamoto & Gunpei Yokoi",
        "accent": "#F97316",
        "history": (
            "Metroid sorprendió en 1986 en NES con su mundo laberíntico interconectado, atmósfera aislada y revelación final "
            "de Samus Aran. Con Super Metroid (1994) en SNES fijó el estándar dorado de la exploración no lineal, antes "
            "de vivir un doble triunfo en 2002 con la tensión de Metroid Fusion en GBA y la maestría en primera persona "
            "de Metroid Prime en GameCube."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Super Metroid (Japan, USA) (En,Ja)",
            "boxartName": "Super Metroid (Japan, USA) (En,Ja)",
            "sgdbQuery": "Super Metroid",
        },
        "includeRegex": (r"\bmetroid\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Metroid", "year": 1986, "consoleKey": "nes", "era": "Era 8-bit", "matchRegex": (r"^metroid$",), "excludeConsoles": ("snes",), "preferConsoles": ("nes", "gba"), "minervaQuery": "Metroid"},
            {"title": "Metroid II: Return of Samus", "year": 1991, "consoleKey": "gb", "era": "Game Boy", "matchRegex": (r"\bmetroid ii\b", r"\breturn of samus\b"), "preferConsoles": ("gb", "3ds"), "minervaQuery": "Return of Samus"},
            {"title": "Super Metroid", "year": 1994, "consoleKey": "snes", "era": "Era 16-bit", "matchRegex": (r"\bsuper metroid\b",), "preferConsoles": ("snes",), "minervaQuery": "Super Metroid"},
            {"title": "Metroid Fusion", "year": 2002, "consoleKey": "gba", "era": "Portátil GBA", "matchRegex": (r"\bmetroid fusion\b",), "preferConsoles": ("gba",), "minervaQuery": "Metroid Fusion"},
            {"title": "Metroid Prime", "year": 2002, "consoleKey": "gc", "era": "Trilogía Prime", "matchRegex": (r"\bmetroid prime\b",), "excludeMatchRegex": (r"\b(2|echoes|3|corruption|hunters|pinball)\b",), "preferConsoles": ("gc", "wii"), "minervaQuery": "Metroid Prime"},
            {"title": "Metroid: Zero Mission", "year": 2004, "consoleKey": "gba", "era": "Remake GBA", "matchRegex": (r"\bzero mission\b",), "preferConsoles": ("gba",), "minervaQuery": "Zero Mission"},
            {"title": "Metroid Prime 2: Echoes", "year": 2004, "consoleKey": "gc", "era": "Trilogía Prime", "matchRegex": (r"\bechoes\b", r"\bmetroid prime 2\b"), "preferConsoles": ("gc", "wii"), "minervaQuery": "Metroid Prime 2"},
        ],
    },
    {
        "id": "donkey-kong",
        "name": "Donkey Kong Country",
        "tagline": "Barriles cañón, vagonetas y plataformas selváticas de Rare y Nintendo.",
        "foundedYear": 1981,
        "creator": "Nintendo & Rare / Gregg Mayles & Tim Stamper",
        "accent": "#FBBF24",
        "history": (
            "Tras nacer en los salones arcade de 1981 de la mano de Shigeru Miyamoto, el gorila más famoso del videojuego "
            "renació en 1994 cuando Rare deslumbró al mundo en Super Nintendo con gráficos ACM pre-renderizados en "
            "Donkey Kong Country. Su trilogía de 16 bits, acompañada por la banda sonora de David Wise, figura entre los "
            "plataformas más queridos de la historia."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Donkey Kong Country (USA)",
            "boxartName": "Donkey Kong Country (USA)",
            "sgdbQuery": "Donkey Kong Country",
        },
        "includeRegex": (r"\bdonkey kong\b", r"\bdiddy kong\b"),
        "excludeRegex": (),
        "timeline": [
            {"title": "Donkey Kong (Arcade / NES)", "year": 1981, "consoleKey": "nes", "era": "Origen Arcade", "matchRegex": (r"^donkey kong$",), "excludeMatchRegex": (r"\b(country|64|land|jr|returns|3)\b",), "preferConsoles": ("arcade", "nes"), "minervaQuery": "Donkey Kong"},
            {"title": "Donkey Kong Country", "year": 1994, "consoleKey": "snes", "era": "Trilogía Rare 16-bit", "matchRegex": (r"^donkey kong country( 1)?$",), "excludeMatchRegex": (r"\b(2|3|returns|tropical)\b",), "preferConsoles": ("snes", "gba"), "minervaQuery": "Donkey Kong Country"},
            {"title": "Donkey Kong Country 2: Diddy's Kong Quest", "year": 1995, "consoleKey": "snes", "era": "Trilogía Rare 16-bit", "matchRegex": (r"\bdonkey kong country 2\b", r"\bdiddy s? kong quest\b"), "preferConsoles": ("snes", "gba"), "minervaQuery": "Donkey Kong Country 2"},
            {"title": "Donkey Kong Country 3: Dixie Kong's Double Trouble!", "year": 1996, "consoleKey": "snes", "era": "Trilogía Rare 16-bit", "matchRegex": (r"\bdonkey kong country 3\b", r"\bdixie kong\b"), "preferConsoles": ("snes", "gba"), "minervaQuery": "Donkey Kong Country 3"},
            {"title": "Diddy Kong Racing", "year": 1997, "consoleKey": "n64", "era": "Era 64-bit", "matchRegex": (r"\bdiddy kong racing\b",), "preferConsoles": ("n64", "nds"), "minervaQuery": "Diddy Kong Racing"},
            {"title": "Donkey Kong 64", "year": 1999, "consoleKey": "n64", "era": "Era 64-bit", "matchRegex": (r"\bdonkey kong 64\b",), "preferConsoles": ("n64",), "minervaQuery": "Donkey Kong 64"},
            {"title": "DK: King of Swing", "year": 2005, "consoleKey": "gba", "era": "Spin-off GBA", "matchRegex": (r"\bking of swing\b",), "preferConsoles": ("gba",), "minervaQuery": "King of Swing"},
        ],
    },
    {
        "id": "street-fighter",
        "name": "Street Fighter & Capcom Vs.",
        "tagline": "El padre moderno de la lucha arcade 2D y los crossovers legendarios de Capcom.",
        "foundedYear": 1987,
        "creator": "Capcom / Takashi Nishiyama & Akira Yasuda",
        "accent": "#FBBF24",
        "history": (
            "En 1991 Street Fighter II desató una fiebre mundial en los salones arcade al establecer los combos, "
            "los movimientos especiales con palanca de 6 botones y un elenco internacional icónico con Ryu, Ken y Chun-Li. "
            "Capcom perfeccionó la fórmula técnica con las subsagas Alpha, Street Fighter III: 3rd Strike (famosa por su sistema "
            "Parry) y la explosiva línea tag-team frente a X-Men y Marvel."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Street Fighter II Turbo (USA)",
            "boxartName": "Street Fighter II Turbo (USA)",
            "sgdbQuery": "Street Fighter II Turbo",
        },
        "includeRegex": (r"\bstreet fighter\b", r"\bx men vs\b", r"\bmarvel super heroes vs\b", r"\bmarvel vs capcom\b", r"\bsuper gem fighter\b", r"\bcapcom vs snk\b"),
        "excludeRegex": (),
        "timeline": [
            {"title": "Street Fighter", "year": 1987, "consoleKey": "arcade", "era": "Origen Arcade", "matchRegex": (r"^street fighter$",), "preferConsoles": ("arcade", "pce"), "minervaQuery": "Street Fighter"},
            {
                "title": "Street Fighter II: The World Warrior / Champion",
                "year": 1991,
                "consoleKey": "arcade",
                "era": "CPS-1 Clásico",
                "matchRegex": (r"\bstreet fighter (ii|2)\b",),
                "excludeMatchRegex": (r"\b(super|alpha|ex|iii|3|vs)\b",),
                "preferConsoles": ("arcade", "snes", "genesis"),
                "minervaQuery": "Street Fighter II",
            },
            {
                "title": "Super Street Fighter II Turbo",
                "year": 1994,
                "consoleKey": "arcade",
                "era": "CPS-2 Clásico",
                "matchRegex": (r"\bsuper street fighter ii\b",),
                "preferConsoles": ("arcade", "snes", "dc"),
                "minervaQuery": "Super Street Fighter II",
            },
            {"title": "Street Fighter Alpha / Zero", "year": 1995, "consoleKey": "arcade", "era": "Sub-serie Alpha", "matchRegex": (r"\bstreet fighter (alpha|zero)( 1)?\b",), "excludeMatchRegex": (r"\b(2|3)\b",), "preferConsoles": ("arcade", "ps1"), "minervaQuery": "Street Fighter Alpha"},
            {"title": "Street Fighter Alpha 2", "year": 1996, "consoleKey": "arcade", "era": "Sub-serie Alpha", "matchRegex": (r"\bstreet fighter (alpha|zero) 2\b",), "preferConsoles": ("arcade", "snes", "ps1"), "minervaQuery": "Street Fighter Alpha 2"},
            {"title": "X-Men Vs. Street Fighter", "year": 1996, "consoleKey": "arcade", "era": "Crossover CPS-2", "matchRegex": (r"\bx men vs street fighter\b",), "preferConsoles": ("arcade", "saturn", "ps1"), "minervaQuery": "X-Men Vs. Street Fighter"},
            {"title": "Super Gem Fighter: Mini Mix", "year": 1997, "consoleKey": "arcade", "era": "Spin-off CPS-2", "matchRegex": (r"\bsuper gem fighter\b", r"\bpocket fighter\b"), "preferConsoles": ("arcade", "ps1"), "minervaQuery": "Super Gem Fighter"},
            {"title": "Marvel Super Heroes Vs. Street Fighter", "year": 1997, "consoleKey": "arcade", "era": "Crossover CPS-2", "matchRegex": (r"\bmarvel super heroes vs\b",), "preferConsoles": ("arcade", "ps1"), "minervaQuery": "Marvel Super Heroes Vs"},
            {"title": "Marvel Vs. Capcom: Clash of Super Heroes", "year": 1998, "consoleKey": "arcade", "era": "Crossover CPS-2", "matchRegex": (r"\bmarvel vs capcom\b",), "excludeMatchRegex": (r"\b2\b",), "preferConsoles": ("arcade", "dc", "ps1"), "minervaQuery": "Marvel vs Capcom"},
            {"title": "Street Fighter Alpha 3", "year": 1998, "consoleKey": "arcade", "era": "Sub-serie Alpha", "matchRegex": (r"\b(alpha|zero) 3\b",), "preferConsoles": ("arcade", "ps1", "dc", "gba"), "minervaQuery": "Street Fighter Alpha 3"},
            {"title": "Street Fighter III: 3rd Strike", "year": 1999, "consoleKey": "arcade", "era": "CPS-3 Obra Cumbre", "matchRegex": (r"\b(street fighter iii|3rd strike|third strike)\b",), "preferConsoles": ("arcade", "dc", "ps2"), "minervaQuery": "Street Fighter III"},
            {"title": "Marvel Vs. Capcom 2: New Age of Heroes", "year": 2000, "consoleKey": "dc", "era": "Crossover Naomi", "matchRegex": (r"\bmarvel vs capcom 2\b",), "preferConsoles": ("dc", "ps2"), "minervaQuery": "Marvel vs Capcom 2"},
            {"title": "Capcom vs. SNK: Millennium Fight 2000", "year": 2000, "consoleKey": "dc", "era": "Crossover Naomi", "matchRegex": (r"\bcapcom vs snk\b",), "excludeMatchRegex": (r"\b2\b",), "preferConsoles": ("dc", "arcade", "ps1"), "minervaQuery": "Capcom vs SNK"},
        ],
    },
    {
        "id": "mortal-kombat",
        "name": "Mortal Kombat",
        "tagline": "El torneo de Outworld, actores digitalizados, secretos y Fatalities.",
        "foundedYear": 1992,
        "creator": "Midway / Ed Boon & John Tobias",
        "accent": "#FF4D6D",
        "history": (
            "En 1992 Ed Boon y John Tobias revolucionaron los arcades con Mortal Kombat usando actores reales digitalizados, "
            "una mitología oscura de artes marciales y los infames Fatalities que impulsaron la creación del sistema ESRB. "
            "Mortal Kombat II (1993) y Ultimate Mortal Kombat 3 (1995) llevaron la jugabilidad 2D y los combos en cadena a su "
            "máxima expresión antes de su paso al 3D con Deadly Alliance."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Mortal Kombat II (USA)",
            "boxartName": "Mortal Kombat II (USA)",
            "sgdbQuery": "Mortal Kombat II",
        },
        "includeRegex": (r"\bmortal kombat\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Mortal Kombat", "year": 1992, "consoleKey": "arcade", "era": "Trilogía Arcade 2D", "matchRegex": (r"^mortal kombat$",), "excludeMatchRegex": (r"\b(ii|iii|iv|2|3|4|trilogy|mythologies|gold|deadly|shaolin|armageddon|deception)\b",), "preferConsoles": ("arcade", "genesis", "snes"), "minervaQuery": "Mortal Kombat"},
            {"title": "Mortal Kombat II", "year": 1993, "consoleKey": "arcade", "era": "Trilogía Arcade 2D", "matchRegex": (r"\bmortal kombat (ii|2)\b",), "preferConsoles": ("arcade", "snes", "genesis"), "minervaQuery": "Mortal Kombat II"},
            {"title": "Mortal Kombat 3", "year": 1995, "consoleKey": "arcade", "era": "Trilogía Arcade 2D", "matchRegex": (r"^mortal kombat 3$",), "preferConsoles": ("arcade", "snes", "genesis", "ps1"), "minervaQuery": "Mortal Kombat 3"},
            {"title": "Ultimate Mortal Kombat 3", "year": 1995, "consoleKey": "arcade", "era": "Trilogía Arcade 2D", "matchRegex": (r"\bultimate mortal kombat 3\b",), "preferConsoles": ("arcade", "snes", "genesis", "saturn"), "minervaQuery": "Ultimate Mortal Kombat 3"},
            {"title": "Mortal Kombat Trilogy", "year": 1996, "consoleKey": "ps1", "era": "Compilación Clásica", "matchRegex": (r"\bmortal kombat trilogy\b",), "preferConsoles": ("ps1", "n64"), "allowCombo": True, "minervaQuery": "Mortal Kombat Trilogy"},
            {"title": "Mortal Kombat 4 / Gold", "year": 1997, "consoleKey": "n64", "era": "Salto al 3D", "matchRegex": (r"\bmortal kombat (4|gold)\b",), "preferConsoles": ("n64", "ps1", "dc"), "minervaQuery": "Mortal Kombat 4"},
            {"title": "Mortal Kombat: Deadly Alliance", "year": 2002, "consoleKey": "ps2", "era": "Era PS2 3D", "matchRegex": (r"\bdeadly alliance\b",), "preferConsoles": ("ps2", "gc", "gba"), "minervaQuery": "Mortal Kombat Deadly Alliance"},
            {"title": "Mortal Kombat: Deception", "year": 2004, "consoleKey": "ps2", "era": "Era PS2 3D", "matchRegex": (r"\bmortal kombat.*deception\b",), "preferConsoles": ("ps2", "gc", "psp"), "minervaQuery": "Mortal Kombat Deception"},
            {"title": "Mortal Kombat: Shaolin Monks", "year": 2005, "consoleKey": "ps2", "era": "Aventura PS2", "matchRegex": (r"\bshaolin monks\b",), "preferConsoles": ("ps2",), "minervaQuery": "Shaolin Monks"},
        ],
    },
    {
        "id": "metal-slug",
        "name": "Metal Slug",
        "tagline": "Run & gun de SNK con la animación pixel-art más expresiva del arcade.",
        "foundedYear": 1996,
        "creator": "Nazca / SNK",
        "accent": "#A3E635",
        "history": (
            "Desarrollado por ex-talentos de Irem en Nazca Corporation para Neo Geo MVS en 1996, Metal Slug elevó el pixel-art "
            "a obra de arte animada cuadro por cuadro con humor bélico, prisioneros rescatables y el tanque SV-001. "
            "La trilogía original coronada por Metal Slug 3 en el año 2000 sigue siendo la cumbre absoluta del run & gun 2D."
        ),
        "heroArt": {
            "repo": "FBNeo_-_Arcade_Games",
            "titleName": "Metal Slug - Super Vehicle-001",
            "boxartName": "Metal Slug - Super Vehicle-001",
            "sgdbQuery": "Metal Slug",
        },
        "includeRegex": (r"\bmetal slug\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Metal Slug: Super Vehicle-001", "year": 1996, "consoleKey": "arcade", "era": "Neo Geo MVS", "matchRegex": (r"^metal slug( super vehicle 001)?$",), "excludeMatchRegex": (r"\b(2|x|3|4|5|6|7|advance|xx)\b",), "preferConsoles": ("arcade", "ps1"), "minervaQuery": "Metal Slug"},
            {"title": "Metal Slug 2", "year": 1998, "consoleKey": "arcade", "era": "Neo Geo MVS", "matchRegex": (r"\bmetal slug 2\b",), "preferConsoles": ("arcade",), "minervaQuery": "Metal Slug 2"},
            {"title": "Metal Slug X", "year": 1999, "consoleKey": "arcade", "era": "Neo Geo MVS", "matchRegex": (r"\bmetal slug x\b",), "preferConsoles": ("arcade", "ps1"), "minervaQuery": "Metal Slug X"},
            {"title": "Metal Slug 3", "year": 2000, "consoleKey": "arcade", "era": "Neo Geo MVS", "matchRegex": (r"\bmetal slug 3\b",), "preferConsoles": ("arcade", "ps2"), "minervaQuery": "Metal Slug 3"},
            {"title": "Metal Slug 4", "year": 2002, "consoleKey": "arcade", "era": "Neo Geo MVS", "matchRegex": (r"\bmetal slug 4\b",), "preferConsoles": ("arcade", "ps2"), "minervaQuery": "Metal Slug 4"},
            {"title": "Metal Slug 5", "year": 2003, "consoleKey": "arcade", "era": "Neo Geo MVS", "matchRegex": (r"\bmetal slug 5\b",), "preferConsoles": ("arcade", "ps2"), "minervaQuery": "Metal Slug 5"},
            {"title": "Metal Slug Advance", "year": 2004, "consoleKey": "gba", "era": "Portátil GBA", "matchRegex": (r"\bmetal slug advance\b",), "preferConsoles": ("gba",), "minervaQuery": "Metal Slug Advance"},
        ],
    },
    {
        "id": "kirby",
        "name": "Kirby",
        "tagline": "Aventuras coloridas en Dream Land absorbiendo habilidades enemigas.",
        "foundedYear": 1992,
        "creator": "HAL Laboratory / Masahiro Sakurai",
        "accent": "#F9A8D4",
        "history": (
            "Creado por Masahiro Sakurai en 1992 sobre una pantalla monocroma de Game Boy, Kirby conquistó Dream Land como "
            "un héroe accesible pero lleno de secretos. En 1993 incorporó la capacidad de copiar habilidades enemigas y, con "
            "Kirby Super Star en SNES y The Amazing Mirror en GBA, mezcló múltiples estilos de aventura y exploración."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Kirby Super Star (USA)",
            "boxartName": "Kirby Super Star (USA)",
            "sgdbQuery": "Kirby Super Star",
        },
        "includeRegex": (r"\bkirby\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Kirby's Dream Land", "year": 1992, "consoleKey": "gb", "era": "Game Boy", "matchRegex": (r"\bkirby s? dream land$",), "excludeMatchRegex": (r"\b(2|3|nightmare)\b",), "preferConsoles": ("gb",), "minervaQuery": "Kirby's Dream Land"},
            {"title": "Kirby's Adventure", "year": 1993, "consoleKey": "nes", "era": "Era 8-bit", "matchRegex": (r"\bkirby s? adventure\b",), "preferConsoles": ("nes",), "minervaQuery": "Kirby's Adventure"},
            {"title": "Kirby's Dream Course", "year": 1994, "consoleKey": "snes", "era": "Spin-off 16-bit", "matchRegex": (r"\bdream course\b",), "preferConsoles": ("snes",), "minervaQuery": "Kirby's Dream Course"},
            {"title": "Kirby's Avalanche / Ghost Trap", "year": 1995, "consoleKey": "snes", "era": "Spin-off 16-bit", "matchRegex": (r"\bkirby s? avalanche\b", r"\bghost trap\b"), "preferConsoles": ("snes",), "minervaQuery": "Kirby's Avalanche"},
            {"title": "Kirby's Dream Land 2", "year": 1995, "consoleKey": "gb", "era": "Game Boy", "matchRegex": (r"\bdream land 2\b",), "preferConsoles": ("gb",), "minervaQuery": "Kirby's Dream Land 2"},
            {"title": "Kirby Super Star", "year": 1996, "consoleKey": "snes", "era": "Era 16-bit", "matchRegex": (r"\bsuper star\b", r"\bfun pak\b"), "preferConsoles": ("snes", "nds"), "minervaQuery": "Kirby Super Star"},
            {"title": "Kirby's Dream Land 3", "year": 1997, "consoleKey": "snes", "era": "Era 16-bit", "matchRegex": (r"\bdream land 3\b",), "preferConsoles": ("snes",), "minervaQuery": "Kirby's Dream Land 3"},
            {"title": "Kirby 64: The Crystal Shards", "year": 2000, "consoleKey": "n64", "era": "Era 64-bit", "matchRegex": (r"\bkirby 64\b", r"\bcrystal shards\b"), "preferConsoles": ("n64",), "minervaQuery": "Kirby 64"},
            {"title": "Kirby: Nightmare in Dream Land", "year": 2002, "consoleKey": "gba", "era": "Portátil GBA", "matchRegex": (r"\bnightmare in dream\b",), "preferConsoles": ("gba",), "minervaQuery": "Nightmare in Dream Land"},
            {"title": "Kirby & The Amazing Mirror", "year": 2004, "consoleKey": "gba", "era": "Metroidvania GBA", "matchRegex": (r"\bamazing mirror\b",), "preferConsoles": ("gba",), "minervaQuery": "Amazing Mirror"},
        ],
    },
    {
        "id": "pokemon",
        "name": "Pokémon",
        "tagline": "Captura, estrategia por turnos y viajes inolvidables de Kanto a Hoenn.",
        "foundedYear": 1996,
        "creator": "Game Freak / Satoshi Tajiri & Ken Sugimori",
        "accent": "#FACC15",
        "history": (
            "Inspirado en la pasión infantil de Satoshi Tajiri por coleccionar insectos y el uso del Cable Link, Pokémon "
            "nació en 1996 en Game Boy para convertirse en un fenómeno cultural sin precedentes. Desde las ediciones Rojo, "
            "Azul y Amarillo en Kanto hasta la doble región de Johto en Oro/Plata y las épicas aventuras en color de GBA "
            "con Rubí, Zafiro, Esmeralda y los remakes Rojo Fuego y Verde Hoja."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Game_Boy_Advance",
            "titleName": "Pokemon - Emerald Version (USA, Europe)",
            "boxartName": "Pokemon - Emerald Version (USA, Europe)",
            "sgdbQuery": "Pokemon Emerald",
        },
        "includeRegex": (r"\bpokemon\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Pokémon Rojo / Azul / Amarillo", "year": 1996, "consoleKey": "gb", "era": "Gen I · Kanto", "matchRegex": (r"\bpokemon (red|blue|yellow|rojo|azul|amarillo)\b",), "excludeMatchRegex": (r"\bfire|fuego|rescue|misterioso\b",), "preferConsoles": ("gb",), "minervaQuery": "Pokemon Red"},
            {"title": "Pokémon Snap", "year": 1999, "consoleKey": "n64", "era": "Spin-off N64", "matchRegex": (r"\bpokemon snap\b",), "preferConsoles": ("n64",), "minervaQuery": "Pokemon Snap"},
            {"title": "Pokémon Oro / Plata / Cristal", "year": 1999, "consoleKey": "gbc", "era": "Gen II · Johto", "matchRegex": (r"\bpokemon (gold|silver|crystal|oro|plata|cristal)\b",), "preferConsoles": ("gbc",), "minervaQuery": "Pokemon Crystal"},
            {"title": "Pokémon Stadium", "year": 1999, "consoleKey": "n64", "era": "N64 Arena", "matchRegex": (r"^pokemon stadium$",), "excludeMatchRegex": (r"\b2\b",), "excludeConsoles": ("snes",), "preferConsoles": ("n64",), "minervaQuery": "Pokemon Stadium"},
            {"title": "Pokémon Stadium 2", "year": 2000, "consoleKey": "n64", "era": "N64 Arena", "matchRegex": (r"\bpokemon stadium 2\b",), "preferConsoles": ("n64",), "minervaQuery": "Pokemon Stadium 2"},
            {"title": "Pokémon Rubí / Zafiro", "year": 2002, "consoleKey": "gba", "era": "Gen III · Hoenn", "matchRegex": (r"\bpokemon (ruby|sapphire|rubi|zafiro)\b",), "preferConsoles": ("gba",), "minervaQuery": "Pokemon Ruby"},
            {"title": "Pokémon Rojo Fuego / Verde Hoja", "year": 2004, "consoleKey": "gba", "era": "Gen III · Remake", "matchRegex": (r"\bpokemon (fire ?red|leaf ?green|rojo fuego|verde hoja)\b",), "preferConsoles": ("gba",), "minervaQuery": "Pokemon FireRed"},
            {"title": "Pokémon Esmeralda", "year": 2004, "consoleKey": "gba", "era": "Gen III · Hoenn", "matchRegex": (r"\bpokemon.*(emerald|esmeralda)\b",), "preferConsoles": ("gba",), "minervaQuery": "Pokemon Emerald"},
            {"title": "Pokémon Mundo Misterioso: Equipo de Rescate Rojo", "year": 2005, "consoleKey": "gba", "era": "Spin-off Roguelike", "matchRegex": (r"\b(mystery dungeon|mundo misterioso)\b",), "preferConsoles": ("gba", "nds"), "minervaQuery": "Pokemon Mystery Dungeon"},
        ],
    },
    {
        "id": "streets-of-rage",
        "name": "Streets of Rage",
        "tagline": "Beat 'em up callejero clásico de Sega al ritmo electrónico de Yuzo Koshiro.",
        "foundedYear": 1991,
        "creator": "Sega / Yuzo Koshiro",
        "accent": "#38BDF8",
        "history": (
            "Conocida en Japón como Bare Knuckle, Streets of Rage debutó en 1991 para enfrentar la fiebre de Final Fight "
            "desde Sega Mega Drive. Aclamada por la vanguardista banda sonora electrónica de Yuzo Koshiro y Motohiro "
            "Kawashima, su secuela Streets of Rage 2 (1992) está considerada uno de los mejores beat 'em up cooperativos "
            "jamás hechos."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Streets of Rage 2 (USA)",
            "boxartName": "Streets of Rage 2 (USA)",
            "sgdbQuery": "Streets of Rage 2",
        },
        "includeRegex": (r"\b(streets of rage|bare knuckle)\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Streets of Rage", "year": 1991, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"^(streets of rage|bare knuckle)( 1| i)?$", r"\bbare knuckle.*streets of rage\b"), "excludeMatchRegex": (r"\b(2|3|4|ii|iii)\b",), "preferConsoles": ("genesis", "sms"), "minervaQuery": "Streets of Rage"},
            {"title": "Streets of Rage 2", "year": 1992, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\b(streets of rage 2|bare knuckle ii)\b",), "preferConsoles": ("genesis", "sms"), "minervaQuery": "Streets of Rage 2"},
            {"title": "Streets of Rage 3", "year": 1994, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\b(streets of rage 3|bare knuckle iii)\b",), "preferConsoles": ("genesis",), "minervaQuery": "Streets of Rage 3"},
        ],
    },
    {
        "id": "phantasy-star",
        "name": "Phantasy Star",
        "tagline": "Ciencia ficción, hechicería y épicas espaciales del sistema estelar Algol.",
        "foundedYear": 1987,
        "creator": "Sega / Rieko Kodama & Yuji Naka",
        "accent": "#7DD3FC",
        "history": (
            "Pionera absoluta entre los RPG de consola, Phantasy Star debutó en Master System en 1987 protagonizada por Alis "
            "Landale y mazmorras en primera persona con scroll suave para su época. Su tetralogía clásica en el sistema Algol "
            "cerró con broche de oro en Phantasy Star IV (1993) en Mega Drive mediante secuencias estilo cómic y combos mágicos."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Phantasy Star IV (USA)",
            "boxartName": "Phantasy Star IV (USA)",
            "sgdbQuery": "Phantasy Star IV",
        },
        "includeRegex": (r"\bphantasy star\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Phantasy Star", "year": 1987, "consoleKey": "sms", "era": "Master System", "matchRegex": (r"^phantasy star$",), "preferConsoles": ("sms", "gba"), "minervaQuery": "Phantasy Star"},
            {"title": "Phantasy Star II", "year": 1989, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"^phantasy star (ii|2)$", r"\bkaerazaru toki\b"), "preferConsoles": ("genesis",), "minervaQuery": "Phantasy Star II"},
            {"title": "Phantasy Star III: Generations of Doom", "year": 1990, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\bphantasy star (iii|3)\b", r"\bgenerations of doom\b"), "preferConsoles": ("genesis",), "minervaQuery": "Phantasy Star III"},
            {"title": "Phantasy Star IV: The End of the Millennium", "year": 1993, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\bphantasy star (iv|4)\b", r"\bend of the millennium\b", r"\bsennenki no owari ni\b"), "preferConsoles": ("genesis",), "minervaQuery": "Phantasy Star IV"},
            {"title": "Phantasy Star Online", "year": 2000, "consoleKey": "dc", "era": "Dreamcast Online", "matchRegex": (r"\bphantasy star online\b",), "preferConsoles": ("dc", "gc"), "minervaQuery": "Phantasy Star Online"},
        ],
    },
    {
        "id": "chrono",
        "name": "Saga Chrono",
        "tagline": "Viajes en el tiempo, mundos paralelos y música inmortal de Yasunori Mitsuda.",
        "foundedYear": 1995,
        "creator": "Square / Dream Team (Sakaguchi, Horii & Toriyama)",
        "accent": "#A78BFA",
        "history": (
            "Chrono Trigger (1995) reunió en Super Nintendo al 'Dream Team' irrepetible formado por Hironobu Sakaguchi, "
            "Yuji Horii y Akira Toriyama, revolucionando los JRPG con combates sin transición, técnicas dobles/triples y más de "
            "una docena de finales. Su secuela espiritual Chrono Cross (1999) expandió esa mitología con 45 reclutables y "
            "mundos divididos."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Chrono Trigger (USA)",
            "boxartName": "Chrono Trigger (USA)",
            "sgdbQuery": "Chrono Trigger",
        },
        "includeRegex": (r"\bchrono (trigger|cross)\b", r"\bradical dreamers\b"),
        "excludeRegex": (),
        "timeline": [
            {"title": "Chrono Trigger", "year": 1995, "consoleKey": "snes", "era": "SNES / DS", "matchRegex": (r"\bchrono trigger\b",), "preferConsoles": ("snes", "nds", "ps1"), "minervaQuery": "Chrono Trigger"},
            {"title": "Radical Dreamers", "year": 1996, "consoleKey": "snes", "era": "Satellaview", "matchRegex": (r"\bradical dreamers\b",), "preferConsoles": ("snes",), "minervaQuery": "Radical Dreamers"},
            {"title": "Chrono Cross", "year": 1999, "consoleKey": "ps1", "era": "PlayStation", "matchRegex": (r"\bchrono cross\b",), "preferConsoles": ("ps1",), "minervaQuery": "Chrono Cross"},
        ],
    },
    {
        "id": "silent-hill",
        "name": "Silent Hill",
        "tagline": "Terror psicológico envuelto en niebla, culpa y radios con estática.",
        "foundedYear": 1999,
        "creator": "Konami / Team Silent & Akira Yamaoka",
        "accent": "#94A3B8",
        "history": (
            "Nacida en 1999 bajo la dirección de Keiichiro Toyama en Team Silent, Silent Hill convirtió las limitaciones "
            "de distancia de dibujado de PS1 en su mayor virtud estética: una niebla sofocante que esconde horrores psicológicos. "
            "Su cuatrilogía original junto a los diseños de criaturas de Masahiro Ito y la música de Akira Yamaoka es cima "
            "indiscutible del horror de autor."
        ),
        "heroArt": {
            "repo": "Sony_-_PlayStation",
            "titleName": "Silent Hill (USA)",
            "boxartName": "Silent Hill (USA)",
            "sgdbQuery": "Silent Hill",
        },
        "includeRegex": (r"\bsilent hill\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Silent Hill", "year": 1999, "consoleKey": "ps1", "era": "Team Silent", "matchRegex": (r"^silent hill$",), "preferConsoles": ("ps1",), "minervaQuery": "Silent Hill"},
            {"title": "Silent Hill 2", "year": 2001, "consoleKey": "ps2", "era": "Team Silent", "matchRegex": (r"\bsilent hill 2\b",), "preferConsoles": ("ps2",), "minervaQuery": "Silent Hill 2"},
            {"title": "Silent Hill 3", "year": 2003, "consoleKey": "ps2", "era": "Team Silent", "matchRegex": (r"\bsilent hill 3\b",), "preferConsoles": ("ps2",), "minervaQuery": "Silent Hill 3"},
            {"title": "Silent Hill 4: The Room", "year": 2004, "consoleKey": "ps2", "era": "Team Silent", "matchRegex": (r"\bsilent hill 4\b", r"\bthe room\b"), "preferConsoles": ("ps2",), "minervaQuery": "Silent Hill 4"},
        ],
    },
    {
        "id": "metal-gear",
        "name": "Metal Gear",
        "tagline": "Infiltración táctica, tramas cinematográficas y espionaje junto a Solid Snake.",
        "foundedYear": 1987,
        "creator": "Konami / Hideo Kojima",
        "accent": "#5EEAD4",
        "history": (
            "En 1987 Hideo Kojima desafió las normas de los juegos de acción creando en MSX2 (y NES) un título donde evitar "
            "al enemigo era más inteligente que disparar sin descanso. Once años después, Metal Gear Solid (1998) transformó "
            "el medio en PlayStation con doblaje cinematográfico, jefes icónicos que rompían la cuarta pared y una narrativa "
            "antibélica seguida por Sons of Liberty y Snake Eater."
        ),
        "heroArt": {
            "repo": "Sony_-_PlayStation",
            "titleName": "Metal Gear Solid (USA) (Disc 1)",
            "boxartName": "Metal Gear Solid (USA) (Disc 1)",
            "sgdbQuery": "Metal Gear Solid",
        },
        "includeRegex": (r"\bmetal gear\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Metal Gear", "year": 1987, "consoleKey": "nes", "era": "Origen 8-bit", "matchRegex": (r"^metal gear$",), "preferConsoles": ("msx", "nes"), "minervaQuery": "Metal Gear"},
            {"title": "Metal Gear 2: Solid Snake", "year": 1990, "consoleKey": "msx", "era": "MSX2", "matchRegex": (r"\bmetal gear 2\b",), "preferConsoles": ("msx",), "minervaQuery": "Metal Gear 2"},
            {"title": "Metal Gear Solid", "year": 1998, "consoleKey": "ps1", "era": "Era Solid", "matchRegex": (r"^metal gear solid$", r"\btwin snakes\b"), "preferConsoles": ("ps1", "gc"), "minervaQuery": "Metal Gear Solid"},
            {"title": "Metal Gear Solid 2: Sons of Liberty", "year": 2001, "consoleKey": "ps2", "era": "Era PS2", "matchRegex": (r"\bmetal gear solid 2\b", r"\bsons of liberty\b"), "preferConsoles": ("ps2",), "minervaQuery": "Metal Gear Solid 2"},
            {"title": "Metal Gear Solid 3: Snake Eater", "year": 2004, "consoleKey": "ps2", "era": "Era PS2", "matchRegex": (r"\bmetal gear solid 3\b", r"\bsnake eater\b"), "preferConsoles": ("ps2", "3ds"), "minervaQuery": "Snake Eater"},
        ],
    },
    {
        "id": "007-bond",
        "name": "007 · James Bond",
        "tagline": "Misiones secretas, espionaje táctico y multijugador mítico del agente 007.",
        "foundedYear": 1993,
        "creator": "Rare / EA / MGM",
        "accent": "#FBBF24",
        "history": (
            "Tras diversas adaptaciones en los 8 y 16 bits como James Bond 007: The Duel en Mega Drive, el agente británico "
            "hizo historia en 1997 cuando Rare lanzó GoldenEye 007 para Nintendo 64, redefiniendo para siempre los shooters "
            "en consola y el multijugador a pantalla dividida. En la generación de PS2 y GameCube brillaron Everything or "
            "Nothing y From Russia with Love con actores de cine."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Nintendo_64",
            "titleName": "GoldenEye 007 (USA)",
            "boxartName": "GoldenEye 007 (USA)",
            "sgdbQuery": "GoldenEye 007",
        },
        "includeRegex": (r"\b007\b", r"\bgoldeneye\b", r"\bjames bond\b"),
        "excludeRegex": (r"\bjames pond\b",),
        "timeline": [
            {"title": "James Bond Jr.", "year": 1992, "consoleKey": "snes", "era": "16-bit / 8-bit", "matchRegex": (r"\bjames bond jr\b",), "preferConsoles": ("snes", "nes"), "minervaQuery": "James Bond Jr"},
            {"title": "James Bond 007: The Duel", "year": 1993, "consoleKey": "genesis", "era": "16-bit", "matchRegex": (r"\bthe duel\b", r"\bshitou\b"), "preferConsoles": ("genesis", "sms", "gg"), "minervaQuery": "007 The Duel"},
            {"title": "GoldenEye 007", "year": 1997, "consoleKey": "n64", "era": "Clásico N64", "matchRegex": (r"\bgoldeneye\b",), "preferConsoles": ("n64", "wii"), "minervaQuery": "GoldenEye 007"},
            {"title": "007: Nightfire", "year": 2002, "consoleKey": "gc", "era": "128-bit", "matchRegex": (r"\bnightfire\b",), "preferConsoles": ("gc", "ps2", "gba"), "minervaQuery": "007 Nightfire"},
            {"title": "007: Everything or Nothing", "year": 2004, "consoleKey": "gc", "era": "128-bit", "matchRegex": (r"\beverything or nothing\b",), "preferConsoles": ("gc", "ps2", "gba"), "minervaQuery": "Everything or Nothing"},
            {"title": "007: From Russia with Love", "year": 2005, "consoleKey": "gc", "era": "128-bit", "matchRegex": (r"\brussia with love\b",), "preferConsoles": ("gc", "ps2", "psp"), "minervaQuery": "From Russia with Love"},
        ],
    },
    {
        "id": "contra",
        "name": "Contra",
        "tagline": "Acción explosiva cooperativa contra hordas alienígenas y el mito del Código Konami.",
        "foundedYear": 1987,
        "creator": "Konami",
        "accent": "#F87171",
        "history": (
            "Nacido en arcades en 1987 e inmortalizado un año después en NES junto al legendario Código Konami de las 30 vidas, "
            "Contra definió el run & gun cooperativo frenético. En los 16 bits alcanzó cimas gemelas en dos consolas: "
            "Contra III: The Alien Wars en Super Nintendo y Contra: Hard Corps en Sega Mega Drive."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Contra III - The Alien Wars (USA)",
            "boxartName": "Contra III - The Alien Wars (USA)",
            "sgdbQuery": "Contra III: The Alien Wars",
        },
        "includeRegex": (r"\bcontra\b", r"\bsuper c\b", r"\bprobotector\b"),
        "excludeRegex": (),
        "timeline": [
            {"title": "Contra", "year": 1987, "consoleKey": "nes", "era": "Era 8-bit", "matchRegex": (r"^contra$",), "excludeMatchRegex": (r"\b(iii|3|hard|force|shattered|rebirth|4)\b",), "preferConsoles": ("nes", "arcade"), "minervaQuery": "Contra"},
            {"title": "Super C (Super Contra)", "year": 1988, "consoleKey": "nes", "era": "Era 8-bit", "matchRegex": (r"\bsuper c(ontra)?\b",), "preferConsoles": ("nes", "arcade"), "minervaQuery": "Super C"},
            {"title": "Contra III: The Alien Wars", "year": 1992, "consoleKey": "snes", "era": "SNES 16-bit", "matchRegex": (r"\bcontra iii\b", r"\balien wars\b"), "preferConsoles": ("snes", "gba"), "minervaQuery": "Contra III"},
            {"title": "Contra: Hard Corps", "year": 1994, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\b(the )?hard corps\b", r"^probotector$"), "preferConsoles": ("genesis",), "minervaQuery": "Contra Hard Corps"},
            {"title": "Contra: Shattered Soldier", "year": 2002, "consoleKey": "ps2", "era": "Era PS2", "matchRegex": (r"\bshattered soldier\b",), "preferConsoles": ("ps2",), "minervaQuery": "Shattered Soldier"},
        ],
    },
    {
        "id": "shinobi",
        "name": "Shinobi",
        "tagline": "Ninjutsu letal, shurikens y estilo clásico arcade de Sega con Joe Musashi.",
        "foundedYear": 1987,
        "creator": "Sega / Yutaka Sugano & Noriyoshi Ohba",
        "accent": "#F43F5E",
        "history": (
            "Tras conquistar los salones arcade en 1987, Shinobi se transformó en uno de los pilares de acción de Mega Drive "
            "con The Revenge of Shinobi (con banda sonora de Yuzo Koshiro), Shadow Dancer y la obra maestra ágil e intensa "
            "Shinobi III: Return of the Ninja Master en 1993."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Shinobi III - Return of the Ninja Master (USA)",
            "boxartName": "Shinobi III - Return of the Ninja Master (USA)",
            "sgdbQuery": "Shinobi III",
        },
        "includeRegex": (r"\bshinobi\b", r"\bshadow dancer\b"),
        "excludeRegex": (),
        "timeline": [
            {"title": "Shinobi", "year": 1987, "consoleKey": "arcade", "era": "Arcade / SMS", "matchRegex": (r"^shinobi$",), "excludeMatchRegex": (r"\b(revenge|shadow|iii|3|ii|2|super|legions)\b",), "preferConsoles": ("arcade", "sms"), "minervaQuery": "Shinobi"},
            {"title": "The Revenge of Shinobi (The Super Shinobi)", "year": 1989, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\brevenge of shinobi\b", r"\bsuper shinobi\b(?!.*\b(?:ii|2)\b)"), "preferConsoles": ("genesis",), "minervaQuery": "Revenge of Shinobi"},
            {"title": "Shadow Dancer: The Secret of Shinobi", "year": 1990, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\bshadow dancer\b",), "preferConsoles": ("genesis", "arcade"), "minervaQuery": "Shadow Dancer"},
            {"title": "Shinobi III: Return of the Ninja Master", "year": 1993, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\bshinobi iii\b", r"\breturn of the ninja\b", r"\bsuper shinobi ii\b"), "preferConsoles": ("genesis",), "minervaQuery": "Shinobi III"},
        ],
    },
    {
        "id": "golden-axe",
        "name": "Golden Axe",
        "tagline": "Fantasía heroica, bestias montables y espadas arcade de Sega.",
        "foundedYear": 1989,
        "creator": "Sega / Makoto Uchida",
        "accent": "#FBBF24",
        "history": (
            "Inspirada en la fantasía espada y brujería de los años ochenta, Golden Axe debutó en 1989 en placas System 16 "
            "permitiendo a Ax Battler, Tyris Flare y Gilius Thunderhead liberar magias elementales y montar dragones. "
            "Sus secuelas exclusivas de Mega Drive y el monumental The Revenge of Death Adder marcaron la edad dorada de Sega."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Golden Axe (World)",
            "boxartName": "Golden Axe (World)",
            "sgdbQuery": "Golden Axe",
        },
        "includeRegex": (r"\bgolden axe\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Golden Axe", "year": 1989, "consoleKey": "genesis", "era": "Arcade / Mega Drive", "matchRegex": (r"^golden axe$",), "excludeMatchRegex": (r"\b(ii|iii|death adder)\b",), "preferConsoles": ("arcade", "genesis"), "minervaQuery": "Golden Axe"},
            {"title": "Golden Axe II", "year": 1991, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\bgolden axe ii\b",), "preferConsoles": ("genesis",), "minervaQuery": "Golden Axe II"},
            {"title": "Golden Axe: The Revenge of Death Adder", "year": 1992, "consoleKey": "arcade", "era": "Arcade System 32", "matchRegex": (r"\bdeath adder\b",), "preferConsoles": ("arcade",), "minervaQuery": "Golden Axe Revenge"},
            {"title": "Golden Axe III", "year": 1993, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\bgolden axe iii\b",), "preferConsoles": ("genesis",), "minervaQuery": "Golden Axe III"},
        ],
    },
    {
        "id": "double-dragon",
        "name": "Double Dragon",
        "tagline": "Los hermanos Billy y Jimmy Lee inaugurando el beat 'em up cooperativo moderno.",
        "foundedYear": 1987,
        "creator": "Technōs Japan / Yoshihisa Kishimoto",
        "accent": "#FB923C",
        "history": (
            "Technōs Japan estableció en 1987 las reglas definitivas del beat 'em up urbano con scroll lateral y juego a dos "
            "jugadores simultáneos en Double Dragon. Acompañada por clásicos inolvidables en NES y la aclamada entrega "
            "Super Double Dragon en Super Nintendo, la saga también protagonizó el explosivo cruce Battletoads & Double Dragon."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Nintendo_Entertainment_System",
            "titleName": "Double Dragon II - The Revenge (USA)",
            "boxartName": "Double Dragon II - The Revenge (USA)",
            "sgdbQuery": "Double Dragon II: The Revenge",
        },
        "includeRegex": (r"\bdouble dragon\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Double Dragon", "year": 1987, "consoleKey": "genesis", "era": "Arcade / Consolas", "matchRegex": (r"^double dragon$",), "preferConsoles": ("arcade", "nes", "genesis"), "minervaQuery": "Double Dragon"},
            {"title": "Double Dragon II: The Revenge", "year": 1988, "consoleKey": "nes", "era": "Arcade / NES", "matchRegex": (r"\bdouble dragon ii\b",), "preferConsoles": ("nes", "genesis", "arcade"), "minervaQuery": "Double Dragon II"},
            {"title": "Double Dragon 3: The Rosetta Stone / Sacred Stones", "year": 1990, "consoleKey": "genesis", "era": "Arcade / 16-bit", "matchRegex": (r"\bdouble dragon (3|iii)\b",), "preferConsoles": ("nes", "genesis", "arcade"), "minervaQuery": "Double Dragon 3"},
            {"title": "Super Double Dragon", "year": 1992, "consoleKey": "snes", "era": "SNES 16-bit", "matchRegex": (r"\bsuper double dragon\b",), "preferConsoles": ("snes",), "minervaQuery": "Super Double Dragon"},
            {"title": "Battletoads & Double Dragon", "year": 1993, "consoleKey": "snes", "era": "Crossover Rare", "matchRegex": (r"\bbattletoads.*double dragon\b",), "preferConsoles": ("snes", "genesis", "nes"), "allowCombo": True, "minervaQuery": "Battletoads Double Dragon"},
            {"title": "Double Dragon V: The Shadow Falls", "year": 1994, "consoleKey": "snes", "era": "Lucha 16-bit", "matchRegex": (r"\bdouble dragon v\b", r"\bshadow falls\b"), "preferConsoles": ("snes", "genesis"), "minervaQuery": "Double Dragon V"},
        ],
    },
    {
        "id": "breath-of-fire",
        "name": "Breath of Fire",
        "tagline": "Dragones legendarios, clanes antropomorfos y JRPG clásicos de Capcom.",
        "foundedYear": 1993,
        "creator": "Capcom / Makoto Ikehara",
        "accent": "#60A5FA",
        "history": (
            "Breath of Fire fue la gran apuesta de Capcom dentro del rol japonés tradicional en 1993, siguiendo las "
            "encarnaciones recurrentes de Ryu y Nina a través de milenios. Con transformaciones en dragón, fusión de chamanes "
            "en la segunda entrega y el salto al pixel-art con fondos 3D de PS1 en III y IV, forjó un culto fiel."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Breath of Fire II (USA)",
            "boxartName": "Breath of Fire II (USA)",
            "sgdbQuery": "Breath of Fire II",
        },
        "includeRegex": (r"\bbreath of fire\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Breath of Fire", "year": 1993, "consoleKey": "snes", "era": "SNES / GBA", "matchRegex": (r"^breath of fire$",), "preferConsoles": ("snes", "gba"), "minervaQuery": "Breath of Fire"},
            {"title": "Breath of Fire II", "year": 1994, "consoleKey": "snes", "era": "SNES / GBA", "matchRegex": (r"^breath of fire (ii|2)$",), "preferConsoles": ("snes", "gba"), "minervaQuery": "Breath of Fire II"},
            {"title": "Breath of Fire III", "year": 1997, "consoleKey": "ps1", "era": "PlayStation", "matchRegex": (r"^breath of fire (iii|3)$",), "preferConsoles": ("ps1", "psp"), "minervaQuery": "Breath of Fire III"},
            {"title": "Breath of Fire IV", "year": 2000, "consoleKey": "ps1", "era": "PlayStation", "matchRegex": (r"^breath of fire (iv|4)$",), "preferConsoles": ("ps1",), "minervaQuery": "Breath of Fire IV"},
        ],
    },
    {
        "id": "dragon-quest",
        "name": "Dragon Quest",
        "tagline": "La saga fundadora del JRPG con diseños inconfundibles de Akira Toriyama.",
        "foundedYear": 1986,
        "creator": "Enix / Yuji Horii, Akira Toriyama & Koichi Sugiyama",
        "accent": "#38BDF8",
        "history": (
            "Desde su debut en Famicom en 1986, Dragon Quest se convirtió en patrimonio cultural de Japón gracias al genio "
            "narrativo de Yuji Horii, los monstruos icónicos de Akira Toriyama y la música sinfónica de Koichi Sugiyama. "
            "Entregas generacionales como Dragon Quest III, V: La Prometida Celestial y VIII marcaron hitos inolvidables."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Dragon Quest V - Tenkuu no Hanayome (Japan)",
            "boxartName": "Dragon Quest V - Tenkuu no Hanayome (Japan)",
            "sgdbQuery": "Dragon Quest V",
        },
        "includeRegex": (r"\bdragon quest\b", r"\bdragon warrior\b"),
        "excludeRegex": (),
        "timeline": [
            {"title": "Dragon Quest III", "year": 1988, "consoleKey": "nes", "era": "Trilogía Erdrick", "matchRegex": (r"\bdragon (quest|warrior) (iii|3)\b",), "preferConsoles": ("snes", "nes", "gbc"), "minervaQuery": "Dragon Quest III"},
            {"title": "Dragon Quest IV", "year": 1990, "consoleKey": "nes", "era": "Trilogía Zenithia", "matchRegex": (r"\bdragon (quest|warrior) (iv|4)\b",), "preferConsoles": ("nds", "nes", "ps1"), "minervaQuery": "Dragon Quest IV"},
            {"title": "Dragon Quest V: Hand of the Heavenly Bride", "year": 1992, "consoleKey": "snes", "era": "Trilogía Zenithia", "matchRegex": (r"\bdragon quest (v|5)\b", r"\btenkuu no hanayome\b"), "preferConsoles": ("snes", "nds", "ps2"), "minervaQuery": "Dragon Quest V"},
            {"title": "Dragon Quest VI", "year": 1995, "consoleKey": "snes", "era": "Trilogía Zenithia", "matchRegex": (r"\bdragon quest (vi|6)\b",), "preferConsoles": ("snes", "nds"), "minervaQuery": "Dragon Quest VI"},
            {"title": "Dragon Quest VIII", "year": 2004, "consoleKey": "ps2", "era": "Era PS2", "matchRegex": (r"\bdragon quest (viii|8)\b",), "preferConsoles": ("ps2", "3ds"), "minervaQuery": "Dragon Quest VIII"},
        ],
    },
    {
        "id": "ninja-gaiden",
        "name": "Ninja Gaiden",
        "tagline": "Cinemáticas pioneras y precisión ninja extrema protagonizada por Ryu Hayabusa.",
        "foundedYear": 1988,
        "creator": "Tecmo / Hideo Yoshizawa & Tomonobu Itagaki",
        "accent": "#C084FC",
        "history": (
            "Tecmo inauguró en 1988 una trilogía legendaria en NES que combinaba acción vertiginosa por paredes con escenas "
            "cinematográficas al estilo anime nunca antes vistas en 8 bits. Reunida luego en SNES con Ninja Gaiden Trilogy, "
            "la saga renacería con acción 3D de culto en la década siguiente."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Ninja Gaiden Trilogy (USA)",
            "boxartName": "Ninja Gaiden Trilogy (USA)",
            "sgdbQuery": "Ninja Gaiden",
        },
        "includeRegex": (r"\bninja gaiden\b", r"\bninja ryukenden\b"),
        "excludeRegex": (),
        "timeline": [
            {"title": "Ninja Gaiden", "year": 1988, "consoleKey": "nes", "era": "Trilogía 8-bit", "matchRegex": (r"^ninja gaiden$",), "preferConsoles": ("nes", "arcade"), "minervaQuery": "Ninja Gaiden"},
            {"title": "Ninja Gaiden II: The Dark Sword of Chaos", "year": 1990, "consoleKey": "nes", "era": "Trilogía 8-bit", "matchRegex": (r"\bninja gaiden ii\b",), "preferConsoles": ("nes",), "minervaQuery": "Ninja Gaiden II"},
            {"title": "Ninja Gaiden III: The Ancient Ship of Doom", "year": 1991, "consoleKey": "nes", "era": "Trilogía 8-bit", "matchRegex": (r"\bninja gaiden iii\b",), "preferConsoles": ("nes",), "minervaQuery": "Ninja Gaiden III"},
            {"title": "Ninja Gaiden Trilogy", "year": 1995, "consoleKey": "snes", "era": "Compilación 16-bit", "matchRegex": (r"\bninja gaiden trilogy\b",), "preferConsoles": ("snes",), "allowCombo": True, "minervaQuery": "Ninja Gaiden Trilogy"},
        ],
    },
    {
        "id": "king-of-fighters",
        "name": "The King of Fighters",
        "tagline": "Equipos 3 contra 3 y el torneo definitivo de estrellas arcade de SNK.",
        "foundedYear": 1994,
        "creator": "SNK / Takashi Nishiyama",
        "accent": "#F59E0B",
        "history": (
            "En 1994 SNK reunió los elencos de Fatal Fury, Art of Fighting, Ikari Warriors y Psycho Soldier en el primer "
            "crossover anual 3v3 de la lucha arcade. Con la saga de Orochi culminando en KOF '97 y el eterno Dream Match "
            "KOF '98 y '99 Millennium Battle, definió los salones recreativos de toda Latinoamérica."
        ),
        "heroArt": {
            "repo": "SNK_-_Neo_Geo",
            "titleName": "The King of Fighters '98 - The Slugfest _ King of Fighters '98 - dream match never ends (NGM-2420)",
            "boxartName": "The King of Fighters '98 - The Slugfest _ King of Fighters '98 - dream match never ends (NGM-2420)",
            "sgdbQuery": "The King of Fighters '98",
        },
        "includeRegex": (r"\bking of fighters\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "The King of Fighters '94", "year": 1994, "consoleKey": "arcade", "era": "Saga Orochi", "matchRegex": (r"\bking of fighters '?94\b",), "preferConsoles": ("arcade",), "minervaQuery": "King of Fighters 94"},
            {"title": "The King of Fighters '96", "year": 1996, "consoleKey": "arcade", "era": "Saga Orochi", "matchRegex": (r"\bking of fighters '?96\b",), "preferConsoles": ("arcade", "ps1"), "minervaQuery": "King of Fighters 96"},
            {"title": "The King of Fighters '97", "year": 1997, "consoleKey": "arcade", "era": "Saga Orochi", "matchRegex": (r"\bking of fighters '?97\b",), "preferConsoles": ("arcade", "ps1"), "minervaQuery": "King of Fighters 97"},
            {"title": "The King of Fighters '98", "year": 1998, "consoleKey": "arcade", "era": "Dream Match", "matchRegex": (r"\bking of fighters '?98\b",), "preferConsoles": ("arcade", "ps1", "dc"), "minervaQuery": "King of Fighters 98"},
            {"title": "The King of Fighters '99", "year": 1999, "consoleKey": "arcade", "era": "Crónicas NESTS", "matchRegex": (r"\bking of fighters '?99\b",), "preferConsoles": ("arcade", "ps1", "dc"), "minervaQuery": "King of Fighters 99"},
            {"title": "The King of Fighters 2002", "year": 2002, "consoleKey": "arcade", "era": "Dream Match", "matchRegex": (r"\bking of fighters 2002\b",), "preferConsoles": ("arcade", "dc", "ps2"), "minervaQuery": "King of Fighters 2002"},
        ],
    },
    {
        "id": "dragon-ball",
        "name": "Dragon Ball / Z",
        "tagline": "Torneos de artes marciales, Kamehamehas y batallas legendarias desde Butoden a Budokai.",
        "foundedYear": 1986,
        "creator": "Akira Toriyama / Bandai",
        "accent": "#F97316",
        "history": (
            "Las adaptaciones de Dragon Ball Z marcaron la era de los 16 bits en SNES y Mega Drive con la serie "
            "Super Butouden y el mítico Buyuu Retsuden (L'Appel du Destin) con pantalla dividida para lanzar ataques "
            "especiales a distancia. Más tarde llegaron las entregas portátiles de Legacy of Goku y Supersonic Warriors en GBA "
            "junto a los torneos poligonales de PlayStation."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Game_Boy_Advance",
            "titleName": "Dragon Ball Z - Supersonic Warriors (USA)",
            "boxartName": "Dragon Ball Z - Supersonic Warriors (USA)",
            "sgdbQuery": "Dragon Ball Z: Supersonic Warriors",
        },
        "includeRegex": (r"\bdragon ?ball\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Dragon Ball Z: Super Butouden", "year": 1993, "consoleKey": "snes", "era": "Butouden 16-bit", "matchRegex": (r"\bsuper butouden$", r"\bsuper butoden$", r"\bla legende saien\b"), "preferConsoles": ("snes",), "minervaQuery": "Super Butouden"},
            {"title": "Dragon Ball Z: Buyuu Retsuden (L'Appel du Destin)", "year": 1994, "consoleKey": "genesis", "era": "Mega Drive 16-bit", "matchRegex": (r"\bbuyuu retsuden\b", r"\bl appel du destin\b"), "preferConsoles": ("genesis",), "minervaQuery": "Buyuu Retsuden"},
            {"title": "Dragon Ball Z: Super Butouden 2", "year": 1994, "consoleKey": "snes", "era": "Butouden 16-bit", "matchRegex": (r"\bsuper buto[u]?den 2\b",), "preferConsoles": ("snes",), "minervaQuery": "Super Butouden 2"},
            {"title": "Dragon Ball Z: Super Butouden 3", "year": 1994, "consoleKey": "snes", "era": "Butouden 16-bit", "matchRegex": (r"\bsuper buto[u]?den 3\b", r"\bultime menace\b"), "preferConsoles": ("snes",), "minervaQuery": "Super Butouden 3"},
            {"title": "Dragon Ball Z: Hyper Dimension", "year": 1996, "consoleKey": "snes", "era": "SNES Tardío", "matchRegex": (r"\bhyper dimension\b",), "preferConsoles": ("snes",), "minervaQuery": "Hyper Dimension"},
            {"title": "Dragon Ball GT: Final Bout", "year": 1997, "consoleKey": "ps1", "era": "PlayStation", "matchRegex": (r"\bfinal bout\b",), "preferConsoles": ("ps1",), "minervaQuery": "Final Bout"},
            {"title": "Dragon Ball Z: The Legacy of Goku", "year": 2002, "consoleKey": "gba", "era": "Action RPG GBA", "matchRegex": (r"\blegacy of goku$",), "excludeMatchRegex": (r"\b(ii|2|buu)\b",), "preferConsoles": ("gba",), "minervaQuery": "Legacy of Goku"},
            {"title": "Dragon Ball Z: The Legacy of Goku II", "year": 2003, "consoleKey": "gba", "era": "Action RPG GBA", "matchRegex": (r"\blegacy of goku (ii|2)\b",), "preferConsoles": ("gba",), "minervaQuery": "Legacy of Goku II"},
            {"title": "Dragon Ball: Advanced Adventure", "year": 2004, "consoleKey": "gba", "era": "Acción GBA", "matchRegex": (r"\badvanced adventure\b",), "preferConsoles": ("gba",), "minervaQuery": "Advanced Adventure"},
            {"title": "Dragon Ball Z: Supersonic Warriors", "year": 2004, "consoleKey": "gba", "era": "Combate Aéreo", "matchRegex": (r"\bsupersonic warriors\b",), "preferConsoles": ("gba", "nds"), "minervaQuery": "Supersonic Warriors"},
        ],
    },
    {
        "id": "shining",
        "name": "Shining Force",
        "tagline": "Rol táctico y mazmorras legendarias nacidas en Sega Mega Drive por Camelot.",
        "foundedYear": 1991,
        "creator": "Sonic Software Planning (Camelot) / Hiroyuki & Shugo Takahashi",
        "accent": "#60A5FA",
        "history": (
            "Iniciada en 1991 con Shining in the Darkness, la franquicia Shining se consagró con Shining Force I y II en "
            "Sega Mega Drive como el gran referente occidental del RPG táctico por turnos, combinando exploración de pueblos, "
            "retratos animados en combate y decenas de aliados secretos antes de brillar en Game Boy Advance."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Shining Force II (USA)",
            "boxartName": "Shining Force II (USA)",
            "sgdbQuery": "Shining Force II",
        },
        "includeRegex": (r"\bshining (and|in|force|soul|the holy)\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Shining in the Darkness", "year": 1991, "consoleKey": "genesis", "era": "Mazmorra 16-bit", "matchRegex": (r"\bshining (in|and) the darkness\b",), "preferConsoles": ("genesis",), "minervaQuery": "Shining in the Darkness"},
            {"title": "Shining Force", "year": 1992, "consoleKey": "genesis", "era": "SRPG Mega Drive", "matchRegex": (r"^shining force$", r"\bkamigami no isan\b"), "excludeConsoles": ("gba",), "preferConsoles": ("genesis",), "minervaQuery": "Shining Force"},
            {"title": "Shining Force II", "year": 1993, "consoleKey": "genesis", "era": "SRPG Mega Drive", "matchRegex": (r"\bshining force ii\b", r"\binishie no fuuin\b"), "preferConsoles": ("genesis",), "minervaQuery": "Shining Force II"},
            {"title": "Shining Force CD", "year": 1994, "consoleKey": "segacd", "era": "Sega CD", "matchRegex": (r"\bshining force cd\b",), "preferConsoles": ("segacd",), "minervaQuery": "Shining Force CD"},
            {"title": "Shining Force: Resurrection of the Dark Dragon", "year": 2004, "consoleKey": "gba", "era": "Remake GBA", "matchRegex": (r"\bshining force\b", r"\bresurrection of the dark dragon\b"), "preferConsoles": ("gba",), "minervaQuery": "Shining Force GBA"},
        ],
    },
    {
        "id": "tmnt",
        "name": "Tortugas Ninja (TMNT)",
        "tagline": "Pizza, alcantarillas y beat 'em ups cooperativos míticos de Konami.",
        "foundedYear": 1989,
        "creator": "Konami / Mirage Studios",
        "accent": "#34D399",
        "history": (
            "Entre 1989 y 1994, Konami firmó algunas de las recreativas y adaptaciones domésticas más queridas de toda la era "
            "retro con las Tortugas Ninja: desde los clásicos de NES hasta el inolvidable Turtles in Time en SNES, su contraparte "
            "The Hyperstone Heist en Mega Drive y los duelos competitivos de Tournament Fighters."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Teenage Mutant Ninja Turtles IV - Turtles in Time (USA)",
            "boxartName": "Teenage Mutant Ninja Turtles IV - Turtles in Time (USA)",
            "sgdbQuery": "Turtles in Time",
        },
        "includeRegex": (r"\bteenage mutant (?:ninja|hero) turtles\b", r"\btmnt\b", r"\bturtles in time\b", r"\bhyperstone heist\b"),
        "excludeRegex": (),
        "timeline": [
            {"title": "Teenage Mutant Ninja Turtles", "year": 1989, "consoleKey": "nes", "era": "Era 8-bit", "matchRegex": (r"^teenage mutant (?:ninja|hero) turtles$",), "preferConsoles": ("nes", "arcade"), "minervaQuery": "Teenage Mutant Ninja Turtles"},
            {"title": "TMNT II: The Arcade Game", "year": 1990, "consoleKey": "nes", "era": "Arcade / NES", "matchRegex": (r"\bturtles (ii|2)\b", r"\barcade game\b"), "preferConsoles": ("nes", "arcade"), "minervaQuery": "TMNT Arcade"},
            {"title": "TMNT IV: Turtles in Time", "year": 1991, "consoleKey": "snes", "era": "SNES 16-bit", "matchRegex": (r"\bturtles in time\b",), "preferConsoles": ("snes", "arcade"), "minervaQuery": "Turtles in Time"},
            {"title": "TMNT: The Hyperstone Heist", "year": 1992, "consoleKey": "genesis", "era": "Mega Drive 16-bit", "matchRegex": (r"\bhyperstone heist\b", r"\breturn of the shredder\b"), "preferConsoles": ("genesis",), "minervaQuery": "Hyperstone Heist"},
            {"title": "TMNT: Tournament Fighters", "year": 1993, "consoleKey": "snes", "era": "Lucha 16-bit", "matchRegex": (r"\btournament fighters\b",), "preferConsoles": ("snes", "genesis", "nes"), "minervaQuery": "Tournament Fighters"},
        ],
    },
    {
        "id": "batman",
        "name": "Batman Retro",
        "tagline": "El Caballero Oscuro entre el plataformas de Sunsoft, Konami y el cómic animado de los 90.",
        "foundedYear": 1989,
        "creator": "Sunsoft / Konami / Sega / DC",
        "accent": "#A78BFA",
        "history": (
            "Batman protagonizó joyas técnicas irrepetibles en los años 90: Sunsoft elevó la música FM de Mega Drive y NES, "
            "Konami adaptó Batman Returns en un brutal beat 'em up arcade y The Adventures of Batman & Robin brilló con efectos "
            "especiales al límite en Mega Drive y un fidelísimo diseño animado en Super Nintendo."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Adventures of Batman & Robin, The (USA)",
            "boxartName": "Adventures of Batman & Robin, The (USA)",
            "sgdbQuery": "The Adventures of Batman & Robin",
        },
        "includeRegex": (r"\bbatman\b",),
        "excludeRegex": (r"\bgotham\b",),
        "timeline": [
            {"title": "Batman: The Video Game", "year": 1989, "consoleKey": "genesis", "era": "Sunsoft Clásico", "matchRegex": (r"^batman$", r"\bbatman.*video game\b"), "preferConsoles": ("nes", "genesis"), "minervaQuery": "Batman"},
            {"title": "Batman: Return / Revenge of the Joker", "year": 1991, "consoleKey": "snes", "era": "Sunsoft 16-bit", "matchRegex": (r"\b(return|revenge) of the joker\b",), "preferConsoles": ("genesis", "snes", "nes"), "minervaQuery": "Revenge of the Joker"},
            {"title": "Batman Returns", "year": 1992, "consoleKey": "snes", "era": "Konami 16-bit", "matchRegex": (r"\bbatman returns\b",), "preferConsoles": ("snes", "genesis", "nes"), "minervaQuery": "Batman Returns"},
            {"title": "The Adventures of Batman & Robin", "year": 1994, "consoleKey": "snes", "era": "Serie Animada", "matchRegex": (r"\badventures of batman\b",), "preferConsoles": ("snes", "genesis"), "minervaQuery": "Adventures of Batman"},
            {"title": "Batman Forever", "year": 1995, "consoleKey": "snes", "era": "Era Acclaim", "matchRegex": (r"\bbatman forever\b",), "preferConsoles": ("snes", "genesis"), "minervaQuery": "Batman Forever"},
        ],
    },
    {
        "id": "spider-man",
        "name": "Spider-Man & Venom",
        "tagline": "Redes, simbiotes y combates urbanos de Nueva York de Sega a Neversoft.",
        "foundedYear": 1991,
        "creator": "Sega / Software Creations / Neversoft",
        "accent": "#EF4444",
        "history": (
            "Desde el clásico Spider-Man vs. The Kingpin en Mega Drive hasta los explosivos beat 'em ups junto a Venom "
            "(Maximum Carnage y Separation Anxiety) y la revolución poligonal de Neversoft en PS1 y Dreamcast en el año 2000, "
            "el trepamuros construyó una de las trayectorias más variadas del catálogo retro."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Spider-Man and Venom - Maximum Carnage (USA)",
            "boxartName": "Spider-Man and Venom - Maximum Carnage (USA)",
            "sgdbQuery": "Spider-Man & Venom: Maximum Carnage",
        },
        "includeRegex": (r"\bspider[\s-]*man\b", r"\bmaximum carnage\b", r"\bseparation anxiety\b"),
        "excludeRegex": (),
        "timeline": [
            {"title": "Spider-Man vs. The Kingpin", "year": 1991, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"^spider[\s-]*man$", r"\bkingpin\b"), "preferConsoles": ("genesis", "segacd"), "minervaQuery": "Spider-Man"},
            {"title": "Spider-Man / X-Men: Arcade's Revenge", "year": 1992, "consoleKey": "snes", "era": "Crossover 16-bit", "matchRegex": (r"\barcade s? revenge\b",), "preferConsoles": ("snes", "genesis"), "allowCombo": True, "minervaQuery": "Arcade's Revenge"},
            {"title": "Spider-Man & Venom: Maximum Carnage", "year": 1994, "consoleKey": "snes", "era": "Simbiotes 16-bit", "matchRegex": (r"\bmaximum carnage\b",), "preferConsoles": ("snes", "genesis"), "minervaQuery": "Maximum Carnage"},
            {"title": "Venom / Spider-Man: Separation Anxiety", "year": 1995, "consoleKey": "snes", "era": "Simbiotes 16-bit", "matchRegex": (r"\bseparation anxiety\b",), "preferConsoles": ("snes", "genesis"), "minervaQuery": "Separation Anxiety"},
            {"title": "Spider-Man (Neversoft)", "year": 2000, "consoleKey": "ps1", "era": "Era 3D PS1/N64", "matchRegex": (r"\bspider[\s-]*man\b",), "excludeMatchRegex": (r"\b(maximum|separation|arcade|kingpin|mysterio|lethal|2|enter)\b",), "excludeConsoles": ("snes", "genesis"), "preferConsoles": ("ps1", "dc", "n64"), "minervaQuery": "Spider-Man"},
        ],
    },
    {
        "id": "x-men",
        "name": "X-Men",
        "tagline": "Poderes mutantes en equipo desde Mega Drive hasta las arenas de Capcom y Activision.",
        "foundedYear": 1992,
        "creator": "Konami / Sega / Capcom",
        "accent": "#FACC15",
        "history": (
            "Los mutantes de Marvel destacaron tanto en beat 'em ups cooperativos como en lucha: Sega y Headgames firmaron los "
            "exigentes X-Men y X-Men 2: Clone Wars en Mega Drive, Capcom creó Mutant Apocalypse en SNES y su espectacular "
            "cruce frente a Street Fighter antes de pasar a la lucha 3D en Mutant Academy."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "X-Men 2 - Clone Wars (USA, Europe)",
            "boxartName": "X-Men 2 - Clone Wars (USA, Europe)",
            "sgdbQuery": "X-Men 2: Clone Wars",
        },
        "includeRegex": (r"\bx[\s-]*men\b", r"\bwolverine\b"),
        "excludeRegex": (),
        "timeline": [
            {"title": "X-Men (Mega Drive)", "year": 1993, "consoleKey": "genesis", "era": "16-bit", "matchRegex": (r"^x[\s-]*men$",), "preferConsoles": ("genesis",), "minervaQuery": "X-Men"},
            {"title": "X-Men: Mutant Apocalypse", "year": 1994, "consoleKey": "snes", "era": "Capcom SNES", "matchRegex": (r"\bmutant apocalypse\b",), "preferConsoles": ("snes",), "minervaQuery": "Mutant Apocalypse"},
            {"title": "X-Men 2: Clone Wars", "year": 1995, "consoleKey": "genesis", "era": "16-bit", "matchRegex": (r"\bclone wars\b",), "preferConsoles": ("genesis",), "minervaQuery": "Clone Wars"},
            {"title": "X-Men vs. Street Fighter", "year": 1996, "consoleKey": "arcade", "era": "Capcom CPS-2", "matchRegex": (r"\bx[\s-]*men vs street fighter\b",), "preferConsoles": ("arcade", "ps1"), "minervaQuery": "X-Men vs Street Fighter"},
            {"title": "X-Men: Mutant Academy 2", "year": 2001, "consoleKey": "ps1", "era": "PlayStation", "matchRegex": (r"\bmutant academy 2\b",), "preferConsoles": ("ps1",), "minervaQuery": "Mutant Academy 2"},
        ],
    },
    {
        "id": "star-wars",
        "name": "Star Wars",
        "tagline": "Trilogía galáctica de Super Nintendo, Rogue Squadron y aventuras Jedi en GBA.",
        "foundedYear": 1992,
        "creator": "LucasArts / Sculptured Software / Factor 5",
        "accent": "#FDE047",
        "history": (
            "Desde la icónica trilogía Super Star Wars de SNES hasta las batallas aéreas en N64 con Rogue Squadron y las "
            "entregas portátiles de Game Boy Advance centradas en las precuelas y la trilogía original, Star Wars ha sido "
            "sinónimo de ambición técnica en cada generación."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Super Star Wars (USA)",
            "boxartName": "Super Star Wars (USA)",
            "sgdbQuery": "Super Star Wars",
        },
        "includeRegex": (r"\bstar wars\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Super Star Wars", "year": 1992, "consoleKey": "snes", "era": "Trilogía SNES", "matchRegex": (r"^super star wars$",), "preferConsoles": ("snes",), "minervaQuery": "Super Star Wars"},
            {"title": "Super Star Wars: The Empire Strikes Back", "year": 1993, "consoleKey": "snes", "era": "Trilogía SNES", "matchRegex": (r"\bempire strikes back\b",), "preferConsoles": ("snes",), "minervaQuery": "Empire Strikes Back"},
            {"title": "Super Star Wars: Return of the Jedi", "year": 1994, "consoleKey": "snes", "era": "Trilogía SNES", "matchRegex": (r"\breturn of the jedi\b",), "preferConsoles": ("snes",), "minervaQuery": "Return of the Jedi"},
            {"title": "Star Wars: Episode II - Attack of the Clones", "year": 2002, "consoleKey": "gba", "era": "Precuelas GBA", "matchRegex": (r"\battack of the clones\b",), "preferConsoles": ("gba",), "minervaQuery": "Attack of the Clones"},
            {"title": "Star Wars: The New Droid Army", "year": 2002, "consoleKey": "gba", "era": "Precuelas GBA", "matchRegex": (r"\bnew droid army\b",), "preferConsoles": ("gba",), "minervaQuery": "New Droid Army"},
            {"title": "Star Wars Trilogy: Apprentice of the Force", "year": 2004, "consoleKey": "gba", "era": "GBA", "matchRegex": (r"\bapprentice of the force\b",), "preferConsoles": ("gba",), "minervaQuery": "Apprentice of the Force"},
            {"title": "Star Wars: Episode III - Revenge of the Sith", "year": 2005, "consoleKey": "gba", "era": "Precuelas GBA", "matchRegex": (r"\brevenge of the sith\b",), "preferConsoles": ("gba", "ps2"), "minervaQuery": "Revenge of the Sith"},
        ],
    },
    {
        "id": "disney-illusion",
        "name": "Disney: Illusion & Magical Quest",
        "tagline": "Las joyas plataformeras de Sega y Capcom con Mickey, Minnie y Donald.",
        "foundedYear": 1990,
        "creator": "Sega / Tokuro Fujiwara (Capcom)",
        "accent": "#38BDF8",
        "history": (
            "La década de los 90 representó la edad de oro de Disney en videojuegos: Sega AM7 inició con Castle of Illusion "
            "y el carismático QuackShot protagonizado por Donald, mientras Capcom replicaba su magia en SNES con The Magical Quest "
            "y sus secuelas cooperativas llenas de disfraces con habilidades."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Castle of Illusion Starring Mickey Mouse (USA, Europe)",
            "boxartName": "Castle of Illusion Starring Mickey Mouse (USA, Europe)",
            "sgdbQuery": "Castle of Illusion",
        },
        "includeRegex": (r"\b(castle of illusion|world of illusion|quackshot|magical quest|great circus mystery|aladdin|lion king|donald in maui)\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Castle of Illusion Starring Mickey Mouse", "year": 1990, "consoleKey": "genesis", "era": "Serie Illusion (Sega)", "matchRegex": (r"\bcastle of illusion\b", r"\bfushigi no oshiro\b"), "excludeMatchRegex": (r"\bquackshot\b",), "preferConsoles": ("genesis", "sms"), "minervaQuery": "Castle of Illusion"},
            {"title": "QuackShot Starring Donald Duck", "year": 1991, "consoleKey": "genesis", "era": "Serie Illusion (Sega)", "matchRegex": (r"^quackshot\b",), "excludeMatchRegex": (r"\bcastle of illusion\b",), "preferConsoles": ("genesis",), "minervaQuery": "QuackShot"},
            {"title": "World of Illusion (Mickey & Donald)", "year": 1992, "consoleKey": "genesis", "era": "Serie Illusion (Sega)", "matchRegex": (r"\bworld of illusion\b",), "preferConsoles": ("genesis",), "minervaQuery": "World of Illusion"},
            {"title": "The Magical Quest Starring Mickey Mouse", "year": 1992, "consoleKey": "snes", "era": "Magical Quest (Capcom)", "matchRegex": (r"\bmagical quest\b",), "preferConsoles": ("snes", "gba"), "minervaQuery": "Magical Quest"},
            {"title": "Disney's Aladdin", "year": 1993, "consoleKey": "genesis", "era": "Clásicos 16-bit", "matchRegex": (r"^aladdin$", r"\bdisney s aladdin\b"), "preferConsoles": ("genesis", "snes", "gba"), "minervaQuery": "Aladdin"},
            {"title": "The Great Circus Mystery (Magical Quest 2)", "year": 1994, "consoleKey": "snes", "era": "Magical Quest (Capcom)", "matchRegex": (r"\bgreat circus mystery\b", r"\bmagical adventure 2\b"), "preferConsoles": ("snes", "genesis"), "minervaQuery": "Great Circus Mystery"},
            {"title": "The Lion King", "year": 1994, "consoleKey": "snes", "era": "Clásicos 16-bit", "matchRegex": (r"\blion king\b",), "preferConsoles": ("snes", "genesis"), "minervaQuery": "Lion King"},
            {"title": "Donald in Maui Mallard (Cold Shadow)", "year": 1995, "consoleKey": "genesis", "era": "16-bit Tardío", "matchRegex": (r"\bmaui mallard\b",), "preferConsoles": ("snes", "genesis"), "minervaQuery": "Maui Mallard"},
        ],
    },
    {
        "id": "fatal-fury",
        "name": "Fatal Fury & Art of Fighting",
        "tagline": "Las raíces de South Town con Terry Bogard, Ryo Sakazaki y los combates de SNK.",
        "foundedYear": 1991,
        "creator": "SNK / Takashi Nishiyama",
        "accent": "#FBBF24",
        "history": (
            "Antes de unirse en The King of Fighters, Fatal Fury (Garou Densetsu) y Art of Fighting construyeron el universo "
            "de South Town en Neo Geo con planos dobles de profundidad, barras de espíritu y desesperación y animaciones "
            "con zoom dinámico que definieron la rivalidad frente a Capcom en los años 90."
        ),
        "heroArt": {
            "repo": "SNK_-_Neo_Geo",
            "titleName": "Fatal Fury Special (NGM-058 ~ NGH-058)",
            "boxartName": "Fatal Fury Special (NGM-058 ~ NGH-058)",
            "sgdbQuery": "Fatal Fury Special",
        },
        "includeRegex": (r"\b(fatal fury|garou densetsu|art of fighting|ryuuko no ken)\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Fatal Fury: King of Fighters", "year": 1991, "consoleKey": "genesis", "era": "South Town Clásico", "matchRegex": (r"^fatal fury$", r"\bgarou densetsu shukumei\b"), "preferConsoles": ("arcade", "genesis", "snes"), "minervaQuery": "Fatal Fury"},
            {"title": "Art of Fighting", "year": 1992, "consoleKey": "snes", "era": "South Town Clásico", "matchRegex": (r"^art of fighting$",), "preferConsoles": ("arcade", "snes", "genesis"), "minervaQuery": "Art of Fighting"},
            {"title": "Fatal Fury 2", "year": 1992, "consoleKey": "genesis", "era": "South Town Clásico", "matchRegex": (r"\bfatal fury 2\b", r"\bgarou densetsu 2\b"), "preferConsoles": ("arcade", "genesis", "snes"), "minervaQuery": "Fatal Fury 2"},
            {"title": "Fatal Fury Special", "year": 1993, "consoleKey": "snes", "era": "Era Dorada SNK", "matchRegex": (r"\bfatal fury special\b",), "preferConsoles": ("arcade", "snes", "segacd"), "minervaQuery": "Fatal Fury Special"},
            {"title": "Art of Fighting 2", "year": 1994, "consoleKey": "arcade", "era": "Era Dorada SNK", "matchRegex": (r"\bart of fighting 2\b",), "preferConsoles": ("arcade", "snes"), "minervaQuery": "Art of Fighting 2"},
            {"title": "Garou: Mark of the Wolves", "year": 1999, "consoleKey": "dc", "era": "Obra Cumbre", "matchRegex": (r"\bmark of the wolves\b",), "preferConsoles": ("arcade", "dc", "ps2"), "minervaQuery": "Mark of the Wolves"},
        ],
    },
    {
        "id": "thunder-force",
        "name": "Thunder Force",
        "tagline": "Shmups espaciales vertiginosos con rock metálico de Technosoft en Mega Drive.",
        "foundedYear": 1988,
        "creator": "Technosoft / Naosuke Arai",
        "accent": "#38BDF8",
        "history": (
            "Considerada una de las mejores trilogías de disparos horizontales de la generación de 16 bits, Thunder Force "
            "destacó en Mega Drive por su cambio dinámico de velocidad de nave, arsenal seleccionable en pleno vuelo, "
            "múltiples planos de scroll parallax y la inolvidable banda sonora rock sintetizada de Technosoft."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Thunder Force IV (Europe)",
            "boxartName": "Thunder Force IV (Europe)",
            "sgdbQuery": "Thunder Force IV",
        },
        "includeRegex": (r"\b(thunder force|lightening force)\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Thunder Force II", "year": 1988, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\bthunder force ii\b",), "preferConsoles": ("genesis",), "minervaQuery": "Thunder Force II"},
            {"title": "Thunder Force III", "year": 1990, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\bthunder force iii\b", r"\bthunder spirits\b"), "preferConsoles": ("genesis", "snes"), "minervaQuery": "Thunder Force III"},
            {"title": "Thunder Force IV (Lightening Force)", "year": 1992, "consoleKey": "genesis", "era": "Obra Maestra 16-bit", "matchRegex": (r"\bthunder force iv\b", r"\blightening force\b"), "preferConsoles": ("genesis", "saturn"), "minervaQuery": "Thunder Force IV"},
            {"title": "Thunder Force V", "year": 1997, "consoleKey": "ps1", "era": "32-bit", "matchRegex": (r"\bthunder force v\b",), "preferConsoles": ("saturn", "ps1"), "minervaQuery": "Thunder Force V"},
        ],
    },
    {
        "id": "wonder-boy",
        "name": "Wonder Boy & Monster World",
        "tagline": "Aventura, tiendas, equipamiento RPG y plataformas inolvidables de Westone.",
        "foundedYear": 1986,
        "creator": "Westone Bit Entertainment / Ryuichi Nishizawa",
        "accent": "#34D399",
        "history": (
            "De la patineta arcade original de 1986, Westone transformó Monster World en una de las sagas pioneras en fusionar "
            "plataformas laterales con tiendas de equipo, corazones coleccionables y exploración no lineal, coronando en Mega Drive "
            "con Wonder Boy V y Monster World IV."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Wonder Boy in Monster World (USA, Europe)",
            "boxartName": "Wonder Boy in Monster World (USA, Europe)",
            "sgdbQuery": "Wonder Boy in Monster World",
        },
        "includeRegex": (r"\b(wonder boy|monster world|dragon s trap|monster lair)\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Wonder Boy / III: Monster Lair", "year": 1988, "consoleKey": "genesis", "era": "Arcade / Mega Drive", "matchRegex": (r"\bmonster lair\b", r"^wonder boy$"), "preferConsoles": ("genesis", "pce"), "minervaQuery": "Monster Lair"},
            {"title": "Wonder Boy III: The Dragon's Trap", "year": 1989, "consoleKey": "sms", "era": "Master System", "matchRegex": (r"\bdragon s? trap\b",), "preferConsoles": ("sms", "gg"), "minervaQuery": "Dragon's Trap"},
            {"title": "Wonder Boy V: Monster World III", "year": 1991, "consoleKey": "genesis", "era": "Mega Drive RPG", "matchRegex": (r"\bwonder boy in monster world\b", r"\bwonder boy v\b"), "preferConsoles": ("genesis", "sms"), "minervaQuery": "Wonder Boy in Monster World"},
            {"title": "Monster World IV", "year": 1994, "consoleKey": "genesis", "era": "Obra Cumbre MD", "matchRegex": (r"\bmonster world iv\b",), "preferConsoles": ("genesis",), "minervaQuery": "Monster World IV"},
        ],
    },
    {
        "id": "ecco",
        "name": "Ecco the Dolphin",
        "tagline": "Sonares, océanos profundos y ciencia ficción submarina en Sega.",
        "foundedYear": 1992,
        "creator": "Novotrade / Ed Annunziata",
        "accent": "#38BDF8",
        "history": (
            "Obra singular dentro del catálogo de Sega Mega Drive y Sega CD, Ecco the Dolphin combinó físicas fluidas de natación, "
            "comunicación por sonar con ballenas, enigmas ambientales y una oscura historia de ciencia ficción acompañada por "
            "memorables bandas sonoras atmosféricas."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Ecco the Dolphin (USA, Europe)",
            "boxartName": "Ecco the Dolphin (USA, Europe)",
            "sgdbQuery": "Ecco the Dolphin",
        },
        "includeRegex": (r"\becco\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Ecco the Dolphin", "year": 1992, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"^ecco the dolphin$",), "preferConsoles": ("genesis", "segacd"), "minervaQuery": "Ecco the Dolphin"},
            {"title": "Ecco: The Tides of Time", "year": 1994, "consoleKey": "genesis", "era": "Mega Drive", "matchRegex": (r"\btides of time\b", r"\becco.*ii\b"), "preferConsoles": ("genesis", "segacd"), "minervaQuery": "Tides of Time"},
            {"title": "Ecco Jr.", "year": 1995, "consoleKey": "genesis", "era": "Spin-off Familiar", "matchRegex": (r"\becco jr\b",), "preferConsoles": ("genesis",), "minervaQuery": "Ecco Jr"},
            {"title": "Ecco the Dolphin: Defender of the Future", "year": 2000, "consoleKey": "dc", "era": "Dreamcast / PS2", "matchRegex": (r"\bdefender of the future\b",), "preferConsoles": ("dc", "ps2"), "minervaQuery": "Defender of the Future"},
        ],
    },
    {
        "id": "earthworm-jim",
        "name": "Earthworm Jim",
        "tagline": "Humor absurdo de dibujos animados, animación fluida y acción surrealista de los 90.",
        "foundedYear": 1994,
        "creator": "Shiny Entertainment / David Perry & Doug TenNapel",
        "accent": "#FBBF24",
        "history": (
            "Shiny Entertainment deslumbró en 1994 al llevar la fluidez de la animación tradicional hecha a mano directo a SNES y "
            "Mega Drive, convirtiendo a una lombriz con traje espacial cibernético en un icono irreverente de los 16 bits."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Earthworm Jim (USA)",
            "boxartName": "Earthworm Jim (USA)",
            "sgdbQuery": "Earthworm Jim",
        },
        "includeRegex": (r"\bearthworm jim\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Earthworm Jim", "year": 1994, "consoleKey": "genesis", "era": "16-bit Shiny", "matchRegex": (r"^earthworm jim$",), "preferConsoles": ("genesis", "snes", "segacd"), "minervaQuery": "Earthworm Jim"},
            {"title": "Earthworm Jim 2", "year": 1995, "consoleKey": "snes", "era": "16-bit / PS1", "matchRegex": (r"\bearthworm jim 2\b",), "preferConsoles": ("snes", "genesis", "ps1"), "minervaQuery": "Earthworm Jim 2"},
            {"title": "Earthworm Jim 3D", "year": 1999, "consoleKey": "n64", "era": "Era 64-bit", "matchRegex": (r"\bearthworm jim 3d\b",), "preferConsoles": ("n64",), "minervaQuery": "Earthworm Jim 3D"},
        ],
    },
    {
        "id": "strike-series",
        "name": "Strike Series (Desert / Jungle / Urban)",
        "tagline": "Combate táctico isométrico en helicóptero de Electronic Arts.",
        "foundedYear": 1992,
        "creator": "Electronic Arts / Mike Posehn",
        "accent": "#A3E635",
        "history": (
            "La trilogía Strike de 16 bits (Desert Strike, Jungle Strike y Urban Strike) fusionó acción táctica no lineal en vista "
            "isométrica con administración real de combustible, blindaje, rescate de rehenes y objetivos múltiples abiertos, "
            "dando paso luego a Soviet Strike y Nuclear Strike en 32 bits."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Desert Strike - Return to the Gulf (USA, Europe)",
            "boxartName": "Desert Strike - Return to the Gulf (USA, Europe)",
            "sgdbQuery": "Desert Strike",
        },
        "includeRegex": (r"\b(?:desert|jungle|urban|soviet|nuclear) strike\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Desert Strike: Return to the Gulf", "year": 1992, "consoleKey": "genesis", "era": "Trilogía 16-bit", "matchRegex": (r"\bdesert strike\b",), "preferConsoles": ("genesis", "snes", "gba"), "minervaQuery": "Desert Strike"},
            {"title": "Jungle Strike", "year": 1993, "consoleKey": "genesis", "era": "Trilogía 16-bit", "matchRegex": (r"\bjungle strike\b",), "preferConsoles": ("genesis", "snes"), "minervaQuery": "Jungle Strike"},
            {"title": "Urban Strike", "year": 1994, "consoleKey": "genesis", "era": "Trilogía 16-bit", "matchRegex": (r"\burban strike\b",), "preferConsoles": ("genesis", "snes"), "minervaQuery": "Urban Strike"},
            {"title": "Soviet Strike", "year": 1996, "consoleKey": "ps1", "era": "32-bit", "matchRegex": (r"\bsoviet strike\b",), "preferConsoles": ("ps1", "saturn"), "minervaQuery": "Soviet Strike"},
            {"title": "Nuclear Strike", "year": 1997, "consoleKey": "ps1", "era": "32-bit", "matchRegex": (r"\bnuclear strike\b",), "preferConsoles": ("ps1", "n64"), "minervaQuery": "Nuclear Strike"},
        ],
    },
    {
        "id": "sega-super-scaler",
        "name": "Sega Super Scalers (Arcade)",
        "tagline": "Velocidad pseudo-3D en Ferrari, motos y cazas de combate diseñada por Yu Suzuki.",
        "foundedYear": 1985,
        "creator": "Sega AM2 / Yu Suzuki",
        "accent": "#F43F5E",
        "history": (
            "Entre 1985 y finales de los 80, Yu Suzuki y Sega AM2 sorprendieron al mundo con cabinas hidráulicas arcade y placas "
            "Super Scaler capaces de mover sprites a 60 cuadros en clásicos inmortales como Space Harrier, Super Hang-On, "
            "OutRun y After Burner II, adaptados brillantemente a Sega Mega Drive."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "OutRun (USA, Europe)",
            "boxartName": "OutRun (USA, Europe)",
            "sgdbQuery": "OutRun",
        },
        "includeRegex": (r"\b(?:outrun|out run|turbo outrun|outrun 2019|after burner|space harrier|super hang[\s-]*on)\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Space Harrier II", "year": 1988, "consoleKey": "genesis", "era": "Lanzamiento Mega Drive", "matchRegex": (r"\bspace harrier ii\b",), "preferConsoles": ("genesis",), "minervaQuery": "Space Harrier II"},
            {"title": "Super Hang-On", "year": 1989, "consoleKey": "genesis", "era": "Motos AM2", "matchRegex": (r"\bsuper hang[\s-]*on\b",), "preferConsoles": ("genesis", "arcade"), "minervaQuery": "Super Hang-On"},
            {"title": "After Burner II", "year": 1990, "consoleKey": "genesis", "era": "Combate Aéreo", "matchRegex": (r"\bafter burner ii\b",), "preferConsoles": ("genesis", "arcade"), "minervaQuery": "After Burner II"},
            {"title": "OutRun", "year": 1991, "consoleKey": "genesis", "era": "Ferrari Testarossa", "matchRegex": (r"^outrun$", r"^out run$"), "preferConsoles": ("genesis", "arcade"), "minervaQuery": "OutRun"},
            {"title": "Turbo OutRun", "year": 1992, "consoleKey": "genesis", "era": "Secuela Arcade", "matchRegex": (r"\bturbo outrun\b",), "preferConsoles": ("genesis", "arcade"), "minervaQuery": "Turbo OutRun"},
            {"title": "OutRun 2019", "year": 1993, "consoleKey": "genesis", "era": "Futurista MD", "matchRegex": (r"\boutrun 2019\b",), "preferConsoles": ("genesis",), "minervaQuery": "OutRun 2019"},
        ],
    },
    {
        "id": "road-rash",
        "name": "Road Rash",
        "tagline": "Carreras ilegales en motocicleta, cadenas, tráfico y asfalto en Mega Drive.",
        "foundedYear": 1991,
        "creator": "Electronic Arts / Randy Breen & Dan Geisler",
        "accent": "#FB923C",
        "history": (
            "Road Rash mezcló velocidad por carreteras abiertas llenas de tráfico y colinas con combate cuerpo a cuerpo sobre "
            "dos ruedas para escapar de la policía y ganar premios para mejorar la motocicleta, convirtiéndose en una de las "
            "trilogías más queridas de Electronic Arts en Mega Drive."
        ),
        "heroArt": {
            "repo": "Sega_-_Mega_Drive_-_Genesis",
            "titleName": "Road Rash II (USA, Europe)",
            "boxartName": "Road Rash II (USA, Europe)",
            "sgdbQuery": "Road Rash II",
        },
        "includeRegex": (r"\broad rash\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Road Rash", "year": 1991, "consoleKey": "genesis", "era": "Trilogía Mega Drive", "matchRegex": (r"^road rash$",), "preferConsoles": ("genesis", "sms"), "minervaQuery": "Road Rash"},
            {"title": "Road Rash II", "year": 1992, "consoleKey": "genesis", "era": "Trilogía Mega Drive", "matchRegex": (r"\broad rash (ii|2)\b",), "preferConsoles": ("genesis",), "minervaQuery": "Road Rash II"},
            {"title": "Road Rash 3", "year": 1995, "consoleKey": "genesis", "era": "Trilogía Mega Drive", "matchRegex": (r"\broad rash (3|iii)\b",), "preferConsoles": ("genesis",), "minervaQuery": "Road Rash 3"},
            {"title": "Road Rash 64 / 3D", "year": 1998, "consoleKey": "n64", "era": "Era 3D", "matchRegex": (r"\broad rash (3d|64)\b",), "preferConsoles": ("n64", "ps1"), "minervaQuery": "Road Rash 64"},
        ],
    },
    {
        "id": "bomberman",
        "name": "Bomberman",
        "tagline": "Laberintos explosivos y multijugador legendario de Hudson Soft.",
        "foundedYear": 1985,
        "creator": "Hudson Soft / Shinichi Nakamoto",
        "accent": "#F472B6",
        "history": (
            "Desde su estreno en Famicom y el salto multijugador masivo de Super Bomberman en SNES y Mega Bomberman en Mega Drive, "
            "pocas sagas han logrado ser sinónimo tan inmediato de partidas locales llenas de risas, trampas cronometradas y "
            "cadenas de explosiones."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Super Bomberman (USA)",
            "boxartName": "Super Bomberman (USA)",
            "sgdbQuery": "Super Bomberman",
        },
        "includeRegex": (r"\bbomberman\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Super Bomberman", "year": 1993, "consoleKey": "snes", "era": "Multijugador 16-bit", "matchRegex": (r"^super bomberman$",), "preferConsoles": ("snes",), "minervaQuery": "Super Bomberman"},
            {"title": "Mega Bomberman", "year": 1994, "consoleKey": "genesis", "era": "Mega Drive 16-bit", "matchRegex": (r"\bmega bomberman\b",), "preferConsoles": ("genesis",), "minervaQuery": "Mega Bomberman"},
            {"title": "Super Bomberman 2", "year": 1994, "consoleKey": "snes", "era": "Multijugador 16-bit", "matchRegex": (r"\bsuper bomberman 2\b",), "preferConsoles": ("snes",), "minervaQuery": "Super Bomberman 2"},
            {"title": "Super Bomberman 3", "year": 1995, "consoleKey": "snes", "era": "Multijugador 16-bit", "matchRegex": (r"\bsuper bomberman 3\b",), "preferConsoles": ("snes",), "minervaQuery": "Super Bomberman 3"},
            {"title": "Bomberman 64", "year": 1997, "consoleKey": "n64", "era": "Era 64-bit", "matchRegex": (r"\bbomberman 64\b",), "preferConsoles": ("n64",), "minervaQuery": "Bomberman 64"},
        ],
    },
    {
        "id": "seiken-densetsu",
        "name": "Saga Mana (Seiken Densetsu)",
        "tagline": "Action RPG coloridos, música mágica y cooperativo desde Secret of Mana.",
        "foundedYear": 1991,
        "creator": "Square / Koichi Ishii & Hiroki Kikuta",
        "accent": "#34D399",
        "history": (
            "Nacida como spin-off de Final Fantasy en Game Boy y elevada a obra cumbre en Super Nintendo con Secret of Mana "
            "y Seiken Densetsu 3, la saga Mana redefinió los Action RPG cooperativos con combates en tiempo real, menús en "
            "anillo giratorio y el esplendor visual pintado a mano de Legend of Mana y Sword of Mana."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Secret of Mana (USA)",
            "boxartName": "Secret of Mana (USA)",
            "sgdbQuery": "Secret of Mana",
        },
        "includeRegex": (r"\b(?:secret of mana|seiken densetsu|trials of mana|legend of mana|sword of mana|final fantasy adventure)\b",),
        "excludeRegex": (),
        "timeline": [
            {"title": "Secret of Mana", "year": 1993, "consoleKey": "snes", "era": "SNES Cooperativo", "matchRegex": (r"\bsecret of mana\b", r"\bseiken densetsu 2\b"), "preferConsoles": ("snes",), "minervaQuery": "Secret of Mana"},
            {"title": "Trials of Mana (Seiken Densetsu 3)", "year": 1995, "consoleKey": "snes", "era": "Obra Cumbre SNES", "matchRegex": (r"\bseiken densetsu 3\b", r"\btrials of mana\b"), "preferConsoles": ("snes",), "minervaQuery": "Seiken Densetsu 3"},
            {"title": "Legend of Mana", "year": 1999, "consoleKey": "ps1", "era": "PlayStation", "matchRegex": (r"\blegend of mana\b",), "preferConsoles": ("ps1",), "minervaQuery": "Legend of Mana"},
            {"title": "Sword of Mana", "year": 2003, "consoleKey": "gba", "era": "Remake GBA", "matchRegex": (r"\bsword of mana\b",), "preferConsoles": ("gba",), "minervaQuery": "Sword of Mana"},
        ],
    },
    {
        "id": "lufia",
        "name": "Saga Lufia (Estpolis)",
        "tagline": "Calabozos con ingeniosos acertijos, monstruos cápsula y RPG inolvidables de 16 bits.",
        "foundedYear": 1993,
        "creator": "Neverland / Taito",
        "accent": "#A78BFA",
        "history": (
            "Lufia & The Fortress of Doom y, sobre todo, su precuela Lufia II: Rise of the Sinistrals en Super Nintendo, "
            "cautivaron a los amantes del JRPG gracias a calabozos llenos de acertijos estilo Zelda, monstruos cápsula evolucionables "
            "y la adictiva Ancient Cave de 99 pisos generados al azar."
        ),
        "heroArt": {
            "repo": "Nintendo_-_Super_Nintendo_Entertainment_System",
            "titleName": "Lufia II - Rise of the Sinistrals (USA)",
            "boxartName": "Lufia II - Rise of the Sinistrals (USA)",
            "sgdbQuery": "Lufia II",
        },
        "includeRegex": (r"\blufia\b", r"\bestpolis\b"),
        "excludeRegex": (),
        "timeline": [
            {"title": "Lufia & The Fortress of Doom", "year": 1993, "consoleKey": "snes", "era": "16-bit", "matchRegex": (r"\bfortress of doom\b",), "preferConsoles": ("snes", "genesis"), "minervaQuery": "Lufia Fortress of Doom"},
            {"title": "Lufia II: Rise of the Sinistrals", "year": 1995, "consoleKey": "snes", "era": "Obra Cumbre SNES", "matchRegex": (r"\blufia ii\b", r"\brise of the sinistrals\b"), "preferConsoles": ("snes",), "minervaQuery": "Lufia II"},
            {"title": "Lufia: The Legend Returns", "year": 2001, "consoleKey": "gbc", "era": "Portátil", "matchRegex": (r"\blegend returns\b",), "preferConsoles": ("gbc",), "minervaQuery": "Lufia The Legend Returns"},
            {"title": "Lufia: The Ruins of Lore", "year": 2002, "consoleKey": "gba", "era": "GBA", "matchRegex": (r"\bruins of lore\b",), "preferConsoles": ("gba",), "minervaQuery": "Ruins of Lore"},
        ],
    },
]


_UNOFFICIAL_NOISE_RE = re.compile(
    r"\((?:pirate|aftermarket|proto(?:type)?|seganet|demo|sample)[^)]*\)|\[b[0-9a-z]*\]",
    re.I,
)


def _is_unofficial_saga_noise(item: dict[str, Any]) -> bool:
    raw = str(item.get("title") or "")
    return bool(_UNOFFICIAL_NOISE_RE.search(raw))


def _pick_best_match(
    candidates: list[dict[str, Any]],
    prefer_consoles: tuple[str, ...] = (),
    allow_combo: bool = False,
) -> dict[str, Any]:
    def score(x: dict[str, Any]) -> tuple[Any, ...]:
        raw = str(x.get("title") or "").lower()
        is_noise = any(w in raw for w in ("(beta", "(proto", "(pirate", "(unl", "(aftermarket", "(seganet", "[b"))
        is_combo = (" + " in raw or " ~ " in raw or "compilation" in raw or "collection" in raw) and not allow_combo
        ck = str(x.get("consoleKey") or "")
        pref_score = (len(prefer_consoles) - prefer_consoles.index(ck)) if ck in prefer_consoles else 0
        return (
            not is_noise,
            not is_combo,
            pref_score,
            bool(x.get("hasSpanishPatch")),
            bool(x.get("hasCover")),
            int(x.get("playCount") or 0),
            bool(x.get("favorite")),
            "world" in str(x.get("region") or "").lower(),
            "usa" in str(x.get("region") or "").lower(),
        )

    return sorted(candidates, key=score, reverse=True)[0]


def _serialize_owned_entry(
    order: int,
    item: dict[str, Any],
    override_title: str = "",
    year: int | None = None,
    era: str = "",
    variant_count: int = 1,
    is_extra: bool = False,
) -> dict[str, Any]:
    real_clean = item.get("cleanTitle") or item.get("title") or ""
    variants = list(item.get("variants") or [])
    total_variants = max(variant_count, len(variants))
    return {
        "order": order,
        "title": override_title or real_clean,
        "matchedTitle": real_clean if override_title and _norm(override_title) != _norm(real_clean) else "",
        "year": year,
        "era": era,
        "isExtra": is_extra,
        "variantCount": total_variants,
        "variants": variants,
        "consoleKey": item.get("consoleKey") or "other",
        "owned": True,
        "entryId": item["entryId"],
        "coverUrl": item.get("coverUrl"),
        "hasCover": bool(item.get("hasCover")),
        "hasSpanishPatch": bool(item.get("hasSpanishPatch")),
        "playCount": int(item.get("playCount") or 0),
        "favorite": bool(item.get("favorite")),
        "region": item.get("region") or "",
        "minervaQuery": (override_title or real_clean).split(":")[0],
    }


def _should_skip_demo_disc(item: dict[str, Any]) -> bool:
    raw = str(item.get("title") or "")
    # Multi-game compilation discs like "Rival Schools + Resident Evil 2 + Bloody Roar + ..."
    return raw.count(" + ") >= 2


def build_sagas(
    library_items: list[dict[str, Any]],
    custom_sagas: list[dict[str, Any]] | None = None,
    covers_dir: Path | None = None,
) -> list[dict[str, Any]]:
    base = [
        it
        for it in library_items
        if not it.get("isSys") and not _should_skip_demo_disc(it) and not _is_unofficial_saga_noise(it)
    ]
    by_id = {it["entryId"]: it for it in base}
    custom_map = {
        str(c.get("id")): c
        for c in (custom_sagas or [])
        if isinstance(c, dict) and c.get("id")
    }

    sagas_out: list[dict[str, Any]] = []
    seen_custom_ids: set[str] = set()

    for saga in CURATED_SAGAS:
        sid = saga["id"]
        inc_pats: tuple[str, ...] = saga.get("includeRegex") or ()
        exc_pats: tuple[str, ...] = saga.get("excludeRegex") or ()

        pool: list[dict[str, Any]] = []
        for it in base:
            norm_clean, norm_raw = _clean_match_text(it)
            if not _matches_any(inc_pats, norm_clean, norm_raw):
                continue
            if exc_pats and _matches_any(exc_pats, norm_clean, norm_raw):
                continue
            pool.append(it)

        # Merge any manually pinned entries for this curated saga id
        custom_ext = custom_map.get(sid)
        if custom_ext:
            seen_custom_ids.add(sid)
            pool_ids = {it["entryId"] for it in pool}
            for eid in custom_ext.get("entryIds") or []:
                if eid in by_id and eid not in pool_ids:
                    pool.append(by_id[eid])
                    pool_ids.add(eid)

        if not pool:
            # Keep researched sagas discoverable even with 0 owned ROMs yet; UI can toggle "En mi bóveda" vs "Todas"
            pass

        entries: list[dict[str, Any]] = []
        used_ids: set[str] = set()
        used_norms: set[str] = set()
        # Sort canonical timeline strictly by release year
        sorted_timeline = sorted(saga["timeline"], key=lambda m: (int(m.get("year") or 9999), m["title"]))

        order = 1
        for ms in sorted_timeline:
            m_pats: tuple[str, ...] = ms.get("matchRegex") or ()
            m_exc: tuple[str, ...] = ms.get("excludeMatchRegex") or ()
            excl_consoles = tuple(ms.get("excludeConsoles") or ())

            hits: list[dict[str, Any]] = []
            for it in pool:
                if it["entryId"] in used_ids:
                    continue
                if excl_consoles and it.get("consoleKey") in excl_consoles:
                    continue
                norm_clean, norm_raw = _clean_match_text(it)
                if m_exc and _matches_any(m_exc, norm_clean, norm_raw):
                    continue
                match_ok = _matches_any(m_pats, norm_clean, norm_raw)
                # Special guard when a US SNES numbering ("Final Fantasy II/III") conflicts with NES Japanese numbering
                if not match_ok and ms.get("snesAliasRegex"):
                    if it.get("consoleKey") == "snes" and _matches_any(ms["snesAliasRegex"], norm_clean, norm_raw):
                        match_ok = True
                if match_ok:
                    hits.append(it)

            if hits:
                best = _pick_best_match(
                    hits,
                    prefer_consoles=tuple(ms.get("preferConsoles") or ()),
                    allow_combo=bool(ms.get("allowCombo")),
                )
                # Consume all duplicate editions/ports matched by this milestone so timeline stays clean
                for h in hits:
                    used_ids.add(h["entryId"])
                    nc, _ = _clean_match_text(h)
                    if nc:
                        used_norms.add(nc)
                entries.append(
                    _serialize_owned_entry(
                        order,
                        best,
                        override_title=ms["title"],
                        year=ms.get("year"),
                        era=ms.get("era") or "",
                        variant_count=len(hits),
                        is_extra=False,
                    )
                )
            else:
                ghost_cover_url = None
                if covers_dir is not None:
                    ext_id = _ext_cover_id(f"saga:{sid}:{ms['title'].lower()}")
                    ghost_cover_url = _cover_url_if_exists(covers_dir / f"{ext_id}.webp")
                entries.append(
                    {
                        "order": order,
                        "title": ms["title"],
                        "matchedTitle": "",
                        "year": ms.get("year"),
                        "era": ms.get("era") or "",
                        "isExtra": False,
                        "variantCount": 0,
                        "consoleKey": ms.get("consoleKey") or "arcade",
                        "owned": False,
                        "entryId": None,
                        "coverUrl": ghost_cover_url,
                        "hasCover": bool(ghost_cover_url),
                        "hasSpanishPatch": False,
                        "playCount": 0,
                        "favorite": False,
                        "region": "",
                        "minervaQuery": ms.get("minervaQuery") or ms["title"],
                    }
                )
            order += 1

        # Add remaining non-duplicate owned spin-offs/extras belonging strictly to the saga
        extra_candidates: dict[str, list[dict[str, Any]]] = {}
        for it in pool:
            if it["entryId"] in used_ids:
                continue
            raw_t = str(it.get("title") or "")
            # Lock-on combos (e.g. "Sonic & Knuckles + Sonic The Hedgehog 2") shouldn't clutter extras if base titles were matched
            if " + " in raw_t and any(un in _clean_match_text(it)[0] for un in used_norms if len(un) >= 6):
                used_ids.add(it["entryId"])
                continue
            nc, _ = _clean_match_text(it)
            if nc and nc in used_norms:
                used_ids.add(it["entryId"])
                continue
            extra_candidates.setdefault(nc or it["entryId"], []).append(it)

        for key in sorted(extra_candidates.keys()):
            group = extra_candidates[key]
            best = _pick_best_match(group)
            for g in group:
                used_ids.add(g["entryId"])
            entries.append(
                _serialize_owned_entry(
                    order,
                    best,
                    era="Extra / Spin-off",
                    variant_count=len(group),
                    is_extra=True,
                )
            )
            order += 1

        owned_entries = [e for e in entries if e["owned"]]

        hero_banner_url = _cover_url_if_exists(covers_dir / f"saga-hero-{sid}.webp") if covers_dir else None
        key_art_url = _cover_url_if_exists(covers_dir / f"saga-key-{sid}.webp") if covers_dir else None
        first_owned_cover = next((e["coverUrl"] for e in owned_entries if e.get("hasCover") and e.get("coverUrl")), None)
        banner = hero_banner_url or key_art_url or first_owned_cover

        canonical_owned = sum(1 for e in entries if e["owned"] and not e.get("isExtra"))
        canonical_total = sum(1 for e in entries if not e.get("isExtra"))
        consoles = list(dict.fromkeys([e["consoleKey"] for e in owned_entries if e.get("consoleKey")]))
        if not consoles:
            consoles = list(dict.fromkeys([e["consoleKey"] for e in entries[:4] if e.get("consoleKey")]))
        played_count = sum(1 for e in owned_entries if e["playCount"] > 0)

        sagas_out.append(
            {
                "id": sid,
                "name": saga["name"],
                "tagline": saga["tagline"],
                "history": saga.get("history") or saga["tagline"],
                "foundedYear": saga.get("foundedYear"),
                "creator": saga.get("creator") or "",
                "accent": saga["accent"],
                "isCustom": False,
                "ownedCount": len(owned_entries),
                "totalCount": len(entries),
                "canonicalOwnedCount": canonical_owned,
                "canonicalTotalCount": canonical_total,
                "playedCount": played_count,
                "consoles": consoles,
                "heroBannerUrl": hero_banner_url,
                "keyArtUrl": key_art_url,
                "bannerCoverUrl": banner,
                "entries": entries,
            }
        )

    # Purely user-created custom sagas
    for cid, cs in custom_map.items():
        if cid in seen_custom_ids:
            continue
        items = [by_id[eid] for eid in (cs.get("entryIds") or []) if eid in by_id]
        entries = [_serialize_owned_entry(idx + 1, it) for idx, it in enumerate(items)]
        banner = next((e["coverUrl"] for e in entries if e.get("hasCover") and e.get("coverUrl")), None)
        consoles = list(dict.fromkeys([e["consoleKey"] for e in entries if e.get("consoleKey")]))
        sagas_out.append(
            {
                "id": cid,
                "name": str(cs.get("name") or "Mi Saga"),
                "tagline": str(cs.get("tagline") or "Colección personalizada por orden cronológico"),
                "history": str(cs.get("tagline") or "Colección personalizada creada dentro de Game Vault."),
                "foundedYear": None,
                "creator": "Personalizada",
                "accent": str(cs.get("accent") or "#FFB000"),
                "isCustom": True,
                "ownedCount": len(entries),
                "totalCount": len(entries),
                "canonicalOwnedCount": len(entries),
                "canonicalTotalCount": len(entries),
                "playedCount": sum(1 for e in entries if e["playCount"] > 0),
                "consoles": consoles,
                "heroBannerUrl": banner,
                "keyArtUrl": banner,
                "bannerCoverUrl": banner,
                "entries": entries,
            }
        )

    sagas_out.sort(
        key=lambda s: (
            bool(s["isCustom"]),
            s["ownedCount"] > 0,
            s.get("canonicalOwnedCount", s["ownedCount"]),
            s["ownedCount"],
        ),
        reverse=True,
    )
    return sagas_out


def find_saga_for_entry(entry_id: str, sagas: list[dict[str, Any]]) -> dict[str, Any] | None:
    for s in sagas:
        for e in s.get("entries", []):
            if not e.get("owned"):
                continue
            if e.get("entryId") == entry_id or any(v.get("entryId") == entry_id for v in (e.get("variants") or [])):
                return {
                    "id": s["id"],
                    "name": s["name"],
                    "ownedCount": s["ownedCount"],
                    "totalCount": s["totalCount"],
                    "accent": s["accent"],
                }
    return None

from __future__ import annotations

import os
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent


def is_frozen() -> bool:
    return bool(getattr(sys, "frozen", False))


def resource_path(rel: str) -> Path:
    base = Path(getattr(sys, "_MEIPASS", BASE_DIR)) if is_frozen() else BASE_DIR
    return base / rel


def resolve_default_data_dir() -> Path:
    env_dir = os.environ.get("OZ_DATA_DIR", "").strip()
    if env_dir:
        return Path(env_dir)
    if is_frozen():
        exe_dir = Path(sys.executable).resolve().parent
        if (exe_dir / "portable.flag").is_file():
            return exe_dir / "data"
        local_app = os.environ.get("LOCALAPPDATA", "").strip()
        if local_app:
            return Path(local_app) / "OzRetroRom" / "data"
        return exe_dir / "data"
    return BASE_DIR / "data"


def resolve_default_roms_dir() -> str:
    env_dir = os.environ.get("OZ_ROMS_DIR", "").strip()
    if env_dir:
        return env_dir
    legacy_path = Path(r"F:\Games\RetroArch\roms")
    if legacy_path.is_dir():
        return str(legacy_path)
    return str(Path.home() / "Documents" / "OzRetroRom" / "roms")


DEFAULT_ROMS_DIR = resolve_default_roms_dir()
DEFAULT_DATA_DIR = resolve_default_data_dir()
DEFAULT_JAVA_URL = os.environ.get("OZ_JAVA_COMPANION_URL", "http://192.168.100.173:17893")
DEFAULT_HOST = os.environ.get("OZ_HOST", "0.0.0.0")
DEFAULT_PORT = int(os.environ.get("OZ_PORT", "17894"))

NON_ROM_EXTS = {
    "txt", "cfg", "opt", "ini", "lpl", "log", "xml",
    "png", "jpg", "jpeg", "webp", "json", "db", "srm",
    "state", "rtc", "stfolder", "bak", "tmp",
    "sav", "cht", "eep", "fla", "mpk", "mcr", "mcd",
    "vmu", "nv", "hi", "nfo", "md5", "sfv", "dat",
    "ips", "bps", "ups", "xdelta", "oops",
    "ozpart", "part", "aria2", "torrent", "crdownload",
}

ARCADE_PLAYABLE_EXTS = {"zip", "7z", "chd", "rar", "iso", "cue", "bin", "fba"}

CONSOLES: dict[str, dict[str, str]] = {
    "nes": {"label": "NES", "full": "Nintendo NES", "color": "#FCA5A5", "bg": "#2B161B", "target": "Roms/nes"},
    "snes": {"label": "SNES", "full": "Super Nintendo", "color": "#C4B5FD", "bg": "#1D1833", "target": "Roms/snes"},
    "gb": {"label": "GB", "full": "Game Boy", "color": "#BEF264", "bg": "#1B2413", "target": "Roms/gb"},
    "gbc": {"label": "GBC", "full": "Game Boy Color", "color": "#A3E635", "bg": "#182413", "target": "Roms/gbc"},
    "gba": {"label": "GBA", "full": "Game Boy Advance", "color": "#A5B4FC", "bg": "#181E36", "target": "Roms/gba"},
    "nds": {"label": "NDS", "full": "Nintendo DS", "color": "#93C5FD", "bg": "#142033", "target": "Roms/nds"},
    "3ds": {"label": "3DS", "full": "Nintendo 3DS", "color": "#F9A8D4", "bg": "#2A1624", "target": "Roms/3ds"},
    "n64": {"label": "N64", "full": "Nintendo 64", "color": "#6EE7B7", "bg": "#122820", "target": "Roms/n64"},
    "gc": {"label": "GameCube", "full": "Nintendo GameCube", "color": "#93C5FD", "bg": "#152035", "target": "Roms/gc"},
    "wii": {"label": "Wii", "full": "Nintendo Wii", "color": "#BAE6FD", "bg": "#132433", "target": "Roms/wii"},
    "wiiu": {"label": "Wii U", "full": "Nintendo Wii U", "color": "#67E8F9", "bg": "#12242E", "target": "Roms/wiiu"},
    "switch": {"label": "Switch", "full": "Nintendo Switch", "color": "#F87171", "bg": "#2C1419", "target": "switch/nsp"},
    "genesis": {"label": "Genesis", "full": "Mega Drive / Genesis", "color": "#7DD3FC", "bg": "#122233", "target": "Roms/md"},
    "sms": {"label": "Master Sys", "full": "Sega Master System", "color": "#60A5FA", "bg": "#131F33", "target": "Roms/sms"},
    "gg": {"label": "Game Gear", "full": "Sega Game Gear", "color": "#818CF8", "bg": "#181D35", "target": "Roms/gg"},
    "segacd": {"label": "Sega CD", "full": "Sega CD / Mega-CD", "color": "#38BDF8", "bg": "#102130", "target": "Roms/segacd"},
    "sega32x": {"label": "32X", "full": "Sega 32X", "color": "#FBBF24", "bg": "#282012", "target": "Roms/sega32x"},
    "saturn": {"label": "Saturn", "full": "Sega Saturn", "color": "#E9D5FF", "bg": "#231730", "target": "Roms/saturn"},
    "dc": {"label": "Dreamcast", "full": "Sega Dreamcast", "color": "#FDBA74", "bg": "#2B1D12", "target": "Roms/dc"},
    "ps1": {"label": "PS1", "full": "PlayStation", "color": "#5EEAD4", "bg": "#102627", "target": "Roms/ps1"},
    "ps2": {"label": "PS2", "full": "PlayStation 2", "color": "#93C5FD", "bg": "#131D34", "target": "Roms/ps2"},
    "ps3": {"label": "PS3", "full": "PlayStation 3", "color": "#A5B4FC", "bg": "#161B33", "target": "PS3ISO"},
    "ps4": {"label": "PS4", "full": "PlayStation 4", "color": "#60A5FA", "bg": "#111C33", "target": "PS4"},
    "psp": {"label": "PSP", "full": "PlayStation Portable", "color": "#CBD5E1", "bg": "#1A202C", "target": "Roms/psp"},
    "psvita": {"label": "PS Vita", "full": "PlayStation Vita", "color": "#7DD3FC", "bg": "#12202E", "target": "PSVITA"},
    "arcade": {"label": "Arcade", "full": "Arcade (FBNeo / MAME)", "color": "#FCD34D", "bg": "#292011", "target": "Roms/arcade"},
    "pce": {"label": "PC Engine", "full": "PC Engine / TurboGrafx", "color": "#FDBA74", "bg": "#281C14", "target": "Roms/pce"},
    "atari2600": {"label": "Atari 2600", "full": "Atari 2600", "color": "#FDA4AF", "bg": "#28161B", "target": "Roms/atari2600"},
    "atari7800": {"label": "Atari 7800", "full": "Atari 7800", "color": "#FDBA74", "bg": "#261B14", "target": "Roms/atari7800"},
    "atarilynx": {"label": "Lynx", "full": "Atari Lynx", "color": "#FCD34D", "bg": "#241E12", "target": "Roms/atarilynx"},
    "atarijaguar": {"label": "Jaguar", "full": "Atari Jaguar", "color": "#FCA5A5", "bg": "#261618", "target": "Roms/atarijaguar"},
    "msx": {"label": "MSX", "full": "MSX / MSX2", "color": "#CBD5E1", "bg": "#191E28", "target": "Roms/msx"},
    "amiga": {"label": "Amiga", "full": "Commodore Amiga", "color": "#F9A8D4", "bg": "#241621", "target": "Roms/amiga"},
}

PLATFORM_ALIASES: dict[str, str] = {
    "mega drive _ genesis": "genesis",
    "megadrive": "genesis",
    "md": "genesis",
    "sega mega drive": "genesis",
    "sfc": "snes",
    "snesna": "snes",
    "super nintendo": "snes",
    "super nintendo entertainment system": "snes",
    "nintendo entertainment system": "nes",
    "famicom": "nes",
    "game boy advance": "gba",
    "game boy color": "gbc",
    "game boy": "gb",
    "nintendo ds": "nds",
    "nintendo 3ds": "3ds",
    "n3ds": "3ds",
    "nintendo 64": "n64",
    "n64dd": "n64",
    "nintendo gamecube": "gc",
    "gamecube": "gc",
    "nintendo wii": "wii",
    "nintendo switch": "switch",
    "nsw": "switch",
    "nx": "switch",
    "fbneo": "arcade",
    "mame": "arcade",
    "neogeo": "arcade",
    "cps1": "arcade",
    "cps2": "arcade",
    "cps3": "arcade",
    "consolearcade": "arcade",
    "playstation": "ps1",
    "sony playstation": "ps1",
    "psx": "ps1",
    "playstation 2": "ps2",
    "sony playstation 2": "ps2",
    "playstation 3": "ps3",
    "sony playstation 3": "ps3",
    "playstation 4": "ps4",
    "playstation portable": "psp",
    "sony playstation portable": "psp",
    "vita": "psvita",
    "pcengine": "pce",
    "tg16": "pce",
    "mastersystem": "sms",
    "sega master system": "sms",
    "gamegear": "gg",
    "sega game gear": "gg",
    "dreamcast": "dc",
    "sega dreamcast": "dc",
    "sega saturn": "saturn",
    "saturnjp": "saturn",
    "msx2": "msx",
    "msxturbor": "msx",
    "amiga600": "amiga",
    "amiga1200": "amiga",
    "amigacd32": "amiga",
}

LIBRETRO_SYSTEMS: dict[str, str] = {
    "nes": "Nintendo - Nintendo Entertainment System",
    "snes": "Nintendo - Super Nintendo Entertainment System",
    "gb": "Nintendo - Game Boy",
    "gbc": "Nintendo - Game Boy Color",
    "gba": "Nintendo - Game Boy Advance",
    "nds": "Nintendo - Nintendo DS",
    "3ds": "Nintendo - Nintendo 3DS",
    "n64": "Nintendo - Nintendo 64",
    "gc": "Nintendo - GameCube",
    "wii": "Nintendo - Wii",
    "genesis": "Sega - Mega Drive - Genesis",
    "sms": "Sega - Master System - Mark III",
    "gg": "Sega - Game Gear",
    "segacd": "Sega - Mega-CD - Sega CD",
    "sega32x": "Sega - 32X",
    "saturn": "Sega - Saturn",
    "dc": "Sega - Dreamcast",
    "ps1": "Sony - PlayStation",
    "ps2": "Sony - PlayStation 2",
    "ps3": "Sony - PlayStation 3",
    "psp": "Sony - PlayStation Portable",
    "arcade": "FBNeo - Arcade Games",
    "pce": "NEC - PC Engine - TurboGrafx 16",
    "atari2600": "Atari - 2600",
    "atari7800": "Atari - 7800",
    "atarilynx": "Atari - Lynx",
    "atarijaguar": "Atari - Jaguar",
}

LIBRETRO_SYSTEM_REPOS: dict[str, list[str]] = {
    "arcade": ["FBNeo - Arcade Games", "MAME"],
    "mame": ["MAME", "FBNeo - Arcade Games"],
}


def normalize_console_key(raw_platform: str) -> str:
    raw = (raw_platform or "").strip().lower()
    return PLATFORM_ALIASES.get(raw, raw or "other")


DEFAULT_CONSOLE_PATHS: dict[str, str] = {
    "mame": "arcade",
    "arcade": "arcade",
    "snes": "snes",
    "nes": "nes",
    "genesis": "genesis",
    "gba": "gba",
    "gb": "gb",
    "gbc": "gbc",
    "nds": "nds",
    "3ds": "3ds",
    "n64": "n64",
    "gc": "gc",
    "wii": "wii",
    "switch": "switch",
    "ps1": "ps1",
    "ps2": "ps2",
    "ps3": "ps3",
    "psp": "psp",
    "psvita": "psvita",
    "dc": "dc",
    "saturn": "saturn",
    "sms": "sms",
    "gg": "gg",
}


def resolve_console_local_dir(
    roms_dir: str,
    console_paths: dict[str, str] | None,
    platform: str,
    arcade_core: str = "",
) -> tuple[Path, str]:
    root = Path(roms_dir or ".").resolve()
    plat_raw = (platform or "").strip().lower()
    core = (arcade_core or "").strip().lower()
    console_key = normalize_console_key(plat_raw)
    mapping = {str(k).strip().lower(): str(v).strip() for k, v in (console_paths or {}).items() if str(v).strip()}

    if plat_raw == "mame" or core == "mame":
        effective_key = "mame"
        configured = mapping.get("mame") or mapping.get("arcade") or DEFAULT_CONSOLE_PATHS["mame"]
    else:
        effective_key = console_key
        configured = (
            mapping.get(plat_raw)
            or mapping.get(console_key)
            or DEFAULT_CONSOLE_PATHS.get(console_key)
            or console_key
        )

    cand = Path(configured)
    if cand.is_absolute():
        return cand, effective_key
    return root / configured.strip("/\\"), effective_key

from __future__ import annotations

import hashlib
import json
import os
import re
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .config import ARCADE_PLAYABLE_EXTS, NON_ROM_EXTS, normalize_console_key
from .db import HubDatabase

_TAG_RE = re.compile(r"\s*[\(\[][^)\]]*[\)\]]")


def parse_region(title: str) -> str:
    t = title or ""
    lower = t.lower()
    if re.search(r"\(world(?:\b[^)]*)?\)", t, re.I):
        return "World"
    if re.search(r"\(usa(?:\b[^)]*)?\)|\(u\)", t, re.I):
        return "USA"
    if re.search(r"\(europe(?:\b[^)]*)?\)|\(e\)|\(pal\)", t, re.I):
        return "Europe"
    if re.search(r"\(japan(?:\b[^)]*)?\)|\(j\)", t, re.I):
        return "Japan"
    if re.search(r"\(korea(?:n)?(?:\b[^)]*)?\)", t, re.I):
        return "Korea"
    if re.search(r"\(asia(?:\b[^)]*)?\)", t, re.I):
        return "Asia"
    if re.search(r"\(brazil(?:\b[^)]*)?\)", t, re.I):
        return "Brazil"
    if re.search(r"\(spain(?:\b[^)]*)?\)", t, re.I):
        return "Spain"
    if re.search(r"\(france(?:\b[^)]*)?\)", t, re.I):
        return "France"
    if re.search(r"\(germany(?:\b[^)]*)?\)", t, re.I):
        return "Germany"
    if "(en," in lower:
        return "Multi"
    return "Sin región"


def clean_game_title(title: str) -> str:
    cleaned = _TAG_RE.sub("", title or "").strip()
    return re.sub(r"\s+", " ", cleaned) or title


def is_system_artifact(raw_platform: str, ext: str, console_key: str = "") -> bool:
    p = (raw_platform or "").lower()
    if p.startswith(".") or p.endswith((".txt", ".cfg", ".opt", ".zip")):
        return True
    ext_low = ext.lower()
    if ext_low in NON_ROM_EXTS:
        return True
    if console_key == "arcade" and ext_low not in ARCADE_PLAYABLE_EXTS:
        return True
    return False


@dataclass(slots=True)
class RomRecord:
    entry_id: str
    title: str
    clean_title: str
    platform: str
    console_key: str
    relative_path: str
    full_path: Path
    ext: str
    region: str
    size: int
    is_sys: bool
    has_metadata: bool
    romset: str = ""
    arcade_core: str = ""


class CatalogIndex:
    """In-memory ROM directory scanner hydrated with persistent SQLite metadata."""

    def __init__(self, db: HubDatabase, covers_dir: Path) -> None:
        self.db = db
        self.covers_dir = covers_dir
        self.covers_dir.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self._records: list[RomRecord] = []
        self._by_id: dict[str, RomRecord] = {}
        self._arcade_lookup: dict[str, dict[str, str]] = {}
        self.last_scan_ms: int = 0
        self.roms_dir: str = ""

    def set_covers_dir(self, covers_dir: Path) -> None:
        with self._lock:
            self.covers_dir = covers_dir
            self.covers_dir.mkdir(parents=True, exist_ok=True)

    def rescan(
        self,
        roms_dir: str,
        console_paths: dict[str, str] | None = None,
        arcade_lookup: dict[str, dict[str, str]] | None = None,
    ) -> dict[str, Any]:
        if arcade_lookup is not None:
            self._arcade_lookup = dict(arcade_lookup)
        t0 = time.perf_counter()
        root = Path(roms_dir)
        records: list[RomRecord] = []
        by_id: dict[str, RomRecord] = {}
        seen_paths: set[Path] = set()

        if root.is_dir():
            try:
                with os.scandir(root) as top_entries:
                    for top in top_entries:
                        if top.is_dir(follow_symlinks=False):
                            raw_platform = top.name
                            self._walk_platform(Path(top.path), Path(top.path), raw_platform, records, by_id, seen_paths)
                        elif top.is_file(follow_symlinks=False):
                            self._add_file(Path(top.path), top.name, top.name, top.stat().st_size, records, by_id, seen_paths)
            except OSError:
                pass

        for raw_plat, configured in (console_paths or {}).items():
            cfg_str = str(configured or "").strip()
            if not cfg_str:
                continue
            cand = Path(cfg_str)
            if cand.is_absolute() and cand.is_dir():
                try:
                    resolved = cand.resolve()
                    if root.is_dir() and resolved.is_relative_to(root.resolve()):
                        continue
                except OSError:
                    continue
                self._walk_platform(cand, cand, str(raw_plat).strip().lower(), records, by_id, seen_paths)

        records.sort(key=lambda r: (r.title.lower(), r.console_key))
        elapsed_ms = int((time.perf_counter() - t0) * 1000)

        with self._lock:
            self._records = records
            self._by_id = by_id
            self.last_scan_ms = elapsed_ms
            self.roms_dir = str(root)

        return {"count": len(records), "elapsedMs": elapsed_ms}

    def _walk_platform(
        self,
        platform_root: Path,
        current_dir: Path,
        raw_platform: str,
        records: list[RomRecord],
        by_id: dict[str, RomRecord],
        seen_paths: set[Path],
    ) -> None:
        try:
            with os.scandir(current_dir) as it:
                for entry in it:
                    if entry.is_dir(follow_symlinks=False):
                        if entry.name.startswith("."):
                            continue
                        self._walk_platform(platform_root, Path(entry.path), raw_platform, records, by_id, seen_paths)
                    elif entry.is_file(follow_symlinks=False):
                        rel = Path(entry.path).relative_to(platform_root).as_posix()
                        self._add_file(Path(entry.path), raw_platform, rel, entry.stat().st_size, records, by_id, seen_paths)
        except OSError:
            pass

    def _add_file(
        self,
        full_path: Path,
        raw_platform: str,
        rel_path: str,
        size: int,
        records: list[RomRecord],
        by_id: dict[str, RomRecord],
        seen_paths: set[Path],
    ) -> None:
        try:
            norm_path = full_path.resolve()
        except OSError:
            norm_path = full_path
        if norm_path in seen_paths:
            return
        seen_paths.add(norm_path)
        digest = hashlib.sha256(f"{raw_platform}/{rel_path}".encode("utf-8")).hexdigest()[:24]
        entry_id = f"rom-{digest}"
        stem = full_path.stem or full_path.name
        ext = full_path.suffix.lstrip(".").lower()
        console_key = normalize_console_key(raw_platform)
        is_sys = is_system_artifact(raw_platform, ext, console_key)
        title = stem
        clean_title = clean_game_title(stem)
        region = parse_region(stem)
        has_meta = (not is_sys) and (region != "Sin región")
        romset = ""
        arcade_core = ""

        if console_key == "arcade" and not is_sys:
            meta = self._arcade_lookup.get(stem.lower())
            if meta:
                title = meta["title"]
                clean_title = clean_game_title(title)
                parsed_region = parse_region(title)
                region = parsed_region if parsed_region != "Sin región" else (meta.get("core") or "Arcade")
                has_meta = True
                romset = stem
                arcade_core = meta.get("core") or "Arcade"

        rec = RomRecord(
            entry_id=entry_id,
            title=title,
            clean_title=clean_title,
            platform=raw_platform,
            console_key=console_key,
            relative_path=rel_path,
            full_path=full_path,
            ext=ext.upper() if ext else "ROM",
            region=region,
            size=size,
            is_sys=is_sys,
            has_metadata=has_meta,
            romset=romset,
            arcade_core=arcade_core,
        )
        records.append(rec)
        by_id[entry_id] = rec

    def get_record(self, entry_id: str) -> RomRecord | None:
        with self._lock:
            return self._by_id.get(entry_id)

    def all_records(self) -> list[RomRecord]:
        with self._lock:
            return list(self._records)

    def _cached_cover_ids(self) -> set[str]:
        if not self.covers_dir.exists():
            return set()
        return {p.stem for p in self.covers_dir.glob("*.webp")}

    def serialize_all(self) -> list[dict[str, Any]]:
        meta_map = self.db.get_all_rom_meta()
        cached_covers = self._cached_cover_ids()
        patches_by_entry: dict[str, list[dict[str, Any]]] = {}
        for p in self.db.list_patches():
            patches_by_entry.setdefault(p["entry_id"], []).append(p)
        with self._lock:
            recs = list(self._records)

        out: list[dict[str, Any]] = []
        for r in recs:
            m = meta_map.get(r.entry_id)
            entry_patches = patches_by_entry.get(r.entry_id, [])
            sha256_hex = m["sha256"] if m else None
            favorite = bool(m["favorite"]) if m else False
            has_cover = r.entry_id in cached_covers
            cover_ver = "".join(ch for ch in str(m.get("cover_updated_at") or "") if ch.isalnum())[-10:] if (m and has_cover) else ""
            cover_url = (f"/api/v1/covers/{r.entry_id}.webp?v={cover_ver}" if cover_ver else f"/api/v1/covers/{r.entry_id}.webp") if has_cover else None
            qr_obj: dict[str, Any] | None = None
            raw_qr = m.get("quick_resume_json") if m else None
            if isinstance(raw_qr, str) and raw_qr.strip():
                try:
                    parsed_qr = json.loads(raw_qr)
                    if isinstance(parsed_qr, dict) and parsed_qr:
                        qr_obj = parsed_qr
                except Exception:
                    qr_obj = None
            out.append(
                {
                    "entryId": r.entry_id,
                    "title": r.title,
                    "cleanTitle": r.clean_title,
                    "platform": r.platform,
                    "consoleKey": r.console_key,
                    "relativePath": r.relative_path,
                    "ext": r.ext,
                    "region": r.region,
                    "size": r.size,
                    "isSys": r.is_sys,
                    "hasMetadata": r.has_metadata,
                    "romset": r.romset or None,
                    "arcadeCore": r.arcade_core or None,
                    "expectedSha256": sha256_hex,
                    "isVerified": bool(sha256_hex),
                    "favorite": favorite,
                    "hasCover": has_cover,
                    "coverSource": (m["cover_source"] if m else None) if has_cover else None,
                    "coverUrl": cover_url,
                    "lastPlayedAt": m.get("last_played_at") if m else None,
                    "playCount": int(m.get("play_count") or 0) if m else 0,
                    "description": (m.get("description") or "") if m else "",
                    "releaseYear": (m.get("release_year") or "") if m else "",
                    "genre": (m.get("genre") or "") if m else "",
                    "developer": (m.get("developer") or "") if m else "",
                    "descriptionSource": (m.get("description_source") or "") if m else "",
                    "patchCount": len(entry_patches),
                    "hasSpanishPatch": any(str(p.get("language") or "").upper().startswith("ES") for p in entry_patches),
                    "quickResume": qr_obj,
                    "availability": "local",
                }
            )
        return out

    def compute_sha256(self, entry_id: str) -> dict[str, Any]:
        rec = self.get_record(entry_id)
        if rec is None or not rec.full_path.is_file():
            raise FileNotFoundError(f"ROM no encontrada: {entry_id}")
        h = hashlib.sha256()
        with rec.full_path.open("rb") as fh:
            while chunk := fh.read(1024 * 1024):
                h.update(chunk)
        hex_digest = h.hexdigest()
        verified_at = self.db.set_sha256(entry_id, hex_digest)
        return {
            "entryId": entry_id,
            "sha256": hex_digest,
            "size": rec.size,
            "verifiedAt": verified_at,
        }

from __future__ import annotations

import gzip
import hashlib
import json
import re
import threading
import time
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import httpx

from .config import resource_path

_TAG_GROUP_RE = re.compile(r"(\([^)]+\))")

_REMOTE_CONSOLE_NAMES: dict[str, str] = {
    "dc": "Sega Dreamcast",
    "saturn": "Sega Saturn",
    "psp": "Sony PlayStation Portable",
    "nds": "Nintendo DS",
    "3ds": "Nintendo 3DS",
    "ps2": "Sony PlayStation 2",
    "ps3": "Sony PlayStation 3",
    "gc": "Nintendo GameCube",
    "wii": "Nintendo Wii",
    "wiiu": "Nintendo Wii U",
    "switch": "Nintendo Switch",
    "psvita": "Sony PlayStation Vita",
    "gb": "Nintendo Game Boy",
    "gbc": "Nintendo Game Boy Color",
    "sms": "Sega Master System",
    "gg": "Sega Game Gear",
    "segacd": "Sega Mega-CD - Sega CD",
    "sega32x": "Sega 32X",
}


@dataclass(slots=True)
class MinervaEntry:
    id: str
    title: str
    edition: str
    platform: str
    filename: str
    size: int
    info_hash: str
    dependencies: list[str]
    search_text: str
    provider: str = "MiNERVA"
    identifier: str = ""
    archive_url: str = ""
    arcade_core: str = ""

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "id": self.id,
            "title": self.title,
            "edition": self.edition,
            "platform": self.platform,
            "filename": self.filename,
            "size": self.size,
            "infoHash": self.info_hash,
            "provider": self.provider,
            "dependencies": self.dependencies,
        }
        if self.identifier:
            payload["identifier"] = self.identifier
        if self.archive_url:
            payload["archiveUrl"] = self.archive_url
        if self.arcade_core:
            payload["arcadeCore"] = self.arcade_core
        return payload


def _extract_edition(filename: str, fallback: str = "") -> str:
    tags = _TAG_GROUP_RE.findall(filename or "")
    if tags:
        return " ".join(tags)
    return fallback


def _bucket_rank(pool: list[MinervaEntry], q: str, tokens: list[str]) -> list[MinervaEntry]:
    exact_hits: list[MinervaEntry] = []
    prefix_hits: list[MinervaEntry] = []
    other_hits: list[MinervaEntry] = []

    for e in pool:
        if all(tok in e.search_text for tok in tokens):
            tl = e.title.lower()
            if tl == q:
                exact_hits.append(e)
            elif tl.startswith(q):
                prefix_hits.append(e)
            else:
                other_hits.append(e)
    return exact_hits + prefix_hits + other_hits


class MinervaOfflineIndex:
    """In-memory offline MiNERVA + MAME catalog loaded from compressed shards."""

    def __init__(self, shard_dirs: list[Path] | None = None, jar_fallback: Path | None = None) -> None:
        app_dir = Path(__file__).resolve().parent
        self.shard_dirs = shard_dirs or [
            resource_path("app/source-index"),
            app_dir / "source-index",
            app_dir.parent / "data" / "source-index",
        ]
        self.jar_fallback = jar_fallback or Path(r"C:\OzretroRom\lib\companion-0.1.0-alpha.jar")
        self._lock = threading.RLock()
        self._entries: list[MinervaEntry] = []
        self._by_platform: dict[str, list[MinervaEntry]] = {}
        self._mame_entries: list[MinervaEntry] = []
        self._fbneo_entries: list[MinervaEntry] = []
        self._arcade_lookup: dict[str, dict[str, str]] = {}
        self.load_ms: int = 0

    def _read_shard_bytes(self, shard_name: str) -> bytes | None:
        for d in self.shard_dirs:
            p = d / shard_name
            if p.is_file():
                return p.read_bytes()
        if self.jar_fallback.is_file():
            try:
                with zipfile.ZipFile(self.jar_fallback) as zf:
                    key = f"source-index/{shard_name}"
                    if key in zf.namelist():
                        return zf.read(key)
            except Exception:
                pass
        return None

    def load(self) -> int:
        t0 = time.perf_counter()
        entries: list[MinervaEntry] = []
        fbneo_entries: list[MinervaEntry] = []
        mame_entries: list[MinervaEntry] = []

        # 1. Standard Android Source Index shards
        for plat in ("nes", "snes", "genesis", "gba"):
            raw = self._read_shard_bytes(f"{plat}.json.gz.bin")
            if not raw:
                continue
            data = json.loads(gzip.decompress(raw))
            games = {g["gameId"]: g for g in data.get("games", []) if "gameId" in g}
            releases = {r["releaseId"]: r for r in data.get("releases", []) if "releaseId" in r}
            for cand in data.get("candidates", []):
                art = cand.get("artifact") or {}
                tor = cand.get("torrent") or {}
                rel = releases.get(cand.get("releaseId") or "") or {}
                game = games.get(rel.get("gameId") or "") or {}
                filename = str(art.get("fileName") or "").strip()
                title = str(game.get("canonicalTitle") or Path(filename).stem or "ROM").strip()
                edition = _extract_edition(filename, str(rel.get("region") or ""))
                search_text = f"{title} {edition} {filename}".lower()
                entries.append(
                    MinervaEntry(
                        id=str(cand.get("candidateId") or ""),
                        title=title,
                        edition=edition,
                        platform=plat,
                        filename=filename,
                        size=int(art.get("size") or 0),
                        info_hash=str(tor.get("infoHash") or ""),
                        dependencies=[],
                        search_text=search_text,
                    )
                )

        # 2. Windows MiNERVA Expansion (Arcade FBNeo, PS1, N64)
        exp_raw = self._read_shard_bytes("minerva-expansion.json.gz.bin")
        if exp_raw:
            exp = json.loads(gzip.decompress(exp_raw))
            for col in exp.get("collections", []):
                plat = str(col.get("platform") or "").strip().lower()
                info_hash = str(col.get("infoHash") or "")
                directory = str(col.get("directory") or "").strip()
                for idx, f in enumerate(col.get("files", [])):
                    filename = str(f.get("filename") or "").strip()
                    title = str(f.get("title") or Path(filename).stem or "ROM").strip()
                    edition = str(f.get("edition") or _extract_edition(filename)).strip()
                    file_idx = f.get("index", idx)
                    rel_path = f"{directory}/{filename}" if directory else filename
                    cand_id = hashlib.sha256(
                        f"minerva\x00{plat}\x00{info_hash}\x00{file_idx}\x00{rel_path}".encode("utf-8")
                    ).hexdigest()
                    deps = [str(d) for d in (f.get("dependencies") or [])]
                    search_text = f"{title} {edition} {filename}".lower()
                    entry = MinervaEntry(
                        id=cand_id,
                        title=title,
                        edition=edition,
                        platform=plat,
                        filename=filename,
                        size=int(f.get("size") or 0),
                        info_hash=info_hash,
                        dependencies=deps,
                        search_text=search_text,
                        arcade_core="FBNeo" if plat == "arcade" else "",
                    )
                    entries.append(entry)
                    if plat == "arcade":
                        fbneo_entries.append(entry)

        # 3. MAME Internet Arcade Offline Shard
        mame_raw = self._read_shard_bytes("mame-ia.json.gz.bin")
        if mame_raw:
            mame_data = json.loads(gzip.decompress(mame_raw))
            for item in mame_data.get("items", []):
                ident = str(item.get("identifier") or "").strip()
                if not ident:
                    continue
                filename = str(item.get("filename") or f"{ident}.zip").strip()
                title = str(item.get("title") or ident).strip()
                romset = str(item.get("romset") or Path(filename).stem).strip()
                edition = _extract_edition(title, "MAME · Internet Archive")
                cand_id = f"mame-ia-{hashlib.sha256(ident.encode('utf-8')).hexdigest()[:16]}"
                entry = MinervaEntry(
                    id=cand_id,
                    title=title,
                    edition=edition,
                    platform="arcade",
                    filename=filename,
                    size=int(item.get("size") or 0),
                    info_hash="",
                    dependencies=[],
                    search_text=f"{title} {filename} {romset} mame arcade".lower(),
                    provider="MAME (Internet Archive)",
                    identifier=ident,
                    archive_url=f"https://archive.org/details/{ident}",
                    arcade_core="MAME",
                )
                entries.append(entry)
                mame_entries.append(entry)

        by_plat: dict[str, list[MinervaEntry]] = {}
        for e in entries:
            by_plat.setdefault(e.platform, []).append(e)

        arcade_lookup: dict[str, dict[str, str]] = {}
        for e in fbneo_entries:
            stem = Path(e.filename).stem.lower()
            if stem and stem not in arcade_lookup:
                arcade_lookup[stem] = {
                    "title": e.title,
                    "edition": e.edition,
                    "core": "FBNeo",
                    "romset": stem,
                }
        for e in mame_entries:
            keys = [Path(e.filename).stem.lower()]
            if e.identifier.startswith("arcade_"):
                keys.append(e.identifier.removeprefix("arcade_").lower())
            for k in keys:
                if k and k not in arcade_lookup:
                    arcade_lookup[k] = {
                        "title": e.title,
                        "edition": e.edition,
                        "core": "MAME",
                        "romset": k,
                    }

        with self._lock:
            self._entries = entries
            self._by_platform = by_plat
            self._mame_entries = mame_entries
            self._fbneo_entries = fbneo_entries
            self._arcade_lookup = arcade_lookup
            self.load_ms = int((time.perf_counter() - t0) * 1000)
        return len(entries)

    def get_arcade_romset_lookup(self) -> dict[str, dict[str, str]]:
        with self._lock:
            return dict(self._arcade_lookup)

    def total_count(self) -> int:
        with self._lock:
            return len(self._entries)

    def mame_count(self) -> int:
        with self._lock:
            return len(self._mame_entries)

    def supports_platform(self, platform: str) -> bool:
        p = (platform or "").strip().lower()
        return p in ("", "nes", "snes", "genesis", "gba", "arcade", "mame", "ps1", "n64")

    def search(self, query: str, platform: str = "", limit: int = 100) -> list[dict[str, Any]]:
        q = (query or "").strip().lower()
        tokens = [t for t in q.split() if t]
        if not tokens:
            return []

        plat = (platform or "").strip().lower()
        with self._lock:
            if plat == "mame":
                ranked = _bucket_rank(self._mame_entries, q, tokens)[:limit]
                return [e.to_dict() for e in ranked]
            if plat == "arcade":
                fb_hits = _bucket_rank(self._fbneo_entries, q, tokens)
                mame_hits = _bucket_rank(self._mame_entries, q, tokens)
                if not mame_hits:
                    return [e.to_dict() for e in fb_hits[:limit]]
                if not fb_hits:
                    return [e.to_dict() for e in mame_hits[:limit]]
                # Guarantee MAME results remain represented even if FBNeo has many clones
                mame_quota = min(len(mame_hits), max(limit // 2, limit - len(fb_hits)))
                fb_quota = min(len(fb_hits), limit - mame_quota)
                merged: list[MinervaEntry] = []
                # Put exact/prefix hits cleanly at top while balancing sources
                fb_sel = fb_hits[:fb_quota]
                mame_sel = mame_hits[:mame_quota]
                merged.extend(e for e in fb_sel if e.title.lower().startswith(q))
                merged.extend(e for e in mame_sel if e.title.lower().startswith(q))
                seen_ids = {e.id for e in merged}
                for e in fb_sel + mame_sel:
                    if e.id not in seen_ids:
                        seen_ids.add(e.id)
                        merged.append(e)
                return [e.to_dict() for e in merged[:limit]]

            pool = self._entries if not plat else self._by_platform.get(plat, [])

        ranked = _bucket_rank(pool, q, tokens)
        if not plat and len(ranked) > limit:
            # Ensure MAME matches aren't crowded out in global search when exact/prefix hits exist
            mame_matches = [e for e in ranked if e.arcade_core == "MAME"]
            non_mame = [e for e in ranked if e.arcade_core != "MAME"]
            if mame_matches:
                mame_slice = mame_matches[: min(len(mame_matches), limit // 4)]
                rest_slice = non_mame[: limit - len(mame_slice)]
                ranked = (rest_slice + mame_slice)[:limit]
                return [e.to_dict() for e in ranked]
        return [e.to_dict() for e in ranked[:limit]]


_REMOTE_DOWN_UNTIL: float = 0.0


async def search_minerva_remote(query: str, platform: str) -> list[dict[str, Any]]:
    global _REMOTE_DOWN_UNTIL
    console_name = _REMOTE_CONSOLE_NAMES.get((platform or "").strip().lower())
    if not console_name:
        return []
    now = time.monotonic()
    if now < _REMOTE_DOWN_UNTIL:
        return []
    try:
        async with httpx.AsyncClient(timeout=2.5, follow_redirects=True) as client:
            resp = await client.get(
                "https://minerva-archive.org/v1/api/rom/search",
                params={"query": query.strip(), "console": console_name, "limit": 20},
                headers={"User-Agent": "OzRetroRom/2.0"},
            )
        if resp.status_code != 200:
            return []
        rows = resp.json()
        if not isinstance(rows, list):
            return []
        out: list[dict[str, Any]] = []
        for r in rows[:20]:
            full_path = str(r.get("full_path") or "").strip()
            filename = Path(full_path).name or f"rom-{r.get('id', '0')}.zip"
            stem = Path(filename).stem
            title = _TAG_GROUP_RE.sub("", stem).strip() or stem
            edition = _extract_edition(filename, "MiNERVA Online")
            magnet = str(r.get("magnet") or "")
            btih_match = re.search(r"btih:([a-fA-F0-9]{40})", magnet)
            info_hash = btih_match.group(1).lower() if btih_match else ""
            out.append({
                "id": f"minerva-remote-{r.get('id', hashlib.sha1(filename.encode()).hexdigest()[:12])}",
                "title": title,
                "edition": edition,
                "platform": platform.lower(),
                "filename": filename,
                "size": int(r.get("size") or 0),
                "infoHash": info_hash,
                "magnet": magnet,
                "provider": "MiNERVA Online",
                "dependencies": [],
            })
        return out
    except Exception:
        _REMOTE_DOWN_UNTIL = time.monotonic() + 300.0
        return []

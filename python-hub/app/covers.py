from __future__ import annotations

import asyncio
import hashlib
import io
import re
import threading
import time
from pathlib import Path
from typing import Any
from urllib.parse import quote

import httpx
from PIL import Image

from .catalog import RomRecord, clean_game_title
from .config import LIBRETRO_SYSTEM_REPOS, LIBRETRO_SYSTEMS
from .db import HubDatabase

_SGDB_BASE = "https://www.steamgriddb.com/api/v2"


def _normalize_text(value: str) -> str:
    return (
        (value or "")
        .replace("\u00d7", "x")
        .replace("\u2019", "'")
        .replace("\u2018", "'")
        .replace("\u2013", "-")
        .replace("\u2014", "-")
        .replace("&", "_")
        .strip()
    )


def optimize_to_webp(raw_bytes: bytes, max_width: int = 260, quality: int = 76) -> bytes:
    """Resize cover image to compact portrait dimensions and encode as lightweight WebP."""
    with Image.open(io.BytesIO(raw_bytes)) as img:
        if img.mode not in ("RGB", "RGBA"):
            img = img.convert("RGB")
        max_h = int(max_width * 1.55)
        img.thumbnail((max_width, max_h), Image.Resampling.LANCZOS)
        buf = io.BytesIO()
        img.save(buf, format="WEBP", quality=quality, method=4)
        return buf.getvalue()


def optimize_banner_to_webp(raw_bytes: bytes, max_width: int = 960, quality: int = 82) -> bytes:
    """Resize wide franchise artwork/title screens preserving natural aspect ratio as WebP."""
    with Image.open(io.BytesIO(raw_bytes)) as img:
        if img.mode not in ("RGB", "RGBA"):
            img = img.convert("RGB")
        img.thumbnail((max_width, max_width), Image.Resampling.LANCZOS)
        buf = io.BytesIO()
        img.save(buf, format="WEBP", quality=quality, method=4)
        return buf.getvalue()


def _clean_tag_noise(text: str) -> str:
    s = re.sub(r"\[U\]", "(USA)", text)
    s = re.sub(r"\[E\]", "(Europe)", s)
    s = re.sub(r"\[J\]", "(Japan)", s)
    s = re.sub(r"\[(?:!|b\d*|T\+[^\]]*|a\d*|o\d*|h[^\]]*)\]", "", s, flags=re.I)
    return re.sub(r"\s+", " ", s).strip()


def _libretro_candidate_names(title: str, clean_title: str | None = None, console_key: str = "") -> list[str]:
    norm_title = _clean_tag_noise(_normalize_text(title))
    norm_clean = _clean_tag_noise(_normalize_text(clean_title or clean_game_title(norm_title)))
    candidates: list[str] = []

    if console_key in ("arcade", "mame"):
        if norm_clean:
            arcade_variants = [norm_clean]
            if ": " in norm_clean:
                arcade_variants.append(norm_clean.replace(": ", " - "))
            if "Vs." in norm_clean:
                arcade_variants.append(norm_clean.replace("Vs.", "vs."))
            ver_match = re.search(r"\((v[0-9][^)]*)\)", norm_title, re.I)
            if ver_match:
                candidates.append(f"{norm_clean} ({ver_match.group(1)})")
            for av in arcade_variants:
                candidates.append(av)
            candidates.extend([f"{norm_clean} (World)", f"{norm_clean} (USA)"])

    if "(" in norm_title:
        expanded = re.sub(r"\(U\)", "(USA)", norm_title)
        expanded = re.sub(r"\(E\)", "(Europe)", expanded)
        expanded = re.sub(r"\(J\)", "(Japan)", expanded)
        candidates.extend([expanded, norm_title])
        no_disc = re.sub(r"\s*\(Disc\s+\d+\)", "", expanded, flags=re.I).strip()
        if no_disc and no_disc != expanded:
            candidates.append(no_disc)

    clean_forms: list[str] = []
    if norm_clean:
        clean_forms.append(norm_clean)
        if " the " in norm_clean:
            clean_forms.append(norm_clean.replace(" the ", " The "))
        if re.search(r"\bmegaman\b", norm_clean, re.I):
            clean_forms.append(re.sub(r"\bmegaman\b", "Mega Man", norm_clean, flags=re.I))

    for cf in clean_forms:
        candidates.extend([
            f"{cf} (USA)",
            f"{cf} (USA, Europe)",
            f"{cf} (World)",
            f"{cf} (Europe)",
            f"{cf} (Japan)",
            cf,
        ])

    deduped: list[str] = []
    for c in candidates:
        s = re.sub(r"\s+", " ", c).strip()
        if s and s not in deduped:
            deduped.append(s)
    return deduped[:10]


async def test_steamgriddb_key(api_key: str) -> dict[str, Any]:
    key = (api_key or "").strip()
    if not key:
        return {"ok": False, "message": "API Key vacía."}
    headers = {"Authorization": f"Bearer {key}"}
    try:
        async with httpx.AsyncClient(timeout=6.0) as client:
            resp = await client.get(f"{_SGDB_BASE}/search/autocomplete/mario", headers=headers)
            if resp.status_code == 200:
                payload = resp.json()
                hits = len(payload.get("data", [])) if isinstance(payload, dict) else 0
                return {"ok": True, "message": f"Clave válida ({hits} juegos devueltos en prueba)."}
            if resp.status_code in (401, 403):
                return {"ok": False, "message": "API Key rechazada por SteamGridDB (401/403)."}
            return {"ok": False, "message": f"Respuesta inesperada HTTP {resp.status_code}."}
    except Exception as exc:
        return {"ok": False, "message": f"Error de red: {exc}"}


async def _fetch_cover_bytes(
    client: httpx.AsyncClient,
    title: str,
    clean_title: str,
    console_key: str,
    settings: dict[str, Any],
) -> tuple[bytes | None, str | None]:
    max_w = int(settings.get("cover_max_width", 260))
    quality = int(settings.get("cover_quality", 76))
    sgdb_key = (settings.get("steamgriddb_api_key") or "").strip()

    # 1. SteamGridDB (if API key configured)
    if sgdb_key and clean_title:
        headers = {"Authorization": f"Bearer {sgdb_key}"}
        try:
            search_resp = await client.get(
                f"{_SGDB_BASE}/search/autocomplete/{quote(_normalize_text(clean_title))}",
                headers=headers,
            )
            if search_resp.status_code == 200:
                games = search_resp.json().get("data") or []
                if games:
                    game_id = games[0]["id"]
                    grid_resp = await client.get(
                        f"{_SGDB_BASE}/grids/game/{game_id}?dimensions=600x900&limit=1",
                        headers=headers,
                    )
                    grids = grid_resp.json().get("data") if grid_resp.status_code == 200 else []
                    if not grids:
                        grid_resp = await client.get(
                            f"{_SGDB_BASE}/grids/game/{game_id}?limit=1",
                            headers=headers,
                        )
                        grids = grid_resp.json().get("data") if grid_resp.status_code == 200 else []
                    if grids:
                        img_url = grids[0].get("thumb") or grids[0].get("url")
                        if img_url:
                            img_resp = await client.get(img_url)
                            if img_resp.status_code == 200 and len(img_resp.content) > 256:
                                return optimize_to_webp(img_resp.content, max_width=max_w, quality=quality), "steamgriddb"
        except Exception:
            pass

    # 2. Libretro Thumbnails GitHub CDN fallback (no API key needed)
    if settings.get("enable_libretro_fallback", True):
        repos = LIBRETRO_SYSTEM_REPOS.get(console_key)
        if not repos and console_key in LIBRETRO_SYSTEMS:
            repos = [LIBRETRO_SYSTEMS[console_key]]
        if repos:
            names = _libretro_candidate_names(title, clean_title, console_key)
            attempts = 0
            for system_name in repos:
                repo_name = system_name.replace(" ", "_")
                if console_key in ("arcade", "mame"):
                    kinds = ("Named_Boxarts", "Named_Snaps") if system_name == "MAME" else ("Named_Snaps",)
                else:
                    kinds = ("Named_Boxarts",)
                cand_slice = names[:3] if console_key in ("arcade", "mame") else names
                for kind in kinds:
                    for name in cand_slice:
                        if attempts >= 12:
                            return None, None
                        attempts += 1
                        url = f"https://raw.githubusercontent.com/libretro-thumbnails/{quote(repo_name)}/master/{kind}/{quote(name)}.png"
                        try:
                            resp = await client.get(url)
                            if resp.status_code == 200 and len(resp.content) > 256:
                                return optimize_to_webp(resp.content, max_width=max_w, quality=quality), "libretro"
                        except Exception:
                            continue

    return None, None


async def fetch_cover_for_entry(
    client: httpx.AsyncClient,
    rec: RomRecord,
    settings: dict[str, Any],
    db: HubDatabase,
    covers_dir: Path,
) -> dict[str, Any]:
    covers_dir.mkdir(parents=True, exist_ok=True)
    target_path = covers_dir / f"{rec.entry_id}.webp"
    webp, source = await _fetch_cover_bytes(client, rec.title, rec.clean_title, rec.console_key, settings)
    if webp and source:
        target_path.write_bytes(webp)
        db.set_cover_meta(rec.entry_id, "found", source)
        return {
            "found": True,
            "source": source,
            "bytes": len(webp),
            "coverUrl": f"/api/v1/covers/{rec.entry_id}.webp",
        }
    db.set_cover_meta(rec.entry_id, "miss", None)
    return {"found": False, "source": None, "bytes": 0, "coverUrl": None}


def external_cover_id(key: str) -> str:
    return "ext-" + hashlib.sha256(key.encode("utf-8")).hexdigest()[:20]


async def resolve_external_cover(
    client: httpx.AsyncClient,
    key: str,
    title: str,
    console_key: str,
    settings: dict[str, Any],
    covers_dir: Path,
) -> dict[str, Any]:
    covers_dir.mkdir(parents=True, exist_ok=True)
    cov_id = external_cover_id(key)
    target_path = covers_dir / f"{cov_id}.webp"
    if target_path.is_file():
        return {"found": True, "source": "cache", "bytes": target_path.stat().st_size, "coverUrl": f"/api/v1/covers/{cov_id}.webp"}

    clean = clean_game_title(title)
    webp, source = await _fetch_cover_bytes(client, title, clean, console_key, settings)
    if webp and source:
        target_path.write_bytes(webp)
        return {"found": True, "source": source, "bytes": len(webp), "coverUrl": f"/api/v1/covers/{cov_id}.webp"}
    return {"found": False, "source": None, "bytes": 0, "coverUrl": None}


async def resolve_external_batch(
    items: list[dict[str, str]],
    settings: dict[str, Any],
    covers_dir: Path,
) -> dict[str, str]:
    capped = items[:20]
    if not capped:
        return {}
    sem = asyncio.Semaphore(4)
    out: dict[str, str] = {}
    async with httpx.AsyncClient(timeout=4.5, follow_redirects=True) as client:
        async def one(item: dict[str, str]) -> None:
            key = str(item.get("key") or "").strip()
            title = str(item.get("title") or "").strip()
            console_key = str(item.get("consoleKey") or "").strip().lower()
            if not key or not title:
                return
            async with sem:
                res = await resolve_external_cover(client, key, title, console_key, settings, covers_dir)
                if res.get("found") and res.get("coverUrl"):
                    out[key] = res["coverUrl"]

        await asyncio.gather(*(one(it) for it in capped))
    return out


async def resolve_saga_artwork(
    sagas_defs: list[dict[str, Any]],
    settings: dict[str, Any],
    covers_dir: Path,
    saga_ids: list[str] | None = None,
) -> dict[str, dict[str, str]]:
    """Fetch and locally cache representative hero banner & key art for curated sagas."""
    covers_dir.mkdir(parents=True, exist_ok=True)
    filter_ids = {s.strip() for s in (saga_ids or []) if s and s.strip()}
    targets = [s for s in sagas_defs if s.get("heroArt") and (not filter_ids or s["id"] in filter_ids)]
    out: dict[str, dict[str, str]] = {}
    if not targets:
        return out

    sgdb_key = (settings.get("steamgriddb_api_key") or "").strip()
    allow_libretro = bool(settings.get("enable_libretro_fallback", True))

    def _url_for(path: Path) -> str:
        try:
            ver = int(path.stat().st_mtime)
        except OSError:
            ver = int(time.time())
        return f"/api/v1/covers/{path.name}?v={ver}"

    pending: list[dict[str, Any]] = []
    for s in targets:
        sid = s["id"]
        hero_path = covers_dir / f"saga-hero-{sid}.webp"
        key_path = covers_dir / f"saga-key-{sid}.webp"
        entry: dict[str, str] = {}
        if hero_path.is_file():
            entry["heroBannerUrl"] = _url_for(hero_path)
        if key_path.is_file():
            entry["keyArtUrl"] = _url_for(key_path)
        if entry:
            out[sid] = entry
        if not (hero_path.is_file() and key_path.is_file()):
            pending.append(s)

    if not pending:
        return out

    sem = asyncio.Semaphore(4)
    async with httpx.AsyncClient(timeout=6.0, follow_redirects=True) as client:
        async def resolve_one(s: dict[str, Any]) -> None:
            sid = s["id"]
            art = s["heroArt"]
            hero_path = covers_dir / f"saga-hero-{sid}.webp"
            key_path = covers_dir / f"saga-key-{sid}.webp"
            res: dict[str, str] = dict(out.get(sid, {}))

            async with sem:
                sgdb_game_id = None
                if sgdb_key and art.get("sgdbQuery"):
                    headers = {"Authorization": f"Bearer {sgdb_key}"}
                    try:
                        sr = await client.get(
                            f"{_SGDB_BASE}/search/autocomplete/{quote(_normalize_text(art['sgdbQuery']))}",
                            headers=headers,
                        )
                        if sr.status_code == 200:
                            games = sr.json().get("data") or []
                            if games:
                                sgdb_game_id = games[0]["id"]
                    except Exception:
                        sgdb_game_id = None

                # 1. Hero banner
                if not hero_path.is_file():
                    hero_written = False
                    if sgdb_key and sgdb_game_id:
                        headers = {"Authorization": f"Bearer {sgdb_key}"}
                        try:
                            hr = await client.get(f"{_SGDB_BASE}/heroes/game/{sgdb_game_id}?limit=1", headers=headers)
                            heroes = hr.json().get("data") if hr.status_code == 200 else []
                            if heroes:
                                h_url = heroes[0].get("url") or heroes[0].get("thumb")
                                if h_url:
                                    img_r = await client.get(h_url)
                                    if img_r.status_code == 200 and len(img_r.content) > 512:
                                        hero_path.write_bytes(optimize_banner_to_webp(img_r.content, max_width=960, quality=82))
                                        hero_written = True
                        except Exception:
                            pass
                    if not hero_written and allow_libretro and art.get("repo"):
                        repo = art["repo"]
                        attempts: list[tuple[str, str]] = []
                        if art.get("titleName"):
                            attempts.append(("Named_Titles", art["titleName"]))
                        if art.get("boxartName"):
                            attempts.append(("Named_Boxarts", art["boxartName"]))
                        for kind, fname in attempts:
                            url = f"https://raw.githubusercontent.com/libretro-thumbnails/{quote(repo)}/master/{kind}/{quote(fname)}.png"
                            try:
                                r = await client.get(url)
                                if r.status_code == 200 and len(r.content) > 512:
                                    hero_path.write_bytes(optimize_banner_to_webp(r.content, max_width=800, quality=82))
                                    hero_written = True
                                    break
                            except Exception:
                                continue
                    if hero_written and hero_path.is_file():
                        res["heroBannerUrl"] = _url_for(hero_path)

                # 2. Key art portrait
                if not key_path.is_file():
                    key_written = False
                    if sgdb_key and sgdb_game_id:
                        headers = {"Authorization": f"Bearer {sgdb_key}"}
                        try:
                            gr = await client.get(
                                f"{_SGDB_BASE}/grids/game/{sgdb_game_id}?dimensions=600x900&limit=1",
                                headers=headers,
                            )
                            grids = gr.json().get("data") if gr.status_code == 200 else []
                            if grids:
                                g_url = grids[0].get("thumb") or grids[0].get("url")
                                if g_url:
                                    img_r = await client.get(g_url)
                                    if img_r.status_code == 200 and len(img_r.content) > 512:
                                        key_path.write_bytes(optimize_to_webp(img_r.content, max_width=320, quality=80))
                                        key_written = True
                        except Exception:
                            pass
                    if not key_written and allow_libretro and art.get("repo") and art.get("boxartName"):
                        url = (
                            f"https://raw.githubusercontent.com/libretro-thumbnails/{quote(art['repo'])}"
                            f"/master/Named_Boxarts/{quote(art['boxartName'])}.png"
                        )
                        try:
                            r = await client.get(url)
                            if r.status_code == 200 and len(r.content) > 512:
                                key_path.write_bytes(optimize_to_webp(r.content, max_width=320, quality=80))
                                key_written = True
                        except Exception:
                            pass
                    if key_written and key_path.is_file():
                        res["keyArtUrl"] = _url_for(key_path)

            if res:
                out[sid] = res

        await asyncio.gather(*(resolve_one(s) for s in pending))

    return out


class CoverSyncManager:
    """Runs bounded background cover fetching with live progress stats."""

    def __init__(self, db: HubDatabase, covers_dir: Path, event_bus: Any = None) -> None:
        self.db = db
        self.covers_dir = covers_dir
        self._event_bus = event_bus
        self._lock = threading.RLock()
        self._running = False
        self._total = 0
        self._processed = 0
        self._found = 0
        self._missed = 0
        self._current_title = ""
        self._newly_cached: list[dict[str, str]] = []
        self._task: asyncio.Task[None] | None = None

    def set_covers_dir(self, covers_dir: Path) -> None:
        with self._lock:
            self.covers_dir = covers_dir
            self.covers_dir.mkdir(parents=True, exist_ok=True)
        self._total = 0
        self._processed = 0
        self._found = 0
        self._missed = 0
        self._current_title = ""
        self._newly_cached: list[dict[str, str]] = []
        self._task: asyncio.Task[None] | None = None

    def cache_stats(self) -> dict[str, Any]:
        files = list(self.covers_dir.glob("*.webp")) if self.covers_dir.exists() else []
        total_bytes = sum(f.stat().st_size for f in files)
        with self._lock:
            return {
                "running": self._running,
                "total": self._total,
                "processed": self._processed,
                "found": self._found,
                "missed": self._missed,
                "currentTitle": self._current_title,
                "newlyCached": list(self._newly_cached),
                "cachedCount": len(files),
                "cachedBytes": total_bytes,
            }

    async def start_batch(
        self,
        records: list[RomRecord],
        settings: dict[str, Any],
        limit: int = 24,
        console_key: str = "",
        entry_ids: list[str] | None = None,
        retry_misses: bool = False,
    ) -> dict[str, Any]:
        with self._lock:
            if self._running:
                return self.cache_stats()

        meta_map = self.db.get_all_rom_meta()
        cached_ids = {p.stem for p in self.covers_dir.glob("*.webp")} if self.covers_dir.exists() else set()
        candidates: list[RomRecord] = []

        if entry_ids is not None and len(entry_ids) > 0:
            by_id = {r.entry_id: r for r in records}
            for eid in entry_ids:
                rec = by_id.get(eid)
                if rec is None or rec.is_sys:
                    continue
                if rec.entry_id in cached_ids:
                    continue
                m = meta_map.get(rec.entry_id)
                if (not retry_misses) and m and m.get("cover_status") == "miss":
                    continue
                candidates.append(rec)
                if len(candidates) >= limit:
                    break
        else:
            for rec in records:
                if rec.is_sys:
                    continue
                if console_key and rec.console_key != console_key:
                    continue
                if rec.entry_id in cached_ids:
                    continue
                m = meta_map.get(rec.entry_id)
                if m and m.get("cover_status") == "miss":
                    continue
                candidates.append(rec)
                if len(candidates) >= limit:
                    break

        with self._lock:
            self._total = len(candidates)
            self._processed = 0
            self._found = 0
            self._missed = 0
            self._current_title = ""
            self._newly_cached = []
            if not candidates:
                self._running = False
                return self.cache_stats()
            self._running = True

        self._task = asyncio.create_task(self._worker(candidates, settings))
        stats = self.cache_stats()
        if self._event_bus is not None:
            self._event_bus.publish("covers.progress", stats)
        return stats

    async def _worker(self, candidates: list[RomRecord], settings: dict[str, Any]) -> None:
        sem = asyncio.Semaphore(4)
        async with httpx.AsyncClient(timeout=5.0, follow_redirects=True) as client:
            async def one(rec: RomRecord) -> None:
                async with sem:
                    with self._lock:
                        self._current_title = rec.title
                    res = await fetch_cover_for_entry(client, rec, settings, self.db, self.covers_dir)
                    with self._lock:
                        self._processed += 1
                        if res["found"]:
                            self._found += 1
                            self._newly_cached.append({
                                "entryId": rec.entry_id,
                                "coverUrl": res["coverUrl"],
                                "source": res["source"] or "libretro",
                            })
                        else:
                            self._missed += 1
                    if self._event_bus is not None:
                        self._event_bus.publish("covers.progress", self.cache_stats())

            await asyncio.gather(*(one(r) for r in candidates))

        with self._lock:
            self._current_title = ""
            self._running = False
        if self._event_bus is not None:
            self._event_bus.publish("covers.complete", self.cache_stats())

    def clear_cache(self) -> int:
        removed = 0
        if self.covers_dir.exists():
            for f in self.covers_dir.glob("*.webp"):
                try:
                    f.unlink()
                    removed += 1
                except OSError:
                    pass
        self.db.clear_cover_meta()
        return removed

from __future__ import annotations

import asyncio
import re
import threading
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable
from urllib.parse import quote

import httpx

from .config import normalize_console_key, resolve_console_local_dir

_IA_PLATFORM_TERMS: dict[str, str] = {
    "mame": "(MAME OR collection:internetarcade OR Arcade)",
    "arcade": "(MAME OR collection:internetarcade OR Arcade)",
    "ps2": '("PlayStation 2" OR PS2)',
    "ps3": '("PlayStation 3" OR PS3)',
    "gc": '(GameCube OR "Nintendo GameCube")',
    "wii": '(Wii OR "Nintendo Wii")',
    "wiiu": '("Wii U")',
    "switch": '("Nintendo Switch" OR NSP OR XCI)',
    "psvita": '("PS Vita" OR PSVita)',
    "gb": '("Game Boy" AND NOT Advance AND NOT Color)',
    "gbc": '("Game Boy Color")',
    "sms": '("Master System")',
    "gg": '("Game Gear")',
    "segacd": '("Sega CD" OR "Mega-CD")',
    "sega32x": '("32X")',
    "dc": "(Dreamcast)",
    "saturn": "(Saturn)",
    "psp": '(PSP OR "PlayStation Portable")',
    "nds": '("Nintendo DS" OR NDS)',
    "3ds": '("Nintendo 3DS" OR 3DS)',
}

_ROM_EXTS = {
    ".chd", ".iso", ".rvz", ".wbfs", ".nsp", ".xci", ".nsz", ".xcz",
    ".zip", ".7z", ".rar", ".bin", ".cue", ".cso", ".nds", ".3ds", ".cia",
    ".sfc", ".smc", ".md", ".gba", ".gbc", ".gb", ".z64", ".n64", ".nes",
    ".pkg", ".gdi", ".cdi", ".pbp", ".vpk",
}

_IGNORED_EXTS = {
    ".xml", ".sqlite", ".torrent", ".png", ".jpg", ".jpeg", ".gif",
    ".webp", ".txt", ".log", ".json", ".md5", ".sha1", ".sha256",
}


def ia_auth_headers(settings: dict[str, Any]) -> dict[str, str]:
    headers: dict[str, str] = {"User-Agent": "OzRetroRom/2.0"}
    access = str(settings.get("ia_access_key") or "").strip()
    secret = str(settings.get("ia_secret_key") or "").strip()
    cookie = str(settings.get("ia_cookie") or "").strip()
    if access and secret:
        headers["Authorization"] = f"LOW {access}:{secret}"
    if cookie:
        headers["Cookie"] = cookie
    return headers


async def test_ia_credentials(access_key: str, secret_key: str, cookie: str = "") -> dict[str, Any]:
    access = (access_key or "").strip()
    secret = (secret_key or "").strip()
    ck = (cookie or "").strip()
    if not (access and secret) and not ck:
        return {"ok": False, "message": "Ingresa Access Key + Secret Key S3 (o Cookie de sesión)."}

    headers = ia_auth_headers({"ia_access_key": access, "ia_secret_key": secret, "ia_cookie": ck})
    try:
        async with httpx.AsyncClient(timeout=8.0, follow_redirects=True) as client:
            if access and secret:
                resp = await client.get("https://s3.us.archive.org/", headers=headers)
                if resp.status_code == 200:
                    return {"ok": True, "message": "Credenciales S3 de Internet Archive verificadas (HTTP 200)."}
                if resp.status_code in (401, 403):
                    return {"ok": False, "message": f"Claves rechazadas por Internet Archive (HTTP {resp.status_code})."}
                return {"ok": False, "message": f"Respuesta inesperada de S3 IA: HTTP {resp.status_code}."}
            resp = await client.get("https://archive.org/services/users/whoami", headers=headers)
            if resp.status_code == 200 and resp.text.strip():
                return {"ok": True, "message": "Cookie de sesión válida en Internet Archive."}
            return {"ok": False, "message": f"Cookie no válida o expirada (HTTP {resp.status_code})."}
    except Exception as exc:
        return {"ok": False, "message": f"Error conectando con Internet Archive: {exc}"}


_IA_ROM_FILTER = (
    '-collection:thingiverse '
    '-title:("TAS WIP" OR "Soundtrack" OR "Manual" OR "Commercial" OR "Boxart" OR "Holder" OR "for Xbox") '
    'AND (format:("ZIP" OR "7z" OR "ISO Image" OR "CHD" OR "RVZ" OR "WBFS" OR "Nintendo DS" OR "GDI" OR "CSO") '
    'OR subject:(rom OR iso OR redump OR "no-intro" OR chd OR wbfs OR rvz OR nds OR psp OR dreamcast OR gamecube OR wii)) '
    'AND mediatype:(software OR data)'
)

_NON_ROM_WORDS = (
    "holder", "stand ", "3d print", "thingiverse", ".stl", "soundtrack",
    "ost ", "manual", "strategy guide", "tas wip", "boxart", "for xbox 360",
)


def _is_clean_ia_rom_doc(title: str, ident: str, plat: str) -> bool:
    blob = f"{title} {ident}".lower()
    if any(bad in blob for bad in _NON_ROM_WORDS):
        return False
    if plat == "dc" and "xbox" in blob:
        return False
    if plat in ("gc", "wii") and "wii u" in blob and plat == "gc":
        return False
    return True


def _infer_extended_platform(title: str, ident: str, subject: str = "") -> str:
    blob = f"{title} {ident} {subject}".lower()
    if any(k in blob for k in ("nintendo ds", "nds", ".nds", "desmume")):
        return "nds"
    if any(k in blob for k in ("playstation portable", " psp", "(psp)", "psp-", "cso", "ppsspp")):
        return "psp"
    if any(k in blob for k in ("dreamcast", "gdi", "dc-redump")):
        return "dc"
    if any(k in blob for k in ("gamecube", "game cube", "rvz", "gc-")):
        return "gc"
    if any(k in blob for k in ("nintendo wii", " wii", "(wii)", "wbfs")):
        return "wii"
    if any(k in blob for k in ("playstation 2", "ps2")):
        return "ps2"
    return "arcade"


async def _ia_advanced_search(
    params: dict[str, Any],
    settings: dict[str, Any],
) -> list[dict[str, Any]]:
    headers = ia_auth_headers(settings)
    for attempt in range(2):
        try:
            async with httpx.AsyncClient(timeout=11.0, follow_redirects=True) as client:
                resp = await client.get(
                    "https://archive.org/advancedsearch.php",
                    params=params,
                    headers=headers,
                )
            if resp.status_code == 200:
                return list(resp.json().get("response", {}).get("docs", []))
            if resp.status_code in (429, 500, 502, 503, 504) and attempt == 0:
                await asyncio.sleep(0.4)
                continue
            return []
        except Exception:
            if attempt == 0:
                await asyncio.sleep(0.4)
                continue
            return []
    return []


async def search_ia_roms(query: str, platform: str, settings: dict[str, Any]) -> list[dict[str, Any]]:
    plat = (platform or "").strip().lower()
    term = _IA_PLATFORM_TERMS.get(plat, f'"{plat}"' if plat else "ROM")
    safe_q = re.sub(r'["()]+', " ", (query or "").strip()).strip()
    if not safe_q:
        return []
    rom_filter = _IA_ROM_FILTER if plat not in ("mame", "arcade") else "AND mediatype:software"
    ia_query = (
        f"title:({safe_q}) AND {term} AND {rom_filter}"
        if plat not in ("mame", "arcade")
        else f"title:({safe_q}) AND {term} AND mediatype:software"
    )
    docs = await _ia_advanced_search(
        params={
            "q": ia_query,
            "fl[]": ["identifier", "title", "item_size", "subject"],
            "rows": 20,
            "page": 1,
            "output": "json",
        },
        settings=settings,
    )
    is_arcade = plat in ("mame", "arcade")
    rows: list[dict[str, Any]] = []
    for d in docs:
        ident = str(d.get("identifier") or "").strip()
        title = str(d.get("title") or ident).strip()
        if not ident or (not is_arcade and not _is_clean_ia_rom_doc(title, ident, plat)):
            continue
        item: dict[str, Any] = {
            "id": ident,
            "identifier": ident,
            "title": title,
            "edition": "MAME · Internet Archive" if is_arcade else "Internet Archive",
            "platform": "arcade" if is_arcade else (plat or "other"),
            "filename": f"{ident}",
            "size": int(d.get("item_size") or 0),
            "provider": "MAME (Internet Archive)" if is_arcade else "Internet Archive",
            "archiveUrl": f"https://archive.org/details/{ident}",
            "dependencies": [],
        }
        if is_arcade:
            item["arcadeCore"] = "MAME"
        rows.append(item)
    return rows


async def search_ia_extended_multi(query: str, settings: dict[str, Any], rows_limit: int = 16) -> list[dict[str, Any]]:
    safe_q = re.sub(r'["()]+', " ", (query or "").strip()).strip()
    if len(safe_q) < 2:
        return []
    term = '("Nintendo DS" OR NDS OR PSP OR "PlayStation Portable" OR Dreamcast OR GameCube OR "Nintendo Wii")'
    ia_query = f"title:({safe_q}) AND {term} AND {_IA_ROM_FILTER}"
    docs = await _ia_advanced_search(
        params={
            "q": ia_query,
            "fl[]": ["identifier", "title", "item_size", "subject"],
            "rows": rows_limit,
            "page": 1,
            "output": "json",
        },
        settings=settings,
    )
    rows: list[dict[str, Any]] = []
    for d in docs:
        ident = str(d.get("identifier") or "").strip()
        title = str(d.get("title") or ident).strip()
        subj_raw = d.get("subject")
        subj = " ".join(subj_raw) if isinstance(subj_raw, list) else str(subj_raw or "")
        plat = _infer_extended_platform(title, ident, subj)
        if not ident or plat == "arcade" or not _is_clean_ia_rom_doc(title, ident, plat):
            continue
        rows.append({
            "id": ident,
            "identifier": ident,
            "title": title,
            "edition": "Internet Archive",
            "platform": plat,
            "filename": f"{ident}",
            "size": int(d.get("item_size") or 0),
            "provider": "Internet Archive",
            "archiveUrl": f"https://archive.org/details/{ident}",
            "dependencies": [],
        })
    return rows


async def list_ia_files(identifier: str, settings: dict[str, Any]) -> list[dict[str, Any]]:
    safe_id = Path(identifier).name
    async with httpx.AsyncClient(timeout=10.0, follow_redirects=True) as client:
        resp = await client.get(
            f"https://archive.org/metadata/{quote(safe_id)}/files",
            headers=ia_auth_headers(settings),
        )
    if resp.status_code != 200:
        raise RuntimeError(f"Error consultando archivos de {safe_id} (HTTP {resp.status_code})")

    raw_files = resp.json().get("result", [])
    if not isinstance(raw_files, list):
        return []

    files: list[dict[str, Any]] = []
    for f in raw_files:
        name = str(f.get("name") or "").strip()
        if not name or name.endswith("_meta.xml") or name.endswith("_files.xml"):
            continue
        ext = Path(name).suffix.lower()
        if ext in _IGNORED_EXTS:
            continue
        size = int(f.get("size") or 0)
        if size <= 0:
            continue
        is_rom = ext in _ROM_EXTS
        files.append({
            "name": name,
            "size": size,
            "format": str(f.get("format") or ext.lstrip(".").upper()),
            "sha1": str(f.get("sha1") or ""),
            "isRom": is_rom,
        })

    files.sort(key=lambda x: (0 if x["isRom"] else 1, -x["size"]))
    return files[:40]


def _safe_local_filename(raw_name: str) -> str:
    leaf = Path(raw_name).name.replace("..", "_").strip()
    cleaned = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "_", leaf)
    return cleaned or "download.bin"


class DirectDownloadManager:
    """Streams Internet Archive downloads directly into the local ROMs library."""

    def __init__(
        self,
        transport: httpx.AsyncBaseTransport | None = None,
        db: Any = None,
        event_bus: Any = None,
    ) -> None:
        self._lock = threading.RLock()
        self._db = db
        self._event_bus = event_bus
        self._jobs: dict[str, dict[str, Any]] = {}
        self._tasks: set[asyncio.Task[None]] = set()
        self._transport = transport
        if self._db is not None:
            self._db.mark_interrupted_download_jobs()
            for row in self._db.list_download_jobs():
                self._jobs[row["id"]] = row

    def _persist_and_emit(
        self,
        job_id: str,
        event_name: str,
        *,
        persist: bool = True,
    ) -> dict[str, Any]:
        with self._lock:
            snapshot = dict(self._jobs[job_id])
        if persist and self._db is not None:
            self._db.upsert_download_job(snapshot)
        if self._event_bus is not None:
            self._event_bus.publish(event_name, snapshot)
        return snapshot

    def list_jobs(self) -> list[dict[str, Any]]:
        with self._lock:
            return [dict(j) for j in self._jobs.values()]

    async def start_ia_download(
        self,
        identifier: str,
        filename: str,
        platform: str,
        title: str,
        settings: dict[str, Any],
        on_complete: Callable[[], None] | None = None,
        arcade_core: str = "",
    ) -> dict[str, Any]:
        dest_dir, effective_key = resolve_console_local_dir(
            str(settings.get("roms_dir") or "."),
            settings.get("console_paths"),
            platform,
            arcade_core,
        )
        dest_dir.mkdir(parents=True, exist_ok=True)

        local_name = _safe_local_filename(filename)
        dest_path = dest_dir / local_name
        part_path = dest_dir / f"{local_name}.ozpart"
        job_id = f"ia-{uuid.uuid4().hex[:10]}"
        now = datetime.now(timezone.utc).isoformat()

        job = {
            "id": job_id,
            "identifier": identifier,
            "title": title or local_name,
            "filename": local_name,
            "platform": normalize_console_key(effective_key),
            "arcadeCore": arcade_core or "",
            "provider": "Internet Archive",
            "state": "queued",
            "bytes": 0,
            "size": 0,
            "destDir": str(dest_dir),
            "destPath": str(dest_path),
            "message": f"Preparando descarga en {dest_dir.name}/{local_name}…",
            "createdAt": now,
            "updatedAt": now,
        }
        with self._lock:
            self._jobs[job_id] = job
        snapshot = self._persist_and_emit(job_id, "download.state", persist=True)

        task = asyncio.create_task(
            self._run_download(job_id, identifier, filename, part_path, dest_path, settings, on_complete)
        )
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)
        return snapshot

    async def _run_download(
        self,
        job_id: str,
        identifier: str,
        remote_filename: str,
        part_path: Path,
        dest_path: Path,
        settings: dict[str, Any],
        on_complete: Callable[[], None] | None,
    ) -> None:
        safe_id = Path(identifier).name
        clean_remote = remote_filename.lstrip("/")
        url = f"https://archive.org/download/{quote(safe_id)}/{quote(clean_remote, safe='/')}"

        with self._lock:
            self._jobs[job_id]["state"] = "connecting"
            self._jobs[job_id]["message"] = "Conectando con Internet Archive…"
            self._jobs[job_id]["updatedAt"] = datetime.now(timezone.utc).isoformat()
        self._persist_and_emit(job_id, "download.state", persist=True)

        client_kwargs: dict[str, Any] = {"timeout": 120.0, "follow_redirects": True}
        if self._transport is not None:
            client_kwargs["transport"] = self._transport

        try:
            async with httpx.AsyncClient(**client_kwargs) as client:
                async with client.stream("GET", url, headers=ia_auth_headers(settings)) as resp:
                    if resp.status_code in (401, 403):
                        raise PermissionError(
                            f"Archivo protegido en Internet Archive (HTTP {resp.status_code}). "
                            "Configura tus claves API S3 en Configuración para descargarlo."
                        )
                    if resp.status_code >= 400:
                        raise RuntimeError(f"Internet Archive respondió HTTP {resp.status_code}")

                    total = int(resp.headers.get("content-length") or 0)
                    with self._lock:
                        self._jobs[job_id]["state"] = "downloading"
                        self._jobs[job_id]["size"] = total
                        self._jobs[job_id]["message"] = f"Descargando {dest_path.name}…"
                        self._jobs[job_id]["updatedAt"] = datetime.now(timezone.utc).isoformat()
                    self._persist_and_emit(job_id, "download.state", persist=True)

                    downloaded = 0
                    last_emit = 0.0
                    with part_path.open("wb") as fh:
                        async for chunk in resp.aiter_bytes(chunk_size=65536):
                            fh.write(chunk)
                            downloaded += len(chunk)
                            with self._lock:
                                self._jobs[job_id]["bytes"] = downloaded
                                self._jobs[job_id]["updatedAt"] = datetime.now(timezone.utc).isoformat()
                            now_mono = time.monotonic()
                            if now_mono - last_emit >= 0.25:
                                last_emit = now_mono
                                self._persist_and_emit(job_id, "download.progress", persist=False)

            if part_path.exists():
                if dest_path.exists():
                    dest_path.unlink()
                part_path.replace(dest_path)

            with self._lock:
                final_size = dest_path.stat().st_size if dest_path.exists() else self._jobs[job_id]["bytes"]
                self._jobs[job_id]["state"] = "complete"
                self._jobs[job_id]["bytes"] = final_size
                self._jobs[job_id]["size"] = final_size
                self._jobs[job_id]["message"] = f"Guardado en {dest_path}"
                self._jobs[job_id]["updatedAt"] = datetime.now(timezone.utc).isoformat()
            self._persist_and_emit(job_id, "download.state", persist=True)

            if on_complete:
                on_complete()
        except Exception as exc:
            try:
                if part_path.exists():
                    part_path.unlink()
            except OSError:
                pass
            with self._lock:
                self._jobs[job_id]["state"] = "failed"
                self._jobs[job_id]["message"] = str(exc)
                self._jobs[job_id]["updatedAt"] = datetime.now(timezone.utc).isoformat()
            self._persist_and_emit(job_id, "download.state", persist=True)

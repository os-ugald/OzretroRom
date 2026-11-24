from __future__ import annotations

import asyncio
import base64
import os
import re
import shutil
import socket
import subprocess
import sys
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any
from urllib.parse import quote

import httpx
from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse
from pydantic import BaseModel

from .catalog import CatalogIndex
from .config import BASE_DIR, DEFAULT_DATA_DIR, is_frozen, resource_path
from .covers import (
    CoverSyncManager,
    fetch_cover_for_entry,
    optimize_to_webp,
    resolve_external_batch,
    resolve_saga_artwork,
    test_steamgriddb_key,
)
from .db import HubDatabase
from .descriptions import resolve_game_description
from .engines import HomeEngine, LibraryEngine, SagasEngine
from .ia_client import DirectDownloadManager, ia_auth_headers, list_ia_files, search_ia_extended_multi, search_ia_roms, test_ia_credentials
from .minerva import MinervaOfflineIndex, search_minerva_remote
from .patcher import apply_patch_to_rom_file, search_spanish_patches
from .recommendations import test_ai_provider
from .transfers import TransferManager, test_ftp_target

STATIC_UI_PATH = resource_path("app/static/ui.html")


def _detect_lan_ip() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            if ip and not ip.startswith("127."):
                return ip
    except OSError:
        pass
    return "127.0.0.1"

_FALLBACK_SVG = (
    "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 400'>"
    "<rect width='300' height='400' fill='#141923'/>"
    "<circle cx='150' cy='200' r='44' fill='none' stroke='#38BDF8' stroke-width='2'/>"
    "</svg>"
)


class CoverSyncRequest(BaseModel):
    limit: int = 24
    platform: str = ""
    entryIds: list[str] | None = None
    retryMisses: bool = False


class ResolveCoverItem(BaseModel):
    key: str
    title: str
    consoleKey: str = ""


class ResolveCoverBatchRequest(BaseModel):
    items: list[ResolveCoverItem] = []


class SettingsPatch(BaseModel):
    steamgriddb_api_key: str | None = None
    ia_access_key: str | None = None
    ia_secret_key: str | None = None
    ia_cookie: str | None = None
    enable_libretro_fallback: bool | None = None
    cover_max_width: int | None = None
    cover_quality: int | None = None
    roms_dir: str | None = None
    covers_dir: str | None = None
    onboarding_completed: bool | None = None
    console_paths: dict[str, str] | None = None
    java_companion_url: str | None = None
    pinned_hero_entry_id: str | None = None
    home_section_order: list[str] | None = None
    ai_provider: str | None = None
    ai_api_key: str | None = None
    ai_model: str | None = None
    ai_base_url: str | None = None


class CustomCoverRequest(BaseModel):
    imageDataUrl: str = ""
    imageUrl: str = ""


class AiTestRequest(BaseModel):
    provider: str = "local"
    apiKey: str = ""
    model: str = "gemini-2.5-flash"
    baseUrl: str = ""


class PathInspectRequest(BaseModel):
    path: str = ""


class PickFolderRequest(BaseModel):
    title: str = "Seleccionar carpeta"
    initialDir: str = ""


class TargetPayload(BaseModel):
    id: str | None = None
    name: str
    console: str = "Custom"
    protocol: str = "ftp"
    host: str
    port: int = 21
    username: str = "anonymous"
    password: str = ""
    base_path: str = "Roms"
    path_overrides: dict[str, str] = {}
    notes: str = ""


class TransferCreateRequest(BaseModel):
    entryId: str
    targetId: str
    remoteDir: str | None = None
    patchId: str | None = None


class CustomSagaRequest(BaseModel):
    id: str | None = None
    name: str
    tagline: str = ""
    accent: str = "#FFB000"
    entryIds: list[str] = []


class AddToSagaRequest(BaseModel):
    entryId: str


class ResolveSagaArtRequest(BaseModel):
    sagaIds: list[str] | None = None


class ApplyPatchRequest(BaseModel):
    patchUrl: str = ""
    patchDataBase64: str = ""
    patchFilename: str = ""
    label: str = ""
    language: str = "ES"
    sourceName: str = ""
    saveToLibrary: bool = False


class SteamGridTestRequest(BaseModel):
    apiKey: str


class IaTestRequest(BaseModel):
    accessKey: str = ""
    secretKey: str = ""
    cookie: str = ""


class IaDownloadRequest(BaseModel):
    identifier: str
    filename: str
    platform: str = "other"
    title: str = ""
    arcadeCore: str = ""


def create_app(
    data_dir: Path | None = None,
    roms_dir_override: str | None = None,
    download_transport: httpx.AsyncBaseTransport | None = None,
) -> FastAPI:
    resolved_data_dir = data_dir or DEFAULT_DATA_DIR
    default_covers_dir = resolved_data_dir / "covers"
    default_covers_dir.mkdir(parents=True, exist_ok=True)
    db = HubDatabase(resolved_data_dir / "oz_hub.db")

    def resolve_covers_dir(settings_dict: dict[str, Any]) -> Path:
        raw = str(settings_dict.get("covers_dir") or "").strip()
        target = Path(raw).expanduser() if raw else default_covers_dir
        try:
            target.mkdir(parents=True, exist_ok=True)
        except OSError:
            default_covers_dir.mkdir(parents=True, exist_ok=True)
            return default_covers_dir
        return target

    def get_active_covers_dir() -> Path:
        return resolve_covers_dir(db.get_settings())

    active_covers_dir = resolve_covers_dir(db.get_settings())
    catalog = CatalogIndex(db, active_covers_dir)
    cover_sync = CoverSyncManager(db, active_covers_dir)
    transfers = TransferManager()
    minerva_index = MinervaOfflineIndex()
    direct_downloads = DirectDownloadManager(transport=download_transport)
    library_engine = LibraryEngine()
    sagas_engine = SagasEngine()
    home_engine = HomeEngine()

    if roms_dir_override:
        db.update_settings({"roms_dir": roms_dir_override})

    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        minerva_index.load()
        settings = db.get_settings()
        cov_dir = resolve_covers_dir(settings)
        catalog.set_covers_dir(cov_dir)
        cover_sync.set_covers_dir(cov_dir)
        app.state.covers_dir = cov_dir
        catalog.rescan(
            settings["roms_dir"],
            settings.get("console_paths"),
            arcade_lookup=minerva_index.get_arcade_romset_lookup(),
        )
        yield
        db.close()

    app = FastAPI(title="Oz Game Vault", version="2.1.0", lifespan=lifespan)
    app.state.db = db
    app.state.catalog = catalog
    app.state.cover_sync = cover_sync
    app.state.transfers = transfers
    app.state.minerva_index = minerva_index
    app.state.direct_downloads = direct_downloads
    app.state.library_engine = library_engine
    app.state.sagas_engine = sagas_engine
    app.state.home_engine = home_engine
    app.state.covers_dir = active_covers_dir
    app.state.data_dir = resolved_data_dir

    @app.get("/", response_class=HTMLResponse)
    async def index() -> Response:
        if not STATIC_UI_PATH.is_file():
            raise HTTPException(status_code=500, detail="ui.html no encontrado")
        return HTMLResponse(STATIC_UI_PATH.read_text(encoding="utf-8"))

    @app.get("/health")
    async def health() -> dict[str, Any]:
        stats = cover_sync.cache_stats()
        return {
            "status": "ok",
            "engine": "python-fastapi",
            "contract_version": 2,
            "roms_count": len(catalog.all_records()),
            "minerva_entries": minerva_index.total_count(),
            "mame_entries": minerva_index.mame_count(),
            "cached_covers": stats["cachedCount"],
            "scan_ms": catalog.last_scan_ms,
        }

    @app.get("/api/v1/system/info")
    async def system_info() -> dict[str, Any]:
        settings = db.get_settings()
        candidates = [
            BASE_DIR / "dist" / "OzRetroRom-Portable-Win64.zip",
            Path(sys.executable).resolve().parent / "OzRetroRom-Portable-Win64.zip",
        ]
        zip_path = next((p for p in candidates if p.is_file()), candidates[0])
        return {
            "lanIp": _detect_lan_ip(),
            "hostname": socket.gethostname(),
            "isFrozen": is_frozen(),
            "desktopMode": os.environ.get("OZ_DESKTOP_MODE") == "1",
            "romsDir": settings.get("roms_dir") or "",
            "coversDir": str(get_active_covers_dir()),
            "defaultCoversDir": str(default_covers_dir),
            "dataDir": str(resolved_data_dir),
            "minervaEntries": minerva_index.total_count(),
            "mameEntries": minerva_index.mame_count(),
            "portableZipExists": zip_path.is_file(),
            "portableZipPath": str(zip_path),
        }

    @app.post("/api/v1/system/inspect-path")
    async def inspect_path(payload: PathInspectRequest) -> dict[str, Any]:
        raw = (payload.path or "").strip()
        if not raw:
            return {"valid": False, "exists": False, "entriesCount": 0, "message": "Ruta vacía."}
        p = Path(raw).expanduser()
        if p.is_dir():
            count = 0
            try:
                with os.scandir(p) as it:
                    for _ in it:
                        count += 1
                        if count >= 500:
                            break
            except OSError:
                pass
            suffix = "500+" if count >= 500 else str(count)
            return {
                "valid": True,
                "exists": True,
                "resolvedPath": str(p),
                "entriesCount": count,
                "message": f"Carpeta encontrada ({suffix} elementos).",
            }
        if p.exists():
            return {
                "valid": False,
                "exists": True,
                "resolvedPath": str(p),
                "entriesCount": 0,
                "message": "La ruta apunta a un archivo, no a una carpeta.",
            }
        return {
            "valid": True,
            "exists": False,
            "resolvedPath": str(p),
            "entriesCount": 0,
            "message": "La carpeta aún no existe; se creará automáticamente al guardar.",
        }

    @app.post("/api/v1/system/pick-folder")
    async def pick_folder(request: Request, payload: PickFolderRequest) -> dict[str, Any]:
        client_host = request.client.host if request.client else ""
        is_local = client_host in ("127.0.0.1", "::1", "localhost") or os.environ.get("OZ_DESKTOP_MODE") == "1"
        if sys.platform != "win32" or not is_local:
            return {
                "ok": False,
                "supported": False,
                "message": "El selector nativo solo funciona en el equipo local; escribe la ruta manualmente.",
            }
        title = (payload.title or "Seleccionar carpeta").replace("'", "''")
        init_dir = str(Path(payload.initialDir).expanduser()) if (payload.initialDir or "").strip() else ""
        init_line = ""
        if init_dir and Path(init_dir).is_dir():
            escaped_init = init_dir.replace("'", "''")
            init_line = f"$dlg.SelectedPath = '{escaped_init}';"
        ps_script = (
            "Add-Type -AssemblyName System.Windows.Forms; "
            "$form = New-Object System.Windows.Forms.Form -Property @{TopMost=$true}; "
            "$dlg = New-Object System.Windows.Forms.FolderBrowserDialog; "
            f"$dlg.Description = '{title}'; "
            "$dlg.ShowNewFolderButton = $true; "
            f"{init_line} "
            "if ($dlg.ShowDialog($form) -eq [System.Windows.Forms.DialogResult]::OK) { "
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
            "Write-Output $dlg.SelectedPath }"
        )

        def _run_dialog() -> str:
            res = subprocess.run(
                ["powershell.exe", "-NoProfile", "-STA", "-Command", ps_script],
                capture_output=True,
                text=True,
                encoding="utf-8",
                timeout=120,
            )
            return (res.stdout or "").strip()

        try:
            selected = await asyncio.to_thread(_run_dialog)
        except Exception as exc:
            return {"ok": False, "supported": False, "message": f"No se pudo abrir el selector: {exc}"}
        if selected:
            return {"ok": True, "path": selected}
        return {"ok": False, "cancelled": True}

    @app.get("/api/v1/library")
    @app.get("/oz/v1/library")
    async def get_library(grouped: bool = True) -> list[dict[str, Any]]:
        return library_engine.get_library(catalog.serialize_all(), grouped=grouped)

    @app.get("/api/v1/library/maintenance")
    async def get_library_maintenance() -> dict[str, Any]:
        return library_engine.get_maintenance_report(catalog.serialize_all())

    @app.get("/api/v1/home")
    async def get_home_workspace() -> dict[str, Any]:
        raw = catalog.serialize_all()
        canonical = library_engine.get_library(raw, grouped=True)
        settings = db.get_settings()
        sagas = sagas_engine.list_sagas(
            canonical,
            custom_sagas=settings.get("custom_sagas"),
            covers_dir=get_active_covers_dir(),
        )
        maint = library_engine.get_maintenance_report(raw)
        return home_engine.build_home_payload(
            canonical_items=canonical,
            raw_items=raw,
            sagas=sagas,
            settings=settings,
            maintenance=maint,
        )

    @app.post("/api/v1/library/rescan")
    async def rescan_library() -> dict[str, Any]:
        settings = db.get_settings()
        return catalog.rescan(
            settings["roms_dir"],
            settings.get("console_paths"),
            arcade_lookup=minerva_index.get_arcade_romset_lookup(),
        )

    @app.post("/api/v1/library/{entry_id}/favorite")
    async def toggle_favorite(entry_id: str) -> dict[str, Any]:
        if catalog.get_record(entry_id) is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        fav = db.toggle_favorite(entry_id)
        return {"entryId": entry_id, "favorite": fav}

    @app.post("/api/v1/library/{entry_id}/played")
    async def mark_rom_played(entry_id: str) -> dict[str, Any]:
        if catalog.get_record(entry_id) is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        res = db.mark_played(entry_id)
        return {"entryId": entry_id, **res}

    @app.post("/api/v1/library/{entry_id}/verify")
    async def verify_rom(entry_id: str) -> dict[str, Any]:
        try:
            return catalog.compute_sha256(entry_id)
        except FileNotFoundError as exc:
            raise HTTPException(status_code=404, detail=str(exc))

    @app.api_route("/api/v1/covers/{entry_id}.webp", methods=["GET", "HEAD"])
    async def get_cover(entry_id: str) -> Response:
        safe_id = Path(entry_id).name
        cover_file = get_active_covers_dir() / f"{safe_id}.webp"
        if cover_file.is_file():
            return FileResponse(cover_file, media_type="image/webp", headers={"Cache-Control": "public, max-age=86400"})
        return Response(content=_FALLBACK_SVG, media_type="image/svg+xml")

    @app.post("/api/v1/covers/fetch/{entry_id}")
    async def fetch_single_cover(entry_id: str) -> dict[str, Any]:
        rec = catalog.get_record(entry_id)
        if rec is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        settings = db.get_settings()
        cov_dir = get_active_covers_dir()
        async with httpx.AsyncClient(timeout=5.0, follow_redirects=True) as client:
            return await fetch_cover_for_entry(client, rec, settings, db, cov_dir)

    @app.post("/api/v1/covers/custom/{entry_id}")
    async def upload_custom_cover(entry_id: str, payload: CustomCoverRequest) -> dict[str, Any]:
        rec = catalog.get_record(entry_id)
        if rec is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        settings = db.get_settings()
        max_bytes = 12 * 1024 * 1024
        raw_bytes = b""

        data_url = (payload.imageDataUrl or "").strip()
        img_url = (payload.imageUrl or "").strip()
        if data_url:
            if "," in data_url and data_url.startswith("data:"):
                data_url = data_url.split(",", 1)[1]
            try:
                raw_bytes = base64.b64decode(data_url, validate=True)
            except Exception as exc:
                raise HTTPException(status_code=400, detail=f"Imagen Base64 inválida: {exc}")
            if len(raw_bytes) > max_bytes:
                raise HTTPException(status_code=400, detail="La imagen supera el máximo de 12 MB.")
        elif img_url:
            if not img_url.lower().startswith(("http://", "https://")):
                raise HTTPException(status_code=400, detail="La URL de imagen debe comenzar con http:// o https://")
            try:
                async with httpx.AsyncClient(timeout=10.0, follow_redirects=True) as client:
                    resp = await client.get(img_url)
                    if resp.status_code != 200:
                        raise HTTPException(status_code=400, detail=f"No se pudo descargar la imagen (HTTP {resp.status_code})")
                    raw_bytes = resp.content
            except HTTPException:
                raise
            except Exception as exc:
                raise HTTPException(status_code=400, detail=f"Error descargando imagen: {exc}")
            if len(raw_bytes) > max_bytes:
                raise HTTPException(status_code=400, detail="La imagen descargada supera 12 MB.")
        else:
            raise HTTPException(status_code=400, detail="Envía un archivo de imagen o una URL válida.")

        try:
            webp_bytes = optimize_to_webp(
                raw_bytes,
                max_width=max(320, int(settings.get("cover_max_width", 260))),
                quality=int(settings.get("cover_quality", 76)),
            )
        except Exception as exc:
            raise HTTPException(status_code=400, detail=f"No se pudo procesar la imagen: {exc}")

        cov_dir = get_active_covers_dir()
        cov_dir.mkdir(parents=True, exist_ok=True)
        safe_id = Path(entry_id).name
        (cov_dir / f"{safe_id}.webp").write_bytes(webp_bytes)
        db.set_cover_meta(entry_id, "found", "custom")
        ver = int(time.time())
        return {
            "entryId": entry_id,
            "status": "found",
            "source": "custom",
            "coverUrl": f"/api/v1/covers/{safe_id}.webp?v={ver}",
        }

    @app.get("/api/v1/recommendations")
    async def get_recommendations(
        use_ai: bool = False,
        limit: int = 6,
        offset: int = 0,
        exclude: str = "",
        seed: int = 0,
    ) -> dict[str, Any]:
        canonical = await asyncio.to_thread(
            lambda: library_engine.get_library(catalog.serialize_all(), grouped=True)
        )
        exclude_ids = {s.strip() for s in exclude.split(",") if s.strip()} if exclude else None
        return await home_engine.get_recommendations(
            canonical,
            db.get_settings(),
            use_ai=use_ai,
            limit=limit,
            offset=offset,
            exclude_ids=exclude_ids,
            seed=seed,
            discover_limit=8,
        )

    @app.get("/api/v1/recommendations/mood")
    def get_mood_recommendations(
        mood: str = "quick",
        offset: int = 0,
        exclude: str = "",
    ) -> dict[str, Any]:
        canonical = library_engine.get_library(catalog.serialize_all(), grouped=True)
        exclude_ids = {s.strip() for s in exclude.split(",") if s.strip()} if exclude else None
        return home_engine.get_mood_picks(
            canonical,
            mood=mood,
            offset=offset,
            limit=3,
            exclude_ids=exclude_ids,
        )

    @app.get("/api/v1/library/{entry_id}/description")
    async def get_game_description(entry_id: str, refresh: bool = False) -> dict[str, Any]:
        rec = catalog.get_record(entry_id)
        if rec is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        desc_data = await resolve_game_description(rec, db, force=refresh)
        canonical = library_engine.get_library(catalog.serialize_all(), grouped=True)
        sagas = sagas_engine.list_sagas(canonical, db.get_settings().get("custom_sagas"), covers_dir=get_active_covers_dir())
        return {**desc_data, "saga": sagas_engine.find_saga_for_entry(entry_id, sagas)}

    @app.get("/api/v1/library/{entry_id}/download")
    async def download_library_rom(entry_id: str, patch_id: str | None = None) -> FileResponse:
        rec = catalog.get_record(entry_id)
        if rec is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        if patch_id:
            patch = db.get_patch(patch_id)
            if not patch or patch.get("entry_id") != entry_id:
                raise HTTPException(status_code=404, detail="Parche no encontrado para esta ROM")
            patched_file = Path(patch["patched_path"])
            if not patched_file.is_file():
                raise HTTPException(status_code=404, detail="Archivo parcheado ausente en disco")
            return FileResponse(patched_file, filename=patch["patched_filename"])
        if not rec.full_path.is_file():
            raise HTTPException(status_code=404, detail="Archivo ROM no encontrado en disco")
        return FileResponse(rec.full_path, filename=rec.full_path.name)

    @app.get("/api/v1/sagas")
    async def list_sagas() -> list[dict[str, Any]]:
        canonical = library_engine.get_library(catalog.serialize_all(), grouped=True)
        return sagas_engine.list_sagas(canonical, db.get_settings().get("custom_sagas"), covers_dir=get_active_covers_dir())

    @app.get("/api/v1/sagas/audit")
    async def audit_sagas_endpoint() -> dict[str, Any]:
        canonical = library_engine.get_library(catalog.serialize_all(), grouped=True)
        return sagas_engine.audit_sagas(canonical, db.get_settings().get("custom_sagas"), covers_dir=get_active_covers_dir())

    @app.post("/api/v1/sagas/resolve-art")
    async def resolve_sagas_art(payload: ResolveSagaArtRequest | None = None) -> dict[str, Any]:
        saga_ids = payload.sagaIds if payload else None
        art = await sagas_engine.resolve_art(db.get_settings(), get_active_covers_dir(), saga_ids=saga_ids)
        return {"art": art}

    @app.post("/api/v1/sagas/custom")
    async def upsert_custom_saga(payload: CustomSagaRequest) -> list[dict[str, Any]]:
        name = (payload.name or "").strip()
        if not name:
            raise HTTPException(status_code=400, detail="Escribe un nombre para la saga.")
        settings = db.get_settings()
        customs = [dict(c) for c in (settings.get("custom_sagas") or []) if isinstance(c, dict)]
        slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-") or "mi-saga"
        saga_id = (payload.id or f"custom-{slug}-{int(time.time()) % 10000}").strip()
        new_obj = {
            "id": saga_id,
            "name": name,
            "tagline": (payload.tagline or "").strip() or "Saga personalizada en Oz Game Vault",
            "accent": (payload.accent or "#FFB000").strip(),
            "entryIds": list(dict.fromkeys([e for e in payload.entryIds if e])),
        }
        updated = [c for c in customs if str(c.get("id")) != saga_id] + [new_obj]
        db.update_settings({"custom_sagas": updated})
        canonical = library_engine.get_library(catalog.serialize_all(), grouped=True)
        return sagas_engine.list_sagas(canonical, updated, covers_dir=get_active_covers_dir())

    @app.post("/api/v1/sagas/{saga_id}/add")
    async def add_entry_to_saga(saga_id: str, payload: AddToSagaRequest) -> list[dict[str, Any]]:
        if catalog.get_record(payload.entryId) is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        settings = db.get_settings()
        customs = [dict(c) for c in (settings.get("custom_sagas") or []) if isinstance(c, dict)]
        target = next((c for c in customs if str(c.get("id")) == saga_id), None)
        if target is None:
            target = {"id": saga_id, "name": saga_id.replace("-", " ").title(), "tagline": "", "accent": "#FFB000", "entryIds": []}
            customs.append(target)
        eids = list(target.get("entryIds") or [])
        if payload.entryId not in eids:
            eids.append(payload.entryId)
        target["entryIds"] = eids
        db.update_settings({"custom_sagas": customs})
        canonical = library_engine.get_library(catalog.serialize_all(), grouped=True)
        return sagas_engine.list_sagas(canonical, customs, covers_dir=get_active_covers_dir())

    @app.delete("/api/v1/sagas/custom/{saga_id}")
    async def delete_custom_saga(saga_id: str) -> list[dict[str, Any]]:
        settings = db.get_settings()
        customs = [c for c in (settings.get("custom_sagas") or []) if isinstance(c, dict) and str(c.get("id")) != saga_id]
        db.update_settings({"custom_sagas": customs})
        canonical = library_engine.get_library(catalog.serialize_all(), grouped=True)
        return sagas_engine.list_sagas(canonical, customs, covers_dir=get_active_covers_dir())

    @app.post("/api/v1/sagas/{saga_id}/ai-audit")
    async def ai_audit_single_saga(saga_id: str) -> dict[str, Any]:
        settings = db.get_settings()
        customs = settings.get("custom_sagas") or []
        canonical = library_engine.get_library(catalog.serialize_all(), grouped=True)
        sagas = sagas_engine.list_sagas(canonical, customs, covers_dir=get_active_covers_dir())
        target = next((s for s in sagas if s.get("id") == saga_id), None)
        if target is None:
            raise HTTPException(status_code=404, detail="Saga no encontrada")
        return await sagas_engine.ai_audit_saga(target, settings)

    @app.get("/api/v1/patches/search")
    async def search_patches(q: str = "", platform: str = "") -> list[dict[str, Any]]:
        return await search_spanish_patches(q, platform, db.get_settings())

    @app.get("/api/v1/library/{entry_id}/patches")
    async def list_entry_patches(entry_id: str) -> list[dict[str, Any]]:
        if catalog.get_record(entry_id) is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        return db.list_patches(entry_id)

    @app.post("/api/v1/patches/apply/{entry_id}")
    async def apply_entry_patch(entry_id: str, payload: ApplyPatchRequest) -> dict[str, Any]:
        rec = catalog.get_record(entry_id)
        if rec is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")

        settings = db.get_settings()
        max_bytes = 25 * 1024 * 1024
        patch_raw = b""
        patch_filename = (payload.patchFilename or "").strip()

        if (payload.patchDataBase64 or "").strip():
            raw_b64 = payload.patchDataBase64.strip()
            if "," in raw_b64 and raw_b64.startswith("data:"):
                raw_b64 = raw_b64.split(",", 1)[1]
            try:
                patch_raw = base64.b64decode(raw_b64, validate=True)
            except Exception as exc:
                raise HTTPException(status_code=400, detail=f"Archivo de parche Base64 inválido: {exc}")
            if len(patch_raw) > max_bytes:
                raise HTTPException(status_code=400, detail="El parche supera el máximo de 25 MB.")
        elif (payload.patchUrl or "").strip():
            purl = payload.patchUrl.strip()
            if not purl.lower().startswith(("http://", "https://")):
                raise HTTPException(status_code=400, detail="La URL de parche debe iniciar con http:// o https://")
            if not patch_filename:
                patch_filename = purl.split("?")[0].rstrip("/").split("/")[-1] or "patch.zip"
            try:
                async with httpx.AsyncClient(timeout=20.0, follow_redirects=True) as client:
                    resp = await client.get(purl, headers=ia_auth_headers(settings))
                    if resp.status_code != 200:
                        raise HTTPException(status_code=400, detail=f"No se pudo descargar el parche (HTTP {resp.status_code})")
                    patch_raw = resp.content
            except HTTPException:
                raise
            except Exception as exc:
                raise HTTPException(status_code=400, detail=f"Error descargando parche: {exc}")
            if len(patch_raw) > max_bytes:
                raise HTTPException(status_code=400, detail="El parche descargado supera 25 MB.")
        else:
            raise HTTPException(status_code=400, detail="Indica una URL de parche o sube un archivo .ips/.bps/.zip.")

        patches_dir = resolved_data_dir / "patches" / Path(entry_id).name
        try:
            applied = await asyncio.to_thread(
                apply_patch_to_rom_file,
                rec.full_path,
                rec.console_key,
                patch_raw,
                patch_filename,
                patches_dir,
                rec.clean_title or rec.title,
            )
        except Exception as exc:
            raise HTTPException(status_code=400, detail=str(exc))

        if payload.saveToLibrary and rec.full_path.parent.is_dir():
            lib_copy = rec.full_path.parent / applied["patchedFilename"]
            if lib_copy.resolve() != rec.full_path.resolve():
                shutil.copy2(applied["patchedPath"], lib_copy)
                catalog.rescan(
                    settings["roms_dir"],
                    settings.get("console_paths"),
                    arcade_lookup=minerva_index.get_arcade_romset_lookup(),
                )

        saved_row = db.add_patch(
            {
                "patch_id": applied["patchId"],
                "entry_id": entry_id,
                "label": (payload.label or "").strip() or f"Traducción [{payload.language or 'ES'}]",
                "language": (payload.language or "ES").upper(),
                "patch_format": applied["format"],
                "source_name": payload.sourceName or applied.get("innerPatchName") or patch_filename,
                "patched_filename": applied["patchedFilename"],
                "patched_path": applied["patchedPath"],
                "crc32_hex": applied["crc32"],
            }
        )
        return {
            "patch": saved_row,
            "downloadUrl": f"/api/v1/patches/download/{saved_row['patch_id']}",
            "crc32": applied["crc32"],
            "bytes": applied["bytes"],
        }

    @app.get("/api/v1/patches/download/{patch_id}")
    async def download_patched_rom(patch_id: str) -> FileResponse:
        patch = db.get_patch(patch_id)
        if not patch:
            raise HTTPException(status_code=404, detail="Parche no encontrado")
        patched_file = Path(patch["patched_path"])
        if not patched_file.is_file():
            raise HTTPException(status_code=404, detail="Archivo parcheado no disponible en disco")
        return FileResponse(patched_file, filename=patch["patched_filename"])

    @app.delete("/api/v1/patches/{patch_id}")
    async def remove_patch(patch_id: str) -> dict[str, Any]:
        removed = db.delete_patch(patch_id)
        if not removed:
            raise HTTPException(status_code=404, detail="Parche no encontrado")
        try:
            p = Path(removed["patched_path"])
            if p.is_file():
                p.unlink()
        except OSError:
            pass
        return {"deleted": patch_id}

    @app.post("/api/v1/covers/sync")
    async def sync_covers(payload: CoverSyncRequest) -> dict[str, Any]:
        settings = db.get_settings()
        limit = max(1, min(100, payload.limit))
        return await cover_sync.start_batch(
            catalog.all_records(),
            settings,
            limit=limit,
            console_key=payload.platform,
            entry_ids=payload.entryIds,
            retry_misses=payload.retryMisses,
        )

    @app.post("/api/v1/covers/resolve-batch")
    async def resolve_covers_batch(payload: ResolveCoverBatchRequest) -> dict[str, Any]:
        settings = db.get_settings()
        items = [it.model_dump() for it in payload.items]
        covers = await resolve_external_batch(items, settings, get_active_covers_dir())
        return {"covers": covers}

    @app.get("/api/v1/covers/status")
    async def covers_status() -> dict[str, Any]:
        return cover_sync.cache_stats()

    @app.delete("/api/v1/covers/cache")
    async def clear_covers() -> dict[str, Any]:
        removed = cover_sync.clear_cache()
        return {"removed": removed}

    @app.get("/api/v1/settings")
    async def get_settings() -> dict[str, Any]:
        return db.get_settings()

    @app.put("/api/v1/settings")
    async def put_settings(patch: SettingsPatch) -> dict[str, Any]:
        updates = {k: v for k, v in patch.model_dump().items() if v is not None}
        updated = db.update_settings(updates)
        if "roms_dir" in updates and updated.get("roms_dir"):
            try:
                Path(updated["roms_dir"]).expanduser().mkdir(parents=True, exist_ok=True)
            except OSError:
                pass
        if "covers_dir" in updates:
            new_cov = resolve_covers_dir(updated)
            catalog.set_covers_dir(new_cov)
            cover_sync.set_covers_dir(new_cov)
            app.state.covers_dir = new_cov
        if "roms_dir" in updates or "console_paths" in updates:
            catalog.rescan(
                updated["roms_dir"],
                updated.get("console_paths"),
                arcade_lookup=minerva_index.get_arcade_romset_lookup(),
            )
        return updated

    @app.post("/api/v1/settings/test-steamgriddb")
    async def test_sgdb(body: SteamGridTestRequest) -> dict[str, Any]:
        return await test_steamgriddb_key(body.apiKey)

    @app.post("/api/v1/settings/test-ai")
    async def test_ai(body: AiTestRequest) -> dict[str, Any]:
        return await test_ai_provider(body.provider, body.apiKey, body.model, body.baseUrl)

    @app.get("/api/v1/targets")
    async def list_targets() -> list[dict[str, Any]]:
        return db.get_settings().get("targets", [])

    @app.post("/api/v1/targets")
    async def create_target(payload: TargetPayload) -> dict[str, Any]:
        settings = db.get_settings()
        targets = list(settings.get("targets", []))
        target_id = payload.id or f"target-{len(targets) + 1}"
        item = payload.model_dump()
        item["id"] = target_id
        targets = [t for t in targets if t.get("id") != target_id] + [item]
        db.update_settings({"targets": targets})
        return item

    @app.put("/api/v1/targets/{target_id}")
    async def update_target(target_id: str, payload: TargetPayload) -> dict[str, Any]:
        settings = db.get_settings()
        targets = list(settings.get("targets", []))
        item = payload.model_dump()
        item["id"] = target_id
        found = False
        new_list: list[dict[str, Any]] = []
        for t in targets:
            if t.get("id") == target_id:
                new_list.append(item)
                found = True
            else:
                new_list.append(t)
        if not found:
            new_list.append(item)
        db.update_settings({"targets": new_list})
        return item

    @app.delete("/api/v1/targets/{target_id}")
    async def delete_target(target_id: str) -> dict[str, Any]:
        settings = db.get_settings()
        targets = [t for t in settings.get("targets", []) if t.get("id") != target_id]
        db.update_settings({"targets": targets})
        return {"deleted": target_id}

    @app.post("/api/v1/targets/{target_id}/test")
    async def test_target(target_id: str) -> dict[str, Any]:
        targets = db.get_settings().get("targets", [])
        target = next((t for t in targets if t.get("id") == target_id), None)
        if not target:
            raise HTTPException(status_code=404, detail="Perfil de consola no encontrado")
        return test_ftp_target(target)

    @app.get("/api/v1/transfers")
    async def get_transfers() -> list[dict[str, Any]]:
        return transfers.list_jobs()

    @app.post("/api/v1/transfers")
    async def create_transfer(req: TransferCreateRequest) -> dict[str, Any]:
        rec = catalog.get_record(req.entryId)
        if rec is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        targets = db.get_settings().get("targets", [])
        target = next((t for t in targets if t.get("id") == req.targetId), None)
        if target is None:
            raise HTTPException(status_code=404, detail="Consola destino no encontrada")
        file_override = None
        name_override = ""
        title_override = ""
        if req.patchId:
            patch = db.get_patch(req.patchId)
            if not patch or patch.get("entry_id") != req.entryId:
                raise HTTPException(status_code=404, detail="Parche no encontrado para esta ROM")
            file_override = Path(patch["patched_path"])
            name_override = patch["patched_filename"]
            title_override = f"{rec.title} [{patch['language']}]"
        job = transfers.enqueue(
            rec,
            target,
            remote_dir_override=req.remoteDir or "",
            file_path_override=file_override,
            remote_name_override=name_override,
            title_override=title_override,
        )
        db.mark_played(req.entryId)
        return job

    @app.get("/tinfoil/index.json")
    async def tinfoil_index(request: Request, all: int = 0) -> dict[str, Any]:
        base_url = str(request.base_url).rstrip("/")
        recs = [r for r in catalog.all_records() if not r.is_sys]
        switch_recs = [r for r in recs if r.console_key == "switch" or r.ext in ("NSP", "XCI", "NSZ", "XCZ")]
        selected = recs[:150] if all or not switch_recs else switch_recs
        files = [
            {
                "url": f"{base_url}/tinfoil/files/{r.entry_id}/{quote(r.full_path.name)}",
                "size": r.size,
            }
            for r in selected
        ]
        msg = (
            f"OzRetroRom Switch Shop ({len(files)} archivos Switch)"
            if switch_recs
            else f"OzRetroRom LAN Shop ({len(files)} ROMs indexadas; usa ?all=1 o agrega NSP/XCI en roms/switch)"
        )
        return {"files": files, "success": msg}

    @app.get("/tinfoil/files/{entry_id}/{filename}")
    async def tinfoil_download(entry_id: str, filename: str) -> FileResponse:
        rec = catalog.get_record(entry_id)
        if rec is None or not rec.full_path.is_file():
            raise HTTPException(status_code=404, detail="Archivo no encontrado")
        return FileResponse(rec.full_path, filename=rec.full_path.name)

    async def _proxy_to_java(request: Request, target_path: str) -> Response:
        base = (db.get_settings().get("java_companion_url") or "").rstrip("/")
        if not base:
            return JSONResponse({"message": "Companion Java no configurado"}, status_code=503)
        url = f"{base}/{target_path.lstrip('/')}"
        if request.url.query:
            url += f"?{request.url.query}"
        body = await request.body()
        # Strip browser Host/Origin/Referer so Java Companion accepts proxied local-network API calls
        headers = {
            k: v
            for k, v in request.headers.items()
            if k.lower() not in ("host", "content-length", "origin", "referer")
        }
        try:
            async with httpx.AsyncClient(timeout=60.0) as client:
                upstream = await client.request(request.method, url, content=body, headers=headers)
            resp_headers = {k: v for k, v in upstream.headers.items() if k.lower() not in ("transfer-encoding", "connection", "content-encoding")}
            return Response(content=upstream.content, status_code=upstream.status_code, headers=resp_headers)
        except Exception as exc:
            return JSONResponse({"message": f"Servicio MiNERVA Java no disponible: {exc}"}, status_code=503)

    @app.get("/api/v1/catalog/search")
    @app.get("/api/v1/minerva/search")
    async def catalog_search(q: str = "", platform: str = "") -> list[dict[str, Any]]:
        query = (q or "").strip()
        plat = (platform or "").strip().lower()
        if len(query) < 2:
            raise HTTPException(status_code=400, detail="Escribe al menos 2 caracteres para buscar.")

        settings = db.get_settings()

        if plat in ("arcade", "mame"):
            offline_hits = minerva_index.search(query, plat, limit=100)
            mame_hits_count = sum(1 for r in offline_hits if r.get("identifier"))
            if plat == "mame" or mame_hits_count < 3:
                ia_rows = await search_ia_roms(query, "mame", settings)
                seen_ids = {r.get("identifier") for r in offline_hits if r.get("identifier")}
                for row in ia_rows:
                    ident = row.get("identifier")
                    if ident and ident not in seen_ids:
                        seen_ids.add(ident)
                        offline_hits.append(row)
            return offline_hits[:100]

        if plat and minerva_index.supports_platform(plat):
            offline_hits = minerva_index.search(query, plat, limit=100)
            if offline_hits or plat in ("nes", "snes", "genesis", "gba", "ps1", "n64"):
                return offline_hits

        if not plat:
            offline_hits = minerva_index.search(query, "", limit=80)
            ext_rows: list[dict[str, Any]] = []
            if len(query) >= 3:
                ext_rows = await search_ia_extended_multi(query, settings, rows_limit=16)
            if not ext_rows:
                return offline_hits
            seen_ids = {str(r.get("identifier") or r.get("id")) for r in offline_hits}
            merged = list(offline_hits[:72])
            for row in ext_rows:
                ident = str(row.get("identifier") or row.get("id") or "")
                if ident and ident not in seen_ids:
                    seen_ids.add(ident)
                    merged.append(row)
            return merged[:100]

        remote_rows, ia_rows = await asyncio.gather(
            search_minerva_remote(query, plat),
            search_ia_roms(query, plat, settings),
        )
        seen_ext: set[str] = set()
        combined: list[dict[str, Any]] = []
        for row in ia_rows + remote_rows:
            rid = str(row.get("identifier") or row.get("id") or "")
            if rid and rid not in seen_ext:
                seen_ext.add(rid)
                combined.append(row)
        return combined[:60]

    @app.get("/api/v1/catalog/ia-files/{identifier}")
    async def catalog_ia_files(identifier: str) -> list[dict[str, Any]]:
        settings = db.get_settings()
        try:
            return await list_ia_files(identifier, settings)
        except Exception as exc:
            raise HTTPException(status_code=502, detail=str(exc))

    @app.get("/api/v1/downloads")
    async def list_direct_downloads() -> list[dict[str, Any]]:
        return direct_downloads.list_jobs()

    @app.post("/api/v1/downloads/ia")
    async def start_ia_download(req: IaDownloadRequest) -> dict[str, Any]:
        settings = db.get_settings()
        return await direct_downloads.start_ia_download(
            identifier=req.identifier,
            filename=req.filename,
            platform=req.platform,
            title=req.title,
            settings=settings,
            arcade_core=req.arcadeCore,
            on_complete=lambda: (lambda s: catalog.rescan(s["roms_dir"], s.get("console_paths")))(db.get_settings()),
        )

    @app.post("/api/v1/settings/test-ia")
    async def test_ia(req: IaTestRequest) -> dict[str, Any]:
        return await test_ia_credentials(req.accessKey, req.secretKey, req.cookie)

    @app.api_route("/oz/v1/minerva/{path:path}", methods=["GET", "POST"])
    async def proxy_minerva(request: Request, path: str) -> Response:
        return await _proxy_to_java(request, f"/oz/v1/minerva/{path}")

    @app.api_route("/oz/v1/internet-archive/{path:path}", methods=["GET", "POST"])
    async def proxy_ia(request: Request, path: str) -> Response:
        return await _proxy_to_java(request, f"/oz/v1/internet-archive/{path}")

    @app.api_route("/oz/v1/manifest/{path:path}", methods=["GET", "POST"])
    async def proxy_manifest(request: Request, path: str) -> Response:
        return await _proxy_to_java(request, f"/oz/v1/manifest/{path}")

    @app.api_route("/ports", methods=["GET"])
    @app.api_route("/ports/{path:path}", methods=["GET"])
    async def proxy_ports(request: Request, path: str = "") -> Response:
        suffix = f"/ports/{path}" if path else "/ports"
        return await _proxy_to_java(request, suffix)

    return app


app = create_app()

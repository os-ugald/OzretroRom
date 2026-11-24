from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

import httpx
from fastapi import Request, Response
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from ..catalog import CatalogIndex
from ..covers import CoverSyncManager
from ..db import HubDatabase
from ..engines import HomeEngine, LibraryEngine, SagasEngine
from ..events import EventBus
from ..ia_client import DirectDownloadManager
from ..mdns import MdnsAdvertiser
from ..minerva import MinervaOfflineIndex
from ..transfers import TransferManager


@dataclass
class HubContext:
    """Shared service container passed to modular FastAPI APIRouter factories."""

    db: HubDatabase
    event_bus: EventBus
    catalog: CatalogIndex
    cover_sync: CoverSyncManager
    transfers: TransferManager
    minerva_index: MinervaOfflineIndex
    direct_downloads: DirectDownloadManager
    library_engine: LibraryEngine
    sagas_engine: SagasEngine
    home_engine: HomeEngine
    resolved_data_dir: Path
    default_covers_dir: Path
    resolve_covers_dir: Callable[[dict[str, Any]], Path]
    get_active_covers_dir: Callable[[], Path]
    set_covers_dir_state: Callable[[Path], None]
    mdns_holder: dict[str, MdnsAdvertiser | None]
    detect_lan_ip: Callable[[], str]

    async def proxy_to_java(self, request: Request, target_path: str) -> Response:
        base = (self.db.get_settings().get("java_companion_url") or "").rstrip("/")
        if not base:
            return JSONResponse({"message": "Companion Java no configurado"}, status_code=503)
        url = f"{base}/{target_path.lstrip('/')}"
        if request.url.query:
            url += f"?{request.url.query}"
        body = await request.body()
        headers = {
            k: v
            for k, v in request.headers.items()
            if k.lower() not in ("host", "content-length", "origin", "referer")
        }
        try:
            async with httpx.AsyncClient(timeout=60.0) as client:
                upstream = await client.request(request.method, url, content=body, headers=headers)
            resp_headers = {
                k: v
                for k, v in upstream.headers.items()
                if k.lower() not in ("transfer-encoding", "connection", "content-encoding")
            }
            return Response(content=upstream.content, status_code=upstream.status_code, headers=resp_headers)
        except Exception as exc:
            return JSONResponse({"message": f"Servicio MiNERVA Java no disponible: {exc}"}, status_code=503)


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


class SyncChangeInput(BaseModel):
    entityId: str
    entityType: str = "game_state"
    op: str = "upsert"
    favorite: bool | None = None
    playCount: int | None = None
    lastPlayedAt: str | None = None
    quickResume: dict[str, Any] | None = None
    customMetadata: dict[str, Any] | None = None
    updatedAt: str | None = None


class SyncPushRequest(BaseModel):
    deviceId: str = "android"
    changes: list[SyncChangeInput] = Field(default_factory=list)

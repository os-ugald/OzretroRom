from __future__ import annotations

import os
import socket
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

import httpx
from fastapi import FastAPI, HTTPException, Response
from fastapi.responses import FileResponse, HTMLResponse
from fastapi.staticfiles import StaticFiles

from .catalog import CatalogIndex
from .config import DEFAULT_DATA_DIR, DEFAULT_PORT, resource_path
from .covers import CoverSyncManager
from .db import HubDatabase
from .engines import HomeEngine, LibraryEngine, SagasEngine
from .events import EventBus
from .ia_client import DirectDownloadManager
from .mdns import MdnsAdvertiser
from .minerva import MinervaOfflineIndex
from .routers import (
    HubContext,
    build_catalog_router,
    build_covers_router,
    build_library_router,
    build_sagas_router,
    build_sync_router,
    build_transfers_router,
)
from .transfers import TransferManager

STATIC_DIR = resource_path("app/static")
STATIC_UI_PATH = STATIC_DIR / "ui.html"


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


def create_app(
    data_dir: Path | None = None,
    roms_dir_override: str | None = None,
    download_transport: httpx.AsyncBaseTransport | None = None,
    enable_mdns: bool | None = None,
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
    event_bus = EventBus()
    catalog = CatalogIndex(db, active_covers_dir)
    cover_sync = CoverSyncManager(db, active_covers_dir, event_bus=event_bus)
    transfers = TransferManager(db=db, event_bus=event_bus)
    minerva_index = MinervaOfflineIndex()
    direct_downloads = DirectDownloadManager(transport=download_transport, db=db, event_bus=event_bus)
    library_engine = LibraryEngine()
    sagas_engine = SagasEngine()
    home_engine = HomeEngine()
    mdns_holder: dict[str, MdnsAdvertiser | None] = {"instance": None}

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
        should_mdns = (
            enable_mdns
            if enable_mdns is not None
            else (
                not os.environ.get("PYTEST_CURRENT_TEST")
                and os.environ.get("OZ_DISABLE_MDNS") != "1"
                and os.environ.get("OZ_BIND_HOST") != "127.0.0.1"
            )
        )
        if should_mdns:
            adv_port = int(os.environ.get("OZ_ADVERTISED_PORT") or DEFAULT_PORT)
            adv = MdnsAdvertiser(port=adv_port, lan_ip=_detect_lan_ip())
            adv.start()
            mdns_holder["instance"] = adv
            app.state.mdns = adv
        else:
            app.state.mdns = None
        yield
        if mdns_holder["instance"] is not None:
            mdns_holder["instance"].stop()
            mdns_holder["instance"] = None
        db.close()

    app = FastAPI(title="Oz Game Vault", version="2.1.0", lifespan=lifespan)

    def set_covers_dir_state(new_dir: Path) -> None:
        app.state.covers_dir = new_dir

    ctx = HubContext(
        db=db,
        event_bus=event_bus,
        catalog=catalog,
        cover_sync=cover_sync,
        transfers=transfers,
        minerva_index=minerva_index,
        direct_downloads=direct_downloads,
        library_engine=library_engine,
        sagas_engine=sagas_engine,
        home_engine=home_engine,
        resolved_data_dir=resolved_data_dir,
        default_covers_dir=default_covers_dir,
        resolve_covers_dir=resolve_covers_dir,
        get_active_covers_dir=get_active_covers_dir,
        set_covers_dir_state=set_covers_dir_state,
        mdns_holder=mdns_holder,
        detect_lan_ip=_detect_lan_ip,
    )

    app.state.ctx = ctx
    app.state.db = db
    app.state.event_bus = event_bus
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

    @app.get("/favicon.ico", include_in_schema=False)
    async def favicon() -> Response:
        ico_path = STATIC_DIR / "assets" / "favicon.ico"
        if not ico_path.is_file():
            raise HTTPException(status_code=404, detail="favicon.ico no encontrado")
        return FileResponse(ico_path, media_type="image/x-icon")

    app.include_router(build_library_router(ctx))
    app.include_router(build_catalog_router(ctx))
    app.include_router(build_sagas_router(ctx))
    app.include_router(build_covers_router(ctx))
    app.include_router(build_transfers_router(ctx))
    app.include_router(build_sync_router(ctx))

    if STATIC_DIR.is_dir():
        app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")

    return app


app = create_app()

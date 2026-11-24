from __future__ import annotations

import asyncio
from typing import Any

from fastapi import APIRouter, HTTPException, Request, Response

from ..ia_client import list_ia_files, search_ia_extended_multi, search_ia_roms, test_ia_credentials
from ..minerva import search_minerva_remote
from .schemas import HubContext, IaDownloadRequest, IaTestRequest


def build_catalog_router(ctx: HubContext) -> APIRouter:
    router = APIRouter()

    @router.get("/api/v1/catalog/search")
    @router.get("/api/v1/minerva/search")
    async def catalog_search(q: str = "", platform: str = "") -> list[dict[str, Any]]:
        query = (q or "").strip()
        plat = (platform or "").strip().lower()
        if len(query) < 2:
            raise HTTPException(status_code=400, detail="Escribe al menos 2 caracteres para buscar.")

        settings = ctx.db.get_settings()

        if plat in ("arcade", "mame"):
            offline_hits = ctx.minerva_index.search(query, plat, limit=100)
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

        if plat and ctx.minerva_index.supports_platform(plat):
            offline_hits = ctx.minerva_index.search(query, plat, limit=100)
            if offline_hits or plat in ("nes", "snes", "genesis", "gba", "ps1", "n64"):
                return offline_hits

        if not plat:
            offline_hits = ctx.minerva_index.search(query, "", limit=80)
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

    @router.get("/api/v1/catalog/ia-files/{identifier}")
    async def catalog_ia_files(identifier: str) -> list[dict[str, Any]]:
        settings = ctx.db.get_settings()
        try:
            return await list_ia_files(identifier, settings)
        except Exception as exc:
            raise HTTPException(status_code=502, detail=str(exc))

    @router.get("/api/v1/downloads")
    async def list_direct_downloads() -> list[dict[str, Any]]:
        return ctx.direct_downloads.list_jobs()

    @router.post("/api/v1/downloads/ia")
    async def start_ia_download(req: IaDownloadRequest) -> dict[str, Any]:
        settings = ctx.db.get_settings()

        def _on_download_complete() -> None:
            cur_settings = ctx.db.get_settings()
            ctx.catalog.rescan(
                cur_settings["roms_dir"],
                cur_settings.get("console_paths"),
                arcade_lookup=ctx.minerva_index.get_arcade_romset_lookup(),
            )
            ctx.event_bus.publish(
                "library.updated",
                {"reason": "ia_download", "count": len(ctx.catalog.all_records())},
            )

        return await ctx.direct_downloads.start_ia_download(
            identifier=req.identifier,
            filename=req.filename,
            platform=req.platform,
            title=req.title,
            settings=settings,
            arcade_core=req.arcadeCore,
            on_complete=_on_download_complete,
        )

    @router.post("/api/v1/settings/test-ia")
    async def test_ia(req: IaTestRequest) -> dict[str, Any]:
        return await test_ia_credentials(req.accessKey, req.secretKey, req.cookie)

    @router.api_route("/oz/v1/minerva/{path:path}", methods=["GET", "POST"])
    async def proxy_minerva(request: Request, path: str) -> Response:
        return await ctx.proxy_to_java(request, f"/oz/v1/minerva/{path}")

    @router.api_route("/oz/v1/internet-archive/{path:path}", methods=["GET", "POST"])
    async def proxy_ia(request: Request, path: str) -> Response:
        return await ctx.proxy_to_java(request, f"/oz/v1/internet-archive/{path}")

    @router.api_route("/oz/v1/manifest/{path:path}", methods=["GET", "POST"])
    async def proxy_manifest(request: Request, path: str) -> Response:
        return await ctx.proxy_to_java(request, f"/oz/v1/manifest/{path}")

    @router.api_route("/ports", methods=["GET"])
    @router.api_route("/ports/{path:path}", methods=["GET"])
    async def proxy_ports(request: Request, path: str = "") -> Response:
        suffix = f"/ports/{path}" if path else "/ports"
        return await ctx.proxy_to_java(request, suffix)

    return router

from __future__ import annotations

import base64
import time
from pathlib import Path
from typing import Any

import httpx
from fastapi import APIRouter, HTTPException, Response
from fastapi.responses import FileResponse

from ..covers import fetch_cover_for_entry, optimize_to_webp, resolve_external_batch
from .schemas import CoverSyncRequest, CustomCoverRequest, HubContext, ResolveCoverBatchRequest

_FALLBACK_SVG = (
    "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 400'>"
    "<rect width='300' height='400' fill='#141923'/>"
    "<circle cx='150' cy='200' r='44' fill='none' stroke='#38BDF8' stroke-width='2'/>"
    "</svg>"
)


def build_covers_router(ctx: HubContext) -> APIRouter:
    router = APIRouter()

    @router.api_route("/api/v1/covers/{entry_id}.webp", methods=["GET", "HEAD"])
    async def get_cover(entry_id: str) -> Response:
        safe_id = Path(entry_id).name
        cover_file = ctx.get_active_covers_dir() / f"{safe_id}.webp"
        if cover_file.is_file():
            return FileResponse(cover_file, media_type="image/webp", headers={"Cache-Control": "public, max-age=86400"})
        return Response(content=_FALLBACK_SVG, media_type="image/svg+xml")

    @router.post("/api/v1/covers/fetch/{entry_id}")
    async def fetch_single_cover(entry_id: str) -> dict[str, Any]:
        rec = ctx.catalog.get_record(entry_id)
        if rec is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        settings = ctx.db.get_settings()
        cov_dir = ctx.get_active_covers_dir()
        async with httpx.AsyncClient(timeout=5.0, follow_redirects=True) as client:
            return await fetch_cover_for_entry(client, rec, settings, ctx.db, cov_dir)

    @router.post("/api/v1/covers/custom/{entry_id}")
    async def upload_custom_cover(entry_id: str, payload: CustomCoverRequest) -> dict[str, Any]:
        rec = ctx.catalog.get_record(entry_id)
        if rec is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        settings = ctx.db.get_settings()
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

        cov_dir = ctx.get_active_covers_dir()
        cov_dir.mkdir(parents=True, exist_ok=True)
        safe_id = Path(entry_id).name
        (cov_dir / f"{safe_id}.webp").write_bytes(webp_bytes)
        ctx.db.set_cover_meta(entry_id, "found", "custom")
        ver = int(time.time())
        result = {
            "entryId": entry_id,
            "status": "found",
            "source": "custom",
            "coverUrl": f"/api/v1/covers/{safe_id}.webp?v={ver}",
        }
        ctx.event_bus.publish("library.updated", {"reason": "custom_cover", **result})
        return result

    @router.post("/api/v1/covers/sync")
    async def sync_covers(payload: CoverSyncRequest) -> dict[str, Any]:
        settings = ctx.db.get_settings()
        limit = max(1, min(100, payload.limit))
        return await ctx.cover_sync.start_batch(
            ctx.catalog.all_records(),
            settings,
            limit=limit,
            console_key=payload.platform,
            entry_ids=payload.entryIds,
            retry_misses=payload.retryMisses,
        )

    @router.post("/api/v1/covers/resolve-batch")
    async def resolve_covers_batch(payload: ResolveCoverBatchRequest) -> dict[str, Any]:
        settings = ctx.db.get_settings()
        items = [it.model_dump() for it in payload.items]
        covers = await resolve_external_batch(items, settings, ctx.get_active_covers_dir())
        return {"covers": covers}

    @router.get("/api/v1/covers/status")
    async def covers_status() -> dict[str, Any]:
        return ctx.cover_sync.cache_stats()

    @router.delete("/api/v1/covers/cache")
    async def clear_covers() -> dict[str, Any]:
        removed = ctx.cover_sync.clear_cache()
        return {"removed": removed}

    return router

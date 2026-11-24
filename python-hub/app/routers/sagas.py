from __future__ import annotations

import re
import time
from typing import Any

from fastapi import APIRouter, HTTPException

from ..sagas import get_sagas_catalog_payload
from .schemas import AddToSagaRequest, CustomSagaRequest, HubContext, ResolveSagaArtRequest


def build_sagas_router(ctx: HubContext) -> APIRouter:
    router = APIRouter()

    @router.get("/api/v1/sagas/catalog")
    async def get_sagas_catalog() -> dict[str, Any]:
        return get_sagas_catalog_payload()

    @router.get("/api/v1/sagas")
    async def list_sagas() -> list[dict[str, Any]]:
        canonical = ctx.library_engine.get_library(ctx.catalog.serialize_all(), grouped=True)
        return ctx.sagas_engine.list_sagas(
            canonical,
            ctx.db.get_settings().get("custom_sagas"),
            covers_dir=ctx.get_active_covers_dir(),
        )

    @router.get("/api/v1/sagas/audit")
    async def audit_sagas_endpoint() -> dict[str, Any]:
        canonical = ctx.library_engine.get_library(ctx.catalog.serialize_all(), grouped=True)
        return ctx.sagas_engine.audit_sagas(
            canonical,
            ctx.db.get_settings().get("custom_sagas"),
            covers_dir=ctx.get_active_covers_dir(),
        )

    @router.post("/api/v1/sagas/resolve-art")
    async def resolve_sagas_art(payload: ResolveSagaArtRequest | None = None) -> dict[str, Any]:
        saga_ids = payload.sagaIds if payload else None
        art = await ctx.sagas_engine.resolve_art(
            ctx.db.get_settings(),
            ctx.get_active_covers_dir(),
            saga_ids=saga_ids,
        )
        return {"art": art}

    @router.post("/api/v1/sagas/custom")
    async def upsert_custom_saga(payload: CustomSagaRequest) -> list[dict[str, Any]]:
        name = (payload.name or "").strip()
        if not name:
            raise HTTPException(status_code=400, detail="Escribe un nombre para la saga.")
        settings = ctx.db.get_settings()
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
        ctx.db.update_settings({"custom_sagas": updated})
        canonical = ctx.library_engine.get_library(ctx.catalog.serialize_all(), grouped=True)
        return ctx.sagas_engine.list_sagas(canonical, updated, covers_dir=ctx.get_active_covers_dir())

    @router.post("/api/v1/sagas/{saga_id}/add")
    async def add_entry_to_saga(saga_id: str, payload: AddToSagaRequest) -> list[dict[str, Any]]:
        if ctx.catalog.get_record(payload.entryId) is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        settings = ctx.db.get_settings()
        customs = [dict(c) for c in (settings.get("custom_sagas") or []) if isinstance(c, dict)]
        target = next((c for c in customs if str(c.get("id")) == saga_id), None)
        if target is None:
            target = {
                "id": saga_id,
                "name": saga_id.replace("-", " ").title(),
                "tagline": "",
                "accent": "#FFB000",
                "entryIds": [],
            }
            customs.append(target)
        eids = list(target.get("entryIds") or [])
        if payload.entryId not in eids:
            eids.append(payload.entryId)
        target["entryIds"] = eids
        ctx.db.update_settings({"custom_sagas": customs})
        canonical = ctx.library_engine.get_library(ctx.catalog.serialize_all(), grouped=True)
        return ctx.sagas_engine.list_sagas(canonical, customs, covers_dir=ctx.get_active_covers_dir())

    @router.delete("/api/v1/sagas/custom/{saga_id}")
    async def delete_custom_saga(saga_id: str) -> list[dict[str, Any]]:
        settings = ctx.db.get_settings()
        customs = [
            c
            for c in (settings.get("custom_sagas") or [])
            if isinstance(c, dict) and str(c.get("id")) != saga_id
        ]
        ctx.db.update_settings({"custom_sagas": customs})
        canonical = ctx.library_engine.get_library(ctx.catalog.serialize_all(), grouped=True)
        return ctx.sagas_engine.list_sagas(canonical, customs, covers_dir=ctx.get_active_covers_dir())

    @router.post("/api/v1/sagas/{saga_id}/ai-audit")
    async def ai_audit_single_saga(saga_id: str) -> dict[str, Any]:
        settings = ctx.db.get_settings()
        customs = settings.get("custom_sagas") or []
        canonical = ctx.library_engine.get_library(ctx.catalog.serialize_all(), grouped=True)
        sagas = ctx.sagas_engine.list_sagas(canonical, customs, covers_dir=ctx.get_active_covers_dir())
        target = next((s for s in sagas if s.get("id") == saga_id), None)
        if target is None:
            raise HTTPException(status_code=404, detail="Saga no encontrada")
        return await ctx.sagas_engine.ai_audit_saga(target, settings)

    return router

from __future__ import annotations

from pathlib import Path
from typing import Any
from urllib.parse import quote

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import FileResponse

from ..transfers import test_ftp_target
from .schemas import HubContext, TargetPayload, TransferCreateRequest


def build_transfers_router(ctx: HubContext) -> APIRouter:
    router = APIRouter()

    @router.get("/api/v1/targets")
    async def list_targets() -> list[dict[str, Any]]:
        return ctx.db.get_settings().get("targets", [])

    @router.post("/api/v1/targets")
    async def create_target(payload: TargetPayload) -> dict[str, Any]:
        settings = ctx.db.get_settings()
        targets = list(settings.get("targets", []))
        target_id = payload.id or f"target-{len(targets) + 1}"
        item = payload.model_dump()
        item["id"] = target_id
        targets = [t for t in targets if t.get("id") != target_id] + [item]
        ctx.db.update_settings({"targets": targets})
        return item

    @router.put("/api/v1/targets/{target_id}")
    async def update_target(target_id: str, payload: TargetPayload) -> dict[str, Any]:
        settings = ctx.db.get_settings()
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
        ctx.db.update_settings({"targets": new_list})
        return item

    @router.delete("/api/v1/targets/{target_id}")
    async def delete_target(target_id: str) -> dict[str, Any]:
        settings = ctx.db.get_settings()
        targets = [t for t in settings.get("targets", []) if t.get("id") != target_id]
        ctx.db.update_settings({"targets": targets})
        return {"deleted": target_id}

    @router.post("/api/v1/targets/{target_id}/test")
    async def test_target(target_id: str) -> dict[str, Any]:
        targets = ctx.db.get_settings().get("targets", [])
        target = next((t for t in targets if t.get("id") == target_id), None)
        if not target:
            raise HTTPException(status_code=404, detail="Perfil de consola no encontrado")
        return test_ftp_target(target)

    @router.get("/api/v1/transfers")
    async def get_transfers() -> list[dict[str, Any]]:
        return ctx.transfers.list_jobs()

    @router.post("/api/v1/transfers")
    async def create_transfer(req: TransferCreateRequest) -> dict[str, Any]:
        rec = ctx.catalog.get_record(req.entryId)
        if rec is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        targets = ctx.db.get_settings().get("targets", [])
        target = next((t for t in targets if t.get("id") == req.targetId), None)
        if target is None:
            raise HTTPException(status_code=404, detail="Consola destino no encontrada")
        file_override = None
        name_override = ""
        title_override = ""
        if req.patchId:
            patch = ctx.db.get_patch(req.patchId)
            if not patch or patch.get("entry_id") != req.entryId:
                raise HTTPException(status_code=404, detail="Parche no encontrado para esta ROM")
            file_override = Path(patch["patched_path"])
            name_override = patch["patched_filename"]
            title_override = f"{rec.title} [{patch['language']}]"
        job = ctx.transfers.enqueue(
            rec,
            target,
            remote_dir_override=req.remoteDir or "",
            file_path_override=file_override,
            remote_name_override=name_override,
            title_override=title_override,
        )
        ctx.db.mark_played(req.entryId)
        return job

    @router.get("/tinfoil/index.json")
    async def tinfoil_index(request: Request, all: int = 0) -> dict[str, Any]:
        base_url = str(request.base_url).rstrip("/")
        recs = [r for r in ctx.catalog.all_records() if not r.is_sys]
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

    @router.get("/tinfoil/files/{entry_id}/{filename}")
    async def tinfoil_download(entry_id: str, filename: str) -> FileResponse:
        rec = ctx.catalog.get_record(entry_id)
        if rec is None or not rec.full_path.is_file():
            raise HTTPException(status_code=404, detail="Archivo no encontrado")
        return FileResponse(rec.full_path, filename=rec.full_path.name)

    return router

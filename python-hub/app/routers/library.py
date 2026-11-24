from __future__ import annotations

import asyncio
import base64
import os
import shutil
import socket
import subprocess
import sys
from pathlib import Path
from typing import Any

import httpx
from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import FileResponse

from ..config import BASE_DIR, is_frozen
from ..covers import test_steamgriddb_key
from ..descriptions import resolve_game_description
from ..ia_client import ia_auth_headers
from ..patcher import apply_patch_to_rom_file, search_spanish_patches
from ..recommendations import test_ai_provider
from .schemas import (
    AiTestRequest,
    ApplyPatchRequest,
    HubContext,
    PathInspectRequest,
    PickFolderRequest,
    SettingsPatch,
    SteamGridTestRequest,
)


def build_library_router(ctx: HubContext) -> APIRouter:
    router = APIRouter()

    @router.get("/health")
    async def health() -> dict[str, Any]:
        stats = ctx.cover_sync.cache_stats()
        return {
            "status": "ok",
            "engine": "python-fastapi",
            "contract_version": 2,
            "roms_count": len(ctx.catalog.all_records()),
            "minerva_entries": ctx.minerva_index.total_count(),
            "mame_entries": ctx.minerva_index.mame_count(),
            "cached_covers": stats["cachedCount"],
            "scan_ms": ctx.catalog.last_scan_ms,
        }

    @router.get("/api/v1/system/info")
    async def system_info() -> dict[str, Any]:
        settings = ctx.db.get_settings()
        candidates = [
            BASE_DIR / "dist" / "OzRetroRom-Portable-Win64.zip",
            Path(sys.executable).resolve().parent / "OzRetroRom-Portable-Win64.zip",
        ]
        zip_path = next((p for p in candidates if p.is_file()), candidates[0])
        adv = ctx.mdns_holder.get("instance")
        return {
            "lanIp": ctx.detect_lan_ip(),
            "hostname": socket.gethostname(),
            "isFrozen": is_frozen(),
            "desktopMode": os.environ.get("OZ_DESKTOP_MODE") == "1",
            "romsDir": settings.get("roms_dir") or "",
            "coversDir": str(ctx.get_active_covers_dir()),
            "defaultCoversDir": str(ctx.default_covers_dir),
            "dataDir": str(ctx.resolved_data_dir),
            "minervaEntries": ctx.minerva_index.total_count(),
            "mameEntries": ctx.minerva_index.mame_count(),
            "portableZipExists": zip_path.is_file(),
            "portableZipPath": str(zip_path),
            "eventsUrl": "/api/v1/events",
            "syncUrl": "/api/v1/sync",
            "latestSyncRevision": ctx.db.get_latest_sync_revision(),
            "mdns": adv.status() if adv is not None else {"enabled": False, "running": False},
        }

    @router.post("/api/v1/system/inspect-path")
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

    @router.post("/api/v1/system/pick-folder")
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

    @router.get("/api/v1/library")
    @router.get("/oz/v1/library")
    async def get_library(grouped: bool = True) -> list[dict[str, Any]]:
        return ctx.library_engine.get_library(ctx.catalog.serialize_all(), grouped=grouped)

    @router.get("/api/v1/library/maintenance")
    async def get_library_maintenance() -> dict[str, Any]:
        return ctx.library_engine.get_maintenance_report(ctx.catalog.serialize_all())

    @router.get("/api/v1/home")
    async def get_home_workspace() -> dict[str, Any]:
        raw = ctx.catalog.serialize_all()
        canonical = ctx.library_engine.get_library(raw, grouped=True)
        settings = ctx.db.get_settings()
        sagas = ctx.sagas_engine.list_sagas(
            canonical,
            custom_sagas=settings.get("custom_sagas"),
            covers_dir=ctx.get_active_covers_dir(),
        )
        maint = ctx.library_engine.get_maintenance_report(raw)
        return ctx.home_engine.build_home_payload(
            canonical_items=canonical,
            raw_items=raw,
            sagas=sagas,
            settings=settings,
            maintenance=maint,
        )

    @router.post("/api/v1/library/rescan")
    async def rescan_library() -> dict[str, Any]:
        settings = ctx.db.get_settings()
        res = ctx.catalog.rescan(
            settings["roms_dir"],
            settings.get("console_paths"),
            arcade_lookup=ctx.minerva_index.get_arcade_romset_lookup(),
        )
        ctx.event_bus.publish("library.updated", {"reason": "rescan", "count": len(ctx.catalog.all_records())})
        return res

    @router.post("/api/v1/library/{entry_id}/favorite")
    async def toggle_favorite(entry_id: str) -> dict[str, Any]:
        if ctx.catalog.get_record(entry_id) is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        fav = ctx.db.toggle_favorite(entry_id)
        payload = {"entryId": entry_id, "favorite": fav}
        ctx.event_bus.publish("library.updated", {"reason": "favorite", **payload})
        ctx.event_bus.publish(
            "sync.updated",
            {"latestRevision": ctx.db.get_latest_sync_revision(), "sourceDevice": "pc-hub", "entryId": entry_id},
        )
        return payload

    @router.post("/api/v1/library/{entry_id}/played")
    async def mark_rom_played(entry_id: str) -> dict[str, Any]:
        if ctx.catalog.get_record(entry_id) is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        res = ctx.db.mark_played(entry_id)
        payload = {"entryId": entry_id, **res}
        ctx.event_bus.publish("library.updated", {"reason": "played", **payload})
        ctx.event_bus.publish(
            "sync.updated",
            {"latestRevision": ctx.db.get_latest_sync_revision(), "sourceDevice": "pc-hub", "entryId": entry_id},
        )
        return payload

    @router.post("/api/v1/library/{entry_id}/verify")
    async def verify_rom(entry_id: str) -> dict[str, Any]:
        try:
            return ctx.catalog.compute_sha256(entry_id)
        except FileNotFoundError as exc:
            raise HTTPException(status_code=404, detail=str(exc))

    @router.get("/api/v1/recommendations")
    async def get_recommendations(
        use_ai: bool = False,
        limit: int = 6,
        offset: int = 0,
        exclude: str = "",
        seed: int = 0,
    ) -> dict[str, Any]:
        canonical = await asyncio.to_thread(
            lambda: ctx.library_engine.get_library(ctx.catalog.serialize_all(), grouped=True)
        )
        exclude_ids = {s.strip() for s in exclude.split(",") if s.strip()} if exclude else None
        return await ctx.home_engine.get_recommendations(
            canonical,
            ctx.db.get_settings(),
            use_ai=use_ai,
            limit=limit,
            offset=offset,
            exclude_ids=exclude_ids,
            seed=seed,
            discover_limit=8,
        )

    @router.get("/api/v1/recommendations/mood")
    def get_mood_recommendations(
        mood: str = "quick",
        offset: int = 0,
        exclude: str = "",
    ) -> dict[str, Any]:
        canonical = ctx.library_engine.get_library(ctx.catalog.serialize_all(), grouped=True)
        exclude_ids = {s.strip() for s in exclude.split(",") if s.strip()} if exclude else None
        return ctx.home_engine.get_mood_picks(
            canonical,
            mood=mood,
            offset=offset,
            limit=3,
            exclude_ids=exclude_ids,
        )

    @router.get("/api/v1/library/{entry_id}/description")
    async def get_game_description(entry_id: str, refresh: bool = False) -> dict[str, Any]:
        rec = ctx.catalog.get_record(entry_id)
        if rec is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        desc_data = await resolve_game_description(rec, ctx.db, force=refresh)
        canonical = ctx.library_engine.get_library(ctx.catalog.serialize_all(), grouped=True)
        sagas = ctx.sagas_engine.list_sagas(
            canonical,
            ctx.db.get_settings().get("custom_sagas"),
            covers_dir=ctx.get_active_covers_dir(),
        )
        return {**desc_data, "saga": ctx.sagas_engine.find_saga_for_entry(entry_id, sagas)}

    @router.get("/api/v1/library/{entry_id}/download")
    async def download_library_rom(entry_id: str, patch_id: str | None = None) -> FileResponse:
        rec = ctx.catalog.get_record(entry_id)
        if rec is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        if patch_id:
            patch = ctx.db.get_patch(patch_id)
            if not patch or patch.get("entry_id") != entry_id:
                raise HTTPException(status_code=404, detail="Parche no encontrado para esta ROM")
            patched_file = Path(patch["patched_path"])
            if not patched_file.is_file():
                raise HTTPException(status_code=404, detail="Archivo parcheado ausente en disco")
            return FileResponse(patched_file, filename=patch["patched_filename"])
        if not rec.full_path.is_file():
            raise HTTPException(status_code=404, detail="Archivo ROM no encontrado en disco")
        return FileResponse(rec.full_path, filename=rec.full_path.name)

    @router.get("/api/v1/patches/search")
    async def search_patches(q: str = "", platform: str = "") -> list[dict[str, Any]]:
        return await search_spanish_patches(q, platform, ctx.db.get_settings())

    @router.get("/api/v1/library/{entry_id}/patches")
    async def list_entry_patches(entry_id: str) -> list[dict[str, Any]]:
        if ctx.catalog.get_record(entry_id) is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")
        return ctx.db.list_patches(entry_id)

    @router.post("/api/v1/patches/apply/{entry_id}")
    async def apply_entry_patch(entry_id: str, payload: ApplyPatchRequest) -> dict[str, Any]:
        rec = ctx.catalog.get_record(entry_id)
        if rec is None:
            raise HTTPException(status_code=404, detail="ROM no encontrada")

        settings = ctx.db.get_settings()
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

        patches_dir = ctx.resolved_data_dir / "patches" / Path(entry_id).name
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
                ctx.catalog.rescan(
                    settings["roms_dir"],
                    settings.get("console_paths"),
                    arcade_lookup=ctx.minerva_index.get_arcade_romset_lookup(),
                )

        saved_row = ctx.db.add_patch(
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

    @router.get("/api/v1/patches/download/{patch_id}")
    async def download_patched_rom(patch_id: str) -> FileResponse:
        patch = ctx.db.get_patch(patch_id)
        if not patch:
            raise HTTPException(status_code=404, detail="Parche no encontrado")
        p = Path(patch["patched_path"])
        if not p.is_file():
            raise HTTPException(status_code=404, detail="Archivo parcheado no existe en disco")
        return FileResponse(p, filename=patch["patched_filename"])

    @router.delete("/api/v1/patches/{patch_id}")
    async def remove_patch(patch_id: str) -> dict[str, Any]:
        deleted = ctx.db.delete_patch(patch_id)
        if not deleted:
            raise HTTPException(status_code=404, detail="Parche no encontrado")
        try:
            p = Path(deleted["patched_path"])
            if p.is_file():
                p.unlink()
        except OSError:
            pass
        return {"deleted": patch_id}

    @router.get("/api/v1/settings")
    async def get_settings() -> dict[str, Any]:
        return ctx.db.get_settings()

    @router.put("/api/v1/settings")
    async def put_settings(patch: SettingsPatch) -> dict[str, Any]:
        updates = {k: v for k, v in patch.model_dump().items() if v is not None}
        updated = ctx.db.update_settings(updates)
        if "roms_dir" in updates and updated.get("roms_dir"):
            try:
                Path(updated["roms_dir"]).expanduser().mkdir(parents=True, exist_ok=True)
            except OSError:
                pass
        if "covers_dir" in updates:
            new_cov = ctx.resolve_covers_dir(updated)
            ctx.catalog.set_covers_dir(new_cov)
            ctx.cover_sync.set_covers_dir(new_cov)
            ctx.set_covers_dir_state(new_cov)
        if "roms_dir" in updates or "console_paths" in updates:
            ctx.catalog.rescan(
                updated["roms_dir"],
                updated.get("console_paths"),
                arcade_lookup=ctx.minerva_index.get_arcade_romset_lookup(),
            )
        return updated

    @router.post("/api/v1/settings/test-steamgriddb")
    async def test_sgdb(body: SteamGridTestRequest) -> dict[str, Any]:
        return await test_steamgriddb_key(body.apiKey)

    @router.post("/api/v1/settings/test-ai")
    async def test_ai(body: AiTestRequest) -> dict[str, Any]:
        return await test_ai_provider(body.provider, body.apiKey, body.model, body.baseUrl)

    return router

from __future__ import annotations

import asyncio
import json
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse

from .schemas import HubContext, SyncPushRequest


def build_sync_router(ctx: HubContext) -> APIRouter:
    router = APIRouter()

    @router.get("/api/v1/events")
    async def stream_events(
        request: Request,
        since_id: int | None = None,
        once: bool = False,
    ) -> StreamingResponse:
        last_id = since_id
        if last_id is None:
            raw_header = (request.headers.get("last-event-id") or "").strip()
            if raw_header:
                try:
                    last_id = int(raw_header)
                except ValueError:
                    last_id = None

        async def event_stream():
            hello_payload = {
                "latestEventId": ctx.event_bus.latest_id,
                "latestSyncRevision": ctx.db.get_latest_sync_revision(),
                "serverTime": datetime.now(timezone.utc).isoformat(),
            }
            yield f"event: connected\ndata: {json.dumps(hello_payload, separators=(',', ':'))}\n\n"

            if once:
                replay_from = last_id if last_id is not None else max(0, ctx.event_bus.latest_id - 16)
                for ev in ctx.event_bus.history_since(replay_from):
                    yield ctx.event_bus.format_sse(ev)
                return

            sub_id, replay, queue = ctx.event_bus.open_subscription(last_event_id=last_id)
            last_sent = last_id or 0
            try:
                for ev in replay:
                    if ev.id > last_sent:
                        last_sent = ev.id
                        yield ctx.event_bus.format_sse(ev)
                while True:
                    if await request.is_disconnected():
                        break
                    try:
                        ev = await asyncio.wait_for(queue.get(), timeout=15.0)
                        if ev.id > last_sent:
                            last_sent = ev.id
                            yield ctx.event_bus.format_sse(ev)
                    except asyncio.TimeoutError:
                        yield ": keepalive\n\n"
            finally:
                ctx.event_bus.close_subscription(sub_id)

        return StreamingResponse(
            event_stream(),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no",
            },
        )

    @router.get("/api/v1/sync")
    async def pull_sync_delta(since: int = 0, limit: int = 500) -> dict[str, Any]:
        delta = ctx.db.get_sync_changes(since_revision=since, limit=limit)
        active_dl = [
            j for j in ctx.direct_downloads.list_jobs() if j.get("state") in ("queued", "connecting", "downloading")
        ]
        active_tr = [
            j for j in ctx.transfers.list_jobs() if j.get("state") in ("queued", "connecting", "transferring")
        ]
        return {
            "latestRevision": delta["latestRevision"],
            "hasMore": delta["hasMore"],
            "serverTime": datetime.now(timezone.utc).isoformat(),
            "changes": delta["changes"],
            "activeDownloads": active_dl,
            "activeTransfers": active_tr,
        }

    @router.post("/api/v1/sync")
    async def push_sync_delta(payload: SyncPushRequest) -> dict[str, Any]:
        device_id = (payload.deviceId or "android").strip() or "android"
        items = [c.model_dump(exclude_none=True) for c in payload.changes]
        result = ctx.db.apply_sync_changes(items, source_device=device_id)
        if result["applied"] > 0:
            ctx.event_bus.publish(
                "sync.updated",
                {
                    "latestRevision": result["latestRevision"],
                    "sourceDevice": device_id,
                    "applied": result["applied"],
                },
            )
        return {
            **result,
            "serverTime": datetime.now(timezone.utc).isoformat(),
        }

    return router

from __future__ import annotations

import asyncio
import json
import threading
from collections import deque
from collections.abc import AsyncIterator
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any


@dataclass(frozen=True)
class BusEvent:
    """Immutable event emitted through the in-memory SSE EventBus."""

    id: int
    event: str
    data: dict[str, Any]
    timestamp: str


def _enqueue_nonblocking(queue: asyncio.Queue[BusEvent], ev: BusEvent) -> None:
    if queue.full():
        try:
            queue.get_nowait()
        except asyncio.QueueEmpty:
            pass
    try:
        queue.put_nowait(ev)
    except asyncio.QueueFull:
        pass


class EventBus:
    """Thread-safe in-memory pub/sub EventBus bridging sync workers and asyncio SSE streams."""

    def __init__(self, history_limit: int = 128, queue_size: int = 256) -> None:
        self._lock = threading.RLock()
        self._seq = 0
        self._sub_seq = 0
        self._history: deque[BusEvent] = deque(maxlen=max(16, history_limit))
        self._queue_size = max(16, queue_size)
        self._subscribers: dict[int, tuple[asyncio.AbstractEventLoop, asyncio.Queue[BusEvent]]] = {}

    @property
    def latest_id(self) -> int:
        with self._lock:
            return self._seq

    @property
    def subscriber_count(self) -> int:
        with self._lock:
            return len(self._subscribers)

    def history_since(self, last_id: int) -> list[BusEvent]:
        with self._lock:
            return [ev for ev in self._history if ev.id > last_id]

    def publish(self, event: str, data: dict[str, Any]) -> BusEvent:
        now = datetime.now(timezone.utc).isoformat()
        with self._lock:
            self._seq += 1
            ev = BusEvent(id=self._seq, event=event, data=dict(data), timestamp=now)
            self._history.append(ev)
            subs = list(self._subscribers.values())

        for loop, queue in subs:
            try:
                if not loop.is_closed():
                    loop.call_soon_threadsafe(_enqueue_nonblocking, queue, ev)
            except RuntimeError:
                pass
        return ev

    def open_subscription(
        self,
        last_event_id: int | None = None,
    ) -> tuple[int, list[BusEvent], asyncio.Queue[BusEvent]]:
        loop = asyncio.get_running_loop()
        queue: asyncio.Queue[BusEvent] = asyncio.Queue(maxsize=self._queue_size)
        replay: list[BusEvent] = []
        with self._lock:
            self._sub_seq += 1
            sub_id = self._sub_seq
            self._subscribers[sub_id] = (loop, queue)
            if last_event_id is not None:
                replay = [ev for ev in self._history if ev.id > last_event_id]
        return sub_id, replay, queue

    def close_subscription(self, sub_id: int) -> None:
        with self._lock:
            self._subscribers.pop(sub_id, None)

    async def subscribe(self, last_event_id: int | None = None) -> AsyncIterator[BusEvent]:
        sub_id, replay, queue = self.open_subscription(last_event_id=last_event_id)
        last_sent = last_event_id or 0
        try:
            for ev in replay:
                if ev.id > last_sent:
                    last_sent = ev.id
                    yield ev
            while True:
                ev = await queue.get()
                if ev.id <= last_sent:
                    continue
                last_sent = ev.id
                yield ev
        finally:
            self.close_subscription(sub_id)

    @staticmethod
    def format_sse(ev: BusEvent) -> str:
        payload = json.dumps(ev.data, ensure_ascii=False, separators=(",", ":"))
        return f"id: {ev.id}\nevent: {ev.event}\ndata: {payload}\n\n"

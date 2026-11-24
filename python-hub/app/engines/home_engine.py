from __future__ import annotations

from collections import Counter
from typing import Any

from ..config import CONSOLES
from ..recommendations import (
    compute_ai_recommendations,
    compute_local_recommendations,
    compute_mood_picks,
)


class HomeEngine:
    """Engine 3 — Dedicated curation engine for Oz Game Vault Inicio (Hero, Highlights, Discover, Mood)."""

    def build_home_payload(
        self,
        canonical_items: list[dict[str, Any]],
        raw_items: list[dict[str, Any]],
        sagas: list[dict[str, Any]],
        settings: dict[str, Any] | None = None,
        maintenance: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        cfg = settings or {}
        base = [it for it in canonical_items if not it.get("isSys")]
        raw_playable = [it for it in raw_items if not it.get("isSys")]

        recent = sorted(
            (x for x in base if x.get("lastPlayedAt")),
            key=lambda x: str(x.get("lastPlayedAt") or ""),
            reverse=True,
        )
        favorites = sorted(
            (x for x in base if x.get("favorite")),
            key=lambda x: str(x.get("cleanTitle") or x.get("title") or "").lower(),
        )
        covered = [x for x in base if x.get("hasCover")]

        pinned_id = str(cfg.get("pinned_hero_entry_id") or "")
        pinned = next(
            (x for x in base if x["entryId"] == pinned_id or pinned_id in (x.get("duplicateEntryIds") or ())),
            None,
        )
        hero = pinned or (recent[0] if recent else None) or (favorites[0] if favorites else None) or (covered[0] if covered else None) or (base[0] if base else None)

        # Build diverse console-balanced Destacados showcase without duplicates
        featured = self._pick_diverse_highlights(base, limit=12, exclude_id=hero["entryId"] if hero else "")

        # Aggregate canonical per-console stats
        byte_totals: Counter[str] = Counter()
        game_totals: Counter[str] = Counter()
        for it in base:
            ck = str(it.get("consoleKey") or "other")
            game_totals[ck] += 1
            byte_totals[ck] += sum(int(v.get("size") or 0) for v in (it.get("variants") or ())) or int(it.get("size") or 0)

        consoles = []
        for ck, count in game_totals.most_common():
            meta = CONSOLES.get(ck, {"label": ck.upper(), "full": ck.upper(), "color": "#7DD3FC"})
            consoles.append(
                {
                    "consoleKey": ck,
                    "label": meta["label"],
                    "full": meta["full"],
                    "color": meta["color"],
                    "uniqueCount": count,
                    "sizeBytes": byte_totals[ck],
                }
            )

        featured_sagas = [s for s in sagas if int(s.get("ownedCount") or 0) > 0][:8]

        return {
            "brand": "Oz Game Vault",
            "stats": {
                "uniqueRoms": len(base),
                "rawFiles": len(raw_playable),
                "collapsedDuplicates": max(0, len(raw_playable) - len(base)),
                "consolesCount": len(consoles),
                "recentCount": len(recent),
                "favoritesCount": len(favorites),
                "coversCount": len(covered),
                "sagasTotal": len(sagas),
                "sagasOwned": sum(1 for s in sagas if int(s.get("ownedCount") or 0) > 0),
            },
            "hero": hero,
            "continuePlaying": recent[:12],
            "favorites": favorites[:12],
            "featured": featured,
            "consoles": consoles,
            "featuredSagas": featured_sagas,
            "maintenance": maintenance,
        }

    def _pick_diverse_highlights(
        self,
        base: list[dict[str, Any]],
        limit: int = 12,
        exclude_id: str = "",
    ) -> list[dict[str, Any]]:
        buckets: dict[str, list[dict[str, Any]]] = {}
        # Prefer covered retail games first
        sorted_pool = sorted(
            base,
            key=lambda x: (
                bool(x.get("hasCover")),
                bool(x.get("favorite")),
                int(x.get("playCount") or 0),
                int(x.get("variantCount") or 1),
                str(x.get("cleanTitle") or x.get("title") or "").lower(),
            ),
            reverse=True,
        )
        for it in sorted_pool:
            if it["entryId"] == exclude_id:
                continue
            ck = str(it.get("consoleKey") or "other")
            buckets.setdefault(ck, []).append(it)

        picks: list[dict[str, Any]] = []
        ordered_consoles = sorted(buckets.keys(), key=lambda c: len(buckets[c]), reverse=True)
        cursor = 0
        while len(picks) < limit:
            progressed = False
            for ck in ordered_consoles:
                arr = buckets[ck]
                if cursor < len(arr):
                    picks.append(arr[cursor])
                    progressed = True
                    if len(picks) >= limit:
                        break
            if not progressed:
                break
            cursor += 1
        return picks

    def get_mood_picks(
        self,
        canonical_items: list[dict[str, Any]],
        mood: str = "quick",
        offset: int = 0,
        limit: int = 3,
        exclude_ids: set[str] | None = None,
    ) -> dict[str, Any]:
        return compute_mood_picks(
            canonical_items,
            mood=mood,
            offset=offset,
            limit=limit,
            exclude_ids=exclude_ids,
        )

    async def get_recommendations(
        self,
        canonical_items: list[dict[str, Any]],
        settings: dict[str, Any],
        use_ai: bool = False,
        limit: int = 6,
        offset: int = 0,
        exclude_ids: set[str] | None = None,
        seed: int = 0,
        discover_limit: int = 8,
    ) -> dict[str, Any]:
        import asyncio

        clamped = max(1, min(12, limit))
        if use_ai:
            return await compute_ai_recommendations(
                canonical_items,
                settings,
                limit=clamped,
                offset=offset,
                exclude_ids=exclude_ids,
                seed=seed,
                discover_limit=discover_limit,
            )
        return await asyncio.to_thread(
            compute_local_recommendations,
            canonical_items,
            clamped,
            offset,
            exclude_ids,
            seed,
            discover_limit,
        )

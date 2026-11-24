from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

import httpx

from ..covers import resolve_saga_artwork
from ..sagas import CURATED_SAGAS, _is_unofficial_saga_noise, build_sagas, find_saga_for_entry


class SagasEngine:
    """Engine 2 — Audited chronological franchise curation, art resolution, and diagnostics."""

    def list_sagas(
        self,
        canonical_items: list[dict[str, Any]],
        custom_sagas: list[dict[str, Any]] | None = None,
        covers_dir: Path | None = None,
    ) -> list[dict[str, Any]]:
        return build_sagas(canonical_items, custom_sagas=custom_sagas, covers_dir=covers_dir)

    def find_saga_for_entry(
        self,
        entry_id: str,
        sagas: list[dict[str, Any]],
    ) -> dict[str, Any] | None:
        return find_saga_for_entry(entry_id, sagas)

    async def resolve_art(
        self,
        settings: dict[str, Any],
        covers_dir: Path,
        saga_ids: list[str] | None = None,
    ) -> dict[str, Any]:
        return await resolve_saga_artwork(CURATED_SAGAS, settings, covers_dir, saga_ids=saga_ids)

    def audit_sagas(
        self,
        canonical_items: list[dict[str, Any]],
        custom_sagas: list[dict[str, Any]] | None = None,
        covers_dir: Path | None = None,
    ) -> dict[str, Any]:
        sagas = self.list_sagas(canonical_items, custom_sagas=custom_sagas, covers_dir=covers_dir)
        by_id = {it["entryId"]: it for it in canonical_items if not it.get("isSys")}

        issues: list[dict[str, Any]] = []
        saga_reports: list[dict[str, Any]] = []
        total_extras = 0
        total_leaks = 0
        total_chronology_errors = 0

        for saga in sagas:
            sid = saga["id"]
            entries = saga.get("entries") or []
            extras = [e for e in entries if e.get("isExtra")]
            total_extras += len(extras)

            main_years = [int(e["year"]) for e in entries if not e.get("isExtra") and e.get("year") is not None]
            chronological_ok = main_years == sorted(main_years)
            if not chronological_ok:
                total_chronology_errors += 1
                issues.append({"sagaId": sid, "kind": "chronology", "detail": f"Años fuera de orden: {main_years}"})

            pirate_proto_leaks = []
            for e in entries:
                if e.get("owned") and e.get("entryId") in by_id:
                    src_item = by_id[e["entryId"]]
                    if _is_unofficial_saga_noise(src_item):
                        pirate_proto_leaks.append(src_item["title"])
            total_leaks += len(pirate_proto_leaks)
            if pirate_proto_leaks:
                issues.append({"sagaId": sid, "kind": "unofficial_leak", "detail": pirate_proto_leaks})

            has_banner = bool(saga.get("heroBannerUrl") or saga.get("keyArtUrl") or saga.get("bannerCoverUrl"))
            history_ok = len(str(saga.get("history") or "")) >= 80
            if not history_ok:
                issues.append({"sagaId": sid, "kind": "history_short", "detail": "Sin historia reseñada >= 80 chars"})

            saga_reports.append(
                {
                    "id": sid,
                    "name": saga["name"],
                    "ownedCount": saga["ownedCount"],
                    "canonicalOwnedCount": saga.get("canonicalOwnedCount", saga["ownedCount"]),
                    "totalCount": saga["totalCount"],
                    "extrasCount": len(extras),
                    "chronologicalOk": chronological_ok,
                    "unofficialLeaks": pirate_proto_leaks,
                    "hasHeroBanner": bool(saga.get("heroBannerUrl")),
                    "hasKeyArt": bool(saga.get("keyArtUrl")),
                    "hasAnyArt": has_banner,
                    "historyOk": history_ok,
                }
            )

        return {
            "totalSagas": len(sagas),
            "sagasWithOwnedGames": sum(1 for s in sagas if s.get("ownedCount", 0) > 0),
            "totalExtraEntries": total_extras,
            "totalUnofficialLeaks": total_leaks,
            "chronologyErrors": total_chronology_errors,
            "allClean": total_leaks == 0 and total_chronology_errors == 0,
            "issues": issues,
            "sagas": saga_reports,
        }

    async def ai_audit_saga(self, saga: dict[str, Any], settings: dict[str, Any]) -> dict[str, Any]:
        """Audit a single saga locally and refine suggestions via Gemini/OpenAI when configured."""
        entries = list(saga.get("entries") or [])
        main_entries = [e for e in entries if not e.get("isExtra")]
        extras = [e for e in entries if e.get("isExtra")]
        years = [int(e["year"]) for e in main_entries if e.get("year") is not None]
        chronology_ok = years == sorted(years)

        era_counts: dict[str, int] = {}
        seen_titles: set[str] = set()
        dup_titles: list[str] = []
        for e in entries:
            era = str(e.get("era") or "Sin era")
            era_counts[era] = era_counts.get(era, 0) + 1
            norm = re.sub(r"[^a-z0-9]+", " ", str(e.get("title") or "").lower()).strip()
            if norm and norm in seen_titles:
                dup_titles.append(str(e.get("title") or ""))
            seen_titles.add(norm)

        fragmented_eras = [k for k, v in era_counts.items() if v == 1 and "extra" not in k.lower()]
        issues: list[str] = []
        era_suggestions: list[str] = []
        if not chronology_ok:
            issues.append("Orden cronológico con años intercalados en entregas principales.")
        if dup_titles:
            issues.append(f"Posibles entregas duplicadas: {', '.join(dup_titles[:3])}")
        if len(fragmented_eras) >= 3:
            era_suggestions.append(
                f"Conviene agrupar eras de un solo título ({', '.join(fragmented_eras[:3])}) en bloques por generación."
            )
        if len(extras) > len(main_entries) and len(extras) > 3:
            era_suggestions.append(
                f"Hay {len(extras)} spin-offs o extras sin clasificar en eras principales; revisa si algún título clásico debería integrarse."
            )

        missing_highlights = [
            f"{e.get('title')} ({e.get('consoleKey', '').upper()})"
            for e in main_entries
            if not e.get("owned")
        ][:4]

        score = 96
        if not chronology_ok:
            score -= 22
        if dup_titles:
            score -= 10
        if len(fragmented_eras) >= 3:
            score -= 8
        if len(extras) > len(main_entries) + 3:
            score -= 6
        score = max(45, min(100, score))

        summary = (
            f"{saga.get('name')}: {len(main_entries)} entregas troncales organizadas en {len(era_counts)} grupos de era"
            + (" con orden cronológico verificado." if chronology_ok else " (requiere revisar años).")
        )
        local_report = {
            "sagaId": saga.get("id"),
            "sagaName": saga.get("name"),
            "score": score,
            "source": "local",
            "sourceLabel": "Auditoría curada",
            "chronologyOk": chronology_ok,
            "summary": summary,
            "issues": issues,
            "eraSuggestions": era_suggestions,
            "missingHighlights": missing_highlights,
        }

        provider = str(settings.get("ai_provider") or "local").strip().lower()
        api_key = str(settings.get("ai_api_key") or "").strip()
        model = str(settings.get("ai_model") or "").strip()
        base_url = str(settings.get("ai_base_url") or "").strip()
        if provider == "local" or (provider == "gemini" and not api_key) or (provider == "openai" and not api_key and not base_url):
            return local_report

        prompt_data = {
            "saga": saga.get("name"),
            "entries": [
                {"title": e.get("title"), "year": e.get("year"), "era": e.get("era"), "console": e.get("consoleKey"), "extra": bool(e.get("isExtra"))}
                for e in entries[:35]
            ],
        }
        system_prompt = (
            "Eres un historiador experto en sagas de videojuegos retro. Evalúa la cronología y agrupación por eras. "
            'Responde SOLO JSON válido: {"score": 0..100, "summary": "diagnóstico breve en español", '
            '"issues": ["problema detectado o lista vacía"], '
            '"eraSuggestions": ["mejora concreta de agrupación por era"], '
            '"missingHighlights": ["título clásico ausente importante"]}.'
        )
        try:
            raw_content = ""
            async with httpx.AsyncClient(timeout=10.0) as client:
                if provider == "gemini":
                    target_model = model or "gemini-2.5-flash"
                    url = f"https://generativelanguage.googleapis.com/v1beta/models/{target_model}:generateContent?key={api_key}"
                    resp = await client.post(
                        url,
                        json={
                            "system_instruction": {"parts": [{"text": system_prompt}]},
                            "contents": [{"parts": [{"text": json.dumps(prompt_data, ensure_ascii=False)}]}],
                            "generationConfig": {"responseMimeType": "application/json", "temperature": 0.3},
                        },
                    )
                    resp.raise_for_status()
                    raw_content = resp.json()["candidates"][0]["content"]["parts"][0]["text"]
                else:
                    endpoint = (base_url or "https://api.openai.com/v1").rstrip("/") + "/chat/completions"
                    headers = {"Content-Type": "application/json"}
                    if api_key:
                        headers["Authorization"] = f"Bearer {api_key}"
                    resp = await client.post(
                        endpoint,
                        headers=headers,
                        json={
                            "model": model or "gpt-4o-mini",
                            "temperature": 0.3,
                            "messages": [
                                {"role": "system", "content": system_prompt},
                                {"role": "user", "content": json.dumps(prompt_data, ensure_ascii=False)},
                            ],
                        },
                    )
                    resp.raise_for_status()
                    raw_content = resp.json()["choices"][0]["message"]["content"]

            cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw_content.strip(), flags=re.IGNORECASE | re.DOTALL)
            parsed = json.loads(cleaned)
            return {
                **local_report,
                "score": max(0, min(100, int(parsed.get("score") or score))),
                "source": provider,
                "sourceLabel": "Gemini IA" if provider == "gemini" else "IA Curada",
                "summary": str(parsed.get("summary") or summary),
                "issues": [str(x) for x in (parsed.get("issues") or issues)][:5],
                "eraSuggestions": [str(x) for x in (parsed.get("eraSuggestions") or era_suggestions)][:4],
                "missingHighlights": [str(x) for x in (parsed.get("missingHighlights") or missing_highlights)][:4],
            }
        except Exception:
            return local_report

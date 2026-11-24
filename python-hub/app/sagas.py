from __future__ import annotations

import hashlib
import json
import re
import time
from pathlib import Path
from typing import Any

from .config import BASE_DIR, resource_path


def _norm(val: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", (val or "").lower()).strip()


def _ext_cover_id(key: str) -> str:
    return "ext-" + hashlib.sha256(key.encode("utf-8")).hexdigest()[:20]


def _cover_url_if_exists(path: Path) -> str | None:
    if not path.is_file():
        return None
    try:
        ver = int(path.stat().st_mtime)
    except OSError:
        ver = int(time.time())
    return f"/api/v1/covers/{path.name}?v={ver}"


def _clean_match_text(item: dict[str, Any]) -> tuple[str, str]:
    raw_title = str(item.get("title") or "")
    clean = str(item.get("cleanTitle") or raw_title)
    stripped = re.sub(r"\s*#.*$", "", clean).strip()
    norm_clean = _norm(stripped)
    norm_raw = _norm(raw_title)
    return norm_clean, norm_raw


def _matches_any(patterns: tuple[str, ...], *texts: str) -> bool:
    for pat in patterns:
        rx = re.compile(pat, re.I)
        if any(rx.search(t) for t in texts if t):
            return True
    return False


def resolve_sagas_catalog_path() -> Path:
    shared_path = BASE_DIR.parent / "shared" / "sagas_catalog.json"
    if shared_path.is_file():
        return shared_path
    return resource_path("app/data/sagas_catalog.json")


def _hydrate_saga_definition(raw: dict[str, Any]) -> dict[str, Any]:
    timeline = []
    for ms in raw.get("timeline") or []:
        timeline.append(
            {
                "title": str(ms["title"]),
                "year": int(ms["year"]),
                "consoleKey": str(ms["consoleKey"]),
                "era": str(ms.get("era") or ""),
                "matchRegex": tuple(str(x) for x in (ms.get("matchRegex") or ())),
                "excludeMatchRegex": tuple(str(x) for x in (ms.get("excludeMatchRegex") or ())),
                "snesAliasRegex": tuple(str(x) for x in (ms.get("snesAliasRegex") or ())),
                "excludeConsoles": tuple(str(x) for x in (ms.get("excludeConsoles") or ())),
                "preferConsoles": tuple(str(x) for x in (ms.get("preferConsoles") or ())),
                "allowCombo": bool(ms.get("allowCombo", False)),
                "minervaQuery": str(ms.get("minervaQuery") or ms["title"]),
                "libretroArt": ms.get("libretroArt"),
            }
        )
    return {
        "id": str(raw["id"]),
        "name": str(raw["name"]),
        "tagline": str(raw.get("tagline") or ""),
        "foundedYear": int(raw["foundedYear"]),
        "creator": str(raw.get("creator") or ""),
        "accent": str(raw.get("accent") or "#FFB000"),
        "history": str(raw.get("history") or ""),
        "heroArt": dict(raw.get("heroArt") or {}),
        "includeRegex": tuple(str(x) for x in (raw.get("includeRegex") or ())),
        "excludeRegex": tuple(str(x) for x in (raw.get("excludeRegex") or ())),
        "timeline": timeline,
    }


def load_curated_sagas(catalog_path: Path | None = None) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    target = catalog_path or resolve_sagas_catalog_path()
    raw_payload = json.loads(target.read_text(encoding="utf-8"))
    raw_sagas = [s for s in (raw_payload.get("sagas") or []) if isinstance(s, dict)]
    hydrated = [_hydrate_saga_definition(s) for s in raw_sagas]
    return raw_payload, hydrated


_RAW_CATALOG_PAYLOAD, CURATED_SAGAS = load_curated_sagas()


def get_sagas_catalog_payload() -> dict[str, Any]:
    sagas = list(_RAW_CATALOG_PAYLOAD.get("sagas") or [])
    return {
        "schemaVersion": int(_RAW_CATALOG_PAYLOAD.get("schemaVersion") or 1),
        "count": len(sagas),
        "sagas": sagas,
    }


_UNOFFICIAL_NOISE_RE = re.compile(
    r"\((?:pirate|aftermarket|proto(?:type)?|seganet|demo|sample)[^)]*\)|\[b[0-9a-z]*\]",
    re.I,
)


def _is_unofficial_saga_noise(item: dict[str, Any]) -> bool:
    raw = str(item.get("title") or "")
    return bool(_UNOFFICIAL_NOISE_RE.search(raw))


def _pick_best_match(
    candidates: list[dict[str, Any]],
    prefer_consoles: tuple[str, ...] = (),
    allow_combo: bool = False,
) -> dict[str, Any]:
    def score(x: dict[str, Any]) -> tuple[Any, ...]:
        raw = str(x.get("title") or "").lower()
        is_noise = any(w in raw for w in ("(beta", "(proto", "(pirate", "(unl", "(aftermarket", "(seganet", "[b"))
        is_combo = (" + " in raw or " ~ " in raw or "compilation" in raw or "collection" in raw) and not allow_combo
        ck = str(x.get("consoleKey") or "")
        pref_score = (len(prefer_consoles) - prefer_consoles.index(ck)) if ck in prefer_consoles else 0
        return (
            not is_noise,
            not is_combo,
            pref_score,
            bool(x.get("hasSpanishPatch")),
            bool(x.get("hasCover")),
            int(x.get("playCount") or 0),
            bool(x.get("favorite")),
            "world" in str(x.get("region") or "").lower(),
            "usa" in str(x.get("region") or "").lower(),
        )

    return sorted(candidates, key=score, reverse=True)[0]


def _serialize_owned_entry(
    order: int,
    item: dict[str, Any],
    override_title: str = "",
    year: int | None = None,
    era: str = "",
    variant_count: int = 1,
    is_extra: bool = False,
) -> dict[str, Any]:
    real_clean = item.get("cleanTitle") or item.get("title") or ""
    variants = list(item.get("variants") or [])
    total_variants = max(variant_count, len(variants))
    return {
        "order": order,
        "title": override_title or real_clean,
        "matchedTitle": real_clean if override_title and _norm(override_title) != _norm(real_clean) else "",
        "year": year,
        "era": era,
        "isExtra": is_extra,
        "variantCount": total_variants,
        "variants": variants,
        "consoleKey": item.get("consoleKey") or "other",
        "owned": True,
        "entryId": item["entryId"],
        "coverUrl": item.get("coverUrl"),
        "hasCover": bool(item.get("hasCover")),
        "hasSpanishPatch": bool(item.get("hasSpanishPatch")),
        "playCount": int(item.get("playCount") or 0),
        "favorite": bool(item.get("favorite")),
        "region": item.get("region") or "",
        "minervaQuery": (override_title or real_clean).split(":")[0],
    }


def _should_skip_demo_disc(item: dict[str, Any]) -> bool:
    raw = str(item.get("title") or "")
    return raw.count(" + ") >= 2


def build_sagas(
    library_items: list[dict[str, Any]],
    custom_sagas: list[dict[str, Any]] | None = None,
    covers_dir: Path | None = None,
) -> list[dict[str, Any]]:
    base = [
        it
        for it in library_items
        if not it.get("isSys") and not _should_skip_demo_disc(it) and not _is_unofficial_saga_noise(it)
    ]
    by_id = {it["entryId"]: it for it in base}
    custom_map = {
        str(c.get("id")): c
        for c in (custom_sagas or [])
        if isinstance(c, dict) and c.get("id")
    }

    sagas_out: list[dict[str, Any]] = []
    seen_custom_ids: set[str] = set()

    for saga in CURATED_SAGAS:
        sid = saga["id"]
        inc_pats: tuple[str, ...] = saga.get("includeRegex") or ()
        exc_pats: tuple[str, ...] = saga.get("excludeRegex") or ()

        pool: list[dict[str, Any]] = []
        for it in base:
            norm_clean, norm_raw = _clean_match_text(it)
            if not _matches_any(inc_pats, norm_clean, norm_raw):
                continue
            if exc_pats and _matches_any(exc_pats, norm_clean, norm_raw):
                continue
            pool.append(it)

        custom_ext = custom_map.get(sid)
        if custom_ext:
            seen_custom_ids.add(sid)
            pool_ids = {it["entryId"] for it in pool}
            for eid in custom_ext.get("entryIds") or []:
                if eid in by_id and eid not in pool_ids:
                    pool.append(by_id[eid])
                    pool_ids.add(eid)

        entries: list[dict[str, Any]] = []
        used_ids: set[str] = set()
        used_norms: set[str] = set()
        sorted_timeline = sorted(saga["timeline"], key=lambda m: (int(m.get("year") or 9999), m["title"]))

        order = 1
        for ms in sorted_timeline:
            m_pats: tuple[str, ...] = ms.get("matchRegex") or ()
            m_exc: tuple[str, ...] = ms.get("excludeMatchRegex") or ()
            excl_consoles = tuple(ms.get("excludeConsoles") or ())

            hits: list[dict[str, Any]] = []
            for it in pool:
                if it["entryId"] in used_ids:
                    continue
                if excl_consoles and it.get("consoleKey") in excl_consoles:
                    continue
                norm_clean, norm_raw = _clean_match_text(it)
                if m_exc and _matches_any(m_exc, norm_clean, norm_raw):
                    continue
                match_ok = _matches_any(m_pats, norm_clean, norm_raw)
                if not match_ok and ms.get("snesAliasRegex"):
                    if it.get("consoleKey") == "snes" and _matches_any(ms["snesAliasRegex"], norm_clean, norm_raw):
                        match_ok = True
                if match_ok:
                    hits.append(it)

            if hits:
                best = _pick_best_match(
                    hits,
                    prefer_consoles=tuple(ms.get("preferConsoles") or ()),
                    allow_combo=bool(ms.get("allowCombo")),
                )
                for h in hits:
                    used_ids.add(h["entryId"])
                    nc, _ = _clean_match_text(h)
                    if nc:
                        used_norms.add(nc)
                entries.append(
                    _serialize_owned_entry(
                        order,
                        best,
                        override_title=ms["title"],
                        year=ms.get("year"),
                        era=ms.get("era") or "",
                        variant_count=len(hits),
                        is_extra=False,
                    )
                )
            else:
                ghost_cover_url = None
                if covers_dir is not None:
                    ext_id = _ext_cover_id(f"saga:{sid}:{ms['title'].lower()}")
                    ghost_cover_url = _cover_url_if_exists(covers_dir / f"{ext_id}.webp")
                entries.append(
                    {
                        "order": order,
                        "title": ms["title"],
                        "matchedTitle": "",
                        "year": ms.get("year"),
                        "era": ms.get("era") or "",
                        "isExtra": False,
                        "variantCount": 0,
                        "consoleKey": ms.get("consoleKey") or "arcade",
                        "owned": False,
                        "entryId": None,
                        "coverUrl": ghost_cover_url,
                        "hasCover": bool(ghost_cover_url),
                        "hasSpanishPatch": False,
                        "playCount": 0,
                        "favorite": False,
                        "region": "",
                        "minervaQuery": ms.get("minervaQuery") or ms["title"],
                    }
                )
            order += 1

        extra_candidates: dict[str, list[dict[str, Any]]] = {}
        for it in pool:
            if it["entryId"] in used_ids:
                continue
            raw_t = str(it.get("title") or "")
            if " + " in raw_t and any(un in _clean_match_text(it)[0] for un in used_norms if len(un) >= 6):
                used_ids.add(it["entryId"])
                continue
            nc, _ = _clean_match_text(it)
            if nc and nc in used_norms:
                used_ids.add(it["entryId"])
                continue
            extra_candidates.setdefault(nc or it["entryId"], []).append(it)

        for key in sorted(extra_candidates.keys()):
            group = extra_candidates[key]
            best = _pick_best_match(group)
            for g in group:
                used_ids.add(g["entryId"])
            entries.append(
                _serialize_owned_entry(
                    order,
                    best,
                    era="Extra / Spin-off",
                    variant_count=len(group),
                    is_extra=True,
                )
            )
            order += 1

        owned_entries = [e for e in entries if e["owned"]]
        hero_banner_url = _cover_url_if_exists(covers_dir / f"saga-hero-{sid}.webp") if covers_dir else None
        key_art_url = _cover_url_if_exists(covers_dir / f"saga-key-{sid}.webp") if covers_dir else None
        first_owned_cover = next((e["coverUrl"] for e in owned_entries if e.get("hasCover") and e.get("coverUrl")), None)
        banner = hero_banner_url or key_art_url or first_owned_cover

        canonical_owned = sum(1 for e in entries if e["owned"] and not e.get("isExtra"))
        canonical_total = sum(1 for e in entries if not e.get("isExtra"))
        consoles = list(dict.fromkeys([e["consoleKey"] for e in owned_entries if e.get("consoleKey")]))
        if not consoles:
            consoles = list(dict.fromkeys([e["consoleKey"] for e in entries[:4] if e.get("consoleKey")]))
        played_count = sum(1 for e in owned_entries if e["playCount"] > 0)

        sagas_out.append(
            {
                "id": sid,
                "name": saga["name"],
                "tagline": saga["tagline"],
                "history": saga.get("history") or saga["tagline"],
                "foundedYear": saga.get("foundedYear"),
                "creator": saga.get("creator") or "",
                "accent": saga["accent"],
                "isCustom": False,
                "ownedCount": len(owned_entries),
                "totalCount": len(entries),
                "canonicalOwnedCount": canonical_owned,
                "canonicalTotalCount": canonical_total,
                "playedCount": played_count,
                "consoles": consoles,
                "heroBannerUrl": hero_banner_url,
                "keyArtUrl": key_art_url,
                "bannerCoverUrl": banner,
                "entries": entries,
            }
        )

    for cid, cs in custom_map.items():
        if cid in seen_custom_ids:
            continue
        items = [by_id[eid] for eid in (cs.get("entryIds") or []) if eid in by_id]
        entries = [_serialize_owned_entry(idx + 1, it) for idx, it in enumerate(items)]
        banner = next((e["coverUrl"] for e in entries if e.get("hasCover") and e.get("coverUrl")), None)
        consoles = list(dict.fromkeys([e["consoleKey"] for e in entries if e.get("consoleKey")]))
        sagas_out.append(
            {
                "id": cid,
                "name": str(cs.get("name") or "Mi Saga"),
                "tagline": str(cs.get("tagline") or "Colección personalizada por orden cronológico"),
                "history": str(cs.get("tagline") or "Colección personalizada creada dentro de Game Vault."),
                "foundedYear": None,
                "creator": "Personalizada",
                "accent": str(cs.get("accent") or "#FFB000"),
                "isCustom": True,
                "ownedCount": len(entries),
                "totalCount": len(entries),
                "canonicalOwnedCount": len(entries),
                "canonicalTotalCount": len(entries),
                "playedCount": sum(1 for e in entries if e["playCount"] > 0),
                "consoles": consoles,
                "heroBannerUrl": banner,
                "keyArtUrl": banner,
                "bannerCoverUrl": banner,
                "entries": entries,
            }
        )

    sagas_out.sort(
        key=lambda s: (
            bool(s["isCustom"]),
            s["ownedCount"] > 0,
            s.get("canonicalOwnedCount", s["ownedCount"]),
            s["ownedCount"],
        ),
        reverse=True,
    )
    return sagas_out


def find_saga_for_entry(entry_id: str, sagas: list[dict[str, Any]]) -> dict[str, Any] | None:
    for s in sagas:
        for e in s.get("entries", []):
            if not e.get("owned"):
                continue
            if e.get("entryId") == entry_id or any(v.get("entryId") == entry_id for v in (e.get("variants") or [])):
                return {
                    "id": s["id"],
                    "name": s["name"],
                    "ownedCount": s["ownedCount"],
                    "totalCount": s["totalCount"],
                    "accent": s["accent"],
                }
    return None

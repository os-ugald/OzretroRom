from __future__ import annotations

import re
from collections import Counter, defaultdict
from typing import Any

from ..config import CONSOLES

_PAREN_RE = re.compile(r"\s*[\(\[][^)\]]*[\)\]]")
_HASH_SUFFIX_RE = re.compile(r"\s*#.*$")
_BARE_DISC_RE = re.compile(r"\b(?:disc|disk|cd|side)\s*[0-9a-d]+\b", re.I)

# Curated same-console regional title equivalences so Japanese / European / Alternate
# names for the EXACT SAME retail release collapse cleanly into one game with selectable variants.
REGIONAL_ALIAS_MAP: dict[str, str] = {
    "bare knuckle ikari no tetsuken": "streets of rage",
    "bare knuckle": "streets of rage",
    "bare knuckle ii": "streets of rage 2",
    "bare knuckle iii": "streets of rage 3",
    "super shinobi": "revenge of shinobi",
    "super shinobi ii": "shinobi iii return of the ninja master",
    "007 shitou the duel": "james bond 007 the duel",
    "phantasy star sennenki no owari ni": "phantasy star iv",
    "phantasy star ii kaerazaru toki no owari ni": "phantasy star ii",
    "shining force kamigami no isan": "shining force",
    "shining force ii inishie no fuuin": "shining force ii",
    "garou densetsu shukumei no tatakai": "fatal fury",
    "garou densetsu 2 aratanaru tatakai": "fatal fury 2",
    "wonder boy v monster world iii": "wonder boy in monster world",
    "dragon ball z buyuu retsuden": "dragon ball z buyuu retsuden",
    "dragon ball z l appel du destin": "dragon ball z buyuu retsuden",
    "alex kidd tenkuu majou": "alex kidd in the enchanted castle",
    "alex kidd cheongong maseong": "alex kidd in the enchanted castle",
    "toejam earl in panic auf funkotron": "toejam earl in panic on funkotron",
    "splatterhouse part 2": "splatterhouse 2",
    "splatterhouse part 3": "splatterhouse 3",
    "thunder force ii md": "thunder force ii",
    "strider hiryuu": "strider",
    "mugen senshi valis": "valis",
    "great circus mystery mickey to minnie magical adventure 2": "great circus mystery starring mickey minnie",
    "castle of illusion fushigi no oshiro daibouken": "castle of illusion starring mickey mouse",
    "super mario yoshi island": "super mario world 2 yoshi s island",
    "sonic the hedgehog spinball": "sonic spinball",
    "sonic compilation sonic classics": "sonic compilation",
    "contra hard corps": "contra hard corps",
    "vampire killer": "castlevania bloodlines",
    "biohazard": "resident evil",
    "biohazard 2": "resident evil 2",
    "biohazard 3 last escape": "resident evil 3 nemesis",
    "rockman": "mega man",
    "rockman 2": "mega man 2",
    "rockman x": "mega man x",
}


def _normalize_text(val: str) -> str:
    txt = _HASH_SUFFIX_RE.sub("", val or "")
    txt = _PAREN_RE.sub(" ", txt)
    txt = _BARE_DISC_RE.sub(" ", txt)
    txt = txt.replace("&", " ").replace("+", " ").replace("_", " ").replace(".", " ")
    # Fold trailing ", The" or ", A" so "Addams Family, The" == "The Addams Family"
    txt = re.sub(r",\s*the\b", "", txt, flags=re.I)
    words = [w for w in re.sub(r"[^a-z0-9]+", " ", txt.lower()).split() if w]
    if words and words[0] == "the":
        words = words[1:]
    return " ".join(words)


def canonical_title_key(title: str, clean_title: str = "") -> str:
    """Computes a canonical matching key for grouping duplicate editions of the same game."""
    raw = clean_title or title or ""
    parts = [p.strip() for p in raw.split("~") if p.strip()]
    normalized_parts = [_normalize_text(p) for p in (parts or [raw])]
    normalized_parts = [p for p in normalized_parts if p]
    if not normalized_parts:
        return _normalize_text(title)
    for np in normalized_parts:
        if np in REGIONAL_ALIAS_MAP:
            return REGIONAL_ALIAS_MAP[np]
    # Use the longest retail/Western segment when No-Intro uses "JP Title ~ US/World Title"
    chosen = max(normalized_parts, key=lambda p: (any(ord(c) < 128 for c in p), len(p)))
    return REGIONAL_ALIAS_MAP.get(chosen, chosen)


def parse_edition_metadata(item: dict[str, Any]) -> dict[str, Any]:
    raw_title = str(item.get("title") or "")
    lower = raw_title.lower()
    region = str(item.get("region") or "Sin región")
    ext = str(item.get("ext") or "").lower()

    is_bad = bool(re.search(r"\[b[0-9a-z]*\]", lower))
    is_pirate = "(pirate)" in lower
    is_unl = "(unl)" in lower
    is_proto = "(proto" in lower or "(seganet)" in lower
    is_beta = "(beta" in lower or "(sample" in lower or "(demo" in lower or "(aftermarket" in lower
    is_m3u = ext == "m3u"

    disc_match = re.search(r"\b(?:disc|disk|cd)\s*(\d+)\b", lower)
    disc_num = int(disc_match.group(1)) if disc_match else 0

    rev_rank = 0
    rev_m = re.search(r"\(rev\s*([a-z0-9]+)\)", lower)
    if rev_m:
        tok = rev_m.group(1).upper()
        rev_rank = 20 + (ord(tok[0]) - ord("A") + 1 if tok[0].isalpha() else int(tok) if tok.isdigit() else 1)
    ver_m = re.search(r"\(v(\d+)(?:\.(\d+))?\)", lower)
    if ver_m:
        rev_rank = max(rev_rank, int(ver_m.group(1)) * 10 + int(ver_m.group(2) or 0))
    if "[!]" in lower:
        rev_rank += 2

    has_es = bool(item.get("hasSpanishPatch")) or bool(re.search(r"\((?:spain|es|en,[^)]*\bes\b)\b", lower))

    tags: list[str] = []
    if region and region != "Sin región":
        tags.append(region)
    if rev_m:
        tags.append(f"Rev {rev_m.group(1).upper()}")
    elif ver_m:
        tags.append(f"v{ver_m.group(1)}.{ver_m.group(2) or '0'}")
    if disc_num > 0:
        tags.append(f"Disco {disc_num}")
    elif is_m3u:
        tags.append("Multi-Disco (.m3u)")
    if item.get("hasSpanishPatch") or "[ES]" in raw_title.upper() or "_esp" in lower:
        tags.append("[ES]")
    elif has_es and "Spain" not in tags:
        tags.append("Inc. Español")
    if is_beta:
        tags.append("Beta / Demo")
    if is_proto:
        tags.append("Prototipo")
    if is_pirate:
        tags.append("Pirata")
    elif is_unl:
        tags.append("Unlicensed")
    if is_bad:
        tags.append("Dump [b]")
    if "[!]" in lower:
        tags.append("Verificada [!]")

    edition_label = " · ".join(tags) if tags else (f".{ext.upper()}" if ext else "Edición estándar")

    return {
        "editionLabel": edition_label,
        "isBeta": is_beta,
        "isProto": is_proto,
        "isPirate": is_pirate,
        "isUnl": is_unl,
        "isBadDump": is_bad,
        "isMultiDisc": is_m3u or disc_num > 0,
        "discNumber": disc_num,
        "isPlaylistM3u": is_m3u,
        "hasSpanishLang": has_es,
        "revisionRank": rev_rank,
    }


def _region_priority(region: str) -> int:
    r = (region or "").lower()
    if r == "world":
        return 6
    if r == "usa":
        return 5
    if r == "spain":
        return 4
    if r == "europe":
        return 3
    if r in ("multi", "brazil", "france", "germany"):
        return 2
    if r == "japan":
        return 1
    return 0


def _score_variant(item: dict[str, Any], meta: dict[str, Any]) -> tuple[Any, ...]:
    ext = str(item.get("ext") or "").lower()
    raw = str(item.get("title") or "").lower()
    # Clean final retail titles beat special wrappers like "Virtual Console" / "Switch Online" slightly unless ES
    is_reissue = any(tag in raw for tag in ("virtual console", "switch online", "classic mini", "oz "))
    return (
        not meta["isBadDump"],
        not meta["isPirate"],
        not meta["isProto"],
        not meta["isBeta"],
        not meta["isUnl"],
        meta["isPlaylistM3u"],
        meta["discNumber"] in (0, 1),
        meta["hasSpanishLang"],
        _region_priority(str(item.get("region") or "")),
        meta["revisionRank"],
        ext not in ("zip", "7z", "rar"),
        not is_reissue,
        bool(item.get("isVerified")),
        bool(item.get("hasCover")),
        int(item.get("playCount") or 0),
        bool(item.get("favorite")),
        -len(raw),
    )


def _variant_summary(item: dict[str, Any], meta: dict[str, Any], is_primary: bool) -> dict[str, Any]:
    return {
        "entryId": item["entryId"],
        "title": item["title"],
        "cleanTitle": item.get("cleanTitle") or item["title"],
        "platform": item.get("platform") or "",
        "consoleKey": item.get("consoleKey") or "other",
        "relativePath": item.get("relativePath") or "",
        "ext": item.get("ext") or "",
        "region": item.get("region") or "Sin región",
        "size": int(item.get("size") or 0),
        "editionLabel": meta["editionLabel"],
        "isPrimary": is_primary,
        "isBeta": meta["isBeta"],
        "isProto": meta["isProto"],
        "isPirate": meta["isPirate"],
        "isBadDump": meta["isBadDump"],
        "discNumber": meta["discNumber"],
        "hasCover": bool(item.get("hasCover")),
        "coverUrl": item.get("coverUrl"),
        "hasSpanishPatch": bool(item.get("hasSpanishPatch")),
        "patchCount": int(item.get("patchCount") or 0),
        "favorite": bool(item.get("favorite")),
        "isVerified": bool(item.get("isVerified")),
        "expectedSha256": item.get("expectedSha256"),
        "playCount": int(item.get("playCount") or 0),
        "lastPlayedAt": item.get("lastPlayedAt"),
        "romset": item.get("romset"),
        "arcadeCore": item.get("arcadeCore"),
    }


class LibraryEngine:
    """Engine 1 — Deduplication, edition classification, and maintenance for the local ROM vault."""

    def get_library(self, raw_items: list[dict[str, Any]], grouped: bool = True) -> list[dict[str, Any]]:
        if not raw_items:
            return []

        if not grouped:
            enriched: list[dict[str, Any]] = []
            for it in raw_items:
                meta = parse_edition_metadata(it)
                canon_key = canonical_title_key(str(it.get("title") or ""), str(it.get("cleanTitle") or ""))
                variant_obj = _variant_summary(it, meta, True)
                enriched.append(
                    {
                        **it,
                        "canonicalKey": f"{it.get('consoleKey') or 'other'}::{canon_key}",
                        "editionLabel": meta["editionLabel"],
                        "editionFlags": meta,
                        "isMultiDisc": meta["isMultiDisc"],
                        "variantCount": 1,
                        "duplicateEntryIds": [it["entryId"]],
                        "variants": [variant_obj],
                    }
                )
            return enriched

        sys_items: list[dict[str, Any]] = []
        groups: dict[tuple[str, str], list[tuple[dict[str, Any], dict[str, Any]]]] = defaultdict(list)

        for it in raw_items:
            if it.get("isSys"):
                meta = parse_edition_metadata(it)
                sys_items.append(
                    {
                        **it,
                        "editionLabel": meta["editionLabel"],
                        "variantCount": 1,
                        "isMultiDisc": False,
                        "duplicateEntryIds": [it["entryId"]],
                        "variants": [],
                    }
                )
                continue
            ck = str(it.get("consoleKey") or "other").lower()
            canon = canonical_title_key(str(it.get("title") or ""), str(it.get("cleanTitle") or ""))
            group_key = (ck, canon or str(it["entryId"]))
            groups[group_key].append((it, parse_edition_metadata(it)))

        out: list[dict[str, Any]] = []
        for (ck, canon_key), candidates in groups.items():
            ranked = sorted(candidates, key=lambda pair: _score_variant(pair[0], pair[1]), reverse=True)
            primary_item, primary_meta = ranked[0]
            all_items = [p[0] for p in ranked]
            all_metas = [p[1] for p in ranked]

            is_multi_disc = any(m["discNumber"] > 0 or m["isPlaylistM3u"] for m in all_metas)
            any_fav = any(bool(x.get("favorite")) for x in all_items)
            total_plays = sum(int(x.get("playCount") or 0) for x in all_items)
            last_played = max((x.get("lastPlayedAt") or "" for x in all_items), default="") or None
            any_es_patch = any(bool(x.get("hasSpanishPatch")) for x in all_items)
            total_patches = sum(int(x.get("patchCount") or 0) for x in all_items)

            # Inherit cached cover/description from any sibling variant if primary doesn't yet have one
            cover_donor = next((x for x in all_items if x.get("hasCover") and x.get("coverUrl")), None)
            has_cover = bool(primary_item.get("hasCover")) or (cover_donor is not None)
            cover_url = primary_item.get("coverUrl") or (cover_donor.get("coverUrl") if cover_donor else None)
            cover_source = primary_item.get("coverSource") or (cover_donor.get("coverSource") if cover_donor else None)

            desc_donor = next((x for x in all_items if x.get("description")), None)
            description = primary_item.get("description") or (desc_donor.get("description") if desc_donor else "")
            release_year = primary_item.get("releaseYear") or (desc_donor.get("releaseYear") if desc_donor else "")
            genre = primary_item.get("genre") or (desc_donor.get("genre") if desc_donor else "")
            developer = primary_item.get("developer") or (desc_donor.get("developer") if desc_donor else "")
            desc_source = primary_item.get("descriptionSource") or (desc_donor.get("descriptionSource") if desc_donor else "")

            variants = [
                _variant_summary(it, meta, is_primary=(idx == 0))
                for idx, (it, meta) in enumerate(ranked)
            ]

            merged = {
                **primary_item,
                "canonicalKey": f"{ck}::{canon_key}",
                "editionLabel": primary_meta["editionLabel"],
                "editionFlags": primary_meta,
                "isMultiDisc": is_multi_disc,
                "variantCount": len(variants),
                "duplicateEntryIds": [it["entryId"] for it in all_items],
                "variants": variants,
                "favorite": any_fav,
                "playCount": total_plays,
                "lastPlayedAt": last_played,
                "hasSpanishPatch": any_es_patch,
                "patchCount": total_patches,
                "hasCover": has_cover,
                "coverUrl": cover_url,
                "coverSource": cover_source,
                "description": description,
                "releaseYear": release_year,
                "genre": genre,
                "developer": developer,
                "descriptionSource": desc_source,
            }
            out.append(merged)

        out.sort(key=lambda it: str(it.get("cleanTitle") or it.get("title") or "").lower())
        return out + sys_items

    def find_group_for_entry(self, entry_id: str, grouped_items: list[dict[str, Any]]) -> dict[str, Any] | None:
        for it in grouped_items:
            if it.get("entryId") == entry_id:
                return it
            if entry_id in (it.get("duplicateEntryIds") or ()):
                return it
        return None

    def get_maintenance_report(self, raw_items: list[dict[str, Any]]) -> dict[str, Any]:
        grouped = self.get_library(raw_items, grouped=True)
        playable_raw = [it for it in raw_items if not it.get("isSys")]
        sys_raw = [it for it in raw_items if it.get("isSys")]
        unique_playable = [it for it in grouped if not it.get("isSys")]

        betas_protos = 0
        pirates_unl = 0
        for it in playable_raw:
            meta = parse_edition_metadata(it)
            if meta["isBeta"] or meta["isProto"]:
                betas_protos += 1
            if meta["isPirate"] or meta["isUnl"] or meta["isBadDump"]:
                pirates_unl += 1

        raw_counts = Counter(str(it.get("consoleKey") or "other") for it in playable_raw)
        uniq_counts = Counter(str(it.get("consoleKey") or "other") for it in unique_playable)
        by_console = []
        for ck, raw_n in sorted(raw_counts.items(), key=lambda kv: kv[1], reverse=True):
            uniq_n = uniq_counts.get(ck, 0)
            cmeta = CONSOLES.get(ck, {"label": ck.upper(), "full": ck.upper(), "color": "#7DD3FC"})
            by_console.append(
                {
                    "consoleKey": ck,
                    "label": cmeta["label"],
                    "full": cmeta["full"],
                    "color": cmeta["color"],
                    "rawCount": raw_n,
                    "uniqueCount": uniq_n,
                    "collapsedCount": max(0, raw_n - uniq_n),
                }
            )

        top_duplicates = [
            {
                "entryId": it["entryId"],
                "title": it.get("cleanTitle") or it["title"],
                "consoleKey": it.get("consoleKey"),
                "variantCount": it["variantCount"],
                "isMultiDisc": it.get("isMultiDisc", False),
                "editions": [v["editionLabel"] for v in it.get("variants", [])[:6]],
            }
            for it in sorted(
                (x for x in unique_playable if int(x.get("variantCount") or 1) > 1),
                key=lambda x: int(x.get("variantCount") or 1),
                reverse=True,
            )[:25]
        ]

        return {
            "rawPlayableCount": len(playable_raw),
            "uniqueCount": len(unique_playable),
            "collapsedDuplicates": max(0, len(playable_raw) - len(unique_playable)),
            "systemArtifactsCount": len(sys_raw),
            "multiDiscGroups": sum(1 for x in unique_playable if x.get("isMultiDisc")),
            "betaProtoCount": betas_protos,
            "pirateUnlCount": pirates_unl,
            "missingCoversCount": sum(1 for x in unique_playable if not x.get("hasCover")),
            "byConsole": by_console,
            "topDuplicateGroups": top_duplicates,
        }

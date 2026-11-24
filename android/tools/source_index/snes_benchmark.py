#!/usr/bin/env python3
"""Metadata-only cartridge benchmark normalizer, not Oz's canonical knowledge engine.

Filename-derived values are provisional provider evidence. They must never be
treated as authoritative cross-platform taxonomy or regional exclusivity.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
import time
import tracemalloc
from collections import Counter
from pathlib import Path

from builder import MinervaAdapter, build, canonical_json

UNKNOWN = "UNKNOWN"
REGION_PARTS = {
    "argentina", "asia", "australia", "brazil", "canada", "china", "europe", "france",
    "germany", "hong kong", "italy", "japan", "korea", "mexico", "netherlands", "russia",
    "spain", "sweden", "taiwan", "uk", "usa", "world",
}
LANGUAGE_CODES = {
    "ar": "Arabic", "da": "Danish", "de": "German", "en": "English",
    "es": "Spanish", "fi": "Finnish", "fr": "French", "it": "Italian",
    "ja": "Japanese", "ko": "Korean", "nl": "Dutch", "no": "Norwegian",
    "pl": "Polish", "pt": "Portuguese", "ru": "Russian", "sv": "Swedish",
    "zh": "Chinese",
}


class BencodeDecoder:
    def __init__(self, raw: bytes):
        self.raw = raw
        self.position = 0

    def decode(self):
        marker = self.raw[self.position:self.position + 1]
        if marker == b"i":
            self.position += 1
            end = self.raw.index(b"e", self.position)
            value = int(self.raw[self.position:end])
            self.position = end + 1
            return value
        if marker == b"l":
            self.position += 1
            values = []
            while self.raw[self.position:self.position + 1] != b"e":
                values.append(self.decode())
            self.position += 1
            return values
        if marker == b"d":
            self.position += 1
            values = {}
            while self.raw[self.position:self.position + 1] != b"e":
                key = self.decode()
                values[key] = self.decode()
            self.position += 1
            return values
        colon = self.raw.index(b":", self.position)
        length = int(self.raw[self.position:colon])
        self.position = colon + 1
        value = self.raw[self.position:self.position + length]
        self.position += length
        return value


def parse_torrent(raw: bytes) -> tuple[dict, bytes]:
    decoder = BencodeDecoder(raw)
    if raw[:1] != b"d":
        raise ValueError("Torrent root must be a dictionary")
    decoder.position = 1
    root = {}
    info_bytes = None
    while raw[decoder.position:decoder.position + 1] != b"e":
        key = decoder.decode()
        start = decoder.position
        value = decoder.decode()
        root[key] = value
        if key == b"info":
            info_bytes = raw[start:decoder.position]
    decoder.position += 1
    if decoder.position != len(raw) or info_bytes is None:
        raise ValueError("Torrent metadata is malformed or missing info")
    return root, info_bytes


def text(value: bytes) -> str:
    return value.decode("utf-8", errors="strict")


def torrent_files(path: Path, expected_info_hash: str) -> tuple[list[dict], list[str]]:
    root, info_bytes = parse_torrent(path.read_bytes())
    actual = hashlib.sha1(info_bytes).hexdigest()
    if actual != expected_info_hash.casefold():
        raise ValueError(f"Torrent infohash mismatch: expected {expected_info_hash}, got {actual}")
    info = root[b"info"]
    root_name = text(info[b"name"])
    files = []
    for index, entry in enumerate(info.get(b"files", [])):
        components = [text(value) for value in entry[b"path"]]
        files.append({"fileIndex": index, "exactFullPath": "/".join([root_name, *components]),
                      "canonicalFileName": components[-1], "expectedSize": entry[b"length"]})
    trackers = []
    if b"announce" in root:
        trackers.append(text(root[b"announce"]))
    for tier in root.get(b"announce-list", []):
        for tracker in tier:
            value = text(tracker)
            if value not in trackers:
                trackers.append(value)
    return files, trackers


def canonical_metadata(filename: str, collection_type: str, platform: str = "snes") -> dict:
    stem = filename[:-4] if filename.casefold().endswith(".zip") else filename
    title = stem.split(" (")[0].strip()
    tags = re.findall(r"\(([^()]*)\)", stem)
    bracket_tags = re.findall(r"\[([^\]]+)\]", stem)
    # T-En is currently a deferred GBA source. Keep frozen non-GBA shards
    # byte/identity stable until their own translation contract is authorized.
    translation_tags = [tag for tag in bracket_tags if tag.casefold().startswith("t-en")] if platform == "gba" else []
    region = next((tag for tag in tags if all(
        part.strip().casefold() in REGION_PARTS for part in tag.split(",")
    )), UNKNOWN)
    languages = sorted({
        LANGUAGE_CODES[part.strip().casefold()]
        for tag in tags
        for part in tag.split(",")
        if all(piece.strip().casefold() in LANGUAGE_CODES for piece in tag.split(","))
    })
    revision = next((tag for tag in tags if re.search(r"^(rev(?:ision)?\b|v?\d+[.]\d+)", tag, re.I)), None)
    prototype_tokens = ("prototype", "proto") if platform == "gba" else ("prototype",)
    release_type = "aftermarket" if collection_type == "aftermarket" else next(
        (kind for kind in ("aftermarket", "homebrew", *prototype_tokens, "beta", "demo", "unreleased")
         if any(kind in tag.casefold() for tag in tags)), "retail")
    if release_type == "proto":
        release_type = "prototype"
    normalized_parts = [" ".join(value.casefold().split()) for value in [title, *tags]]
    # Bracketed T-En metadata is release identity, not Game identity. Preserve
    # every explicit marker so translator/version/retranslation variants cannot
    # collapse with the original release or with one another.
    if translation_tags:
        normalized_parts.extend(
            " ".join(value.casefold().split()) for value in bracket_tags
        )
    publication = "AFTERMARKET" if collection_type == "aftermarket" else release_type.upper()
    if publication not in {"RETAIL", "AFTERMARKET", "HOMEBREW", "PROTOTYPE", "BETA", "DEMO", "UNRELEASED"}:
        publication = "UNKNOWN"
    return {
        "gameTitle": title,
        "canonicalTitleKey": normalized_parts[0],
        "canonicalReleaseKey": platform + "|" + "|".join(normalized_parts),
        "region": region,
        "revision": revision,
        "releaseType": release_type,
        "releaseFacets": {
            "regions": [] if region == UNKNOWN else [region],
            "languages": languages,
            "publicationClass": publication,
            "modificationClass": "TRANSLATION" if translation_tags else (
                "ORIGINAL" if publication in {"RETAIL", "AFTERMARKET"} else "UNKNOWN"
            ),
            "translation": {"sourceLanguage": None, "targetLanguages": [], "status": "UNKNOWN"},
            "regionalExclusivity": {"status": "UNKNOWN", "region": None},
            "classificationEvidence": [{
                "source": "MINERVA_CANONICAL_FILENAME",
                "status": "PROVIDER_DERIVED",
                "value": stem,
            }],
            "evidenceStatus": "PROVIDER_DERIVED",
        },
    }


def legacy_prefix_collision_risks(shard: dict) -> list[dict]:
    """Reproduce the removed weak-title predicate for diagnostic exposure only."""
    games = shard["games"]
    result = []
    for target in games:
        matches = [
            candidate for candidate in games
            if candidate["gameId"] != target["gameId"]
            and candidate["canonicalTitleKey"].startswith(target["canonicalTitleKey"] + " ")
        ]
        if matches:
            result.append({
                "targetGameId": target["gameId"],
                "targetTitle": target["canonicalTitle"],
                "targetTitleKey": target["canonicalTitleKey"],
                "exposedDifferentGames": len(matches),
                "examples": [
                    {"gameId": item["gameId"], "title": item["canonicalTitle"]}
                    for item in sorted(matches, key=lambda value: value["canonicalTitleKey"])[:20]
                ],
            })
    return sorted(result, key=lambda item: (-item["exposedDifferentGames"], item["targetTitleKey"]))


def overlap_warnings(shard: dict) -> list[dict]:
    releases = {item["releaseId"]: item for item in shard["releases"]}
    games = {item["gameId"]: item for item in shard["games"]}
    by_release: dict[str, list[dict]] = {}
    for candidate in shard["candidates"]:
        by_release.setdefault(candidate["releaseId"], []).append(candidate)
    result = []
    for release_id, candidates in by_release.items():
        if len(candidates) < 2:
            continue
        release = releases[release_id]
        game = games[release["gameId"]]
        result.append({
            "releaseId": release_id,
            "gameId": release["gameId"],
            "title": game["canonicalTitle"],
            "canonicalReleaseKey": release["canonicalReleaseKey"],
            "candidateCount": len(candidates),
            "candidates": [{
                "candidateId": item["candidateId"],
                "collection": item["collection"],
                "infoHash": item["torrent"]["infoHash"],
                "fileIndex": item["torrent"]["fileIndex"],
                "exactFullPath": item["torrent"]["exactFullPath"],
            } for item in sorted(candidates, key=lambda value: value["candidateId"])],
        })
    return sorted(result, key=lambda item: item["releaseId"])


def records_for_collection(path: Path, *, collection: str, collection_type: str, info_hash: str,
                           artifact_path: str, observed_at: str, platform: str = "snes",
                           header_state: str = "unknown") -> list[dict]:
    files, trackers = torrent_files(path, info_hash)
    records = []
    for item in files:
        identity = canonical_metadata(item["canonicalFileName"], collection_type, platform)
        records.append({
            "platform": platform, **identity, "aliases": [], "headerState": header_state,
            **item, "hashScope": "UNKNOWN", "artifactContentRelation": "WRAPPED",
            "providerItemId": f"{info_hash}:{item['fileIndex']}", "collection": collection,
            "providerClassifications": [collection, f"collection-type:{collection_type}"],
            "infoHash": info_hash, "torrentArtifactStrategy": "MINERVA_ASSET_PATH",
            "torrentArtifactPath": artifact_path, "trackerUrls": trackers,
            "firstSeen": observed_at, "lastSeen": observed_at, "sourceFreshness": "metadata-verified",
        })
    return records


def benchmark(spec_path: Path, output: Path) -> dict:
    started = time.perf_counter()
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    platform = spec.get("platform", "snes")
    records = []
    for source in spec["collections"]:
        records.extend(records_for_collection(
            spec_path.parent / source["torrentFile"], collection=source["collection"],
            collection_type=source["collectionType"], info_hash=source["infoHash"],
            artifact_path=source["torrentArtifactPath"], observed_at=spec["observedAt"],
            platform=platform, header_state=source.get("headerState", "unknown"),
        ))
    normalized_input = output / "normalized-input.json"
    output.mkdir(parents=True, exist_ok=True)
    normalized_input.write_bytes(canonical_json({"records": records}))
    first = output / "generation-a"
    second = output / "generation-b"
    manifest_a = build(normalized_input, first, spec["observedAt"], platform)
    build(normalized_input, second, spec["observedAt"], platform)
    shard_a = first / f"indexes/{platform}.json.gz"
    shard_b = second / f"indexes/{platform}.json.gz"
    deterministic = shard_a.read_bytes() == shard_b.read_bytes()
    if not deterministic:
        raise ValueError("Deterministic rebuild mismatch")

    tracemalloc.start()
    open_started = time.perf_counter_ns()
    with gzip.open(shard_a, "rb") as stream:
        shard = json.load(stream)
    open_ms = (time.perf_counter_ns() - open_started) / 1_000_000
    index = {item["candidateId"]: item for item in shard["candidates"]}
    lookup_id = shard["candidates"][len(shard["candidates"]) // 2]["candidateId"]
    first_started = time.perf_counter_ns()
    found = index.get(lookup_id)
    first_lookup_ms = (time.perf_counter_ns() - first_started) / 1_000_000
    repeated_started = time.perf_counter_ns()
    for _ in range(10_000):
        index.get(lookup_id)
    repeated_total_ms = (time.perf_counter_ns() - repeated_started) / 1_000_000
    _, peak_memory = tracemalloc.get_traced_memory()
    tracemalloc.stop()
    if found is None:
        raise ValueError("Exact host lookup failed")

    warnings = len(records) - len(shard["releases"])
    uncompressed = gzip.decompress(shard_a.read_bytes())
    region_distribution = Counter(
        region for release in shard["releases"]
        for region in release.get("facets", {}).get("regions", [])
    )
    publication_distribution = Counter(
        release.get("facets", {}).get("publicationClass", UNKNOWN) for release in shard["releases"]
    )
    modification_distribution = Counter(
        release.get("facets", {}).get("modificationClass", UNKNOWN) for release in shard["releases"]
    )
    language_distribution = Counter(
        language for release in shard["releases"]
        for language in release.get("facets", {}).get("languages", [])
    )
    unknown_counts = {
        "regionUnknownReleases": sum(not item.get("facets", {}).get("regions") for item in shard["releases"]),
        "languageUnknownReleases": sum(not item.get("facets", {}).get("languages") for item in shard["releases"]),
        "publicationUnknownReleases": sum(item.get("facets", {}).get("publicationClass") == UNKNOWN for item in shard["releases"]),
        "modificationUnknownReleases": sum(item.get("facets", {}).get("modificationClass") == UNKNOWN for item in shard["releases"]),
        "exclusivityUnknownReleases": sum(item.get("facets", {}).get("regionalExclusivity", {}).get("status") == UNKNOWN for item in shard["releases"]),
        "contentHashScopeUnknownReleases": sum(item.get("contentIdentity", {}).get("hashScope") == UNKNOWN for item in shard["releases"]),
        "artifactHashScopeUnknownCandidates": sum(item.get("artifact", {}).get("hashScope") == UNKNOWN for item in shard["candidates"]),
    }
    overlaps = overlap_warnings(shard)
    collision_risks = legacy_prefix_collision_risks(shard)
    (output / "overlap-warnings.json").write_bytes(canonical_json(overlaps))
    (output / "collision-risk.json").write_bytes(canonical_json(collision_risks))
    extension_distribution = Counter(
        Path(record["canonicalFileName"]).suffix.casefold() or "<none>" for record in records
    )
    candidates_per_release = Counter(item["releaseId"] for item in shard["candidates"])
    result = {
        "scope": spec.get("scope", f"{platform.upper()}_BASE_PLUS_AFTERMARKET"),
        "platform": platform, "inputCollections": len(spec["collections"]),
        "inputFileRecords": len(records), "games": len(shard["games"]),
        "releases": len(shard["releases"]), "candidates": len(shard["candidates"]),
        "identityOverlapWarnings": warnings,
        "buildTimeMs": manifest_a["buildTimeMs"],
        "totalBenchmarkMs": round((time.perf_counter() - started) * 1000, 3),
        "jsonBytes": manifest_a["platforms"][platform]["uncompressedBytes"],
        "gzipBytes": manifest_a["platforms"][platform]["compressedBytes"],
        "compressionRatio": round(len(shard_a.read_bytes()) / len(uncompressed), 6),
        "manifestBytes": (first / "manifest.json").stat().st_size,
        "deterministicRebuild": deterministic,
        "shardSha256": hashlib.sha256(shard_a.read_bytes()).hexdigest(),
        "uncompressedSha256": hashlib.sha256(uncompressed).hexdigest(),
        "regions": dict(sorted(region_distribution.items())),
        "languages": dict(sorted(language_distribution.items())),
        "publicationClasses": dict(sorted(publication_distribution.items())),
        "modificationClasses": dict(sorted(modification_distribution.items())),
        "unknownCounts": unknown_counts,
        "extensions": dict(sorted(extension_distribution.items())),
        "singleCandidateReleases": sum(count == 1 for count in candidates_per_release.values()),
        "multipleCandidateReleases": sum(count > 1 for count in candidates_per_release.values()),
        "overlapWarningArtifact": "overlap-warnings.json",
        "legacyPrefixExposureGroups": len(collision_risks),
        "legacyPrefixLargestGroup": max((item["exposedDifferentGames"] + 1 for item in collision_risks), default=0),
        "collisionRiskArtifact": "collision-risk.json",
        "openMs": round(open_ms, 3), "firstExactLookupMs": round(first_lookup_ms, 6),
        "repeatedLookupCount": 10_000, "repeatedLookupTotalMs": round(repeated_total_ms, 3),
        "repeatedLookupAverageMs": round(repeated_total_ms / 10_000, 6),
        "peakTracedMemoryBytes": peak_memory,
        "hashScope": "UNKNOWN", "artifactContentRelation": "WRAPPED",
    }
    (output / "benchmark.json").write_bytes(canonical_json(result))
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(benchmark(args.spec, args.output), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()

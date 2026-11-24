#!/usr/bin/env python3
"""Deterministic Oz Source Index v1 builder and health gate."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

SCHEMA_VERSION = 1
BUILDER_VERSION = "source-index-v1.0"
HASH_SCOPES = {"ROM_CONTENT", "DISTRIBUTED_ARTIFACT", "UNKNOWN"}
ARTIFACT_CONTENT_RELATIONS = {"WRAPPED", "CONTENT_IDENTICAL", "UNKNOWN"}
PUBLICATION_CLASSES = {"RETAIL", "AFTERMARKET", "HOMEBREW", "PROTOTYPE", "BETA", "DEMO", "UNRELEASED", "UNKNOWN"}
MODIFICATION_CLASSES = {"ORIGINAL", "TRANSLATION", "ROM_HACK", "MODIFIED", "UNKNOWN"}
EXCLUSIVITY_STATES = {"CONFIRMED_EXCLUSIVE", "NOT_EXCLUSIVE", "UNKNOWN"}
EVIDENCE_STATUSES = {"VERIFIED", "CURATED", "PROVIDER_DERIVED", "UNKNOWN"}
HASH_LENGTHS = {"crc32": 8, "md5": 32, "sha1": 40, "sha256": 64}


def canonical_json(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def stable_id(namespace: str, *parts: object) -> str:
    encoded = "|".join(f"{len(str(part))}:{part}" for part in parts)
    return hashlib.sha256(f"{len(namespace)}:{namespace}|{encoded}".encode()).hexdigest()


def normalized(value: str) -> str:
    return " ".join(value.strip().casefold().split())


def hash_values(value: dict) -> dict:
    result = {name: value.get(name) for name in HASH_LENGTHS}
    for name, digest in result.items():
        if digest is not None and (not isinstance(digest, str) or not re.fullmatch(rf"[0-9a-fA-F]{{{HASH_LENGTHS[name]}}}", digest)):
            raise ValueError(f"Malformed {name} digest")
    return result


def validate_hash_evidence(scope: str, value: dict, label: str) -> dict:
    if scope not in HASH_SCOPES:
        raise ValueError(f"Unknown {label} hash scope")
    hashes = hash_values(value)
    present = any(hashes.values())
    if scope == "UNKNOWN" and present:
        raise ValueError(f"{label} hashes require an explicit scope")
    if scope in {"ROM_CONTENT", "DISTRIBUTED_ARTIFACT"} and not present:
        raise ValueError(f"{scope} scope requires actual {label} hash evidence")
    return {"hashScope": scope, **hashes}


def release_facets(source: dict) -> dict:
    facets = source.get("releaseFacets") or {}
    publication = facets.get("publicationClass", "UNKNOWN")
    modification = facets.get("modificationClass", "UNKNOWN")
    exclusivity = facets.get("regionalExclusivity", {}).get("status", "UNKNOWN")
    evidence_status = facets.get("evidenceStatus", "UNKNOWN")
    if publication not in PUBLICATION_CLASSES or modification not in MODIFICATION_CLASSES:
        raise ValueError("Unknown release classification")
    if exclusivity not in EXCLUSIVITY_STATES or evidence_status not in EVIDENCE_STATUSES:
        raise ValueError("Unknown classification evidence semantics")
    translation = facets.get("translation") or {}
    return {
        "regions": sorted(set(facets.get("regions", []))),
        "languages": sorted(set(facets.get("languages", []))),
        "publicationClass": publication,
        "modificationClass": modification,
        "translation": {
            "sourceLanguage": translation.get("sourceLanguage"),
            "targetLanguages": sorted(set(translation.get("targetLanguages", []))),
            "status": translation.get("status", "UNKNOWN"),
        },
        "regionalExclusivity": {
            "status": exclusivity,
            "region": facets.get("regionalExclusivity", {}).get("region"),
        },
        "classificationEvidence": sorted(facets.get("classificationEvidence", []), key=canonical_json),
        "evidenceStatus": evidence_status,
    }


def merge_content_identity(current: dict, incoming: dict) -> dict:
    merged = dict(current)
    scopes = {current["hashScope"], incoming["hashScope"]} - {"UNKNOWN"}
    if len(scopes) > 1:
        raise ValueError("Contradictory canonical content hash scopes")
    merged["hashScope"] = next(iter(scopes), "UNKNOWN")
    for name in HASH_LENGTHS:
        left, right = current.get(name), incoming.get(name)
        if left and right and left.casefold() != right.casefold():
            raise ValueError(f"Contradictory ROM_CONTENT {name} evidence")
        merged[name] = left or right
    return merged


def merge_facets(current: dict, incoming: dict) -> dict:
    merged = dict(current)
    merged["regions"] = sorted(set(current["regions"]) | set(incoming["regions"]))
    merged["languages"] = sorted(set(current["languages"]) | set(incoming["languages"]))
    merged["classificationEvidence"] = sorted(
        {canonical_json(item): item for item in current["classificationEvidence"] + incoming["classificationEvidence"]}.values(),
        key=canonical_json,
    )
    # Normalized truth never changes silently when two candidates disagree.
    for field in ("publicationClass", "modificationClass", "translation", "regionalExclusivity"):
        if current[field] != incoming[field]:
            merged[field] = "UNKNOWN" if field in {"publicationClass", "modificationClass"} else {
                "sourceLanguage": None, "targetLanguages": [], "status": "UNKNOWN"
            } if field == "translation" else {"status": "UNKNOWN", "region": None}
    if current["evidenceStatus"] != incoming["evidenceStatus"]:
        merged["evidenceStatus"] = "UNKNOWN"
    return merged


class SourceProviderAdapter(Protocol):
    provider: str

    def records(self) -> list[dict]: ...


class MinervaAdapter:
    provider = "minerva"

    def __init__(self, input_path: Path):
        self.input_path = input_path

    def records(self) -> list[dict]:
        raw = json.loads(self.input_path.read_text(encoding="utf-8"))
        result = []
        for item in raw["records"]:
            required = ("canonicalTitleKey", "canonicalReleaseKey", "canonicalFileName", "region", "expectedSize", "hashScope", "artifactContentRelation", "collection", "infoHash", "fileIndex", "exactFullPath")
            missing = [field for field in required if item.get(field) in (None, "")]
            if missing:
                raise ValueError(f"MiNERVA record missing {missing}")
            validate_hash_evidence(item["hashScope"], item, "artifact")
            if item["artifactContentRelation"] not in ARTIFACT_CONTENT_RELATIONS:
                raise ValueError("Unknown artifact/content relation")
            if item["artifactContentRelation"] == "WRAPPED" and item["hashScope"] == "ROM_CONTENT":
                raise ValueError("Wrapped artifact bytes cannot be ROM_CONTENT evidence")
            result.append(item)
        return result


def build_shard(adapters: list[SourceProviderAdapter], platform: str = "snes") -> dict:
    games: dict[str, dict] = {}
    releases: dict[str, dict] = {}
    candidates: dict[str, dict] = {}
    for adapter in adapters:
        for source in adapter.records():
            if source["platform"] != platform:
                continue
            relation = source.get("artifactContentRelation", "UNKNOWN")
            artifact_scope = source.get("hashScope", "UNKNOWN")
            if relation not in ARTIFACT_CONTENT_RELATIONS or artifact_scope not in HASH_SCOPES:
                raise ValueError("Unknown artifact/content evidence semantics")
            if relation == "WRAPPED" and artifact_scope == "ROM_CONTENT":
                raise ValueError("Wrapped artifact bytes cannot be ROM_CONTENT evidence")
            title_key = normalized(source["canonicalTitleKey"])
            game_id = stable_id("oz-game-v1-provisional-title", platform, title_key)
            game = {
                "gameId": game_id, "platform": platform, "canonicalTitle": source["gameTitle"],
                "canonicalTitleKey": title_key, "aliases": sorted(set(source.get("aliases", []))),
                "identityStatus": "PROVISIONAL_TITLE_KEY",
            }
            artifact_identity = validate_hash_evidence(artifact_scope, source, "artifact")
            content_hashes = source.get("contentHashes")
            if not content_hashes and relation == "CONTENT_IDENTICAL" and artifact_scope == "ROM_CONTENT":
                content_hashes = {
                    "scope": "ROM_CONTENT", "crc32": source.get("crc32"), "md5": source.get("md5"),
                    "sha1": source.get("sha1"), "sha256": source.get("sha256"),
                }
            content_scope = content_hashes.get("scope") if content_hashes else "UNKNOWN"
            if content_scope not in {"ROM_CONTENT", "UNKNOWN"}:
                raise ValueError("Canonical content hashes require ROM_CONTENT or UNKNOWN scope")
            content_identity = validate_hash_evidence(content_scope, content_hashes or {}, "content")
            verified_content = content_scope == "ROM_CONTENT" and any(content_identity[name] for name in ("crc32", "md5", "sha1", "sha256"))
            if relation == "CONTENT_IDENTICAL" and artifact_scope == "ROM_CONTENT":
                for name in ("crc32", "md5", "sha1", "sha256"):
                    artifact_value = artifact_identity.get(name)
                    content_value = content_identity.get(name)
                    if artifact_value and content_value and artifact_value.casefold() != content_value.casefold():
                        raise ValueError(f"Contradictory ROM_CONTENT {name} evidence")
            release_status = "VERIFIED_CONTENT" if verified_content else "PROVISIONAL_METADATA_KEY"
            canonical_release_key = normalized(source["canonicalReleaseKey"])
            release_id = stable_id("oz-release-v1", game_id, canonical_release_key)
            release = {
                "releaseId": release_id, "gameId": game_id, "platform": platform,
                "identityStatus": release_status, "canonicalReleaseKey": canonical_release_key,
                "region": source["region"], "revision": source.get("revision"),
                "releaseType": source.get("releaseType", "retail"),
                "headerState": source.get("headerState", "unknown"),
                "contentIdentity": content_identity,
                "facets": release_facets(source),
            }
            candidate_id = stable_id(
                "oz-source-candidate-v1", adapter.provider, "torrent",
                source["infoHash"].lower(), source["fileIndex"], source["exactFullPath"],
            )
            candidate = {
                "candidateId": candidate_id, "releaseId": release_id, "provider": adapter.provider,
                "transport": "torrent", "providerItemId": str(source["providerItemId"]),
                "collection": source["collection"], "artifact": {
                    "fileName": source["canonicalFileName"], "size": source["expectedSize"],
                    "contentRelation": source["artifactContentRelation"],
                    "hashScope": artifact_identity["hashScope"], "crc32": artifact_identity.get("crc32"),
                    "md5": artifact_identity.get("md5"), "sha1": artifact_identity.get("sha1"), "sha256": artifact_identity.get("sha256"),
                }, "firstSeen": source["firstSeen"],
                "lastSeen": source["lastSeen"], "sourceFreshness": source["sourceFreshness"],
                "providerClassifications": sorted(set(source.get("providerClassifications", [source["collection"]]))),
                "torrent": {
                    "infoHash": source["infoHash"].lower(), "fileIndex": source["fileIndex"],
                    "exactFullPath": source["exactFullPath"],
                    "artifactStrategy": source.get("torrentArtifactStrategy"),
                    "artifactPath": source.get("torrentArtifactPath"),
                    "trackerUrls": source.get("trackerUrls", []),
                },
            }
            games[game_id] = game
            if release_id in releases:
                existing = releases[release_id]
                existing["contentIdentity"] = merge_content_identity(existing["contentIdentity"], content_identity)
                existing["identityStatus"] = "VERIFIED_CONTENT" if existing["contentIdentity"]["hashScope"] == "ROM_CONTENT" else "PROVISIONAL_METADATA_KEY"
                existing["facets"] = merge_facets(existing["facets"], release["facets"])
            else:
                releases[release_id] = release
            candidates[candidate_id] = candidate
    return {
        "schemaVersion": SCHEMA_VERSION, "platform": platform,
        "games": sorted(games.values(), key=lambda value: value["gameId"]),
        "releases": sorted(releases.values(), key=lambda value: value["releaseId"]),
        "candidates": sorted(candidates.values(), key=lambda value: value["candidateId"]),
    }


def metrics(shard: dict) -> dict:
    releases = shard["releases"]
    candidates = shard["candidates"]
    count = len(releases)
    return {
        "gameCount": len(shard["games"]), "releaseCount": count, "candidateCount": len(candidates),
        "sha1Coverage": sum(bool(item["contentIdentity"].get("sha1")) for item in releases) / count if count else 0.0,
        "sha256Coverage": sum(bool(item["contentIdentity"].get("sha256")) for item in releases) / count if count else 0.0,
        "artifactSha256Coverage": sum(bool(item["artifact"].get("sha256")) for item in candidates) / len(candidates) if candidates else 0.0,
        "torrentCoordinateCoverage": sum(bool(item.get("torrent")) for item in candidates) / len(candidates) if candidates else 0.0,
        "httpCoordinateCoverage": sum(bool(item.get("http")) for item in candidates) / len(candidates) if candidates else 0.0,
        "providerCoverage": sorted({item["provider"] for item in candidates}),
    }


@dataclass(frozen=True)
class HealthThresholds:
    suspicious_drop: float = 0.20
    coverage_drop: float = 0.15


def health_gate(current: dict, previous: dict | None, critical_platforms: set[str], thresholds: HealthThresholds = HealthThresholds()) -> tuple[str, list[str]]:
    reasons: list[str] = []
    current_platforms = set(current["platforms"])
    missing = critical_platforms - current_platforms
    if missing:
        return "REJECT", [f"critical platforms missing: {sorted(missing)}"]
    for platform in critical_platforms:
        if current["platforms"][platform]["releaseCount"] == 0:
            return "REJECT", [f"{platform} release count is zero"]
    if previous is None:
        return "ACCEPT", reasons
    for field in ("gameCount", "releaseCount", "candidateCount"):
        old_total = previous.get("totals", {}).get(field, 0)
        new_total = current.get("totals", {}).get(field, 0)
        if old_total and new_total / old_total < 1 - thresholds.suspicious_drop:
            reasons.append(f"global {field} dropped more than {thresholds.suspicious_drop:.0%}")
    previous_platforms = set(previous["platforms"])
    disappeared = (previous_platforms & critical_platforms) - current_platforms
    if disappeared:
        return "REJECT", [f"critical platforms disappeared: {sorted(disappeared)}"]
    for platform, old in previous["platforms"].items():
        if platform not in current["platforms"]:
            continue
        new = current["platforms"][platform]
        if old["releaseCount"] and new["releaseCount"] / old["releaseCount"] < 1 - thresholds.suspicious_drop:
            reasons.append(f"{platform} release count dropped more than {thresholds.suspicious_drop:.0%}")
        for field in ("sha1Coverage", "sha256Coverage", "torrentCoordinateCoverage", "httpCoordinateCoverage"):
            if old.get(field, 0.0) - new.get(field, 0.0) > thresholds.coverage_drop:
                reasons.append(f"{platform} {field} collapsed")
    return ("QUARANTINE", reasons) if reasons else ("ACCEPT", reasons)


def gzip_deterministic(payload: bytes) -> bytes:
    buffer = io.BytesIO()
    with gzip.GzipFile(filename="", mode="wb", fileobj=buffer, mtime=0) as stream:
        stream.write(payload)
    return buffer.getvalue()


def build(input_path: Path, output: Path, generated_at: str, platform: str = "snes") -> dict:
    started = time.perf_counter()
    shard = build_shard([MinervaAdapter(input_path)], platform)
    shard_bytes = canonical_json(shard)
    compressed = gzip_deterministic(shard_bytes)
    shard_path = output / "indexes" / f"{platform}.json.gz"
    shard_path.parent.mkdir(parents=True, exist_ok=True)
    shard_path.write_bytes(compressed)
    platform_metrics = metrics(shard)
    generation_id = stable_id("oz-source-index-generation-v1", hashlib.sha256(shard_bytes).hexdigest())
    manifest = {
        "schemaVersion": SCHEMA_VERSION, "generationId": generation_id, "generatedAt": generated_at,
        "builderVersion": BUILDER_VERSION,
        "providers": [{"provider": "minerva", "freshness": max(item["lastSeen"] for item in shard["candidates"])}],
        "platforms": {platform: {**platform_metrics, "path": f"indexes/{platform}.json.gz", "compression": "gzip", "sha256": hashlib.sha256(compressed).hexdigest(), "uncompressedSha256": hashlib.sha256(shard_bytes).hexdigest(), "compressedBytes": len(compressed), "uncompressedBytes": len(shard_bytes)}},
        "totals": {"platformCount": 1, "gameCount": platform_metrics["gameCount"], "releaseCount": platform_metrics["releaseCount"], "candidateCount": platform_metrics["candidateCount"]},
        "health": {"status": "NOT_EVALUATED", "reasons": []},
        "signing": {"algorithm": "Ed25519", "keyId": None, "signature": None, "status": "NOT_CONFIGURED"},
    }
    (output / "manifest.json").write_bytes(canonical_json(manifest))
    manifest["buildTimeMs"] = round((time.perf_counter() - started) * 1000, 3)
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--generated-at", required=True)
    parser.add_argument("--platform", default="snes")
    args = parser.parse_args()
    print(json.dumps(build(args.input, args.output, args.generated_at, args.platform), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()

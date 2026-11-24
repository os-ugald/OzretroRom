#!/usr/bin/env python3
"""Metadata-only GBA collection classifier; Source Index building stays in builder.py."""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

from builder import canonical_json
from snes_benchmark import torrent_files

CLASSIFICATIONS = {
    "PRIMARY_CONTROLLED", "TRANSLATION", "AFTERMARKET", "SPECIALTY",
    "HACK/MODIFICATION", "PRIVATE", "RETROACHIEVEMENTS_NOISE", "UNKNOWN", "DEFERRED",
}


def classify_collection(name: str, group: str) -> str:
    lower = name.casefold()
    if group == "RetroAchievements" or lower.startswith("retroachievements - ra -") or lower.startswith("ra -"):
        return "RETROACHIEVEMENTS_NOISE"
    if "private" in lower:
        return "PRIVATE"
    if "t-en" in lower or group == "T-En Collection":
        return "TRANSLATION"
    if any(token in lower for token in ("hack", "modification", "rom hack")):
        return "HACK/MODIFICATION"
    if lower in {"nintendo - game boy advance", "no-intro - nintendo - game boy advance"}:
        return "PRIMARY_CONTROLLED"
    if lower in {
        "nintendo - game boy advance (aftermarket)",
        "no-intro - nintendo - game boy advance (aftermarket)",
    }:
        return "AFTERMARKET"
    if any(token in lower for token in ("e-reader", "multiboot", "play-yan", "video", "source code")):
        return "SPECIALTY"
    return "UNKNOWN"


def v1_disposition(classification: str) -> str:
    return "INCLUDED_V1" if classification in {"PRIMARY_CONTROLLED", "AFTERMARKET"} else "DEFERRED"


def naming_convention(file_names: list[str]) -> str:
    if not file_names:
        return "UNKNOWN"
    zip_count = sum(name.casefold().endswith(".zip") for name in file_names)
    tagged_count = sum(" (" in name for name in file_names)
    if zip_count == len(file_names) and tagged_count / len(file_names) >= 0.8:
        return "NO_INTRO_TITLE_WITH_PARENTHETICAL_TAGS_ZIP"
    if zip_count == len(file_names):
        return "ZIP_WRAPPED_PROVIDER_NAMES"
    return "MIXED_OR_UNKNOWN"


def exact_filename_overlaps(collection_files: dict[str, list[str]]) -> tuple[dict[str, dict[str, int]], list[dict]]:
    owners: dict[str, list[str]] = defaultdict(list)
    display: dict[str, str] = {}
    for collection, files in collection_files.items():
        for name in files:
            key = name.casefold()
            owners[key].append(collection)
            display.setdefault(key, name)
    per_collection: dict[str, Counter] = {name: Counter() for name in collection_files}
    warnings = []
    for key, collections in owners.items():
        distinct = sorted(set(collections))
        if len(distinct) < 2:
            continue
        warnings.append({"fileName": display[key], "collections": distinct})
        for left in distinct:
            for right in distinct:
                if left != right:
                    per_collection[left][right] += 1
    return ({name: dict(sorted(counts.items())) for name, counts in per_collection.items()},
            sorted(warnings, key=lambda item: item["fileName"].casefold()))


def inventory(spec_path: Path) -> tuple[list[dict], list[dict]]:
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    rows = []
    files_by_collection = {}
    for source in spec["collections"]:
        classification = classify_collection(source["collectionName"], source["categoryGroup"])
        if classification not in CLASSIFICATIONS:
            raise ValueError(f"Unknown GBA classification: {classification}")
        files, _ = torrent_files(spec_path.parent / source["torrentFile"], source["infoHash"])
        names = [item["canonicalFileName"] for item in files]
        files_by_collection[source["collectionName"]] = names
        rows.append({
            "collectionTitle": source["collectionName"],
            "provider": source.get("provider", "minerva"),
            "categoryGroup": source["categoryGroup"],
            "classification": classification,
            "v1Disposition": v1_disposition(classification),
            "infoHash": source["infoHash"].casefold(),
            "fileCount": len(files),
            "totalBytes": sum(item["expectedSize"] for item in files),
            "extensions": dict(sorted(Counter(
                Path(item["canonicalFileName"]).suffix.casefold() or "<none>" for item in files
            ).items())),
            "regionCharacteristics": source.get("regionCharacteristics", ["UNKNOWN"]),
            "regionEvidenceStatus": "PROVIDER_DERIVED",
            "languageEvidence": "UNKNOWN_UNLESS_EXPLICIT_FILENAME_TAG",
            "namingConvention": naming_convention(names),
            "torrentArtifactPath": source["torrentArtifactPath"],
        })
    overlaps, warnings = exact_filename_overlaps(files_by_collection)
    for row in rows:
        row["overlapWithCollections"] = overlaps[row["collectionTitle"]]
        row["overlapFileNameCount"] = sum(overlaps[row["collectionTitle"]].values())
    return sorted(rows, key=lambda item: item["collectionTitle"]), warnings


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows, warnings = inventory(args.spec)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "collection-inventory.json").write_bytes(canonical_json(rows))
    (args.output / "collection-overlap-warnings.json").write_bytes(canonical_json(warnings))
    print(json.dumps({"collections": len(rows), "overlapWarnings": len(warnings)}, sort_keys=True))


if __name__ == "__main__":
    main()

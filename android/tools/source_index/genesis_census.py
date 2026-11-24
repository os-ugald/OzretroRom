#!/usr/bin/env python3
"""Classify Genesis-adjacent dashboard collections without payload access."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def classify_collection(name: str, group: str) -> str:
    lower = name.casefold()
    if "32x" in lower:
        return "SEPARATE_SYSTEM"
    if "sega cd" in lower or "mega cd" in lower or "mode 1 cd" in lower:
        return "SEPARATE_MEDIA_GATE"
    if "msu-md" in lower or "md+" in lower:
        return "SEPARATE_MEDIA_GATE"
    if "t-en" in lower and "enhanced colors" not in lower:
        return "DEFERRED_TRANSLATION"
    if "enhanced colors" in lower:
        return "DEFERRED_SPECIALTY"
    if group == "RetroAchievements" or "private" in lower:
        return "EXCLUDED_PROVIDER_NOISE"
    if group == "No-Intro" and "mega drive - genesis" in lower:
        return "INCLUDED_V1"
    return "UNKNOWN_REQUIRES_REVIEW"


def inventory(collections: list[dict]) -> list[dict]:
    result = []
    for item in collections:
        name = item.get("collectionName", "")
        if not any(token in name.casefold() for token in ("mega drive", "genesis", "32x", "msu-md")):
            continue
        result.append({
            "collection": name,
            "categoryGroup": item.get("categoryGroup", "UNKNOWN"),
            "infoHash": item.get("torrentInfoHash", "UNKNOWN"),
            "payloadBytes": item.get("payloadBytes", "UNKNOWN"),
            "fileCount": "UNKNOWN",
            "classification": classify_collection(name, item.get("categoryGroup", "UNKNOWN")),
        })
    return sorted(result, key=lambda row: (row["classification"], row["categoryGroup"], row["collection"]))


def included_v1(rows: list[dict]) -> list[dict]:
    """Return only explicitly approved cartridge collections."""
    return [row for row in rows if row["classification"] == "INCLUDED_V1"]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--collections", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = inventory(json.loads(args.collections.read_text(encoding="utf-8")))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(rows, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"collections": len(rows), "output": str(args.output)}, sort_keys=True))


if __name__ == "__main__":
    main()

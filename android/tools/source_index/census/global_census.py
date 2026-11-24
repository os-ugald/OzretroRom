#!/usr/bin/env python3
"""Snapshot-only MiNERVA global coverage census. Performs no network or payload I/O."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import time
from collections import defaultdict
from pathlib import Path

from census import UNKNOWN, discover_all_torrents, parse_dashboard, platform_family, stable_collection_id

SCHEMA_VERSION = 1
TELEMETRY_SEMANTICS = "TELEMETRY_SNAPSHOT"

# Derived from app/src/main/assets/libretro_systems.json at the frozen Nintendo checkpoint.
OZ_SYSTEMS = {
    "arcade-fbneo", "atari-2600", "atari-5200", "atari-7800", "atari-jaguar", "atari-lynx",
    "wonderswan", "wonderswan-color", "colecovision", "commodore-64", "amiga", "msx", "msx2",
    "pc-engine", "pc-engine-cd", "pc-fx", "nes", "fds", "snes", "n64", "n64dd", "gb", "gbc",
    "gba", "nds", "dsi", "3ds", "gamecube", "wii", "pokemon-mini", "virtual-boy", "master-system",
    "genesis", "game-gear", "sega-32x", "sega-cd", "saturn", "dreamcast", "ngp", "ngpc", "ps1",
    "ps2", "psp", "scummvm",
}

# Route existence only. It is never interpreted as current ROMgi catalog content.
ROMGI_ROUTES = {
    "arcade-fbneo", "atari-2600", "atari-5200", "atari-7800", "atari-jaguar", "atari-lynx",
    "colecovision", "pc-engine", "pc-engine-cd", "pc-fx", "nes", "fds", "snes", "n64", "n64dd",
    "gb", "gbc", "gba", "nds", "dsi", "3ds", "gamecube", "wii", "pokemon-mini", "virtual-boy",
    "master-system", "genesis", "game-gear", "sega-32x", "sega-cd", "saturn", "dreamcast", "ps1",
    "ps2", "psp",
}


def collection_name(torrent: dict) -> str:
    name = torrent.get("name", UNKNOWN)
    prefix = "Minerva_Myrient - "
    return name[len(prefix):] if name.startswith(prefix) else name


def category_group(torrent: dict) -> str:
    value = torrent.get("category_group")
    return value.strip() if isinstance(value, str) and value.strip() else UNKNOWN


def classify_platform(name: str) -> tuple[str, str]:
    """Returns provider-neutral family and repository Oz system ID or UNKNOWN."""
    lower = name.casefold()
    rules = [
        ("arcade-fbneo", "arcade-fbneo", r"finalburn neo|\bfbneo\b"),
        ("arcade-mame", UNKNOWN, r"\bmame\b|hbmame"),
        ("sega-dreamcast", "dreamcast", r"dreamcast"),
        ("sega-saturn", "saturn", r"sega\s*-?\s*saturn|\bsaturn\b"),
        ("sega-cd", "sega-cd", r"mega[ -]?cd|sega cd"),
        ("sega-32x", "sega-32x", r"\b32x\b"),
        ("sega-game-gear", "game-gear", r"game gear"),
        ("sega-master-system", "master-system", r"master system|mark iii"),
        ("sega-genesis", "genesis", r"mega drive|genesis|msu-md"),
        ("sony-ps2", "ps2", r"playstation 2"),
        ("sony-psp", "psp", r"playstation portable|\bpsp\b"),
        ("sony-ps1", "ps1", r"playstation(?!\s*[2345])|ps one"),
        ("nec-pc-engine-cd", "pc-engine-cd", r"pc engine cd|turbografx[- ]cd"),
        ("nec-pc-fx", "pc-fx", r"pc-fx"),
        ("nec-pc-engine", "pc-engine", r"pc engine|turbografx[- ]?16"),
        ("snk-ngpc", "ngpc", r"neo geo pocket color"),
        ("snk-ngp", "ngp", r"neo geo pocket"),
        ("snk-neo-geo-cd", UNKNOWN, r"neo geo cd"),
        ("snk-neo-geo", UNKNOWN, r"neo[ -]?geo"),
        ("atari-2600", "atari-2600", r"atari 2600"),
        ("atari-5200", "atari-5200", r"atari 5200"),
        ("atari-7800", "atari-7800", r"atari 7800"),
        ("atari-jaguar", "atari-jaguar", r"atari jaguar"),
        ("atari-lynx", "atari-lynx", r"atari lynx"),
        ("bandai-wonderswan-color", "wonderswan-color", r"wonderswan color"),
        ("bandai-wonderswan", "wonderswan", r"wonderswan"),
        ("commodore-64", "commodore-64", r"commodore 64"),
        ("commodore-amiga", "amiga", r"commodore.*amiga|\bamiga\b"),
        ("microsoft-msx2", "msx2", r"\bmsx2\b"),
        ("microsoft-msx", "msx", r"\bmsx\b"),
        ("colecovision", "colecovision", r"colecovision"),
    ]
    for family, system, pattern in rules:
        if re.search(pattern, lower, re.I):
            return family, system
    nintendo = platform_family(name)
    nintendo_systems = {
        "nes": "nes", "fds": "fds", "snes": "snes", "n64": "n64", "n64dd": "n64dd",
        "gb": "gb", "gbc": "gbc", "gba": "gba", "virtualboy": "virtual-boy", "nds": "nds",
        "dsi": "dsi", "pokemonmini": "pokemon-mini", "3ds": "3ds", "gamecube": "gamecube",
        "wii": "wii", "wiiu": UNKNOWN,
    }
    if nintendo != "nintendo-other":
        return f"nintendo-{nintendo}", nintendo_systems.get(nintendo, UNKNOWN)
    return UNKNOWN, UNKNOWN


def emulation_capability(system_id: str) -> str:
    # Repository configuration proves a libretro route exists, not that Oz has product-certified it.
    return "LIBRETRO_AVAILABLE" if system_id in OZ_SYSTEMS else "RESEARCH_REQUIRED" if system_id == UNKNOWN else "UNAVAILABLE"


def romgi_evidence(system_id: str) -> str:
    return "ROUTE_EXISTS_CONTENT_UNKNOWN" if system_id in ROMGI_ROUTES else "NO_ROUTE_CONTENT_UNKNOWN" if system_id in OZ_SYSTEMS else "UNKNOWN"


def priority_for(system_id: str, capability: str, family: str) -> str:
    if capability == "LIBRETRO_AVAILABLE" and system_id not in ROMGI_ROUTES:
        return "PRIORITY_A"
    if capability == "LIBRETRO_AVAILABLE":
        return "PRIORITY_B"
    if family == UNKNOWN:
        return "NEEDS_RESEARCH"
    return "INVENTORY_ONLY"


def deep_scan(priority: str, system_id: str) -> bool:
    return priority == "PRIORITY_A" or system_id in {"snes", "genesis", "saturn", "ps1", "ngp", "ngpc", "arcade-fbneo"}


def normalize_torrent(torrent: dict, dashboard_timestamp: str) -> dict:
    name = collection_name(torrent)
    group = category_group(torrent)
    family, system_id = classify_platform(name)
    capability = emulation_capability(system_id)
    priority = priority_for(system_id, capability, family)
    timestamp = torrent.get("timestamp") or dashboard_timestamp or UNKNOWN
    return {
        "collectionId": stable_collection_id(group, name), "provider": "minerva", "categoryGroup": group,
        "platformFamily": family, "collectionName": name, "torrentInfoHash": torrent.get("info_hash") or UNKNOWN,
        "payloadBytes": torrent.get("size_bytes", UNKNOWN), "ozSystemId": system_id,
        "catalogPresence": "PRESENT", "emulationCapability": capability,
        "productCertification": "DISCOVERED",
        "romgiEvidence": romgi_evidence(system_id), "priority": priority,
        "deepScanRecommended": deep_scan(priority, system_id),
        "telemetry": {"semantics": TELEMETRY_SEMANTICS, "timestamp": timestamp,
                      "reportedSeeders": torrent.get("seeders", UNKNOWN), "reportedLeechers": torrent.get("leechers", UNKNOWN),
                      "currentSwarmHealth": UNKNOWN},
        "notes": ["Dashboard telemetry is a provider snapshot, not current live swarm truth."],
    }


def normalize_snapshot(raw: bytes) -> dict:
    dashboard = parse_dashboard(raw)
    dashboard_timestamp = dashboard.get("metadata", {}).get("timestamp", UNKNOWN)
    collections = [normalize_torrent(item, dashboard_timestamp) for item in discover_all_torrents(dashboard)]
    collections.sort(key=lambda item: (item["categoryGroup"], item["collectionName"], item["torrentInfoHash"]))
    return {"dashboardTimestamp": dashboard_timestamp, "dashboardTotalTorrents": dashboard["metadata"]["total_torrents"],
            "collections": collections}


def aggregate(normalized: dict) -> tuple[list[dict], list[dict], list[dict]]:
    groups: dict[str, list[dict]] = defaultdict(list)
    platforms: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for item in normalized["collections"]:
        groups[item["categoryGroup"]].append(item)
        platforms[(item["platformFamily"], item["ozSystemId"])].append(item)
    group_rows = [{"categoryGroup": key, "torrentCount": len(items),
                   "payloadBytes": sum(i["payloadBytes"] for i in items if isinstance(i["payloadBytes"], int))}
                  for key, items in sorted(groups.items())]
    platform_rows = []
    for (family, system), items in sorted(platforms.items()):
        priorities = sorted({item["priority"] for item in items})
        platform_rows.append({"platformFamily": family, "ozSystemId": system,
                              "catalogPresence": items[0]["catalogPresence"],
                              "emulationCapability": items[0]["emulationCapability"],
                              "productCertification": items[0]["productCertification"],
                              "romgiEvidence": items[0]["romgiEvidence"],
                              "collectionCount": len(items), "categoryGroups": sorted({i["categoryGroup"] for i in items}),
                              "payloadBytes": sum(i["payloadBytes"] for i in items if isinstance(i["payloadBytes"], int)),
                              "priorities": priorities, "deepScanRecommended": any(i["deepScanRecommended"] for i in items)})
    opportunities = [item for item in platform_rows if item["deepScanRecommended"]]
    return group_rows, platform_rows, opportunities


def write_outputs(snapshot: Path, output: Path, retrieval_ms: int | str = UNKNOWN) -> dict:
    started = time.perf_counter()
    raw = snapshot.read_bytes()
    normalized = normalize_snapshot(raw)
    groups, platforms, opportunities = aggregate(normalized)
    verified_match = re.search(r"(\d{4}-\d{2}-\d{2}T\d{6}Z)", snapshot.name)
    verified_at = verified_match.group(1) if verified_match else UNKNOWN
    manifest = {
        "schemaVersion": SCHEMA_VERSION, "scope": "MINERVA_GLOBAL_DASHBOARD_SNAPSHOT",
        "verifiedAt": verified_at, "rawSnapshotPath": f"raw/{snapshot.name}",
        "rawSnapshotSha256": hashlib.sha256(raw).hexdigest(), "rawSnapshotBytes": len(raw),
        "dashboardTimestamp": normalized["dashboardTimestamp"],
        "dashboardTotalTorrents": normalized["dashboardTotalTorrents"], "requestCount": 1,
        "metadataBytes": len(raw), "retrievalMs": retrieval_ms,
        "executionMs": round((time.perf_counter() - started) * 1000),
        "telemetrySemantics": TELEMETRY_SEMANTICS,
    }
    output.mkdir(parents=True, exist_ok=True)
    values = {"manifest.json": manifest, "groups.json": groups, "platforms.json": platforms,
              "collections.json": normalized["collections"], "opportunities.json": opportunities}
    for name, value in values.items():
        (output / name).write_text(json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False) + "\n", encoding="utf-8")
    lines = ["# MiNERVA Global Coverage Census", "", "Single dashboard snapshot; no browse or payload requests.", "",
             "| System | Catalog | Emulation | Certification | ROMgi evidence | Collections | Groups | Payload bytes | Priority | Deep scan |",
             "|---|---|---|---|---|---:|---|---:|---|---|"]
    for item in platforms:
        lines.append(f"| {item['platformFamily']} | {item['catalogPresence']} | {item['emulationCapability']} | {item['productCertification']} | {item['romgiEvidence']} | {item['collectionCount']} | {', '.join(item['categoryGroups'])} | {item['payloadBytes']} | {', '.join(item['priorities'])} | {item['deepScanRecommended']} |")
    lines += ["", "Dashboard seeders/leechers are preserved only as TELEMETRY_SNAPSHOT; current swarm health remains UNKNOWN.", ""]
    (output / "MiNERVA Global Coverage Census.md").write_text("\n".join(lines), encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--snapshot", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--retrieval-ms", type=int, default=UNKNOWN)
    args = parser.parse_args()
    print(json.dumps(write_outputs(args.snapshot, args.output, args.retrieval_ms), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()

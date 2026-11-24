#!/usr/bin/env python3
"""Metadata-only MiNERVA Nintendo census. Never requests ROM payload URLs."""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import subprocess
import time
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path

BASE = "https://minerva-archive.org"
DASHBOARD = f"{BASE}/v1/api/dashboard/latest"
UNKNOWN = "UNKNOWN"

NINTENDO_TERMS = re.compile(
    r"(?:Nintendo|Famicom|Game Boy|Pokemon Mini|Pokémon Mini|Virtual Boy|Satellaview|Sufami)", re.I
)
ENTRY_RE = re.compile(r'<div class="entry"[^>]*data-name="([^"]*)"[^>]*>(.*?)</div>', re.I | re.S)
HREF_RE = re.compile(r'<a href="([^"]+)"', re.I)
TEXT_RE = re.compile(r'<(?:strong|a)[^>]*>(.*?)</(?:strong|a)>', re.I | re.S)
SIZE_RE = re.compile(r'<span>([^<]+)</span>', re.I)
MAGNET_RE = re.compile(r'btih:([0-9a-f]{40})', re.I)
INDEX_RE = re.compile(r'data-m="(\d+)"', re.I)
REGION_RE = re.compile(r"\((USA|Japan|Europe|World|Korea|China|Taiwan|Australia|Brazil|Canada|Asia)(?:, [^)]+)?\)", re.I)


@dataclass(frozen=True)
class FetchResult:
    url: str
    body: bytes
    elapsed_ms: int


class MetadataClient:
    def __init__(self, timeout: int = 60):
        self.timeout = timeout
        self.requests = 0
        self.bytes = 0

    def fetch(self, url: str) -> FetchResult:
        started = time.perf_counter()
        request = urllib.request.Request(url, headers={"User-Agent": "OzRetroRom-MetadataCensus/1"})
        with urllib.request.urlopen(request, timeout=self.timeout) as response:
            body = response.read()
        self.requests += 1
        self.bytes += len(body)
        return FetchResult(url, body, round((time.perf_counter() - started) * 1000))


class CurlMetadataClient(MetadataClient):
    """Uses the host curl trust store without weakening certificate verification."""
    def fetch(self, url: str) -> FetchResult:
        started = time.perf_counter()
        completed = subprocess.run(
            ["curl.exe", "--fail", "--silent", "--show-error", "--location", "--max-time", str(self.timeout), url],
            check=True, capture_output=True,
        )
        body = completed.stdout
        self.requests += 1
        self.bytes += len(body)
        return FetchResult(url, body, round((time.perf_counter() - started) * 1000))


def clean_text(value: str) -> str:
    return html.unescape(re.sub(r"<[^>]+>", "", value)).strip().rstrip("/")


def parse_entries(source: str) -> list[dict]:
    entries = []
    for data_name, block in ENTRY_RE.findall(source):
        href = HREF_RE.search(block)
        text = TEXT_RE.search(block)
        size = SIZE_RE.search(block)
        magnet = MAGNET_RE.search(block)
        index = INDEX_RE.search(block)
        entries.append({
            "dataName": html.unescape(data_name),
            "name": clean_text(text.group(1)) if text else html.unescape(data_name),
            "href": html.unescape(href.group(1)) if href else None,
            "displaySize": size.group(1).strip() if size else UNKNOWN,
            "infoHash": magnet.group(1).lower() if magnet else None,
            "fileIndex": int(index.group(1)) if index else None,
        })
    return entries


def stable_collection_id(category: str, name: str) -> str:
    value = f"minerva\0{category.strip().casefold()}\0{name.strip().casefold()}".encode()
    return "minerva-collection-" + hashlib.sha256(value).hexdigest()


def platform_family(name: str) -> str:
    rules = [
        ("n64dd", r"64DD"), ("n64", r"Nintendo 64"), ("fds", r"Disk System"),
        ("snes", r"Super Nintendo|Super Famicom|Satellaview|Sufami"),
        ("nes", r"Nintendo Entertainment System|Family Computer Network|Family BASIC"),
        ("gba", r"Game Boy Advance"), ("gbc", r"Game Boy Color"),
        ("gb", r"Game Boy"), ("virtualboy", r"Virtual Boy"),
        ("nds", r"Nintendo DS"), ("dsi", r"Nintendo DSi"),
        ("pokemonmini", r"Pok[eé]mon Mini"), ("3ds", r"Nintendo 3DS"),
        ("gamecube", r"GameCube"), ("wiiu", r"Wii U"), ("wii", r"Nintendo - Wii|\bWii\b"),
    ]
    return next((key for key, pattern in rules if re.search(pattern, name, re.I)), "nintendo-other")


def classify(name: str, platform: str) -> str:
    if re.search(r"SDK|Source Code|Wallpapers|Music|Misc|amiibo|Kiosk", name, re.I):
        return "NOT_RECOMMENDED"
    if platform in {"3ds", "gamecube", "wii", "wiiu", "dsi", "nds"}:
        return "UNSUPPORTED_BY_OZ_CURRENTLY"
    if re.search(r"Private|Deprecated|Encrypted|QD|SmartMedia|Network|Play-Yan|e-Reader|Video|Multiboot", name, re.I):
        return "NEEDS_IDENTITY_RESEARCH"
    if re.search(r"Headered|Headerless|BigEndian|ByteSwapped|Aftermarket|T-En|Translation", name, re.I):
        return "NEEDS_NORMALIZATION"
    return "READY_FOR_SOURCE_INDEX"


def collection_type(name: str) -> str:
    for value in ("Aftermarket", "Private", "Headered", "Headerless", "BigEndian", "ByteSwapped", "Encrypted", "Decrypted", "Digital", "Prototype", "Beta", "Demo"):
        if value.casefold() in name.casefold():
            return value.upper()
    if "T-En" in name:
        return "TRANSLATION"
    return "GENERAL"


def normalized_collection(torrent: dict, entries: list[dict] | None, browse_url: str | None) -> dict:
    raw_name = torrent["name"]
    prefix = "Minerva_Myrient - "
    remainder = raw_name[len(prefix):] if raw_name.startswith(prefix) else raw_name
    category = torrent.get("category_group") or UNKNOWN
    collection_name = remainder[len(category) + 3:] if remainder.startswith(category + " - ") else remainder
    names = [item["name"] for item in (entries or []) if item.get("fileIndex") is not None]
    counts = Counter(name.casefold() for name in names)
    duplicates = sum(count - 1 for count in counts.values() if count > 1)
    regions = sorted({match.group(1).title() for name in names for match in REGION_RE.finditer(name)})
    extensions = sorted({Path(name).suffix.lower().lstrip(".") for name in names if Path(name).suffix})
    header = "HEADERED" if "Headered" in collection_name else "HEADERLESS" if "Headerless" in collection_name else UNKNOWN
    byte_order = "BIG_ENDIAN" if "BigEndian" in collection_name else "BYTE_SWAPPED" if "ByteSwapped" in collection_name else UNKNOWN
    platform = platform_family(collection_name)
    return {
        "provider": "minerva", "categoryGroup": category, "platformFamily": platform,
        "collectionId": stable_collection_id(category, collection_name), "collectionName": collection_name,
        "collectionPath": browse_url or UNKNOWN, "collectionType": collection_type(collection_name),
        "recordCount": len(names) if entries is not None else UNKNOWN,
        "uniqueFilenameCount": len(counts) if entries is not None else UNKNOWN,
        "duplicateFilenameCount": duplicates if entries is not None else UNKNOWN,
        "torrentAvailable": True, "torrentArtifact": "MAGNET_INFOHASH",
        "torrentInfoHash": torrent.get("info_hash") or UNKNOWN,
        "torrentFileCount": len(names) if entries is not None else UNKNOWN,
        "torrentPayloadBytes": torrent.get("size_bytes", UNKNOWN),
        "regionDistribution": regions or UNKNOWN, "releaseTypeDistribution": UNKNOWN,
        "hashAvailability": {"CRC32": UNKNOWN, "MD5": UNKNOWN, "SHA1": UNKNOWN, "SHA256": UNKNOWN},
        "hashScopeConfidence": UNKNOWN,
        "artifactPackaging": extensions if extensions else UNKNOWN,
        "headerState": header, "byteOrder": byte_order,
        "sourceFreshness": torrent.get("timestamp", UNKNOWN), "dashboardTimestamp": torrent.get("timestamp", UNKNOWN),
        "classification": classify(collection_name, platform),
        "notes": [], "warnings": ["Hashes and their byte scope were not exposed by the joined browse/dashboard surfaces."],
    }


def discover_torrents(dashboard: dict) -> list[dict]:
    return sorted(
        [item for item in discover_all_torrents(dashboard) if NINTENDO_TERMS.search(item.get("name", ""))],
        key=lambda item: (item.get("category_group", ""), item.get("name", "")),
    )


def parse_dashboard(raw: bytes) -> dict:
    dashboard = json.loads(raw.decode("utf-8"))
    require = dashboard.get("metadata", {})
    torrents = dashboard.get("torrents")
    if not isinstance(torrents, list) or not isinstance(require.get("total_torrents"), int):
        raise ValueError("MiNERVA dashboard snapshot is missing required fields")
    if require["total_torrents"] != len(torrents):
        raise ValueError("MiNERVA dashboard torrent count contradicts metadata")
    return dashboard


def discover_all_torrents(dashboard: dict) -> list[dict]:
    """Provider-discovery inventory: retains every dashboard torrent before scopes filter it."""
    return list(dashboard.get("torrents", []))


def browse_url(torrent: dict) -> str | None:
    name = torrent.get("name", "")
    prefix = "Minerva_Myrient - "
    if not name.startswith(prefix):
        return None
    relative = name[len(prefix):]
    parts = relative.split(" - ", 1)
    if len(parts) != 2:
        return None
    return f"{BASE}/browse/{urllib.parse.quote(parts[0], safe='')}/{urllib.parse.quote(parts[1], safe='')}/"


def overlap_report(collections: list[dict], file_names: dict[str, list[str]]) -> dict:
    locations: dict[str, list[str]] = defaultdict(list)
    for collection_id, names in file_names.items():
        for name in set(item.casefold() for item in names):
            locations[name].append(collection_id)
    cross = {name: ids for name, ids in locations.items() if len(ids) > 1}
    return {"sameFilenameAcrossCollections": len(cross), "examples": dict(list(sorted(cross.items()))[:50]),
            "trustedHashOverlap": UNKNOWN, "note": "Filename overlap is diagnostic only; no automatic deduplication was performed."}


def write_outputs(output: Path, dashboard: dict, collections: list[dict], file_names: dict[str, list[str]], metrics: dict) -> None:
    output.mkdir(parents=True, exist_ok=True)
    by_platform: dict[str, list[dict]] = defaultdict(list)
    for item in collections:
        by_platform[item["platformFamily"]].append(item)
    platforms = []
    for platform, items in sorted(by_platform.items()):
        known_records = [item["recordCount"] for item in items if isinstance(item["recordCount"], int)]
        platforms.append({
            "platform": platform, "collectionCount": len(items),
            "recordCount": sum(known_records) if len(known_records) == len(items) else UNKNOWN,
            "torrentPayloadBytes": sum(item["torrentPayloadBytes"] for item in items if isinstance(item["torrentPayloadBytes"], int)),
            "classifications": sorted({item["classification"] for item in items}),
        })
    manifest = {
        "schemaVersion": 1, "provider": "minerva", "scope": "NINTENDO_METADATA_ONLY",
        "verifiedAt": metrics["verifiedAt"], "authentication": "NONE_FOR_BASIC_GET",
        "primarySurfaces": [DASHBOARD, f"{BASE}/browse/<category>/<collection>/"],
        "dashboardTimestamp": dashboard.get("metadata", {}).get("timestamp", UNKNOWN),
        "collectionCount": len(collections), "metrics": metrics,
        "overlap": overlap_report(collections, file_names),
    }
    for name, value in (("manifest.json", manifest), ("platforms.json", platforms), ("collections.json", collections)):
        (output / name).write_text(json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False) + "\n", encoding="utf-8")
    lines = ["# MiNERVA Nintendo Coverage Census", "", f"Verified: {metrics['verifiedAt']}", "", "Metadata only; no ROM payload was requested.", "",
             "| Platform | Collections | Records | Torrent bytes | Status |", "|---|---:|---:|---:|---|"]
    for item in platforms:
        lines.append(f"| {item['platform']} | {item['collectionCount']} | {item['recordCount']} | {item['torrentPayloadBytes']} | {', '.join(item['classifications'])} |")
    lines += ["", "## Identity findings", "", "- NES headered/headerless and N64 byte-order sets are distinct provider artifacts requiring normalization, not automatically distinct games.",
              "- Provider/archive hashes remain UNKNOWN in scope unless a source proves they cover canonical ROM bytes.",
              "- Filename overlaps are reported and never deduplicated automatically.", ""]
    (output / "Nintendo Coverage Census.md").write_text("\n".join(lines), encoding="utf-8")


def run(output: Path, workers: int = 8, transport: str = "urllib") -> dict:
    started = time.perf_counter()
    client_type = CurlMetadataClient if transport == "curl" else MetadataClient
    client = client_type()
    dashboard_result = client.fetch(DASHBOARD)
    dashboard = parse_dashboard(dashboard_result.body)
    torrents = discover_torrents(dashboard)
    results: dict[str, tuple[list[dict] | None, str | None, int]] = {}

    def fetch_listing(item: dict):
        url = browse_url(item)
        if not url:
            return item["info_hash"], None, None, 0
        local = client_type()
        try:
            fetched = local.fetch(url)
            return item["info_hash"], parse_entries(fetched.body.decode("utf-8", "replace")), url, len(fetched.body)
        except Exception:
            return item["info_hash"], None, url, 0

    with ThreadPoolExecutor(max_workers=workers) as executor:
        futures = [executor.submit(fetch_listing, item) for item in torrents]
        for future in as_completed(futures):
            key, entries, url, byte_count = future.result()
            results[key] = (entries, url, byte_count)
            client.requests += 1
            client.bytes += byte_count
    collections, file_names = [], {}
    for torrent in torrents:
        entries, url, _ = results.get(torrent["info_hash"], (None, None, 0))
        item = normalized_collection(torrent, entries, url)
        collections.append(item)
        file_names[item["collectionId"]] = [entry["name"] for entry in (entries or []) if entry.get("fileIndex") is not None]
    metrics = {"verifiedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "requestCount": client.requests,
               "metadataBytes": client.bytes, "executionMs": round((time.perf_counter() - started) * 1000)}
    write_outputs(output, dashboard, collections, file_names, metrics)
    return {"collections": len(collections), **metrics}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--transport", choices=("urllib", "curl"), default="urllib")
    args = parser.parse_args()
    print(json.dumps(run(args.output, args.workers, args.transport), indent=2))


if __name__ == "__main__":
    main()

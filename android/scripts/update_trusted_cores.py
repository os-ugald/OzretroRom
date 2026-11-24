#!/usr/bin/env python3
"""Generate the SHA-256 allow-list used by secure release builds.

Run this immediately before publishing a release. The script downloads the
selected official Libretro core archives, hashes both the ZIP and the exact .so
inside it, then rewrites app/src/main/assets/trusted_cores.json.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import tempfile
import urllib.request
import zipfile
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parents[1]
SYSTEMS = ROOT / "app/src/main/assets/libretro_systems.json"
OUTPUT = ROOT / "app/src/main/assets/trusted_cores.json"
DEFAULT_BASE = "https://buildbot.libretro.com/nightly/android/latest"
FALLBACKS = {
    "azahar_libretro_android.so": ["panda3ds_libretro_android.so"],
    "pcsx2_libretro_android.so": ["play_libretro_android.so"],
}


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalize_core(value: str) -> str:
    name = value.strip().split("/")[-1]
    if name.lower() in {"fbneo", "finalburn neo", "finalburnneo"}:
        return "fbneo_libretro_android.so"
    return name if name.endswith(".so") else f"{name}_libretro_android.so"


def discover_cores() -> list[str]:
    systems = json.loads(SYSTEMS.read_text(encoding="utf-8"))
    cores: set[str] = set()
    for system in systems:
        raw = system.get("coreId")
        if not raw:
            continue
        normalized = normalize_core(raw)
        cores.add(normalized)
        cores.update(FALLBACKS.get(normalized, []))
    return sorted(cores)


def download_and_hash(base_url: str, abi: str, core_name: str) -> dict[str, str]:
    url = f"{base_url.rstrip('/')}/{abi}/{core_name}.zip"
    request = urllib.request.Request(url, headers={"User-Agent": "OzRetroRom-CorePin/1"})
    with tempfile.TemporaryDirectory(prefix="oz-core-pin-") as temp_dir:
        archive = pathlib.Path(temp_dir) / f"{core_name}.zip"
        with urllib.request.urlopen(request, timeout=120) as response, archive.open("wb") as output:
            if response.status < 200 or response.status >= 300:
                raise RuntimeError(f"HTTP {response.status}")
            total = 0
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                total += len(chunk)
                if total > 256 * 1024 * 1024:
                    raise RuntimeError("archive exceeds 256 MiB")
                output.write(chunk)

        archive_hash = sha256_file(archive)
        with zipfile.ZipFile(archive) as package:
            candidates = [
                item for item in package.infolist()
                if not item.is_dir() and pathlib.PurePosixPath(item.filename).name.lower() == core_name.lower()
            ]
            if len(candidates) != 1:
                raise RuntimeError(f"expected one {core_name}, found {len(candidates)}")
            digest = hashlib.sha256()
            with package.open(candidates[0]) as library:
                for chunk in iter(lambda: library.read(1024 * 1024), b""):
                    digest.update(chunk)
            library_hash = digest.hexdigest()

    return {
        "abi": abi,
        "coreName": core_name,
        "url": url,
        "archiveSha256": archive_hash,
        "librarySha256": library_hash,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--abi", default="arm64-v8a", choices=("arm64-v8a", "armeabi-v7a"))
    parser.add_argument("--base-url", default=DEFAULT_BASE)
    parser.add_argument("--core", action="append", dest="cores", help="core .so; repeat for multiple cores")
    parser.add_argument("--allow-missing", action="store_true")
    args = parser.parse_args()

    requested = sorted({normalize_core(value) for value in (args.cores or discover_cores())})
    entries: list[dict[str, str]] = []
    failures: list[str] = []
    for index, core in enumerate(requested, start=1):
        print(f"[{index}/{len(requested)}] {core}", flush=True)
        try:
            entries.append(download_and_hash(args.base_url, args.abi, core))
        except Exception as error:  # noqa: BLE001 - CLI reports every failed artifact.
            failures.append(f"{core}: {error}")
            print(f"  ERROR: {error}", flush=True)

    payload = {
        "schema": 1,
        "generatedUtc": datetime.now(timezone.utc).isoformat(),
        "source": args.base_url,
        "cores": sorted(entries, key=lambda item: (item["abi"], item["coreName"])),
    }
    OUTPUT.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {len(entries)} trusted entries to {OUTPUT}")
    if failures:
        print("Missing cores:")
        for failure in failures:
            print(f"- {failure}")
        return 0 if args.allow_missing else 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

from __future__ import annotations

import gzip
import json
from pathlib import Path
from typing import Any

import httpx

CURATED_MAME_ITEMS: list[dict[str, Any]] = [
    {
        "identifier": "killerinstinct_arcade",
        "title": "Killer Instinct (v1.5d) (MAME / Arcade)",
        "filename": "kinst.zip",
        "romset": "kinst",
        "size": 99536225,
    },
    {
        "identifier": "killerinstinct2_arcade",
        "title": "Killer Instinct 2 (v1.4) (MAME / Arcade)",
        "filename": "kinst2.zip",
        "romset": "kinst2",
        "size": 138919246,
    },
    {
        "identifier": "mame-killer-instinct_202401",
        "title": "Killer Instinct (MAME Complete Pack)",
        "filename": "MAME - Killer Instinct.rar",
        "romset": "kinst",
        "size": 288501437,
    },
    {
        "identifier": "mame-mortal-kombat-collection",
        "title": "Mortal Kombat Collection (MAME Arcade)",
        "filename": "mame-mortal-kombat-collection.zip",
        "romset": "mk",
        "size": 378101651,
    },
]


def build_shard(output_path: Path) -> int:
    entries_by_id: dict[str, dict[str, Any]] = {}

    for item in CURATED_MAME_ITEMS:
        ident = item["identifier"]
        entries_by_id[ident] = dict(item)

    with httpx.Client(timeout=20.0, follow_redirects=True) as client:
        page = 1
        while True:
            resp = client.get(
                "https://archive.org/advancedsearch.php",
                params={
                    "q": "collection:internetarcade",
                    "fl[]": ["identifier", "title", "item_size"],
                    "rows": 3000,
                    "page": page,
                    "output": "json",
                },
                headers={"User-Agent": "OzRetroRom/2.0"},
            )
            resp.raise_for_status()
            payload = resp.json().get("response") or {}
            docs = payload.get("docs") or []
            num_found = int(payload.get("numFound") or 0)
            if not docs:
                break

            for d in docs:
                ident = str(d.get("identifier") or "").strip()
                if not ident or ident in entries_by_id:
                    continue
                title = str(d.get("title") or ident).strip()
                romset = ident[len("arcade_") :] if ident.startswith("arcade_") else ident
                filename = f"{romset}.zip"
                try:
                    size = int(d.get("item_size") or 0)
                except (TypeError, ValueError):
                    size = 0
                entries_by_id[ident] = {
                    "identifier": ident,
                    "title": title,
                    "filename": filename,
                    "romset": romset,
                    "size": size,
                }

            if len(entries_by_id) >= num_found or len(docs) < 3000:
                break
            page += 1

    items = sorted(entries_by_id.values(), key=lambda x: (x["title"].lower(), x["identifier"]))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    raw = json.dumps({"source": "internetarcade+curated-mame", "items": items}, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    output_path.write_bytes(gzip.compress(raw, compresslevel=9))
    return len(items)


if __name__ == "__main__":
    out = Path(__file__).resolve().parent.parent / "app" / "source-index" / "mame-ia.json.gz.bin"
    total = build_shard(out)
    print(f"Generated {out} with {total} MAME entries ({out.stat().st_size} bytes)")

from __future__ import annotations

import json
import sqlite3
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .config import DEFAULT_CONSOLE_PATHS, DEFAULT_JAVA_URL, DEFAULT_ROMS_DIR


def default_targets() -> list[dict[str, Any]]:
    return [
        {
            "id": "ps4-emusuni",
            "name": "PS4 (EmuSuNi)",
            "console": "PS4",
            "protocol": "ftp",
            "host": "192.168.100.141",
            "port": 2121,
            "username": "anonymous",
            "password": "anonymous@oz.local",
            "base_path": "/data/EmuSuNi/Roms",
            "path_overrides": {
                "genesis": "/data/EmuSuNi/Roms/md",
                "arcade": "/data/EmuSuNi/Roms/arcade",
                "mame": "/data/EmuSuNi/Roms/arcade",
                "ps1": "/data/EmuSuNi/Roms/ps1",
                "snes": "/data/EmuSuNi/Roms/snes",
                "nes": "/data/EmuSuNi/Roms/nes",
                "gba": "/data/EmuSuNi/Roms/gba",
                "gb": "/data/EmuSuNi/Roms/gb",
                "gbc": "/data/EmuSuNi/Roms/gbc",
                "n64": "/data/EmuSuNi/Roms/n64",
                "dc": "/data/EmuSuNi/Roms/dc",
                "psp": "/data/EmuSuNi/Roms/psp",
                "saturn": "/data/EmuSuNi/Roms/saturn",
                "nds": "/data/EmuSuNi/Roms/nds",
                "3ds": "/data/EmuSuNi/Roms/3ds",
            },
            "notes": "GoldHEN FTP PS4: EmuSuNi interno (/data/EmuSuNi/Roms) o USB (/mnt/usb0/Roms).",
        },
        {
            "id": "ps3-webman",
            "name": "PS3 (webMAN MOD / multiMAN)",
            "console": "PS3",
            "protocol": "ftp",
            "host": "192.168.100.150",
            "port": 21,
            "username": "anonymous",
            "password": "",
            "base_path": "/dev_hdd0/ROMS",
            "path_overrides": {
                "ps1": "/dev_hdd0/PSXISO",
                "ps2": "/dev_hdd0/PS2ISO",
                "ps3": "/dev_hdd0/PS3ISO",
                "psp": "/dev_hdd0/PSPISO",
                "arcade": "/dev_hdd0/ROMS/arcade",
                "mame": "/dev_hdd0/ROMS/arcade",
            },
            "notes": "Carpeta estándar webMAN MOD en /dev_hdd0/.",
        },
        {
            "id": "switch-ftp",
            "name": "Nintendo Switch (FTP / Tinfoil)",
            "console": "Switch",
            "protocol": "ftp",
            "host": "192.168.100.160",
            "port": 5000,
            "username": "anonymous",
            "password": "",
            "base_path": "/roms",
            "path_overrides": {
                "switch": "/switch/nsp",
                "arcade": "/roms/arcade",
                "mame": "/roms/arcade",
            },
            "notes": "Compatible con ftpd (puerto 5000) o tienda LAN Tinfoil (/tinfoil/index.json).",
        },
    ]


def default_settings() -> dict[str, Any]:
    return {
        "steamgriddb_api_key": "",
        "ia_access_key": "",
        "ia_secret_key": "",
        "ia_cookie": "",
        "enable_libretro_fallback": True,
        "cover_max_width": 260,
        "cover_quality": 76,
        "roms_dir": DEFAULT_ROMS_DIR,
        "covers_dir": "",
        "onboarding_completed": False,
        "console_paths": dict(DEFAULT_CONSOLE_PATHS),
        "java_companion_url": DEFAULT_JAVA_URL,
        "targets": default_targets(),
        "pinned_hero_entry_id": "",
        "home_section_order": ["consoles", "recommendations", "featured", "recent", "favorites"],
        "ai_provider": "local",
        "ai_api_key": "",
        "ai_model": "gemini-2.5-flash",
        "ai_base_url": "",
        "custom_sagas": [],
    }


class HubDatabase:
    """Thread-safe SQLite store for ROM metadata, favorites, covers, and settings."""

    def __init__(self, db_path: Path) -> None:
        self.db_path = db_path
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self._conn = sqlite3.connect(str(self.db_path), check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._init_schema()

    def _init_schema(self) -> None:
        with self._lock:
            self._conn.execute("PRAGMA journal_mode=WAL;")
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS rom_meta (
                    entry_id TEXT PRIMARY KEY,
                    sha256 TEXT,
                    verified_at TEXT,
                    favorite INTEGER NOT NULL DEFAULT 0,
                    cover_status TEXT,
                    cover_source TEXT,
                    cover_updated_at TEXT,
                    last_played_at TEXT,
                    play_count INTEGER NOT NULL DEFAULT 0
                );
                """
            )
            cols = {
                row["name"]
                for row in self._conn.execute("PRAGMA table_info(rom_meta)").fetchall()
            }
            if "last_played_at" not in cols:
                self._conn.execute("ALTER TABLE rom_meta ADD COLUMN last_played_at TEXT")
            if "play_count" not in cols:
                self._conn.execute("ALTER TABLE rom_meta ADD COLUMN play_count INTEGER NOT NULL DEFAULT 0")
            if "description" not in cols:
                self._conn.execute("ALTER TABLE rom_meta ADD COLUMN description TEXT")
            if "release_year" not in cols:
                self._conn.execute("ALTER TABLE rom_meta ADD COLUMN release_year TEXT")
            if "genre" not in cols:
                self._conn.execute("ALTER TABLE rom_meta ADD COLUMN genre TEXT")
            if "developer" not in cols:
                self._conn.execute("ALTER TABLE rom_meta ADD COLUMN developer TEXT")
            if "description_source" not in cols:
                self._conn.execute("ALTER TABLE rom_meta ADD COLUMN description_source TEXT")
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS rom_patches (
                    patch_id TEXT PRIMARY KEY,
                    entry_id TEXT NOT NULL,
                    label TEXT NOT NULL,
                    language TEXT NOT NULL DEFAULT 'ES',
                    patch_format TEXT NOT NULL,
                    source_name TEXT,
                    patched_filename TEXT NOT NULL,
                    patched_path TEXT NOT NULL,
                    crc32_hex TEXT,
                    created_at TEXT NOT NULL
                );
                """
            )
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
                """
            )
            self._conn.commit()
            self._seed_defaults()

    def _seed_defaults(self) -> None:
        defaults = default_settings()
        with self._lock:
            had_existing = (self._conn.execute("SELECT COUNT(*) FROM settings").fetchone()[0] > 0)
            for key, val in defaults.items():
                cur = self._conn.execute("SELECT 1 FROM settings WHERE key = ?", (key,)).fetchone()
                if cur is None:
                    seed_val = True if (key == "onboarding_completed" and had_existing) else val
                    self._conn.execute(
                        "INSERT INTO settings (key, value) VALUES (?, ?)",
                        (key, json.dumps(seed_val)),
                    )
            self._conn.commit()
            self._migrate_legacy_targets()
            self._migrate_legacy_home_order()

    def _migrate_legacy_targets(self) -> None:
        row = self._conn.execute("SELECT value FROM settings WHERE key = ?", ("targets",)).fetchone()
        if not row:
            return
        try:
            targets = json.loads(row["value"])
        except Exception:
            return
        if not isinstance(targets, list):
            return

        ps4_defaults = next((t for t in default_targets() if t.get("id") == "ps4-emusuni"), None)
        changed = False
        for t in targets:
            if not isinstance(t, dict) or t.get("id") != "ps4-emusuni":
                continue
            base = str(t.get("base_path") or "").strip()
            if base.lower() in ("", "roms"):
                t["base_path"] = "/data/EmuSuNi/Roms"
                changed = True
            overrides = t.get("path_overrides")
            if isinstance(overrides, dict):
                for k, v in list(overrides.items()):
                    vs = str(v or "").strip()
                    if vs.lower().startswith("roms/"):
                        overrides[k] = "/data/EmuSuNi/Roms/" + vs[5:]
                        changed = True
                if ps4_defaults:
                    for dk, dv in ps4_defaults.get("path_overrides", {}).items():
                        if dk not in overrides:
                            overrides[dk] = dv
                            changed = True
            if str(t.get("notes") or "").strip() == "Fork EmuSuNi o servidor FTP GoldHEN en PS4." and ps4_defaults:
                t["notes"] = ps4_defaults["notes"]
                changed = True

        if changed:
            self._conn.execute(
                "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                ("targets", json.dumps(targets)),
            )
            self._conn.commit()

    def _migrate_legacy_home_order(self) -> None:
        row = self._conn.execute("SELECT value FROM settings WHERE key = ?", ("home_section_order",)).fetchone()
        if not row:
            return
        try:
            order = json.loads(row["value"])
        except Exception:
            return
        legacy_default = ["featured", "recommendations", "consoles", "recent", "favorites"]
        if order == legacy_default:
            new_order = ["consoles", "recommendations", "featured", "recent", "favorites"]
            self._conn.execute(
                "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                ("home_section_order", json.dumps(new_order)),
            )
            self._conn.commit()

    def close(self) -> None:
        with self._lock:
            self._conn.close()

    def get_settings(self) -> dict[str, Any]:
        result = default_settings()
        with self._lock:
            rows = self._conn.execute("SELECT key, value FROM settings").fetchall()
            for r in rows:
                try:
                    result[r["key"]] = json.loads(r["value"])
                except Exception:
                    pass
        return result

    def update_settings(self, patch: dict[str, Any]) -> dict[str, Any]:
        allowed = set(default_settings().keys())
        with self._lock:
            for key, val in patch.items():
                if key not in allowed:
                    continue
                self._conn.execute(
                    "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                    (key, json.dumps(val)),
                )
            self._conn.commit()
        return self.get_settings()

    def get_all_rom_meta(self) -> dict[str, dict[str, Any]]:
        with self._lock:
            rows = self._conn.execute("SELECT * FROM rom_meta").fetchall()
            return {r["entry_id"]: dict(r) for r in rows}

    def get_rom_meta(self, entry_id: str) -> dict[str, Any] | None:
        with self._lock:
            row = self._conn.execute("SELECT * FROM rom_meta WHERE entry_id = ?", (entry_id,)).fetchone()
            return dict(row) if row else None

    def toggle_favorite(self, entry_id: str) -> bool:
        with self._lock:
            row = self._conn.execute("SELECT favorite FROM rom_meta WHERE entry_id = ?", (entry_id,)).fetchone()
            new_val = 0 if (row and row["favorite"]) else 1
            self._conn.execute(
                """
                INSERT INTO rom_meta (entry_id, favorite) VALUES (?, ?)
                ON CONFLICT(entry_id) DO UPDATE SET favorite = excluded.favorite
                """,
                (entry_id, new_val),
            )
            self._conn.commit()
            return bool(new_val)

    def set_sha256(self, entry_id: str, sha256_hex: str) -> str:
        now = datetime.now(timezone.utc).isoformat()
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO rom_meta (entry_id, sha256, verified_at) VALUES (?, ?, ?)
                ON CONFLICT(entry_id) DO UPDATE SET sha256 = excluded.sha256, verified_at = excluded.verified_at
                """,
                (entry_id, sha256_hex, now),
            )
            self._conn.commit()
        return now

    def set_cover_meta(self, entry_id: str, status: str, source: str | None) -> None:
        now = datetime.now(timezone.utc).isoformat()
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO rom_meta (entry_id, cover_status, cover_source, cover_updated_at) VALUES (?, ?, ?, ?)
                ON CONFLICT(entry_id) DO UPDATE SET
                    cover_status = excluded.cover_status,
                    cover_source = excluded.cover_source,
                    cover_updated_at = excluded.cover_updated_at
                """,
                (entry_id, status, source, now),
            )
            self._conn.commit()

    def mark_played(self, entry_id: str) -> dict[str, Any]:
        now = datetime.now(timezone.utc).isoformat()
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO rom_meta (entry_id, last_played_at, play_count) VALUES (?, ?, 1)
                ON CONFLICT(entry_id) DO UPDATE SET
                    last_played_at = excluded.last_played_at,
                    play_count = COALESCE(rom_meta.play_count, 0) + 1
                """,
                (entry_id, now),
            )
            row = self._conn.execute(
                "SELECT last_played_at, play_count FROM rom_meta WHERE entry_id = ?",
                (entry_id,),
            ).fetchone()
            self._conn.commit()
            return {
                "lastPlayedAt": row["last_played_at"] if row else now,
                "playCount": int(row["play_count"] or 1) if row else 1,
            }

    def clear_cover_meta(self) -> None:
        with self._lock:
            self._conn.execute("UPDATE rom_meta SET cover_status = NULL, cover_source = NULL, cover_updated_at = NULL")
            self._conn.commit()

    def set_game_description(
        self,
        entry_id: str,
        description: str,
        release_year: str = "",
        genre: str = "",
        developer: str = "",
        source: str = "",
    ) -> dict[str, Any]:
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO rom_meta (entry_id, description, release_year, genre, developer, description_source)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(entry_id) DO UPDATE SET
                    description = excluded.description,
                    release_year = excluded.release_year,
                    genre = excluded.genre,
                    developer = excluded.developer,
                    description_source = excluded.description_source
                """,
                (entry_id, description, release_year, genre, developer, source),
            )
            self._conn.commit()
        return {
            "entryId": entry_id,
            "description": description,
            "releaseYear": release_year,
            "genre": genre,
            "developer": developer,
            "source": source,
        }

    def list_patches(self, entry_id: str | None = None) -> list[dict[str, Any]]:
        with self._lock:
            if entry_id:
                rows = self._conn.execute(
                    "SELECT * FROM rom_patches WHERE entry_id = ? ORDER BY created_at DESC",
                    (entry_id,),
                ).fetchall()
            else:
                rows = self._conn.execute("SELECT * FROM rom_patches ORDER BY created_at DESC").fetchall()
            return [dict(r) for r in rows]

    def get_patch(self, patch_id: str) -> dict[str, Any] | None:
        with self._lock:
            row = self._conn.execute("SELECT * FROM rom_patches WHERE patch_id = ?", (patch_id,)).fetchone()
            return dict(row) if row else None

    def add_patch(self, patch_row: dict[str, Any]) -> dict[str, Any]:
        now = datetime.now(timezone.utc).isoformat()
        row = {
            "patch_id": patch_row["patch_id"],
            "entry_id": patch_row["entry_id"],
            "label": patch_row.get("label") or "Traducción [ES]",
            "language": patch_row.get("language") or "ES",
            "patch_format": patch_row.get("patch_format") or "IPS",
            "source_name": patch_row.get("source_name") or "",
            "patched_filename": patch_row["patched_filename"],
            "patched_path": patch_row["patched_path"],
            "crc32_hex": patch_row.get("crc32_hex") or "",
            "created_at": patch_row.get("created_at") or now,
        }
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO rom_patches (
                    patch_id, entry_id, label, language, patch_format, source_name,
                    patched_filename, patched_path, crc32_hex, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(patch_id) DO UPDATE SET
                    label = excluded.label,
                    language = excluded.language,
                    patch_format = excluded.patch_format,
                    source_name = excluded.source_name,
                    patched_filename = excluded.patched_filename,
                    patched_path = excluded.patched_path,
                    crc32_hex = excluded.crc32_hex,
                    created_at = excluded.created_at
                """,
                (
                    row["patch_id"],
                    row["entry_id"],
                    row["label"],
                    row["language"],
                    row["patch_format"],
                    row["source_name"],
                    row["patched_filename"],
                    row["patched_path"],
                    row["crc32_hex"],
                    row["created_at"],
                ),
            )
            self._conn.commit()
        return row

    def delete_patch(self, patch_id: str) -> dict[str, Any] | None:
        with self._lock:
            existing = self.get_patch(patch_id)
            if not existing:
                return None
            self._conn.execute("DELETE FROM rom_patches WHERE patch_id = ?", (patch_id,))
            self._conn.commit()
            return existing

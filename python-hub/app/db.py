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
            if "quick_resume_json" not in cols:
                self._conn.execute("ALTER TABLE rom_meta ADD COLUMN quick_resume_json TEXT")
            if "custom_metadata_json" not in cols:
                self._conn.execute("ALTER TABLE rom_meta ADD COLUMN custom_metadata_json TEXT")
            if "updated_at" not in cols:
                self._conn.execute("ALTER TABLE rom_meta ADD COLUMN updated_at TEXT")
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
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS download_jobs (
                    job_id TEXT PRIMARY KEY,
                    identifier TEXT NOT NULL,
                    filename TEXT NOT NULL,
                    title TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    arcade_core TEXT NOT NULL DEFAULT '',
                    state TEXT NOT NULL,
                    bytes INTEGER NOT NULL DEFAULT 0,
                    size INTEGER NOT NULL DEFAULT 0,
                    message TEXT NOT NULL DEFAULT '',
                    target_path TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                """
            )
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS transfer_jobs (
                    job_id TEXT PRIMARY KEY,
                    entry_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    console_key TEXT NOT NULL,
                    target_id TEXT NOT NULL,
                    target_name TEXT NOT NULL,
                    remote_dir TEXT NOT NULL,
                    remote_name TEXT NOT NULL,
                    state TEXT NOT NULL,
                    bytes INTEGER NOT NULL DEFAULT 0,
                    size INTEGER NOT NULL DEFAULT 0,
                    message TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                """
            )
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS sync_log (
                    revision INTEGER PRIMARY KEY AUTOINCREMENT,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    op TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    source_device TEXT NOT NULL DEFAULT 'pc-hub'
                );
                """
            )
            self._conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_sync_log_entity ON sync_log(entity_type, entity_id);"
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
        now = datetime.now(timezone.utc).isoformat()
        with self._lock:
            row = self._conn.execute("SELECT favorite FROM rom_meta WHERE entry_id = ?", (entry_id,)).fetchone()
            new_val = 0 if (row and row["favorite"]) else 1
            self._conn.execute(
                """
                INSERT INTO rom_meta (entry_id, favorite, updated_at) VALUES (?, ?, ?)
                ON CONFLICT(entry_id) DO UPDATE SET
                    favorite = excluded.favorite,
                    updated_at = excluded.updated_at
                """,
                (entry_id, new_val, now),
            )
            self._conn.commit()
        self.append_sync_event(
            "game_state",
            entry_id,
            "upsert",
            {"entryId": entry_id, "favorite": bool(new_val), "updatedAt": now},
            source_device="pc-hub",
        )
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
                INSERT INTO rom_meta (entry_id, last_played_at, play_count, updated_at) VALUES (?, ?, 1, ?)
                ON CONFLICT(entry_id) DO UPDATE SET
                    last_played_at = excluded.last_played_at,
                    play_count = COALESCE(rom_meta.play_count, 0) + 1,
                    updated_at = excluded.updated_at
                """,
                (entry_id, now, now),
            )
            row = self._conn.execute(
                "SELECT last_played_at, play_count FROM rom_meta WHERE entry_id = ?",
                (entry_id,),
            ).fetchone()
            self._conn.commit()
            result = {
                "lastPlayedAt": row["last_played_at"] if row else now,
                "playCount": int(row["play_count"] or 1) if row else 1,
            }
        self.append_sync_event(
            "game_state",
            entry_id,
            "upsert",
            {"entryId": entry_id, **result, "updatedAt": now},
            source_device="pc-hub",
        )
        return result

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

    # ── Persisted download & FTP transfer queues ─────────────────────────────

    def upsert_download_job(self, job: dict[str, Any]) -> None:
        now = datetime.now(timezone.utc).isoformat()
        job_id = str(job.get("id") or job.get("job_id") or "").strip()
        if not job_id:
            return
        created_at = str(job.get("createdAt") or job.get("created_at") or now)
        updated_at = str(job.get("updatedAt") or job.get("updated_at") or now)
        target_path = str(job.get("destPath") or job.get("target_path") or "")
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO download_jobs (
                    job_id, identifier, filename, title, platform, arcade_core,
                    state, bytes, size, message, target_path, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(job_id) DO UPDATE SET
                    identifier = excluded.identifier,
                    filename = excluded.filename,
                    title = excluded.title,
                    platform = excluded.platform,
                    arcade_core = excluded.arcade_core,
                    state = excluded.state,
                    bytes = excluded.bytes,
                    size = excluded.size,
                    message = excluded.message,
                    target_path = excluded.target_path,
                    updated_at = excluded.updated_at
                """,
                (
                    job_id,
                    str(job.get("identifier") or ""),
                    str(job.get("filename") or ""),
                    str(job.get("title") or ""),
                    str(job.get("platform") or "other"),
                    str(job.get("arcadeCore") or job.get("arcade_core") or ""),
                    str(job.get("state") or "queued"),
                    int(job.get("bytes") or 0),
                    int(job.get("size") or 0),
                    str(job.get("message") or ""),
                    target_path,
                    created_at,
                    updated_at,
                ),
            )
            self._conn.commit()

    def list_download_jobs(self, limit: int = 100) -> list[dict[str, Any]]:
        with self._lock:
            rows = self._conn.execute(
                "SELECT * FROM download_jobs ORDER BY created_at ASC LIMIT ?",
                (max(1, int(limit)),),
            ).fetchall()
        out: list[dict[str, Any]] = []
        for r in rows:
            target_path = r["target_path"] or ""
            out.append(
                {
                    "id": r["job_id"],
                    "identifier": r["identifier"],
                    "title": r["title"],
                    "filename": r["filename"],
                    "platform": r["platform"],
                    "arcadeCore": r["arcade_core"] or "",
                    "provider": "Internet Archive",
                    "state": r["state"],
                    "bytes": int(r["bytes"] or 0),
                    "size": int(r["size"] or 0),
                    "destDir": str(Path(target_path).parent) if target_path else "",
                    "destPath": target_path,
                    "message": r["message"],
                    "createdAt": r["created_at"],
                    "updatedAt": r["updated_at"],
                }
            )
        return out

    def mark_interrupted_download_jobs(self) -> int:
        now = datetime.now(timezone.utc).isoformat()
        with self._lock:
            cur = self._conn.execute(
                """
                UPDATE download_jobs
                SET state = 'interrupted',
                    message = 'Interrumpido por reinicio del Hub',
                    updated_at = ?
                WHERE state IN ('queued', 'connecting', 'downloading')
                """,
                (now,),
            )
            self._conn.commit()
            return int(cur.rowcount or 0)

    def upsert_transfer_job(self, job: dict[str, Any]) -> None:
        now = datetime.now(timezone.utc).isoformat()
        job_id = str(job.get("id") or job.get("job_id") or "").strip()
        if not job_id:
            return
        remote_path = str(job.get("remotePath") or "").strip()
        remote_dir = str(job.get("remoteDir") or "").strip()
        remote_name = str(job.get("remoteName") or "").strip()
        if remote_path and (not remote_dir or not remote_name):
            idx = remote_path.rfind("/")
            if idx >= 0:
                remote_dir = remote_dir or remote_path[:idx] or "/"
                remote_name = remote_name or remote_path[idx + 1 :]
        total_size = int(job.get("total") if job.get("total") is not None else (job.get("size") or 0))
        created_at = str(job.get("createdAt") or job.get("created_at") or now)
        updated_at = str(job.get("updatedAt") or job.get("updated_at") or now)
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO transfer_jobs (
                    job_id, entry_id, title, console_key, target_id, target_name,
                    remote_dir, remote_name, state, bytes, size, message, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(job_id) DO UPDATE SET
                    entry_id = excluded.entry_id,
                    title = excluded.title,
                    console_key = excluded.console_key,
                    target_id = excluded.target_id,
                    target_name = excluded.target_name,
                    remote_dir = excluded.remote_dir,
                    remote_name = excluded.remote_name,
                    state = excluded.state,
                    bytes = excluded.bytes,
                    size = excluded.size,
                    message = excluded.message,
                    updated_at = excluded.updated_at
                """,
                (
                    job_id,
                    str(job.get("entryId") or job.get("entry_id") or ""),
                    str(job.get("title") or ""),
                    str(job.get("consoleKey") or job.get("console_key") or ""),
                    str(job.get("targetId") or job.get("target_id") or ""),
                    str(job.get("targetName") or job.get("target_name") or ""),
                    remote_dir,
                    remote_name,
                    str(job.get("state") or "queued"),
                    int(job.get("bytes") or 0),
                    total_size,
                    str(job.get("message") or ""),
                    created_at,
                    updated_at,
                ),
            )
            self._conn.commit()

    def list_transfer_jobs(self, limit: int = 100) -> list[dict[str, Any]]:
        with self._lock:
            rows = self._conn.execute(
                "SELECT * FROM transfer_jobs ORDER BY created_at ASC LIMIT ?",
                (max(1, int(limit)),),
            ).fetchall()
        out: list[dict[str, Any]] = []
        for r in rows:
            remote_dir = (r["remote_dir"] or "").rstrip("/")
            remote_name = r["remote_name"] or ""
            remote_path = f"{remote_dir}/{remote_name}" if remote_dir and remote_name else remote_name
            out.append(
                {
                    "id": r["job_id"],
                    "entryId": r["entry_id"],
                    "title": r["title"],
                    "consoleKey": r["console_key"],
                    "targetId": r["target_id"],
                    "targetName": r["target_name"],
                    "remoteDir": r["remote_dir"],
                    "remoteName": remote_name,
                    "remotePath": remote_path,
                    "state": r["state"],
                    "bytes": int(r["bytes"] or 0),
                    "total": int(r["size"] or 0),
                    "message": r["message"],
                    "createdAt": r["created_at"],
                    "updatedAt": r["updated_at"],
                }
            )
        return out

    def mark_interrupted_transfer_jobs(self) -> int:
        now = datetime.now(timezone.utc).isoformat()
        with self._lock:
            cur = self._conn.execute(
                """
                UPDATE transfer_jobs
                SET state = 'interrupted',
                    message = 'Transferencia interrumpida por reinicio del Hub',
                    updated_at = ?
                WHERE state IN ('queued', 'connecting', 'transferring')
                """,
                (now,),
            )
            self._conn.commit()
            return int(cur.rowcount or 0)

    # ── Incremental delta sync log ───────────────────────────────────────────

    @staticmethod
    def _timestamp_to_epoch(value: Any) -> float:
        if value is None:
            return 0.0
        if isinstance(value, (int, float)):
            v = float(value)
            return v / 1000.0 if v > 1e11 else v
        text = str(value).strip()
        if not text:
            return 0.0
        try:
            num = float(text)
            return num / 1000.0 if num > 1e11 else num
        except ValueError:
            pass
        try:
            norm = text[:-1] + "+00:00" if text.endswith("Z") else text
            dt = datetime.fromisoformat(norm)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return dt.timestamp()
        except Exception:
            return 0.0

    def append_sync_event(
        self,
        entity_type: str,
        entity_id: str,
        op: str,
        payload: dict[str, Any],
        source_device: str = "pc-hub",
    ) -> int:
        now = str(payload.get("updatedAt") or datetime.now(timezone.utc).isoformat())
        encoded = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
        with self._lock:
            cur = self._conn.execute(
                """
                INSERT INTO sync_log (entity_type, entity_id, op, payload_json, updated_at, source_device)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (entity_type, entity_id, op, encoded, now, source_device or "pc-hub"),
            )
            self._conn.commit()
            return int(cur.lastrowid or 0)

    def get_latest_sync_revision(self) -> int:
        with self._lock:
            row = self._conn.execute("SELECT COALESCE(MAX(revision), 0) AS rev FROM sync_log").fetchone()
            return int(row["rev"] or 0) if row else 0

    def get_sync_changes(self, since_revision: int = 0, limit: int = 500) -> dict[str, Any]:
        capped = max(1, min(1000, int(limit)))
        with self._lock:
            latest_rev = self.get_latest_sync_revision()
            rows = self._conn.execute(
                """
                SELECT revision, entity_type, entity_id, op, payload_json, updated_at, source_device
                FROM sync_log
                WHERE revision > ?
                ORDER BY revision ASC
                LIMIT ?
                """,
                (max(0, int(since_revision)), capped + 1),
            ).fetchall()
        has_more = len(rows) > capped
        slice_rows = rows[:capped]
        changes: list[dict[str, Any]] = []
        for r in slice_rows:
            try:
                payload = json.loads(r["payload_json"])
            except Exception:
                payload = {}
            changes.append(
                {
                    "revision": int(r["revision"]),
                    "entityType": r["entity_type"],
                    "entityId": r["entity_id"],
                    "op": r["op"],
                    "payload": payload,
                    "updatedAt": r["updated_at"],
                    "sourceDevice": r["source_device"],
                }
            )
        return {
            "latestRevision": latest_rev,
            "hasMore": has_more,
            "changes": changes,
        }

    def apply_sync_changes(
        self,
        changes: list[dict[str, Any]],
        source_device: str = "android",
    ) -> dict[str, Any]:
        applied = 0
        revisions: list[int] = []
        now = datetime.now(timezone.utc).isoformat()
        with self._lock:
            for item in changes:
                entity_id = str(item.get("entityId") or item.get("entryId") or "").strip()
                if not entity_id:
                    continue
                existing = self._conn.execute("SELECT * FROM rom_meta WHERE entry_id = ?", (entity_id,)).fetchone()
                ex_fav = int(existing["favorite"] or 0) if existing else 0
                ex_count = int(existing["play_count"] or 0) if existing else 0
                ex_last_played = existing["last_played_at"] if existing else None
                ex_updated_at = existing["updated_at"] if existing else None
                try:
                    ex_qr = json.loads(existing["quick_resume_json"]) if (existing and existing["quick_resume_json"]) else {}
                except Exception:
                    ex_qr = {}
                try:
                    ex_cm = json.loads(existing["custom_metadata_json"]) if (existing and existing["custom_metadata_json"]) else {}
                except Exception:
                    ex_cm = {}

                incoming_ts_str = str(item.get("updatedAt") or now)
                incoming_epoch = self._timestamp_to_epoch(incoming_ts_str)
                existing_epoch = self._timestamp_to_epoch(ex_updated_at)

                new_fav = ex_fav
                if item.get("favorite") is not None:
                    if ex_updated_at is None or incoming_epoch >= existing_epoch:
                        new_fav = 1 if bool(item.get("favorite")) else 0

                inc_count = item.get("playCount")
                new_count = max(ex_count, int(inc_count)) if inc_count is not None else ex_count

                new_last_played = ex_last_played
                inc_last_played = item.get("lastPlayedAt")
                if inc_last_played:
                    if not ex_last_played or self._timestamp_to_epoch(inc_last_played) >= self._timestamp_to_epoch(ex_last_played):
                        new_last_played = str(inc_last_played)

                merged_qr = dict(ex_qr)
                inc_qr = item.get("quickResume")
                if isinstance(inc_qr, dict) and inc_qr:
                    qr_inc_epoch = self._timestamp_to_epoch(inc_qr.get("updatedAt") or incoming_ts_str)
                    qr_ex_epoch = self._timestamp_to_epoch(ex_qr.get("updatedAt") or ex_updated_at)
                    if not ex_qr or qr_inc_epoch >= qr_ex_epoch:
                        merged_qr.update(inc_qr)
                    else:
                        for k, v in inc_qr.items():
                            if k not in merged_qr:
                                merged_qr[k] = v

                merged_cm = dict(ex_cm)
                inc_cm = item.get("customMetadata")
                if isinstance(inc_cm, dict) and inc_cm:
                    if not ex_cm or incoming_epoch >= existing_epoch:
                        merged_cm.update(inc_cm)
                    else:
                        for k, v in inc_cm.items():
                            if k not in merged_cm:
                                merged_cm[k] = v

                new_updated_at = incoming_ts_str if incoming_epoch >= existing_epoch else (ex_updated_at or now)
                qr_json = json.dumps(merged_qr, ensure_ascii=False, separators=(",", ":")) if merged_qr else None
                cm_json = json.dumps(merged_cm, ensure_ascii=False, separators=(",", ":")) if merged_cm else None

                self._conn.execute(
                    """
                    INSERT INTO rom_meta (
                        entry_id, favorite, last_played_at, play_count,
                        quick_resume_json, custom_metadata_json, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(entry_id) DO UPDATE SET
                        favorite = excluded.favorite,
                        last_played_at = excluded.last_played_at,
                        play_count = excluded.play_count,
                        quick_resume_json = excluded.quick_resume_json,
                        custom_metadata_json = excluded.custom_metadata_json,
                        updated_at = excluded.updated_at
                    """,
                    (entity_id, new_fav, new_last_played, new_count, qr_json, cm_json, new_updated_at),
                )
                self._conn.commit()

                sync_payload: dict[str, Any] = {
                    "entryId": entity_id,
                    "favorite": bool(new_fav),
                    "playCount": new_count,
                    "lastPlayedAt": new_last_played,
                    "updatedAt": new_updated_at,
                }
                if merged_qr:
                    sync_payload["quickResume"] = merged_qr
                if merged_cm:
                    sync_payload["customMetadata"] = merged_cm

                rev = self.append_sync_event(
                    str(item.get("entityType") or "game_state"),
                    entity_id,
                    str(item.get("op") or "upsert"),
                    sync_payload,
                    source_device=source_device,
                )
                revisions.append(rev)
                applied += 1
        return {
            "applied": applied,
            "latestRevision": self.get_latest_sync_revision(),
            "revisions": revisions,
        }

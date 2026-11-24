#!/usr/bin/env python3
"""Executable SQLite smoke test for the Room 5 -> 6 migration contract."""
from __future__ import annotations

import sqlite3

connection = sqlite3.connect(":memory:")
connection.execute(
    """
    CREATE TABLE games (
        id TEXT NOT NULL PRIMARY KEY,
        title TEXT NOT NULL,
        normalizedTitle TEXT NOT NULL,
        platform TEXT NOT NULL,
        systemId TEXT NOT NULL,
        year INTEGER,
        region TEXT NOT NULL,
        thumbnailRepo TEXT,
        source TEXT NOT NULL,
        launchValidated INTEGER NOT NULL,
        coreId TEXT,
        virtualExtension TEXT NOT NULL,
        contentUri TEXT,
        canonicalTitle TEXT NOT NULL DEFAULT '',
        genre TEXT NOT NULL DEFAULT 'Sin clasificar',
        romSet TEXT,
        biosSetsCsv TEXT NOT NULL DEFAULT '',
        lastPlayedAt INTEGER DEFAULT NULL,
        addedAt INTEGER NOT NULL DEFAULT 0,
        romgiAvailable INTEGER NOT NULL DEFAULT 0
    )
    """
)
connection.executemany(
    "INSERT INTO games (id,title,normalizedTitle,platform,systemId,region,source,launchValidated,virtualExtension,contentUri) "
    "VALUES (?,?,?,?,?,?,?,?,?,?)",
    [
        ("remote", "Remote", "remote", "SNES", "snes", "World", "seed", 1, "sfc", None),
        ("persisted", "Persisted", "persisted", "SNES", "snes", "World", "seed", 1, "sfc", "content://library/persisted"),
    ],
)

# Exact SQL contract from MIGRATION_5_6.
connection.execute("ALTER TABLE games ADD COLUMN libraryOrigin TEXT NOT NULL DEFAULT 'NONE'")
connection.execute("UPDATE games SET libraryOrigin='DOWNLOADED' WHERE COALESCE(contentUri,'')!=''")

columns = {row[1]: row for row in connection.execute("PRAGMA table_info(games)")}
assert columns["libraryOrigin"][3] == 1, columns["libraryOrigin"]  # NOT NULL
assert columns["libraryOrigin"][4] == "'NONE'", columns["libraryOrigin"]
rows = dict(connection.execute("SELECT id, libraryOrigin FROM games ORDER BY id"))
assert rows == {"persisted": "DOWNLOADED", "remote": "NONE"}, rows
uri = connection.execute("SELECT contentUri FROM games WHERE id='persisted'").fetchone()[0]
assert uri == "content://library/persisted", uri
print("ROOM_MIGRATION_5_6_OK default=NONE persisted=DOWNLOADED data_preserved=1")

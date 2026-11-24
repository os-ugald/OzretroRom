#!/usr/bin/env python3
"""Executable SQLite smoke test for the Room 3 -> 4 migration contract."""
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
        biosSetsCsv TEXT NOT NULL DEFAULT ''
    )
    """
)
connection.execute(
    "INSERT INTO games (id,title,normalizedTitle,platform,systemId,region,source,launchValidated,virtualExtension) "
    "VALUES ('test','Test','test','SNES','snes','World','seed',1,'sfc')"
)
connection.execute("ALTER TABLE games ADD COLUMN lastPlayedAt INTEGER DEFAULT NULL")
connection.execute("ALTER TABLE games ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
connection.execute("ALTER TABLE games ADD COLUMN romgiAvailable INTEGER NOT NULL DEFAULT 0")
connection.execute("UPDATE games SET addedAt = CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE addedAt = 0")
connection.execute("CREATE INDEX IF NOT EXISTS index_games_lastPlayedAt ON games(lastPlayedAt)")
connection.execute("CREATE INDEX IF NOT EXISTS index_games_addedAt ON games(addedAt)")
connection.execute("CREATE INDEX IF NOT EXISTS index_games_romgiAvailable ON games(romgiAvailable)")

columns = {row[1]: row for row in connection.execute("PRAGMA table_info(games)")}
assert columns["lastPlayedAt"][4] == "NULL", columns["lastPlayedAt"]
assert columns["addedAt"][4] == "0", columns["addedAt"]
assert columns["romgiAvailable"][4] == "0", columns["romgiAvailable"]
row = connection.execute("SELECT lastPlayedAt, addedAt, romgiAvailable FROM games WHERE id='test'").fetchone()
assert row[0] is None
assert isinstance(row[1], int) and row[1] > 0
assert row[2] == 0
print("ROOM_MIGRATION_3_4_OK default=NULL data_preserved=1")

#!/usr/bin/env python3
"""Static contracts for Ready Core 01B; Gradle remains the authoritative build gate."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/cl/gakuto/kizuna"
TESTS = ROOT / "app/src/test/java/cl/gakuto/kizuna"
checks = 0
failures: list[str] = []


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def require(path: Path, *tokens: str) -> None:
    global checks
    source = text(path)
    for token in tokens:
        checks += 1
        if token not in source:
            failures.append(f"{path.relative_to(ROOT)}: falta {token!r}")


def forbid(path: Path, *tokens: str) -> None:
    global checks
    source = text(path)
    for token in tokens:
        checks += 1
        if token in source:
            failures.append(f"{path.relative_to(ROOT)}: conserva {token!r}")


edition = JAVA / "catalog/RomgiEdition.kt"
catalog = JAVA / "catalog/RomgiCatalog.kt"
picker = JAVA / "ui/compose/RomgiEditionPicker.kt"
session = JAVA / "launch/SessionActivity.kt"
download_set = JAVA / "launch/RomDownloadSet.kt"
task_store = JAVA / "launch/RomDownloadTaskStore.kt"
engine = JAVA / "launch/BackgroundRomDownloadEngine.kt"
collection = JAVA / "launch/CollectionDownloadManager.kt"
persistent = JAVA / "launch/PersistentRomLibrary.kt"
public_store = JAVA / "launch/PublicSessionStore.kt"
filters = JAVA / "util/CatalogFilters.kt"
paging = JAVA / "data/CatalogPaging.kt"
profiles = JAVA / "launch/EmulatorProfiles.kt"

require(edition, "RomgiParsedEdition", "editionKey", "revisionLabel", "forcedDiscIndex", "RomgiReleaseKind", "M3uPlaylist")
require(catalog, "resolveEditions", "readGroupMemberships", 'tableExists(db, "entry_groups")', 'tableExists(db, "entry_group_members")')
require(catalog, "fallback:", "expectedIndexes", "MULTI_DISC_FORMATS", "boxartUrl", "MAX_EDITION_PLANS")
require(catalog, "cb4ba3e49928a537dfa944007a3ae0e62e8b63e9", "178fb285f1b9bc2cd7b6c80a0c09fd519b985c13f05cbd625e183be54a668e72")
require(picker, "Elige la edición", "Descargar juego completo", "Versiones avanzadas", "conjunto completo", "focusable()")
require(picker, "FocusRequester", "focusRequester(initialFocusRequester)", "initialFocusRequester.requestFocus()")
require(picker, "resolution.advanced.isNotEmpty() && resolution.standard.isNotEmpty()", "selectedId = resolution.recommended.id")
forbid(picker, "RadioButton", "setSingleChoiceItems")
require(session, "chooseRomgiEdition", "catalog.resolveEditions", "plan.toDownloadSet()", "launchStoredMultiDisc", "prepareMultiDiscLaunchEntry")
forbid(session, "chooseRomgiSource", "setSingleChoiceItems")
require(download_set, "items.size >= 2", "(1..items.size).toList()", "playlistName", "signature")
require(task_store, 'put("schema", 3)', 'put("downloadSet"', "parseDownloadSet", "previous.downloadSet?.signature")
require(engine, "task.downloadSet?.let", "preparedDiscs", "persistSet", "BACKGROUND_MULTIDISC_SAVED")
require(collection, "catalog.resolveEditions", "selected.toDownloadSet", "downloadSet =", "sources.any")
require(persistent, "StoredRomMember", "isMultiDisc", "persistSet", "M3uPlaylist.content", "created.asReversed()", 'put("schema", 2)', 'put("members"')
require(persistent, "ownedUris.map(::deleteUri).all { it }")
require(public_store, "publishSet", "stored.members.sortedBy", "M3uPlaylist.content", "publishText")
require(filters, "displayTitle", '"usa" in region || "united states" in region -> 0', '"japan" in region -> 1')
require(paging, "discRank", "artworkRank", "THEN 0", "THEN 1", "THEN 2")
require(profiles, 'profile("ps1"', 'setOf("cue", "bin", "chd", "m3u"')
require(TESTS / "catalog/RomgiEditionParserTest.kt", "discTokensCollapseIntoOneEditionWithoutLosingRevision", "m3uRejectsPaths")

if failures:
    print(f"READY_CORE_01B_FAILED checks={checks} failures={len(failures)}")
    for failure in failures:
        print(f"- {failure}")
    raise SystemExit(1)

print(f"READY_CORE_01B_OK checks={checks}")

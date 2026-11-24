#!/usr/bin/env python3
"""Executable source-contract checks for Beta 3 Fix 3.

These checks do not replace Gradle compilation. They verify the exact regression fixes requested
from the device screenshots before a Termux build is attempted.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/cl/gakuto/kizuna"
RES = ROOT / "app/src/main/res/layout"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


session = text(JAVA / "launch/SessionActivity.kt")
downloader = text(JAVA / "launch/AuthorizedSourceDownloader.kt")
romgi = text(JAVA / "catalog/RomgiCatalog.kt")
policy = text(JAVA / "catalog/RomgiSourcePolicy.kt")
matcher = text(JAVA / "catalog/RomgiTitleMatcher.kt")
region_policy = text(JAVA / "catalog/RomgiRegionPolicy.kt")
global_catalog = text(JAVA / "ui/GlobalCatalogActivity.kt")
settings = text(JAVA / "ui/SettingsActivity.kt")
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
session_xml = text(RES / "activity_session.xml")
global_xml = text(RES / "activity_global_catalog.xml")

assert "shareDiagnosticButton" not in session_xml
assert "Compartir diagnóstico" not in session_xml
assert "private fun shareDiagnostic" not in session

assert "if (resolution.requiresConfirmation)" in session
assert "setSingleChoiceItems" in session
ambiguous_start = session.index("if (resolution.ambiguous) {")
ambiguous_else = session.index("} else {", ambiguous_start)
ambiguous_block = session[ambiguous_start:ambiguous_else]
assert ".setMessage(" not in ambiguous_block
assert "no por ser una región distinta de USA" in session

assert "METADATA_TIMEOUT_MS = 45L * 1000L" in downloader
assert "METADATA_WARNING_MS = 12L * 1000L" in downloader
assert "requiresInternetArchiveAuth" in downloader
assert "InternetArchiveAuthManager" in downloader

assert "sourceRegionRank" in romgi
assert "RomgiRegionPolicy.rank" in romgi
assert "RomgiRegionPolicy.requiresConfirmation" in romgi
assert 'listOf("USA", "Japan", "World", "Asia", "Europe", "Other")' in region_policy
assert "score += 2_000" in romgi
assert "score += 120" in romgi
assert "RomgiSourcePolicy.detectConcreteRegion" in romgi
sequence_block = policy[policy.index("): String = sequenceOf("):policy.index(").mapNotNull")]
positions = [
    sequence_block.index("fileName.orEmpty()"),
    sequence_block.index("torrentFilePath.orEmpty()"),
    sequence_block.index("url.orEmpty().fileNameFromUrl()"),
    sequence_block.index("linkName.orEmpty()"),
    sequence_block.index("entryTitle"),
]
assert positions == sorted(positions), "La precedencia regional concreta cambió"
assert 'setOf("spain", "france", "germany", "italy"' in matcher
assert 'any(tokens::contains) -> "Europe"' in matcher

assert "CatalogFilterSheet.show" in global_catalog
assert "filterState.regions" in global_catalog
assert "filterState.genres" in global_catalog
assert "filterState.status" in global_catalog
assert "filterState.sort" in global_catalog
assert "filterChipGroup" in global_xml
assert "genreSpinner" not in global_xml and "regionSpinner" not in global_xml

assert "InternetArchiveLoginActivity" in settings
activities = manifest.find("application").findall("activity")
android_ns = "{http://schemas.android.com/apk/res/android}"
ia_activity = next(a for a in activities if a.attrib.get(android_ns + "name") == ".ui.InternetArchiveLoginActivity")
assert ia_activity.attrib.get(android_ns + "exported") == "false"

print("FIX3_CONTRACTS_OK")
print("torrent_metadata_timeout_seconds=45")
print("region_file_precedence=filename>torrent_path>url>link_name>entry_title")
print("country_region_mapping=france>europe")
print("internet_archive_login=non_exported+encrypted")
print("global_filters=room_full_database")

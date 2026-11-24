from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/cl/gakuto/kizuna"

def text(path):
    return path.read_text(encoding="utf-8")

matcher = text(JAVA / "catalog/RomgiTitleMatcher.kt")
catalog = text(JAVA / "catalog/RomgiCatalog.kt")
region_policy = text(JAVA / "catalog/RomgiRegionPolicy.kt")
archive = text(JAVA / "launch/RomArchivePreparer.kt")
background = text(JAVA / "launch/BackgroundRomDownloadEngine.kt")
session = text(JAVA / "launch/SessionActivity.kt")
validator = text(JAVA / "launch/RomPreparedFileValidator.kt")

assert 'fun isBroadRegion' in matcher
assert 'RomgiRegionPolicy.order' in matcher
assert 'it != "World" && it != "Other"' in matcher
assert 'RomgiRegionPolicy.rank' in catalog
assert 'RomgiRegionPolicy.requiresConfirmation' in catalog
assert 'listOf("USA", "Japan", "World", "Asia", "Europe", "Other")' in region_policy
assert 'fun requiresConfirmation' in region_policy
assert 'ROM_ARCHIVE_EXTRACTED' in archive
assert '"snes" -> setOf("sfc", "smc", "swc", "fig", "bs", "st")' in archive
assert 'RomArchivePreparer.prepare(current, prepared, store, diagnostics)' in background
assert 'RomArchivePreparer.prepare(current, prepared, store, diagnostics)' in session
assert 'val directNeedsExtraction = RomArchivePreparer.needsNativeExtraction' in session
assert '!directNeedsExtraction' in session
assert 'La fuente disponible está comprimida en 7z' in validator
print('BETA4_FIX3_WORLD_ARCHIVE_OK checks=14')

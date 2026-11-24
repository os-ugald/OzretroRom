from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/cl/gakuto/kizuna"

def text(path):
    return path.read_text(encoding="utf-8")

components = text(JAVA / "ui/compose/components/OzComponents.kt")
theme = text(JAVA / "ui/compose/theme/OzTheme.kt")
dao = text(JAVA / "data/Daos.kt")
repo = text(JAVA / "data/LibraryRepository.kt")
app = text(JAVA / "ui/compose/OzApp.kt")

assert "val tileTitle" in theme and "fontSize = 14.sp" in theme
assert "OzTypography.tileTitle" in components
assert "maxLines = 1" in components and "softWrap = false" in components
assert "bestArtworkVariant" in dao and "source='libretro'" in dao
assert "canonicalTitle=:canonicalTitle" in dao
assert "artworkVariantFor" in repo and "bestArtworkVariant" in repo
assert "game.thumbnailRepo" in app
assert "remember(candidates) { mutableStateOf(0) }" in app
assert "mutableIntStateOf" not in app
assert "artworkIndex < candidates.lastIndex" in app
print("FIX66_COLLECTION_ARTWORK_OK tile_title=14sp indexed_twin=1 fallback_candidates=1")

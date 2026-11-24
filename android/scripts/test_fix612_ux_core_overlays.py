#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/cl/gakuto/kizuna"
checks = []

def need(path, *tokens):
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        assert token in text, f"{path}: missing {token!r}"
        checks.append((str(path.relative_to(ROOT)), token))

need(JAVA / "data/LibraryRepository.kt", "favoriteHome", "artworkVariantFor(game)")
need(JAVA / "ui/compose/components/OzComponents.kt", "var artworkIndex by remember(candidates) { mutableStateOf(0) }", "candidates.getOrNull(artworkIndex)")
need(JAVA / "launch/SessionActivity.kt", "The system profile is Oz's maintained recommendation", "profile?.retroArchCore?.takeIf(String::isNotBlank)")
need(JAVA / "ui/LibrarySettingsActivity.kt", "Biblioteca y almacenamiento", "globalRomTreeUri", "systemRomTreeUri", "localArtworkTreeUri")
need(JAVA / "launch/RetroOverlayManager.kt", "https://buildbot.libretro.com/assets/frontend/overlays.zip", "connection.url.protocol.equals(\"https\"", "sanitizeZipPath", "MAX_DOWNLOAD_BYTES", "MAX_UNPACKED_BYTES")
need(JAVA / "ui/SettingsActivity.kt", "downloadOfficialOverlays", "SCOPE_ADVANCED", "romLibrarySection.visibility = View.GONE", "artworkLibrarySection.visibility = View.GONE")
need(JAVA / "ui/compose/RetroPreparationPanel.kt", "OZ // BOOT SEQUENCE", "Preparando partida", "MiniStep")
need(ROOT / "app/src/main/res/layout/activity_session.xml", "@drawable/bg_session_console", "@color/electric_blue", "@color/arcade_cream")
need(ROOT / "app/src/main/res/layout/activity_settings.xml", "romLibrarySection", "artworkLibrarySection", "Overlays RetroArch oficiales", "downloadRetroOverlaysButton")
print(f"FIX612_UX_CORE_OVERLAYS_OK checks={len(checks)}")

# PLATFORM FACTORY V1 — PHASE 1B MEDIA PROFILE WIRING

**Date:** 2026-09-12
**Branch:** `feature/gba-catalog-v1`
**Executor:** Claude Code (repository executor), AUTO mode, R2-classified scope.

## EXACT SCOPE

Wire `RomArchivePreparer.nativeExtensions()` and `RomArchivePreparer.extensionPriority()` (private)
to read from the already-characterized `PlatformMediaProfile` (Phase 1A) for `nes`, `snes`,
`genesis`, `gba`, `n64`, with a per-`systemId` fallback to legacy tables for everything else.
`extractionLimit()`, `needsNativeExtraction()` and `prepareZip()` were left untouched — they either
have no hardcoding to remove for these 5 platforms, or inherit correctness transitively.

## ANCHOR PRE-CHANGE RESULT

`RomArchivePreparerMigratedPlatformAnchorTest` (new, intentionally hardcoded expected values) was
written and run **before** touching `RomArchivePreparer.kt`:

```
tests=9, failures=0, errors=0 (against unmodified RomArchivePreparer.kt)
```

Confirms the anchor is a faithful snapshot of pre-wiring behavior, including the N64 case
(`nativeExtensions("n64")` lists `"n64"` first, but the real `prepareZip()` still picks `.z64`).

## PRODUCTION DIFF

Only `app/src/main/java/cl/gakuto/kizuna/launch/RomArchivePreparer.kt` was modified. Full diff:

```diff
-    fun nativeExtensions(systemId: String): Set<String> = when (systemId) {
-        "nes" -> setOf("nes", "fds", "unf", "unif")
-        "snes" -> setOf("sfc", "smc", "swc", "fig", "bs", "st")
+    fun nativeExtensions(systemId: String): Set<String> =
+        PlatformMediaProfiles.forSystem(systemId)?.nativeExtensions ?: legacyNativeExtensions(systemId)
+
+    private fun legacyNativeExtensions(systemId: String): Set<String> = when (systemId) {
         "gb" -> setOf("gb")
         "gbc" -> setOf("gbc", "gb")
-        "gba" -> setOf("gba")
-        "n64" -> setOf("n64", "z64", "v64")
         "master-system" -> setOf("sms", "bin")
-        "genesis" -> setOf("md", "gen", "bin", "smd")
         "game-gear" -> setOf("gg", "bin")
         ... (unchanged entries omitted) ...
     }

     private fun extensionPriority(name: String, systemId: String): Int {
         val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
-        val preferred = when (systemId) {
-            "snes" -> "sfc"
-            "n64" -> "z64"
-            "genesis" -> "md"
-            "gbc" -> "gbc"
-            "ngpc" -> "ngc"
-            "wonderswan-color" -> "wsc"
-            "ps2" -> "iso"
-            "gamecube", "wii" -> "rvz"
-            else -> nativeExtensions(systemId).firstOrNull().orEmpty()
-        }
+        val preferred = PlatformMediaProfiles.forSystem(systemId)
+            ?.let { it.preferredExtension ?: it.nativeExtensions.firstOrNull() }
+            ?: legacyPreferredExtension(systemId)
         return if (extension == preferred) 2 else 1
     }
+
+    private fun legacyPreferredExtension(systemId: String): String = when (systemId) {
+        "gbc" -> "gbc"
+        "ngpc" -> "ngc"
+        "wonderswan-color" -> "wsc"
+        "ps2" -> "iso"
+        "gamecube", "wii" -> "rvz"
+        else -> legacyNativeExtensions(systemId).firstOrNull().orEmpty()
+    }
```

## HARDCODING REMOVED

1. `nativeExtensions()`: entries for `nes`, `snes`, `genesis`, `gba`, `n64` (5 entries).
2. `extensionPriority()`: entries for `snes`→`sfc`, `genesis`→`md`, `n64`→`z64` (3 entries; `nes`
   and `gba` never had an explicit entry here, so none was removed for them).

## FALLBACK RETAINED

Every other `systemId` (gb, gbc, master-system, game-gear, ngp, ngpc, pc-engine, wonderswan(-color),
atari-lynx/2600/5200/7800, ps1, ps2, gamecube, wii, dreamcast, saturn — plus the implicit `else`
default for anything unmapped) continues through `legacyNativeExtensions`/`legacyPreferredExtension`,
byte-for-byte the same tables as before, just renamed. `extractionLimit()`, the
`arcade-fbneo` early return in `prepare()`, and `MULTI_FILE_OPTICAL_SYSTEMS` handling are
completely untouched.

## TEST RESULTS (post-wiring)

```
RomArchivePreparerMigratedPlatformAnchorTest (re-run UNCHANGED): tests=9, failures=0, errors=0
PlatformMediaProfileCharacterizationTest (Phase 1A, unmodified): tests=6, failures=0, errors=0
PlatformCoreResolutionCharacterizationTest (Phase 0, unmodified): tests=8, failures=0, errors=0
RomArchivePreparerOpticalTest (pre-existing, unmodified):        tests=4, failures=0, errors=0
NesCoreResolutionContractTest (pre-existing, unmodified):        tests=1, failures=0, errors=0
GenesisCoreResolutionContractTest (pre-existing, unmodified):    tests=1, failures=0, errors=0
```

`BUILD SUCCESSFUL`, **29/29 tests passed** across all six suites, both before and after the
production change. No test was modified to make production pass.

**Explicit verification:**
- nes parity: `nesNativeExtensions`/`nesEffectivePreferredExtensionIsNes` PASS (anchor)
- snes parity: `snesNativeExtensions`/`snesEffectivePreferredExtensionIsSfc` PASS (anchor)
- genesis parity: `genesisNativeExtensions`/`genesisEffectivePreferredExtensionIsMd` PASS (anchor)
- gba parity: `gbaNativeExtensions` PASS (anchor)
- n64 parity + z64 winner: `n64NativeExtensions`/`n64EffectivePreferredExtensionIsZ64NotN64` PASS (anchor)
- non-migrated fallback unchanged: `RomArchivePreparerOpticalTest` (ps2, gamecube, wii, ps1,
  dreamcast, snes cross-check) PASS unmodified

## BEFORE/AFTER BEHAVIOR

No observable behavior change for any systemId. `RomArchivePreparer.nativeExtensions("n64")` and
the effective tie-break winner for a synthetic n64/z64/v64 zip are identical before and after
(`{"n64","z64","v64"}` and `"z64"` respectively, confirmed by the same anchor test run twice).

## EVIDENCE

Anchor test result XML pre- and post-change
(`app/build/test-results/testDebugUnitTest/TEST-cl.gakuto.kizuna.launch.RomArchivePreparerMigratedPlatformAnchorTest.xml`,
captured at two different timestamps in this session), the full `git diff` of
`RomArchivePreparer.kt` above, and `git status --porcelain`/`git diff --check` output from this
session.

## INFERENCE

The per-`systemId`-whole (not per-field) fallback rule designed in Phase 1B's plan held exactly as
predicted — no case required mixing profile and legacy data for the same systemId. The anchor
test's necessity (flagged as a Phase 1B planning finding, since Phase 1A's own characterization
test becomes circular once production reads from the profile) is now empirically confirmed: without
it, this changeset would have had no independent regression detector for these 5 platforms.

## UNKNOWN

None new. BIOS, FBNeo, downloader, GBA SAF repair, Source Index and emulator selection were not
touched and no new fact about any of them was produced by this changeset.

## REGRESSIONS

NONE. 29/29 tests passed both before and after the change; the anchor test proves the 5 migrated
platforms behave identically; `RomArchivePreparerOpticalTest` proves representative non-migrated
platforms (ps2, gamecube, wii, ps1, dreamcast) are unaffected.

## ROLLBACK

`git checkout -- app/src/main/java/cl/gakuto/kizuna/launch/RomArchivePreparer.kt` (or revert the
commit once one exists) restores the exact pre-wiring file. `PlatformMediaProfile.kt` and its data
are untouched by this changeset either way — no Room or persisted-data migration is involved, so
rollback carries zero data risk.

## RESULTING ARCHITECTURE

`PlatformMediaProfile` is now the live runtime authority for native-extension and preferred-extension
resolution for NES, SNES, Genesis, GBA and N64. Every other systemId remains governed by the
original (renamed) legacy tables, unchanged. `MediaKind`, `extractionSizeLimit` remain defined in
the profile but are not yet consumed by any production code path (no migrated platform needed a
non-default value for them).

## NEXT PHASE READINESS

Phase 1B is complete. Remaining Platform Factory V1 work — BIOS migration (deferred, semantics
unresolved), migrating additional platforms' `nativeExtensions`/`extensionPriority`/`extractionLimit`
entries, deduplicating `EmulatorProfile.acceptedExtensions` against `PlatformMediaProfile.nativeExtensions`,
and any Neo Geo/CPS integration — all remain separately gated and not authorized by this document.

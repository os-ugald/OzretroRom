# Session Report — GBA REAL SAF/DEVICE EVIDENCE, first run against repaired `persist()`

**Date of work:** 2026-08-31
**Branch:** `feature/gba-catalog-v1`
**Repo HEAD at run time:** `f85dcfe` (`docs(governance): correct REAL SAF/DEVICE EVIDENCE claim and stale HEAD`).
`app/src/main/java/cl/gakuto/kizuna/launch/PersistentRomLibrary.kt` last changed in **`27c7b1f`** — this run exercises the **repaired** `persist()` (with the `renameTo` promotion step), not the pre-repair working-tree code that failed on 2026-08-30.
**Risk level:** R2 (device evidence for a cross-cutting persistence path) — Product-Owner-authorized.
**Executor:** Claude Code (Sonnet 5).
**Report state:** untracked, evidence folder untracked, for Oz Forge — Command review before staging and before any further Dashboard change.

---

## 1. What was requested

Run exactly one instrumented test —
`cl.gakuto.kizuna.launch.PersistentRomLibraryRealSafDeviceTest#externalStorageProviderCreatesAndPromotesPendingArtifactWithoutRoomPendingReference`
— on the real Xiaomi 14T against the current `persist()` code, and capture versioned evidence (JUnit XML, UTP result, filtered logcat, HTML report) under `docs/ai/evidence/2026-08-31_gba-real-saf-device-run/` (not `.artifacts/`). Report the raw result without interpretation. Do not commit the evidence folder.

This is the operation whose only prior device attempt (2026-08-30, pre-repair code, logs only under `.artifacts/gba-real-saf-device-evidence-01/`, unversioned) **FAILED** with
`IllegalArgumentException: "La ROM persistida no coincide con el tamaño esperado."` at `PersistentRomLibrary.kt:262` of that revision.

---

## 2. Steps executed

| Step | Command | Outcome |
|---|---|---|
| 0. Sanity | `adb -s R4RWNBOZPFVSL765 get-state`; `git rev-parse --short HEAD`; `git status --porcelain` | `device`; `f85dcfe`; clean tree. Device `2406APNFAG` (Xiaomi 14T) / Android 15 / API 35 / locale `en-US`. |
| 1. Clear device folder | `adb shell 'rm -f /sdcard/Download/OzSafEvidence/* ; rm -rf …; mkdir -p …'` | BEFORE: 4 stale files from the 30-Aug runs (`.oz-pending-process-death.part`, `PhysicalPendingValid.gba`, `ProcessDeathPending.gba`, `RealSafProbe-450165843041831.gba`). AFTER: `total 0`. |
| 2. Clear log buffer | `adb -s R4RWNBOZPFVSL765 logcat -c` | exit 0 |
| 3. Build + install + run | `ANDROID_SERIAL=R4RWNBOZPFVSL765 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='cl.gakuto.kizuna.launch.PersistentRomLibraryRealSafDeviceTest#externalStorageProviderCreatesAndPromotesPendingArtifactWithoutRoomPendingReference' --stacktrace` | `BUILD SUCCESSFUL in 1m 12s`, `GRADLE_EXIT=0` |
| 4. Capture evidence | `adb logcat -d -s …`; `adb shell ls …`; `cp` of `androidTest-results/` + `reports/androidTests/` | files under this folder |

---

## 3. Raw result

**PASS.**

### 3.1 Verdicts (every layer)

- Gradle: `> Task :app:connectedDebugAndroidTest` → `Starting 1 tests on 2406APNFAG - 15` → `Finished 1 tests on 2406APNFAG - 15` → `BUILD SUCCESSFUL in 1m 12s`. `connectedDebugAndroidTest` fails the build on any test failure; it did not.
- JUnit XML (`androidTest-results/junit.xml`):
  ```xml
  <testsuite name="cl.gakuto.kizuna.launch.PersistentRomLibraryRealSafDeviceTest" tests="1" failures="0" errors="0" skipped="0" time="5.666" timestamp="2026-08-31T18:54:00" hostname="localhost">
    <testcase name="externalStorageProviderCreatesAndPromotesPendingArtifactWithoutRoomPendingReference" classname="cl.gakuto.kizuna.launch.PersistentRomLibraryRealSafDeviceTest" time="4.474" />
  </testsuite>
  ```
- UTP (`androidTest-results/device/test-result.textproto`): `test_status: PASSED` (suite and test case).
- Per-test logcat (`androidTest-results/device/logcat-per-test.txt`): `TestRunner: started: …` … `TestRunner: finished: …` with **no exception**, no `persistFailure`, no `IllegalArgumentException`, no `"no coincide con el tamaño"`.

### 3.2 Behaviour observed on the real `ExternalStorageProvider`

From `androidTest-results/2406APNFAG - 15/logcat-…​.txt`:

- Grant transfer worked: `pickerResult code=-1 …/tree/primary%3ADownload%2FOzSafEvidence flags=195`, `pickerGrantTransferred target=cl.gakuto.kizuna … flags=3`.
- Provider confirmed: `provider=com.android.externalstorage/com.android.externalstorage.ExternalStorageProvider`, `treeUri=… exists=true directory=true write=true`.
- **Rename probe** (independent of `persist`): `renameObservation phase=before … documentId=primary:Download/OzSafEvidence/.oz-rename-probe.part` → `phase=after … documentId=primary:Download/OzSafEvidence/OzRenameProbe.gba` → `renameProbe success=true`. On this provider `renameTo` changes the document id/URI and the file stays readable.
- **`persist()` promotion**:
  `storedUri=…/document/primary%3ADownload%2FOzSafEvidence%2FRealSafProbe-497440426100802.gba storedName=RealSafProbe-497440426100802.gba bytes=4194304`
- **Durable record** promoted to terminal state:
  `"displayName":"RealSafProbe-497440426100802.gba", "state":"COMPLETE", "ownership":"OZ_OWNED", "bytes":4194304, "sha256":"bfeca891da5d7aff1cbaec2fcbecee73c8d15820f9bb07aff93eb208b6eeb4a3"` — the `uri` field is the real file URI, **not** an `.oz-pending-*` URI.
- **Physical tree**: `physicalChildren=[(RealSafProbe-497440426100802.gba, …)]` — exactly one file, no pending marker left.
- **Room**: the only write to `games.contentUri` is
  `UPDATE games SET contentUri=?, libraryOrigin=? WHERE id=?` with
  `content://…/RealSafProbe-497440426100802.gba, DOWNLOADED, real-saf-device-game`.
  Zero `.oz-pending-` substrings in any Room query (`grep -c 'oz-pending'` over the captured `roomQueries` line → `0`). The test's assertion "Room received a URI that still names a pending artifact" therefore did not trip.
- **Device tree after the run** (`device-tree-after.txt`):
  ```
  -rw-rw---- 1 u0_a279 media_rw 4194304 2026-08-31 14:53 RealSafProbe-497440426100802.gba
  ```

### 3.3 Same error as 2026-08-30, or different?

**Neither — it did not error.** The 2026-08-30 failure was
`IllegalArgumentException: "La ROM persistida no coincide con el tamaño esperado."` (`PersistentRomLibrary.kt:262`, pre-repair revision, no `renameTo` step; the artifact was left named `.oz-pending-<hash>.part` and the post-write `canRead(record.uri) && persistedLength == record.bytes` check failed, then the catch deleted it → `physicalChildren=[]`).

This run, against `27c7b1f`'s `persist()`, the added `require(target.renameTo(targetName))` promotion + `finalUri = target.uri` read produced a readable, correctly-sized file and a `COMPLETE` record. The 4.474 s test completed with no thrown exception.

**This is a different code path than the failed attempt.** The 2026-08-30 FAIL and this 2026-08-31 PASS are not the same experiment; they cannot be compared as a regression/fix pair without noting that the code under test changed (`27c7b1f`).

---

## 4. Evidence inventory (this folder)

```
report.md                                     — this file
gradle-connectedDebugAndroidTest.txt          — full gradle console (BUILD SUCCESSFUL, GRADLE_EXIT=0)
logcat-filtered.txt                           — adb logcat -d -s OzRealSafEvidence TestRunner AndroidJUnitRunner AndroidRuntime System.err
device-tree-after.txt                         — ls -la /sdcard/Download/OzSafEvidence after the run
androidTest-results/
  junit.xml                                   — JUnit XML (failures=0 errors=0); was TEST-2406APNFAG - 15-_app-.xml
  test-result.pb                              — UTP suite result (binary)
  device/
    test-result.textproto                     — UTP result, human-readable (test_status: PASSED)
    test-result.pb
    logcat-per-test.txt                       — per-test logcat captured by the runner (renamed from the runner's long name)
    device-info.pb, meminfo, cpuinfo, aapt.exe.*.ok.txt, utp.0.log, testlog/, profiling/
androidTest-report-html/
  index.html, package-report.html, class-report.html, css/, js/  — Gradle HTML report
```

**Curation note.** The files were copied from `app/build/outputs/androidTest-results/connected/debug/` and `app/build/reports/androidTests/connected/debug/`, then a few paths were shortened for cross-platform checkout (Windows path-length limit; the device subdir `2406APNFAG - 15` and the runner's ~180-char per-test logcat name could not be added on Windows). Renames: `2406APNFAG - 15/` → `device/`; the long per-test logcat → `logcat-per-test.txt`; `TEST-…​.xml` → `junit.xml`; the two HTML files → `package-report.html` / `class-report.html`. **File contents are byte-for-byte the runner output.** The HTML report's internal navigation links still point at the original names and will 404; the authoritative machine-readable verdicts are `androidTest-results/junit.xml` and `androidTest-results/device/test-result.textproto`.

Total ≈ 851 KB. `.artifacts/gba-real-saf-device-evidence-01/` (the 30-Aug pre-repair FAIL logs) is untouched and remains the record of that separate attempt.

---

## 5. Open items / decisions for Forge

1. **Dashboard §REAL SAF/DEVICE EVIDENCE.** Currently both lines say `NOT RUN` (commit `f85dcfe`, pushed). If this evidence is accepted, line 1 (create/promote PENDING artifact without a Room PENDING reference) can move `NOT RUN → PASS` with citation to this folder. The other five operations (rename identity, replacement, ownership, active/inactive deletion, PENDING recovery, orphan cleanup) **remain `NOT RUN`** — their device tests (`externalStorageProviderDeletesOnlyOwnedAndPreservesInactiveActiveSemantics`, `externalStorageProviderRecoversAndCleansPendingSafely`, process-death phases) were not run here.
2. **Single run, single device.** One pass on one Xiaomi 14T. No repeat runs, no second device, no other SAF providers (SD card, USB OTG, third-party providers).
3. **`REAL_PROCESS_DEATH_EVIDENCE`** unchanged — still `NOT RUN`, still blocked by DocumentsUI grant transfer for the two-phase proof.
4. **Evidence folder is untracked** by request. If accepted, stage explicitly:
   `git add "docs/ai/evidence/2026-08-31_gba-real-saf-device-run"` — then a separate Dashboard commit for the verdict change. No push performed.

# PLATFORM FACTORY V1 — SHARED FOUNDATION SLICE A: RETROARCH TRANSPORT SPIKE

**Date:** 2026-09-13
**Branch:** `feature/gba-catalog-v1`
**WORK_ID:** PLATFORM-FACTORY-V1.SHARED-FOUNDATION-A-TRANSPORT-SPIKE-01
**Executor:** Claude Code (repository executor), AUTO mode.

## RESULT UP FRONT

**The physical spike was NOT executed.** `adb devices -l` was run at the start of this changeset and
returned an empty device list — no Android device or emulator is connected to this machine. Per this
project's own standing rule ("`NOT RUN` is never `PASS`") and the spike plan's own instruction ("If
the real config cannot be read safely for verification, STOP_TO_FORGE rather than weaken the
non-mutation acceptance criterion"), no device-dependent step was attempted, simulated, or
fabricated. Everything below records exactly what was and was not possible without a device.

## DEVICE

**NOT RUN** — no device connected. `adb devices -l` output: empty list (header line only, zero rows).

## RETROARCH_BUILD

**NOT RUN** — cannot query installed package/version without a connected device.

## BASE_CONFIG

**NOT RUN** — the real `retroarch.cfg` path is device- and installed-package-relative
(`createPackageContext(packageName,0).getExternalFilesDir(null)/retroarch.cfg`, per
`RetroArchManager.defaultConfigPath()`); its actual resolved value cannot be determined without a
device to query.

## BEFORE_HASH

**NOT RUN** — no device to read the real config from. Per the plan's explicit instruction, this
blocks the entire spike rather than proceeding with a weakened acceptance criterion.

## TEMP_FILES

**NOT RUN** — not created. Per the plan, these are meant to be pushed to a live device
(`Environment.DIRECTORY_DOWNLOADS/OzRetroRom/Spike/`); with no device, there is nothing to push to,
and creating them locally with no way to verify their effect would not constitute evidence.

## TRANSPORT_A1 / TRANSPORT_A2

**NOT RUN** (both).

## CONTROL_1 / OPTION_1 / CONTROL_2 / OPTION_2

**NOT RUN** (all four launches).

## STATE_LEAKAGE

**NOT RUN** — cannot be assessed without at least two real launches.

## CONFIG_MUTATION

**NOT RUN** — cannot be assessed without a before/after hash of a real device's config file.

## PERMISSIONS

**NOT RUN** — RetroArch's actual storage-permission state (the central open question this spike
exists to answer) cannot be queried without a device.

## CLEANUP

N/A — no temp files were ever created on any device, so there is nothing to clean up. No local
scratch files were created either, since authoring them without a device to push them to and verify
against would not produce evidence, only unverified artifacts.

## AFTER_HASH

N/A — no before-hash exists to compare against.

## EVIDENCE

What COULD be confirmed from the repository alone, without a device, and is recorded here as
verified repo facts (not spike results):
- Production launch component: `ComponentName(packageName, "com.retroarch.browser.retroactivity.RetroActivityFuture")`
  (`RetroArchManager.kt:587`, used at lines 164, 178).
- Production Intent extras: `EXTRA_ROM="ROM"`, `EXTRA_CORE="LIBRETRO"`, `EXTRA_CONFIGFILE="CONFIGFILE"`
  (`RetroArchManager.kt:589-591`).
- Known RetroArch package variants Oz targets: `com.retroarch.aarch64`, `com.retroarch`,
  `com.retroarch.ra32` (`RetroArchManager.kt:606-609`).
- `adb devices -l` was actually run in this session and returned zero devices — this negative result
  is itself the evidence recorded here.

## UNKNOWN

Everything the spike was designed to determine remains UNKNOWN, exactly as it was before this
changeset: whether RetroArch can read a path under Oz's public
`Environment.DIRECTORY_DOWNLOADS/OzRetroRom/...` staging area; whether `core_options_path`/
`global_core_options` (Transport A1) or a content-specific `.opt` under an isolated Config-directory
(Transport A2) actually applies a per-launch override on the real installed build; whether the
`#12901`-style "only works once" regression affects that build; the installed build's actual
`#include`/override precedence order (the specific point Forge asked to be empirically verified
rather than assumed). None of this can be resolved without a connected device.

## RESULT

**UNKNOWN** — not FAIL, not PASS. The spike could not be run at all; no evidence was gathered that
would let any success or failure criterion be evaluated one way or the other.

## RISK

R2 (unchanged) — no new risk was introduced (no code, no device state changed), and no risk was
retired either, since the central open question remains exactly as open as before this changeset.

## NEXT STEP

This spike requires a physical or emulated Android device, reachable via `adb`, with a RetroArch
variant installed (any of `com.retroarch.aarch64`/`com.retroarch`/`com.retroarch.ra32`) and at least
one core+content pair already working through Oz's normal flow. Re-run this exact plan
(`docs/ai/evidence/2026-09-13_platform-factory-v1-shared-foundation-a-transport-spike-plan.md`,
including Forge's Transport A1/A2 separation and the precedence-verification preliminary step) once
such a device is available.

---

## DEVICE EXECUTION — 2026-09-13

**WORK_ID:** PLATFORM-FACTORY-V1.SHARED-FOUNDATION-A-TRANSPORT-SPIKE-01 (re-run, device connected)
**Executor:** Claude Code (repository executor), AUTO mode.
**Trigger:** Forge instruction — "A device is now connected... Resume from DEVICE PREFLIGHT and
execute the physical spike exactly as specified."

The original NOT RUN record above is preserved unmodified. Everything below is new evidence gathered
in this device-execution pass. **No production or test code was modified. Nothing was staged,
committed, pushed, reset, or cleaned.** All spike artifacts were created either in the session
scratchpad (local) or under a disposable device folder that was deleted at the end (see CLEANUP).

### DEVICE

Physical Android device via `adb`, serial `R4RWNBOZPFVSL765`, Xiaomi `2406APNFAG` ("degas"),
Android 15 (API 35). Confirmed via `adb devices -l` and `adb shell getprop`.

### RETROARCH_BUILD

Package `com.retroarch`, version `1.22.2_GIT`, built `Sep 1 2026`, targetSdk 36, `arm64-v8a`. Confirmed
via `adb shell dumpsys package com.retroarch` and the RetroArch log's own `=== Build ===` header
(`[INFO] Version: 1.22.2`, `[INFO] Built: Sep 1 2026`). `MANAGE_EXTERNAL_STORAGE = allow` confirmed via
`adb shell appops get com.retroarch`.

### CONFIGFILE_PATH_VERIFIED

Real config confirmed at `/storage/emulated/0/Android/data/com.retroarch/files/retroarch.cfg`
(matches `RetroArchManager.defaultConfigPath()`'s computed shape). `BEFORE_HASH` (MD5)
`7ed734e51c72fc0700312b266c384886`, mtime `1788668354`, size `117271` bytes, recorded before any
spike launch.

RetroArch's real libretro core directory is **app-private**
(`/data/user/0/com.retroarch/cores/genesis_plus_gx_libretro_android.so`), not the public files dir —
discovered by reading `libretro_directory` out of the real config, not assumed. RetroArch's own
**Config** directory (`rgui_config_directory`) is set to **public** storage
(`/storage/emulated/0/RetroArch/config`), which already held a real, pre-existing
`Genesis Plus GX/Genesis Plus GX.opt` file from prior genuine use. The real config also has
`game_specific_options = "true"` and `core_option_category_enable = "true"` set, and (before the
spike) `core_options_path = ""`, `global_core_options = "false"`.

Test option chosen directly from that real `.opt` file (not invented):
`genesis_plus_gx_overscan = "disabled"` (default value on this device).

### PRECEDENCE_CHECK

Forge required empirical verification of `#include`/override precedence rather than assuming
documented "first-wins" behavior. Two spike CONFIGFILEs were authored and tested:

- **v1** (override-then-include): override keys first, `#include "<real cfg>"` last.
- **v2** (include-then-override): `#include "<real cfg>"` first, override keys after.

**Result: both orderings produced the identical outcome — the override did not take effect in
either case.** This is itself the empirical precedence-check result: on this build, this test does
not distinguish a precedence effect, because the override failed regardless of position.

### TRANSPORT_A1

**FAIL.** Transport A1 (`global_core_options="true"` + `core_options_path="<public path>/spike-options.opt"`,
mixed into a CONFIGFILE with a trailing/leading `#include` of the real config) was tested three times
total: OPTION_1 (v1 ordering), OPTION_1-retry (v2 ordering), OPTION_2 (v1 ordering, repeat launch to
probe the `#12901`-style "only works once" regression). All three produced the same negative,
reproducible result: the core reported the baseline value, not the override.

Direct log evidence (RetroArch's own native log file, not logcat — this build does not emit
RetroArch-tagged lines to logcat; log files live under `/storage/emulated/0/RetroArch/logs/`):
- OPTION_1 (`retroarch__2026_09_13__11_41_24.log`): `[Environ] GET_VARIABLE: genesis_plus_gx_overscan = "disabled"`
- OPTION_2 (`retroarch__2026_09_13__11_47_54.log`): `[Environ] GET_VARIABLE: genesis_plus_gx_overscan = "disabled"`

Both expected `"enabled"` per `spike-options.opt`; both returned the untouched baseline. On-screen
confirmation via RetroArch's own "CORE AV_INFO" debug overlay corroborates this: every override
launch showed `Size: 256x224` / `Scale X/Y: 6.22/5.45`, identical to the CONTROL baseline, when
captured during a comparable steady-state scene (see CONTROL_1/OPTION_1/CONTROL_2/OPTION_2 below for
the one initial false-lead this project corrected before concluding).

The `#include` mechanism itself was confirmed working, isolating the failure specifically to the
`core_options_path`/`global_core_options` keys: the same logs show
`[Override] Redirecting save file to "/storage/emulated/0/RetroArch/saves/Genesis Plus GX/CrusaderOfCenty.srm"`
and the equivalent save-state line — both of which are the REAL user config's custom paths, not
compiled-in defaults, proving the included real config was genuinely read and applied for at least
those settings.

A full-log grep for any mention of `core_options_path`, `global_core_options`, `.opt`, `#include`,
or "loading config" (`grep -n -iE "config file|configfile|global_core_options|core_options_path|\.opt|loading config|append"`)
returned **zero matches** in both pulled logs. This is inconclusive by itself — it is not known
whether this build simply never logs these specific settings even when they work, or whether they
were silently ignored — so it is recorded as UNKNOWN rather than treated as proof of a specific
mechanism of failure.

### TRANSPORT_A2

**NOT RUN.** Transport A2 (a content-specific `<game>.opt` dropped directly into RetroArch's own
public Config directory, e.g. `/storage/emulated/0/RetroArch/config/Genesis Plus GX/CrusaderOfCenty.opt`,
relying on `game_specific_options="true"` which is already enabled on this device) was designed in
the plan and in Forge's field list, but was not executed in this pass — TRANSPORT_A1 consumed the
available spike time and, given its clean 3-for-3 reproducible failure, priority went to confirming
that result solidly (both precedence orderings + repeat-launch) and completing the non-mutation/
cleanup verification, rather than starting a second, less time-bounded transport test. This is an
honest gap, not a fabricated PASS/FAIL — recorded under UNKNOWN/NEXT STEP below.

### CONTROL_1

Real, unmodified `CONFIGFILE` (the real `retroarch.cfg`) launched via `adb shell am start` replicating
`buildDirectLaunchIntent()`'s extras (`ROM`, `LIBRETRO`, `CONFIGFILE`). Baseline confirmed on-screen:
`Size: 256x224`, `Scale X/Y: 6.22/5.45` (screenshot `control1.png`).

### OPTION_1

v1 spike CONFIGFILE (override-then-include). First screenshot was ambiguous — captured during an
achievement-popup/SEGA-splash transition, showing a different `Scale X/Y` (4.98/5.45) that is a
scene-type artifact, not a real effect (`option1.png`). Caught before drawing a false-positive
conclusion; a second screenshot 4s later during a comparable steady-state scene showed
`Size: 256x224` / `Scale X/Y: 6.22/5.45` — identical to CONTROL_1, i.e. the override did not apply
(`option1b.png`). Confirmed via log (`GET_VARIABLE: genesis_plus_gx_overscan = "disabled"`).

### CONTROL_2

Real, unmodified `CONFIGFILE` launched again (`am force-stop` first for a clean cold start). First
screenshot again landed on the same achievement/SEGA-splash transition (`control2.png`); a second
screenshot 4s later during steady state confirmed the identical baseline:
`Size: 256x224` / `Scale X/Y: 6.22/5.45` (`control2b.png`). No leakage from the OPTION_1/precedence-
check launches that preceded it.

### OPTION_2

Reconciliation note: Forge's field list names one CONTROL_1/OPTION_1/CONTROL_2/OPTION_2 sequence, but
this execution also needed a separate precedence check (v1 vs v2 ordering) per Forge's own earlier
correction to the plan. The v2-ordering launch was run as an "OPTION_1-retry" for the precedence
check (see PRECEDENCE_CHECK above); **OPTION_2 here is a repeat launch of the v1 override config**,
run a second consecutive time (with `am force-stop` in between) specifically to test the
`libretro/RetroArch#12901`-style "core_options_path only works the first time a core is loaded"
regression. Result: `Size: 256x224` / `Scale X/Y: 6.22/5.45` (`option2.png`), and log confirms
`GET_VARIABLE: genesis_plus_gx_overscan = "disabled"` — identical failure on the repeat launch. Since
the override never worked even once, this test cannot distinguish "#12901 regression" from "the
override mechanism does not work on this build/setup at all" — recorded as UNKNOWN, not as a
positive confirmation of #12901 specifically.

### STATE_LEAKAGE

None observed. CONTROL_2 (after two failed OPTION launches) returned exactly to the same baseline as
CONTROL_1. No new `.opt` file was created for this content under RetroArch's Config directory despite
`game_specific_options="true"` being enabled (`find /storage/emulated/0/RetroArch/config -newer
retroarch.cfg` found no `Crusader`-named file; the one newer file found, `Beetle PSX HW.opt`, is for an
unrelated core and unrelated to this spike's content).

### USER_CONFIG_MUTATION

**None.** `AFTER_HASH` (MD5) `7ed734e51c72fc0700312b266c384886` — identical to `BEFORE_HASH`. mtime
and size also unchanged (`1788668354`, `117271` bytes). Re-read of `core_options_path`,
`global_core_options`, `config_save_on_exit` after all launches shows their original real values
(`""`, `"false"`, `"true"` respectively) — confirming `config_save_on_exit="false"` in the spike
CONFIGFILE successfully prevented any write-back to the real config, and more fundamentally that the
real config was never touched at all.

### NEW_PERMISSION_REQUIRED

None. All spike files were staged under the same public shared-storage pattern Oz already uses in
production for core provisioning (`Environment.DIRECTORY_DOWNLOADS/OzRetroRom/...`, here a sibling
`Spike/` folder instead of `Cores/`). RetroArch's existing `MANAGE_EXTERNAL_STORAGE=allow` state (not
newly granted by this spike) was sufficient for it to read the spike CONFIGFILE, the `.opt` file, and
the ROM — confirmed by the fact that the `#include` of the real config worked and the ROM loaded and
played correctly in every launch.

### CLEANUP

All spike files deleted from device: `/storage/emulated/0/Download/OzRetroRom/Spike/CrusaderOfCenty.md`,
`spike-configfile.cfg`, `spike-configfile-v2.cfg`, `spike-options.opt`. Post-cleanup listing of the
parent folder shows no `Spike` entry remaining. Verified a normal, fully unmodified launch (real ROM
path from Oz's own existing session history, real `CONFIGFILE`) still works correctly after cleanup —
game loaded into live gameplay (`cleanup-verify.png`). Real config re-hashed one final time and still
matches `BEFORE_HASH`. RetroArch force-stopped to leave the device idle.

### RESULT

**FAIL** (for Transport A1, the only transport actually tested). The override mechanism
(`global_core_options` + `core_options_path`, mixed with `#include` of the real user config) did not
apply the test core option in 3 out of 3 attempts, across both `#include` orderings and a repeat
launch. This is a clean, reproducible negative result — not a NOT RUN, not an UNKNOWN on the core
question of "does Transport A1 work." Per this project's standing rule, this FAIL is reported as-is
and is not softened.

Two of the plan's non-negotiable acceptance criteria that do NOT depend on the override working were
independently confirmed PASS: (4) the real user config was not mutated (hash-identical before/after),
and (5) no private/root storage access was required (public Downloads staging was sufficient for
RetroArch to read every spike file).

Transport A2 (content-specific `.opt` in RetroArch's own public Config directory) remains
**NOT RUN** — this is the recommended next empirical step, since `game_specific_options` is already
enabled on this device and a real per-core `.opt` file already exists there, making it at least as
plausible a transport as A1 and untested rather than failed.

### EVIDENCE

- Screenshots: `control1.png`, `option1.png`, `option1b.png`, `control2.png`, `control2b.png`,
  `option2.png`, `cleanup-verify.png` (session scratchpad, not part of the repo).
- RetroArch native logs pulled in full: `ra-log-option1-v1.log` (561 lines, OPTION_1),
  `ra-log-option2.log` (OPTION_2) (session scratchpad, not part of the repo).
- Device commands and their raw output, run via `adb` this session (device preflight queries, config
  reads/hashes, `am start` launches, `am force-stop`, log pulls, cleanup `rm`/`ls` verification).
- Spike CONFIGFILEs authored this session: `spike-configfile.cfg` (v1), `spike-configfile-v2.cfg`
  (v2), `spike-options.opt` (session scratchpad, not part of the repo, deleted from device per
  CLEANUP).

### UNKNOWN

- **Root cause of the Transport A1 failure** is not established. Candidates not yet distinguished:
  whether `core_options_path`/`global_core_options` require a config file loaded a specific way
  (not mixed with `#include`); whether this specific installed build (`1.22.2_GIT`, built
  Sep 1 2026) has a bug or behavior change versus the documented upstream mechanism; whether some
  other precondition (e.g. a UI-side toggle) is required despite both keys being set directly in the
  active CONFIGFILE. The log's total silence on these two keys (zero matches for
  `core_options_path`/`global_core_options`/`.opt`/`#include` anywhere in either pulled log) could
  mean either "processed silently, no log line exists for this at any verbosity captured" or "never
  read at all" — not distinguishable from the evidence gathered.
  **Recorded as UNKNOWN, not guessed.**
- Whether Transport A2 (content-specific `.opt` in RetroArch's own public Config directory) succeeds
  where Transport A1 failed — **NOT RUN**, recommended as the next spike step.
  **Recorded as UNKNOWN, not guessed.**
- Whether the `libretro/RetroArch#12901` regression specifically applies to this build — not
  distinguishable from "the mechanism doesn't work here at all," since it never worked even once.
  **Recorded as UNKNOWN, not guessed.**

### RISK

R2 (unchanged). No code changed, no device state persisted beyond the spike (fully cleaned up), no
new risk introduced. The central open question this spike exists to answer (a viable per-launch
core-option transport for Platform Factory V1) is now *partially* retired: Transport A1 is
empirically disqualified for this build; Transport A2 remains open.

### NEXT STEP

Re-run the same plan's Transport A2 procedure specifically (content-specific `.opt` dropped into
RetroArch's own public Config directory for the test content, relying on the already-enabled
`game_specific_options="true"`), on the same or an equivalent device, before any Forge decision on
which transport Platform Factory V1 should standardize on. Do not select Transport A1 for production
use based on this evidence — it failed empirically, 3 for 3, on the currently supported build.

### STOP_TO_FORGE

YES.

---

## TRANSPORT A2 DEVICE EXECUTION — 2026-09-13

**WORK_ID:** PLATFORM-FACTORY-V1.SHARED-FOUNDATION-A-TRANSPORT-SPIKE-01 (Transport A2 only)
**Executor:** Claude Code (repository executor), AUTO mode.
**Trigger:** Forge instruction — "DO NOT rerun Transport A1... Execute ONLY Transport A2:
content-specific `<game>.opt` in RetroArch's verified public Config directory." Same device, build,
core, content, and test option as the A1 pass above. **No production or test code was modified.
Nothing was staged, committed, pushed, reset, or cleaned. No RetroArch settings were changed manually.
No new permission was requested or granted.**

### PRECONDITIONS

- **CONTENT_OPT_PATH verification method:** no real per-content `.opt` file existed anywhere on this
  device before this pass (`find /storage/emulated/0/RetroArch/config -iname '*.opt'` returned only
  10 core-level files, one per core, all named `<CoreName>/<CoreName>.opt` — no per-content example to
  copy). The content-specific path was therefore derived from RetroArch's documented convention (same
  directory as the core-level file, named after the content's basename instead of the core's name) and
  then **empirically confirmed correct by the device itself**: the resulting log explicitly reports
  `[Core] Game-specific core options found at "<the exact path we chose>"` (see OPTION_1/OPTION_2
  below) — i.e., the path was verified by RetroArch's own runtime behavior, not merely asserted.
- Real content path used (unchanged from the A1 pass's production Sessions ROM, read-only, never
  modified): `/storage/emulated/0/Download/OzRetroRom/Sessions/b8eabf55-9caa-465b-9670-6acd4cd6a666/Crusader of Centy (USA).md`.
- `game_specific_options = "true"` and `core_option_category_enable = "true"` were already set in
  the real config before this spike began — confirmed by direct read, not changed by this spike.

### CONTENT_OPT_PATH

`/storage/emulated/0/RetroArch/config/Genesis Plus GX/Crusader of Centy (USA).opt`
(core display name `Genesis Plus GX`, same directory RetroArch already uses for that core's
core-level `.opt`; content basename taken verbatim from the real ROM filename minus extension).

### PREEXISTING_FILE_STATE

None. No file existed at `CONTENT_OPT_PATH` before this spike (confirmed by directory listing before
creating it). Per the plan's precondition, since nothing pre-existed, there is nothing to restore —
CLEANUP only needed to delete the file this spike created.

Hashes recorded before any OPTION launch, for files that could be affected:
- Real `retroarch.cfg`: MD5 `7ed734e51c72fc0700312b266c384886`, mtime `1788668354`, size `117271`
  (identical to the value already recorded in the A1 pass above — unchanged in the interim).
- Core-level `Genesis Plus GX/Genesis Plus GX.opt` (the file that must NOT be touched by this
  content-specific test): MD5 `7483690ec4d828cc4c0cdf7c5c784190`, mtime `1787226920`, size `2556`.

### CONTROL_1

Real config, real ROM, no content-specific `.opt` present. Log (`retroarch__2026_09_13__11_59_01.log`):
`[Environ] GET_VARIABLE: genesis_plus_gx_overscan = "disabled"` — baseline, as expected.

**Methodology note:** the on-screen "CORE AV_INFO" `Size`/`Scale` readout, used as a secondary visual
signal in the A1 pass, turned out to be an unreliable comparator for this content when using the real
Sessions ROM: RetroArch auto-loaded that ROM's own real `.state.auto` save state
(`[State] Loading state ".../Crusader of Centy (USA).state.auto"`, confirmed in the log), dropping the
game into live outdoor gameplay where Genesis Plus GX legitimately renders `Size: 320x224` (H40 mode)
during action versus `256x224` (H32 mode) during dialogue/menu scenes — a game-internal mode switch
unrelated to any RetroArch override. This was visible when comparing `a2-control1b.png` (320x224,
outdoor gameplay) against the A1 pass's screenshots (256x224, dialogue scenes) using the file-only
spike copy, which had no save state and always opened on a dialogue/intro scene. **For this reason,
the log's `GET_VARIABLE` line — not the screenshot — is treated as the authoritative signal for every
launch in this A2 pass**; screenshots are recorded only as supplementary color.

### OPTION_1

Content-specific `.opt` created at `CONTENT_OPT_PATH` with `genesis_plus_gx_overscan = "enabled"`
(same key/value used throughout this investigation), byte-format-verified identical in style to the
real core-level `.opt` (`od -c` comparison: same `key = "value"\n` layout, LF line ending, no CRLF).
Real `CONFIGFILE` used unmodified — Transport A2 requires no config-file changes at all, unlike
Transport A1.

Log (`retroarch__2026_09_13__12_00_25.log`) shows a **new, positive signal never seen in the entire
A1 pass**:
`[Core] Game-specific core options found at "/storage/emulated/0/RetroArch/config/Genesis Plus GX/Crusader of Centy (USA).opt".`
— RetroArch actively detected and named the exact file this spike created, confirming the
`CONTENT_OPT_PATH` naming convention is correct.

However, the actual value returned was still the baseline:
`[Environ] GET_VARIABLE: genesis_plus_gx_overscan = "disabled"` — the override was detected but not
applied to the value the core received. All neighboring `GET_VARIABLE` lines for other options also
show unmodified core-level defaults, consistent with our file only containing the one overridden key.

### CONTROL_2

Content-specific `.opt` deleted (`rm`), real config, real ROM relaunched. Log
(`retroarch__2026_09_13__12_01_45.log`) shows no `Game-specific core options found` line (file
correctly absent) and `[Environ] GET_VARIABLE: genesis_plus_gx_overscan = "disabled"` — clean
reversion to baseline, matching CONTROL_1 exactly. No leftover effect from OPTION_1.

### OPTION_2

Content-specific `.opt` recreated at the identical `CONTENT_OPT_PATH` with the identical content,
relaunched. Log (`retroarch__2026_09_13__12_02_16.log`) again shows
`[Core] Game-specific core options found at ".../Crusader of Centy (USA).opt".` and again
`[Environ] GET_VARIABLE: genesis_plus_gx_overscan = "disabled"` — **identical result to OPTION_1**:
file detected both times, override applied neither time. This rules out a `#12901`-style
"only-works-once" pattern specifically (the behavior is consistent, not degrading between the first
and second load) but does not identify why the value itself never takes effect.

### STATE_LEAKAGE

None observed. CONTROL_2 (after OPTION_1) returned to the exact baseline value. No new files were
created by RetroArch itself as a side effect of any A2 launch (spot-checked: only the file this spike
created/removed appeared/disappeared from the `Genesis Plus GX/` config folder across all four
launches).

### USER_CONFIG_MUTATION

None. Real `retroarch.cfg` AFTER_HASH `7ed734e51c72fc0700312b266c384886`, mtime `1788668354`, size
`117271` — identical to the value recorded before this A2 pass began (and identical to the A1 pass's
own before/after values, confirming no drift across the whole day's spike work).

### OTHER_OPT_MUTATION

None. Core-level `Genesis Plus GX/Genesis Plus GX.opt` AFTER_HASH `7483690ec4d828cc4c0cdf7c5c784190`,
mtime `1787226920`, size `2556` — byte-identical to the value recorded before this A2 pass. The
content-specific mechanism did not read from, write to, or otherwise disturb the core-level file.

### PERMISSIONS

None newly required or granted. The content-specific `.opt` was written to RetroArch's own existing
public Config directory (`rgui_config_directory`, already `/storage/emulated/0/RetroArch/config` in
the real, pre-existing config) using the same `adb push`-level access already exercised in the A1
pass; RetroArch read it under its existing `MANAGE_EXTERNAL_STORAGE=allow` state. No RetroArch UI
setting was changed manually — `game_specific_options` and `core_option_category_enable` were already
`"true"` before this spike and were left untouched throughout.

### CLEANUP

Content-specific `.opt` deleted from device (`rm -f ".../Crusader of Centy (USA).opt"`); post-delete
listing of the `Genesis Plus GX/` folder shows only the two files that existed before this spike
(`Genesis Plus GX.opt`, `Genesis Plus GX.slangp`) — nothing to "restore" since nothing pre-existed at
that path. Real config and core-level `.opt` re-hashed one final time: both still match their
pre-spike hashes exactly (see USER_CONFIG_MUTATION / OTHER_OPT_MUTATION above). A final normal launch
(real ROM, real config, no spike files present) was run and confirmed working (`a2-cleanup-verify.png`
— live gameplay). RetroArch force-stopped, leaving the device idle.

### RESULT

**FAIL**, with one materially positive finding worth separating from the A1 result: RetroArch's
runtime **did** locate and acknowledge the content-specific `.opt` file at the exact path this spike
derived and created, on both OPTION launches (`[Core] Game-specific core options found at ...`) — a
signal Transport A1 never produced once. But the actual overridden value was never observed to reach
the core (`GET_VARIABLE` returned the baseline `"disabled"` on both OPTION launches, matching the
un-overridden `CONTROL_1`/`CONTROL_2` baseline exactly). Behavior was fully deterministic and
reproducible across two independent create/delete/recreate cycles — not a timing or nondeterminism
issue. All non-negotiable safety criteria passed: no config mutation, no other-`.opt` mutation, no
state leakage, no new permission, no manual RetroArch UI setup.

Per Forge's stated PASS/FAIL criteria: "the `.opt` is ignored" is not a fully accurate description
(the file was demonstrably NOT ignored — it was found and logged by name), but "OPTION_1 visibly/
logically applies the requested value" did not hold. This is reported as **FAIL** on the applied-value
criterion, while explicitly recording the partial positive (file discovery) rather than collapsing it
into an undifferentiated FAIL identical to Transport A1's complete silence.

### EVIDENCE

- Screenshots: `a2-control1.png`, `a2-control1b.png`, `a2-option1.png`, `a2-control2.png`,
  `a2-option2.png`, `a2-cleanup-verify.png` (session scratchpad, not part of the repo).
- RetroArch native logs pulled in full: `a2-control1.log`, `a2-option1.log`, `a2-control2.log`,
  `a2-option2.log` (session scratchpad, not part of the repo).
- Content-specific `.opt` file authored this session: `a2-option.opt` (session scratchpad, not part
  of the repo; pushed to and deleted from the device per CLEANUP).
- Direct device commands and output this session: precondition hashes, `find`/`ls` directory
  listings, `adb push`/`rm`, `am start`/`am force-stop`, log pulls, post-cleanup hash re-verification.

### UNKNOWN

- **Why the detected content-specific `.opt` file's value was not applied to the core**, despite
  being found and logged by exact path and despite byte-identical formatting to the working core-level
  file. Not distinguishable from the evidence gathered whether this is: a version/schema mismatch
  (the file may need to declare a full/versioned option set rather than a single partial key, unlike
  what a device-side experiment can confirm without expanding scope into modifying more files — which
  Forge's instructions explicitly forbid without a STOP_TO_FORGE); a build-specific defect in how
  `.opt` values are merged after the "found" log line; or an additional undocumented precondition.
  **Recorded as UNKNOWN, not guessed** — testing the "full option set" hypothesis would require
  writing a content-specific `.opt` with every key the core exposes, which goes beyond "create only
  the exact content-specific `.opt` required" and was correctly not attempted per Forge's explicit
  scope boundary ("if doing so becomes necessary, STOP_TO_FORGE instead of expanding scope").
- Whether a real, RetroArch-UI-created content-specific `.opt` (i.e., one saved through RetroArch's
  own Quick Menu "Save Game Options" flow, which this spike deliberately avoided per "do not change
  RetroArch settings manually / do not require manual RetroArch UI setup") would differ in format or
  content from what this spike authored, and if so, whether that format is what "Game-specific core
  options found" actually requires to also apply the value, not just detect the file.
  **Recorded as UNKNOWN, not guessed.**

### RISK

R2 (unchanged). No code changed, no device state persisted beyond the spike (fully cleaned up and
re-verified byte-identical), no new risk introduced. Both transports investigated under Slice A
(A1 and A2) are now empirically FAIL for applying a per-launch core-option override on this build,
though A2 is a partial/more-promising FAIL (file path and naming convention confirmed correct by the
runtime itself) versus A1's total silence.

### NEXT STEP

Before any further device time is spent, Forge should decide whether the "full option-set `.opt`
file" hypothesis is worth authorizing as a scoped follow-up (would require explicitly enumerating and
writing every `genesis_plus_gx_*` option key the core exposes into the content-specific file, not just
the one deterministic test key) — this is a scope expansion this executor did not take unilaterally,
per Forge's explicit instruction to STOP_TO_FORGE rather than expand scope on its own judgment.

### STOP_TO_FORGE

YES.

---

## TRANSPORT A2 FULL-OPTION-SET EXECUTION — 2026-09-13

**WORK_ID:** PLATFORM-FACTORY-V1.SHARED-FOUNDATION-A-TRANSPORT-SPIKE-01 (Transport A2, full-option-set
variant only — the scoped follow-up Forge authorized after the partial-file A2 pass above)
**Executor:** Claude Code (repository executor), AUTO mode.
**Trigger:** Forge instruction — "Test A2 using a FULL option-set content-specific `.opt` derived from
the device's existing working core-level `.opt`... If this full-set test FAILS, do not attempt further
A2 variants. Return to Forge." Same device, build, core, content, and test option as both prior
passes. **No production or test code was modified. Nothing was staged, committed, pushed, reset, or
cleaned. No RetroArch settings were changed manually. No new permission was requested or granted. No
RetroArch UI was used to generate any file.**

### SOURCE_OPT

`/storage/emulated/0/RetroArch/config/Genesis Plus GX/Genesis Plus GX.opt` — verified present at this
exact path again before use (directory listing, unchanged from both prior A2 passes). This file was
only ever **read** (via `adb pull`) to build the content-specific copy locally; it was never written
to on-device.

### SOURCE_HASH

MD5 `7483690ec4d828cc4c0cdf7c5c784190`, mtime `1787226920`, size `2556` bytes — identical to the value
recorded in the prior A2 (partial) pass, confirming it was never touched in the interim. The locally
pulled copy (`a2full-source.opt`) hashed identically (`7483690ec4d828cc4c0cdf7c5c784190`), confirming
a bit-exact transfer with nothing lost or altered by the pull.

### CONTENT_OPT

`/storage/emulated/0/RetroArch/config/Genesis Plus GX/Crusader of Centy (USA).opt`, built by copying
`a2full-source.opt` verbatim (62 lines) and changing exactly one existing line —
`genesis_plus_gx_overscan = "disabled"` → `genesis_plus_gx_overscan = "enabled"` — via a single-line
`sed` substitution anchored to the full original line, with no RetroArch UI involved at any point.
Resulting file: 62 lines (unchanged count), MD5 `5ba279e1af86886482d806416fab7953`. Pushed to the
device and re-hashed on-device: identical (`5ba279e1af86886482d806416fab7953`), confirming a bit-exact
push.

### DIFF_VERIFICATION

`diff a2full-source.opt a2full-content.opt`:

```
37c37
< genesis_plus_gx_overscan = "disabled"
---
> genesis_plus_gx_overscan = "enabled"
```

Exactly one line differs, at line 37, and it is precisely the intended key/value change — no other
keys were added, removed, reordered, or reformatted; line counts match (62/62) in both files.

### CONTROL_1

No content-specific `.opt` present (confirmed via directory listing: only `Genesis Plus GX.opt` and
`Genesis Plus GX.slangp`). Log (`retroarch__2026_09_13__12_09_26.log`):
`[Environ] GET_VARIABLE: genesis_plus_gx_overscan = "disabled"` — baseline, as expected.

### OPTION_1

Full-option-set content-specific `.opt` installed at `CONTENT_OPT` (hash-verified as pushed correctly,
see above). Log (`retroarch__2026_09_13__12_09_52.log`):

- `[Core] Game-specific core options found at "/storage/emulated/0/RetroArch/config/Genesis Plus GX/Crusader of Centy (USA).opt".`
  — file detected by exact path again, as in the partial-file pass.
- `[Environ] GET_VARIABLE: genesis_plus_gx_overscan = "disabled"` — **the requested value was still
  not applied**, despite the file now containing the complete 62-key option set (matching the
  documented "full core-option configuration" shape) rather than a single partial key.

### CONTROL_2

Content-specific `.opt` deleted, real config and ROM relaunched. Log
(`retroarch__2026_09_13__12_10_18.log`): no `Game-specific core options found` line (file correctly
absent), `[Environ] GET_VARIABLE: genesis_plus_gx_overscan = "disabled"` — clean reversion, matching
CONTROL_1 exactly.

### OPTION_2

The identical full-set content-specific `.opt` (same file, same hash `5ba279e1af86886482d806416fab7953`)
recreated at the identical path and relaunched. Log (`retroarch__2026_09_13__12_10_44.log`): again
`[Core] Game-specific core options found at ".../Crusader of Centy (USA).opt".` and again
`[Environ] GET_VARIABLE: genesis_plus_gx_overscan = "disabled"` — **identical result to OPTION_1**.
Deterministic and reproducible across two independent create/delete/recreate cycles, exactly mirroring
the partial-file pass's behavior.

### STATE_LEAKAGE

None observed. CONTROL_2 returned to the exact baseline after OPTION_1. No new files appeared under
`Genesis Plus GX/` beyond the one this spike created and removed itself.

### USER_CONFIG_MUTATION

None. Real `retroarch.cfg` re-hashed after CLEANUP: MD5 `7ed734e51c72fc0700312b266c384886`, mtime
`1788668354`, size `117271` — identical to the value recorded before this pass began, and identical
to every prior hash recorded across the entire day's Transport A1/A2 spike work.

### CORE_OPT_MUTATION

None. Core-level `Genesis Plus GX/Genesis Plus GX.opt` re-hashed after CLEANUP: MD5
`7483690ec4d828cc4c0cdf7c5c784190`, mtime `1787226920`, size `2556` — byte-identical to `SOURCE_HASH`
above. The file used as the copy source was never written to.

### PERMISSIONS

None newly required or granted. Same public Config-directory write path already exercised in the
partial-file A2 pass; no RetroArch UI setting was touched (the content-specific `.opt` was built
entirely offline from a pulled copy of the real core-level file, per Forge's explicit "do not use
RetroArch UI to generate it" instruction).

### CLEANUP

Content-specific `.opt` deleted from device; post-delete listing of `Genesis Plus GX/` shows only the
two files that existed before any A2 work today. Real config and core-level `.opt` re-hashed one final
time: both match their pre-spike values exactly (see USER_CONFIG_MUTATION / CORE_OPT_MUTATION above).
A final normal launch (real ROM, real config, no spike files present) was run and confirmed working —
live gameplay observed (`a2full-cleanup-verify.png`). RetroArch force-stopped, leaving the device idle.

### RESULT

**FAIL.** Even a complete, 62-key, full-option-set content-specific `.opt` — built by copying the
device's own real, working core-level file and changing exactly one already-present key, matching the
documented shape of a full core-option configuration — was detected by RetroArch
(`[Core] Game-specific core options found at ...`, both OPTION launches) but did **not** have its
overridden value applied to the core (`GET_VARIABLE` returned the baseline `"disabled"` both times,
identical to the partial-file result). This is the same outcome as the single-key partial-file test,
now with the "incomplete file" hypothesis eliminated as the explanation. Behavior was fully
deterministic across two independent cycles. All non-negotiable safety criteria passed: zero state
leakage, real config hash-identical, real core-level `.opt` hash-identical, no new permissions, no
manual UI setup.

Per Forge's explicit instruction, **no further A2 variants were attempted** following this FAIL.

### EVIDENCE

- Screenshots: `a2full-control1.png`, `a2full-option1.png`, `a2full-control2.png`, `a2full-option2.png`,
  `a2full-cleanup-verify.png` (session scratchpad, not part of the repo).
- RetroArch native logs pulled in full: `a2full-control1.log`, `a2full-option1.log`,
  `a2full-control2.log`, `a2full-option2.log` (session scratchpad, not part of the repo).
- Locally built files: `a2full-source.opt` (pulled copy of the real core-level file, read-only
  reference), `a2full-content.opt` (the full-set content-specific file actually pushed to the device)
  (session scratchpad, not part of the repo; the device copy was deleted per CLEANUP).
- `diff` output above, showing the single intended line change.
- Direct device commands and output this session: precondition hashes, directory listings,
  `adb pull`/`push`/`rm`, `am start`/`am force-stop`, log pulls, post-cleanup hash re-verification.

### UNKNOWN

- **Root cause of the Transport A2 failure remains unresolved** even with the "incomplete file"
  hypothesis now eliminated. The file is demonstrably found and named correctly by RetroArch's own
  runtime in all four OPTION launches across both A2 passes (partial and full), yet the override value
  never reaches the core. Candidates not distinguishable from the evidence gathered: a build-specific
  defect in this `1.22.2_GIT` (built Sep 1 2026) build's game-specific-options merge path; an
  additional undocumented precondition beyond `game_specific_options`/`core_option_category_enable`
  (both already `"true"` throughout); or a requirement that the file be written through RetroArch's
  own save mechanism specifically (metadata, versioning, or a companion file this investigation did
  not know to look for) rather than being placed directly by an external process — this last
  possibility was explicitly out of scope per Forge's "do not use RetroArch UI to generate it"
  instruction and is reported as an open question, not tested. **Recorded as UNKNOWN, not guessed.**
- Per Forge's explicit instruction, this is the final bounded A2 experiment; no further A2 variant
  hypotheses were tested.

### RISK

R2 (unchanged). No code changed, no device state persisted beyond the spike (fully cleaned up and
re-verified byte-identical), no new risk introduced. Both Transport A1 and Transport A2 (in both its
partial and full-option-set forms) are now empirically closed as FAIL for applying a per-launch
core-option override on this build. The central open question this Slice A spike exists to answer —
a viable per-launch core-option transport for Platform Factory V1 — is now fully exhausted for the
transports identified in the original plan; none succeeded.

### NEXT STEP

Return to Forge, as instructed. No further Transport A2 variant should be attempted by this executor
without new direction. Any path forward (e.g., investigating RetroArch's own save-options code path
directly, testing on a different RetroArch build/version, or abandoning per-launch core-option
overrides as a Platform Factory V1 mechanism in favor of a different design) is a product/architecture
decision for Forge, not something this executor should decide or attempt unilaterally.

### STOP_TO_FORGE

YES.

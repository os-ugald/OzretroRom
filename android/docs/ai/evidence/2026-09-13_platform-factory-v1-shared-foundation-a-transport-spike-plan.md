# PLATFORM FACTORY V1 — SHARED FOUNDATION SLICE A: RETROARCH TRANSPORT SPIKE PLAN

**Date:** 2026-09-13
**Branch:** `feature/gba-catalog-v1`
**WORK_ID:** PLATFORM-FACTORY-V1.SHARED-FOUNDATION-A-TRANSPORT-SPIKE-01
**Status:** Materialized from a completed Plan Mode analysis (read-only investigation, re-synthesis
of official RetroArch/Libretro research already gathered this session, plus targeted repo
verification). No production code touched during planning.

PLAN STATUS: **READY**

## CURRENT CONFIGFILE REALITY

`RetroArchManager.defaultConfigPath(packageName)` computes (never reads/writes)
`createPackageContext(packageName,0).getExternalFilesDir(null)/retroarch.cfg` — the REAL path to the
user's `retroarch.cfg`. `docs/FIX6.11_RETROARCH_CONFIG_BRIDGE.md` documents this as a deliberate
security decision: "Oz only passes RetroArch a path belonging to the selected RetroArch package. It
does not try to obtain private content from the file." RetroArch itself does not validate the
CONFIGFILE path (raw JNI read via `strlcpy`, confirmed in `frontend_unix_get_env()` in the official
source) — the real constraint is Android scoped storage and Oz's own policy, not RetroArch.

## TEMP CONFIG FEASIBILITY

**Important reframing versus the earlier investigation:** the prior "BLOCKED" conclusion applied
specifically to writing into Oz's **private** storage (`cacheDir`/`getExternalCacheDir`), which
Android scoped storage categorically blocks for cross-app reads (not even `MANAGE_EXTERNAL_STORAGE`
lifts this). **But Oz already writes today, in production, to a different public location**:
`Environment.DIRECTORY_DOWNLOADS + "/OzRetroRom/Cores/"` (`RetroArchManager.kt:476,518`, function
`writeToSharedDownloads`, already used for core provisioning). This folder is **not**
`Android/data/<package>` of any app — it is traditional shared public storage, governed by less
restrictive scoped-storage rules. This reopens Option A as plausible **with no new permission**,
reusing the exact same write mechanism already in production — whether RetroArch can actually
*read* that path via native `fopen()` depends on RetroArch's own storage-permission state (which Oz
does not control or guarantee), not on Oz. **This is exactly what the spike must prove empirically —
not assumed.**

## CORE OPTIONS PATH FEASIBILITY

`core_options_path`/`global_core_options` are real `retroarch.cfg` keys (confirmed in official
`configuration.c`). The requirement to enable "Load Content Specific Core Options Automatically" in
RetroArch's UI **stops being a blocker** if Oz places both keys directly INSIDE its own one-shot
launch config (`global_core_options="true"` + `core_options_path="<public path>/<file>.opt"`) —
since that temp file is the active CONFIGFILE only for that one launch, it doesn't require the user
to have pre-configured anything in RetroArch. RetroArch supports a native `#include "file.cfg"`
directive (confirmed in `config_file.c`), with documented "first-wins" precedence — allowing the two
override keys to precede an `#include` of the user's real config, preserving the rest of their
settings without copying them. **Known risk, not resolved by documentation**: the open official issue
`libretro/RetroArch#12901` reports that `core_options_path` "only works the first time a core is
loaded" — an unconfirmed-as-fixed regression. This is exactly what success criteria #2/#3 and the
"SECOND CONTROL LAUNCH" of this spike must verify empirically.

## ANDROID STORAGE BOUNDARY

- Oz's private storage (`cacheDir`/`getExternalCacheDir`): **blocked** for cross-app reads, confirmed
  against official Android documentation (scoped storage, Android 11+), with no permission that
  lifts it.
- `CONFIGFILE` is a raw POSIX file path (not a `content://` URI) — RetroArch opens it natively, not
  via `ContentResolver`. This means the `FLAG_GRANT_READ_URI_PERMISSION` trick (which Oz already uses
  for external apps via SAF) **does not apply here** — there is no way to "grant" SAF permission over
  a path consumed as a plain POSIX string.
- The only viable location category is therefore traditional shared public storage
  (`Environment.DIRECTORY_DOWNLOADS/...` or equivalent) — accessible to any app with storage
  permission, **not** governed by `Android/data`'s stricter rules. Oz already writes there today (see
  above). Whether RetroArch itself can read that path depends on whether RetroArch has been granted
  shared-storage access (which it likely already needs to load the user's own ROMs/BIOS from
  arbitrary folders) — not verified, exactly the spike's question.

## SPIKE PROCEDURE

**None of this requires new Oz code.** It runs entirely via `adb push` + `adb shell am start` with
manual extras, replicating exactly the same extras `RetroArchManager.buildDirectLaunchIntent()`
already builds in production, except the `CONFIGFILE` value.

1. Pick an already-production-supported core/content (e.g. Genesis Plus GX with a test ROM) and ONE
   core option already exposed by that core with an observable effect (e.g. a visual/aspect
   setting) — **the exact key/value is confirmed in RetroArch's own menu on the test device at spike
   time, not invented here.**
2. Push two plain-text files to the device, under `Environment.DIRECTORY_DOWNLOADS/OzRetroRom/Spike/`
   (same folder pattern already used in production for cores, just a `Spike/` subfolder instead of
   `Cores/`):
   - `spike-configfile.cfg`: `global_core_options = "true"` + `core_options_path = "<path to the
     .opt below>"` + `config_save_on_exit = "false"` (added explicitly to remove the risk of
     RetroArch rewriting anything on exit) + `#include "<real path to the user's retroarch.cfg>"`.
   - `spike-options.opt`: only the chosen test option's key/value.
3. Record checksum/timestamp of the user's REAL `retroarch.cfg` before any launch.

## CONTROL LAUNCH

Launch the same core+content through the EXISTING unmodified flow (CONFIGFILE = RetroArch's real
path, exactly as Oz does in production today). Record the chosen option's default value/effect
(baseline).

## OPTION LAUNCH

Launch the SAME core+content via `adb shell am start`, replicating `buildDirectLaunchIntent()`'s
extras but with `CONFIGFILE` pointed at `spike-configfile.cfg`. Confirm visually/via logcat whether
the test option took the override value instead of the baseline.

## SECOND CONTROL LAUNCH

Relaunch through the EXISTING unmodified flow (same as CONTROL LAUNCH). Confirm: (a) the option
returns exactly to baseline (no leak), (b) the user's real `retroarch.cfg` did NOT change
(checksum/timestamp identical to before step 1). Additionally, repeat the OPTION LAUNCH a second time
to specifically test the known `#12901` regression ("only works the first time") — confirm whether
the override still applies on a second run of the same core, or whether RetroArch starts ignoring
`core_options_path` in favor of a `.opt` file it created on its own.

## EVIDENCE TO CAPTURE

Exact installed RetroArch package/variant/version; device model and Android API level; exact
absolute paths used (real retroarch.cfg, temp config, .opt file); checksum/timestamp of the real
retroarch.cfg before and after the 4 runs; screenshots/logcat of each launch (control #1, option,
control #2, option repeat); whether RetroArch created any file on its own (e.g. a `.opt` in its own
config folder) as a side effect — checkable by listing RetroArch's config folder before/after, via
Oz's existing SAF-picker pattern for BIOS folders (`retroArchSystemTreeUri`) or manual file
inspection during the spike.

## SUCCESS CRITERIA

The 7 from Forge's brief, unmodified: (1) launch #1 receives the requested test option; (2) launch #2
without the option returns to baseline; (3) no option leaks between games/launches; (4) the user's
normal RetroArch config is not modified; (5) no private-storage/root write is required; (6) behavior
survives the normal Oz→RetroArch launch flow; (7) exact RetroArch build/device/paths are recorded.

## FAILURE CRITERIA

The 6 from Forge's brief, unmodified — FAIL/UNSUITABLE if: the option only works after manual
RetroArch UI setup beforehand; global config must be mutated; Oz must write into RetroArch's
inaccessible private storage; state leaks into later launches; behavior depends on an undocumented
timing/race; only a rooted device makes it work. UNKNOWN if the evidence cannot distinguish clearly.

## FILES TO CREATE FOR SPIKE

Two plain-text files (`spike-configfile.cfg`, `spike-options.opt`), authored locally and pushed to
the device via `adb push` — **not part of Oz's codebase, never committed**; live in the session
scratchpad or are discarded after the spike.

## PRODUCTION FILES TO MODIFY

NONE — the entire spike runs via `adb`/manual steps, without touching `RetroArchManager.kt` or any
other production file.

## DEVICE REQUIRED

YES — this specific question cannot be resolved by documentation alone (that avenue was already
exhausted in the prior investigation); it requires empirical confirmation on a real Android device
with RetroArch installed.

## UNKNOWN

Whether RetroArch, as normally installed/permissioned by a typical user, can read files under
`Environment.DIRECTORY_DOWNLOADS/OzRetroRom/...` (depends on RetroArch's own permission state, not
Oz's — not verified). Whether the temp config's save-on-exit behavior could, through some
undocumented mechanism, touch the real `#include`d file (proactively mitigated by setting
`config_save_on_exit="false"`, but not confirmed sufficient without the spike). Whether the `#12901`
regression affects whichever RetroArch version Oz actually supports today.

## RISK

**R2** (classification already given by Forge for Slice A as a whole) — although the spike itself
(adb + 2 disposable text files, zero production code) is low-risk/reversible in isolation, the
architecture decision it feeds remains R2 for touching the shared launch contract used by every
platform.

---

## FORGE CORRECTION — 2026-09-13

Recorded verbatim, applied as a correction to the design above (the original design is preserved,
not rewritten, per this session's established practice):

- `core_options_path` is a **GLOBAL** core-options file path.
- The content-specific `<game>.opt` mechanism uses RetroArch's **Config-directory** mechanism
  instead — a *different* transport.
- **Do not conflate these transports.** They must be evaluated as two separate candidates:
  **TRANSPORT A1** (`core_options_path`, the global core-options file redirect) and **TRANSPORT A2**
  (the content-specific `<game>.opt` file placed under an isolated, Oz-controlled RetroArch
  Config-directory path) — matching the two-transport structure the actual spike execution is
  scoped around, superseding this plan document's earlier blended description of both under one
  "CORE OPTIONS PATH FEASIBILITY" section.
- The `#include`/base-config load order must be **verified against the actual precedence behavior of
  the installed RetroArch build** before being relied upon — not assumed from upstream documentation
  alone. This plan's design (override keys first, `#include` of the base config after, relying on
  documented "first-wins" precedence from `config_file.c`) is the documented *upstream* behavior, but
  Forge is correct that the specific installed build's real behavior must be empirically confirmed
  as a preliminary step of the spike itself before the main transport tests proceed, since a
  version/fork mismatch between documented and installed behavior cannot be ruled out from
  documentation alone. **Resolution:** the spike execution must include a small preliminary
  precedence check (a trivial config file defining the same key twice, in each order, confirming
  which value the installed build actually honors) before trusting either ordering for the real
  override.

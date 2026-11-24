PS C:\Users\Osvaldo\StudioProjects\gakuto-kizuna-android> Get-Content .\docs\PROJECT_DASHBOARD.md -TotalCount 110
# Oz RetroRom â€” Project Dashboard

**DASHBOARD_VERSION:** 4.0.13
**UPDATED:** 2026-09-13
**EXECUTIVE_PRIORITY:** `PLATFORM-FACTORY-V1` (product/architecture expansion â€” see
`docs/ai/evidence/2026-09-12_platform-factory-v1-forge-decision.md`)
**TECHNICAL_GATE:** `GBA-CATALOG-01C.IMPLEMENTATION-01.REPAIR-01` â€” remains **open**
as a separate residual validation stream; not superseded, not PASS, not CLOSED
by the Platform Factory priority above
**PUSH_STATE:** `HOLD` â€” evidence + Dashboard commits pending Forge review before push
**MANDATORY_PRE-PUSH_REVIEW:** None; `DOWNLOAD-UX-START-01` is cancelled by Product
Owner and remains cancelled â€” this sync does not reactivate it

**GOVERNANCE_MODE:** Forge Executive Mode (governance/orchestration only; Claude
Code or the assigned workspace executes repository work)

> Operational snapshot only. Product/architecture truth lives in
> `AI_PROJECT_MASTER.md`; repository/Git remains technical truth.

---

## NOW

Repository state verified directly via `git status`/`git log`/`git rev-list` (2026-09-13):

- Branch: `feature/gba-catalog-v1`
- HEAD: `139207e1af935bc75c92f1fd8470ee7bf41a0ffd` ("fix: remove message from
  source-options AlertDialog to unblock SAF item")
- Upstream: `origin/feature/gba-catalog-v1` â€” **ahead 0, behind 0**
- Staged: none
- Tracked modifications (uncommitted, all from PASSed Platform Factory V1
  Phase 1A/1B/Shared-Foundation-B/C work â€” see
  `docs/ai/evidence/2026-09-12_platform-factory-v1-*.md` and
  `docs/ai/evidence/2026-09-13_platform-factory-v1-shared-foundation-{b,c}-*.md`):
  `app/src/main/java/cl/gakuto/kizuna/launch/RomArchivePreparer.kt`,
  `app/src/main/java/cl/gakuto/kizuna/launch/RetroBiosValidator.kt`,
  `docs/AI_PROJECT_MASTER.md`, `docs/AI_WORKSPACE_ROLES.md`, this file.
- Untracked, relevant, same Platform Factory V1 work: `PlatformMediaProfile.kt`,
  `PlatformFirmwareProfile.kt`, `PlatformCoreResolutionCharacterizationTest.kt`,
  `PlatformMediaProfileCharacterizationTest.kt`,
  `RomArchivePreparerMigratedPlatformAnchorTest.kt`,
  `RomArchivePreparerArchiveAsRomsetAnchorTest.kt`,
  `RomArchivePreparerArchiveAsRomsetWiringTest.kt`,
  `RetroBiosValidatorRequirementsAnchorTest.kt`,
  `RetroBiosValidatorFirmwareProfileWiringTest.kt`, and the full
  `docs/ai/evidence/2026-09-12_platform-factory-v1-*.md` +
  `docs/ai/evidence/2026-09-13_platform-factory-v1-*.md` evidence set (Phase 0
  through the Shared Foundation Slice A/B/C closures, the Neo Geo pilot
  qualification/repo-fit plan, and the MAME4droid launch-contract
  qualification) â€” none of this is committed yet, per `PUSH_STATE` below.
- Untracked, pre-existing GBA evidence from earlier sessions (2026-08-31
  through 2026-09-05) also remains present and unreviewed for staging; see the
  repo's own RESUME CHECK history for that inventory rather than duplicating
  it here.
- Important untracked `.artifacts/*`, androidTest assets and caches are present
  and must be preserved.

### GBA Source Index

- Games: **2,368**
- Releases: **3,962**
- Candidates: **4,021**
- Asset SHA-256:
  `6ff70a3763fbbd6871c7e9a29135161c84deec4144d470ff5bb993d519d2af50`
- Source Index route: **AVAILABLE**
- acquisitionEnabled: **FALSE**
- launchValidated: **FALSE**
- physical launch/provisioning/OZ-EQS/public certification: **NO / NOT RUN**

---

## CURRENT REPAIR

Approved multi-local architecture:

- multiple local Releases per Game
- one explicit active `GameEntity.contentUri`
- same Release/new Candidate replaces only that Release
- active deletion clears active pointer; no silent auto-switch
- durable Candidate provenance
- no Room schema migration required

Repair implemented in working tree:

- missing/invalid ownership defaults to `LEGACY_UNKNOWN`
- physical deletion only for explicit `OZ_OWNED`
- durable pre-artifact pending intent
- deterministic `.oz-pending-*.part` marker
- Room update only after COMPLETE
- basic ownership policy host test

### HOST BEHAVIORAL EVIDENCE â€” COMPLETE / PASS

- mutable synthetic SAF provider/backing store covering flags, document/child queries,
  create/write/existence/delete/absence and shared state across `DocumentFile` calls
- `PersistentRomLibraryBehaviorTest`: **11/11 PASS**
- `DownloadSourcePlanTest`: **10/10 PASS**
- `PENDING_INTENT_DURABLE`: **PASS** as host restart/reconstruction evidence
- `ROOM_PENDING_REFERENCE_FORBIDDEN`: **PASS** through Room query interception
- `HOST_BEHAVIORAL_EVIDENCE`: **PASS**
- `HOST_SYNTHETIC_SAF_LIMIT_REACHED`: **NO**

### REAL SAF/DEVICE EVIDENCE â€” REQUIRED / 1 PASS Â· 5 NOT RUN

- `REAL_SAF_DEVICE_EVIDENCE` â€” create/promote a PENDING artifact without a Room
  PENDING reference (test
  `externalStorageProviderCreatesAndPromotesPendingArtifactWithoutRoomPendingReference`,
  run 2026-08-31 on Xiaomi 14T / Android 15 / API 35 / ExternalStorageProvider
  against `persist()` at `27c7b1f`): **PASS**. Source:
  `docs/ai/evidence/2026-08-31_gba-real-saf-device-run/` â€” Gradle BUILD
PS C:\Users\Osvaldo\StudioProjects\gakuto-kizuna-android> Get-Content .\docs\ai\evidence\2026-09-13_platform-factory-v1-neo-geo-mame4droid-launch-contract-qualification.md
# PLATFORM FACTORY V1 â€” NEO GEO MAME4DROID LAUNCH CONTRACT QUALIFICATION

**WORK_ID:** `PLATFORM-FACTORY-V1.NEO-GEO-MAME4DROID-LAUNCH-CONTRACT-QUALIFICATION-01`  
**DATE:** 2026-09-13  
**WORKSPACE:** ðŸŽ® Oz Platform Lab â€” Systems & Emulation  
**RISK:** R2  
**NEXT OWNER:** ðŸ”¥ Oz Forge IV â€” Command  
**IMPLEMENTATION_READY:** NO  
**STOP_TO_FORGE:** YES  

## Scope

This qualification evaluates only whether **MAME4droid Current** exposes an Android external-launch contract that could plausibly allow Oz to select a deterministic Neo Geo AES or MVS target without relying on the failed RetroArch per-launch core-option transports.

This document does **not** implement code, modify the Oz repository, select BEST EMULATOR, declare OZ support/certification/public eligibility, benchmark emulation quality, provision/install MAME4droid, or run a Golden Game Suite.

## Executive summary

Current upstream MAME4droid Current exposes a real Android launcher surface:

- application id: `com.seleuco.mame4d2024`;
- exported activity: `com.seleuco.mame4droid.MAME4droid`;
- `android.intent.action.VIEW`;
- per-Intent string extra named `cli_params`;
- `Intent.getData()` used as the external content location.

The current Java source explicitly reads `cli_params` from the incoming `ACTION_VIEW` Intent and forwards it into the emulator's native value channel as `CLI_PARAMS`. Current MAME documentation independently establishes that machine/system and software/media can be selected through command-line arguments.

Current MAME upstream defines distinct Neo Geo machine short names including:

- `aes` â€” Neo-Geo AES (NTSC);
- `neogeo` â€” Neo-Geo MV-6F BIOS-root MVS system.

This is materially different from a Libretro core-option mode switch: the requested machine can, in principle, be expressed in the MAME launch command.

However, **no physical Oz-device launch was run in this qualification** because this Platform Lab session has no device/ADB bridge and cannot inspect the installed application. Installed status is therefore **UNKNOWN**, not "not installed". No installation or provisioning action was taken.

Therefore:

- `AES_LAUNCH: NOT RUN`
- `MVS_LAUNCH: NOT RUN`
- `AUTOMATABLE_WITH_CURRENT_ANDROID_CONTRACT: UNKNOWN`
- `ZERO_ADMIN_FIT: CONDITIONAL`

The upstream contract is strong enough to justify a **further narrow physical contract investigation**, but not strong enough to declare integration proven.

---

# EVIDENCE

## E1 â€” Current upstream application identity

Current upstream `android-MAME4droid/app/build.gradle` declares:

- `applicationId "com.seleuco.mame4d2024"`
- namespace `com.seleuco.mame4droid`
- `minSdkVersion 29`
- `targetSdkVersion 37`
- current main-tree `versionName '1.38.2'`

Source:  
https://raw.githubusercontent.com/seleuco/MAME4droid-Current/main/android-MAME4droid/app/build.gradle

The repository README currently identifies MAME4droid Current as an Android port of MAME 0.289.

Source:  
https://github.com/seleuco/MAME4droid-Current

## E2 â€” Exported Android activity and Intent filter

Current upstream `AndroidManifest.xml` declares:

- activity: `com.seleuco.mame4droid.MAME4droid`
- `android:launchMode="singleTask"`
- `android:exported="true"`

It exposes an `ACTION_VIEW` filter with `CATEGORY_DEFAULT`, ZIP / 7z MIME types, `content://`, and a `file://` declaration limited by `android:maxSdkVersion="23"`.

Source:  
https://raw.githubusercontent.com/seleuco/MAME4droid-Current/main/android-MAME4droid/app/src/main/AndroidManifest.xml

For the current Android 15 target, the qualification does not rely on the old implicit `file://` route.

## E3 â€” DATA semantics are content-oriented

Current `Emulator.java` reads:

- `Intent.getData()`;
- `Intent.getStringExtra("cli_params")`.

For `content://` data it resolves the name and opens the URI through `ContentResolver`; if necessary, it copies the supplied content into MAME4droid's ROM working area for the session.

For non-`content://` data, current code treats the URI path as a filesystem path and derives filename and parent path.

Therefore:

> `DATA = external content location`

is supported by current source.

The source does **not** establish:

> `DATA = documented AES/MVS machine selector`

Source:  
https://raw.githubusercontent.com/seleuco/MAME4droid-Current/main/android-MAME4droid/app/src/main/java/com/seleuco/mame4droid/Emulator.java

## E4 â€” `cli_params` is a real per-Intent channel

Current `Emulator.java` reads the exact extra name `cli_params` and forwards the resulting string into `Emulator.CLI_PARAMS`.

The JNI bridge exposes the native value setter used by the Java layer.

Sources:

- https://raw.githubusercontent.com/seleuco/MAME4droid-Current/main/android-MAME4droid/app/src/main/java/com/seleuco/mame4droid/Emulator.java
- https://raw.githubusercontent.com/seleuco/MAME4droid-Current/main/android-MAME4droid/app/src/main/jni/mame4droid-jni.c

A MAME4droid Current upstream issue also records external-launcher use of `ACTION_VIEW` plus `cli_params` for MAME command-line switches.

Source:  
https://github.com/seleuco/MAME4droid-Current/issues/79

That issue is compatibility evidence, not proof that the exact installed Oz-device build behaves identically.

## E5 â€” MAME CLI can identify machine/system

Current official MAME 0.289 documentation states that software-list content can be launched as:

`mame <system> <software>`

and general media as:

`mame <system> <media> <software> <options>`

Source:  
https://docs.mamedev.org/usingmame/usingmame.html

Machine/system selection is therefore a first-class MAME launch concept rather than a Neo Geo frontend core option.

## E6 â€” Current MAME Neo Geo machine identity

Current MAME upstream defines:

- `neogeo` â€” Neo-Geo MV-6F, BIOS root, MVS;
- `aes` â€” Neo-Geo AES (NTSC).

It also associates the Neo Geo software list with MVS configurations and filters the AES software-list exposure with `AES`.

Source:  
https://raw.githubusercontent.com/mamedev/mame/master/src/mame/snk/neogeo.cpp

This is emulator capability evidence only.

## E7 â€” Initial storage setup is user-visible

Current MAME4droid documentation recommends an initial External Storage setup using Android Scoped Storage:

1. launch MAME4droid;
2. choose External Storage;
3. choose a folder in the Android picker;
4. grant folder access.

Source:  
https://github.com/seleuco/MAME4droid-Current

Current `MAME4droid.java` also shows storage setup is presented when installation/ROM state is not configured.

Therefore one-time storage setup must be separated from per-game automation.

## E8 â€” External-session exit behavior

For an external ROM session, current `Emulator.java` calls `finishAndRemoveTask()` after native emulation returns and then terminates its process. No structured Activity result contract is established in this flow.

Source:  
https://raw.githubusercontent.com/seleuco/MAME4droid-Current/main/android-MAME4droid/app/src/main/java/com/seleuco/mame4droid/Emulator.java

## E9 â€” Relaunch while already emulating

The exported activity uses `singleTask`.

On a new `ACTION_VIEW` while already emulating, `onNewIntent()` delegates to `checkNewViewIntent()`. Current source updates the Intent and schedules an app restart after a hard-coded 500 ms delay.

Sources:

- https://raw.githubusercontent.com/seleuco/MAME4droid-Current/main/android-MAME4droid/app/src/main/java/com/seleuco/mame4droid/MAME4droid.java
- https://raw.githubusercontent.com/seleuco/MAME4droid-Current/main/android-MAME4droid/app/src/main/java/com/seleuco/mame4droid/helpers/MainHelper.java

Oz must not assume repeated-launch lifecycle reliability until this path is physically verified.

---

# INSTALLED APPLICATION STATUS

**INSTALLED_APPLICATION_STATUS:** UNKNOWN

This Platform Lab environment has no Android device/ADB bridge and cannot enumerate packages or inspect the current Oz device.

No installation, update, provisioning, permission grant, or emulator setup was attempted.

---

# VERIFIED UPSTREAM CONTRACT

## PACKAGE

`com.seleuco.mame4d2024`

Status: **VERIFIED UPSTREAM**

## ACTIVITY

`com.seleuco.mame4droid.MAME4droid`

Properties:

- exported: `true`
- launch mode: `singleTask`

Status: **VERIFIED UPSTREAM**

## INTENT ACTION

`android.intent.action.VIEW`

Status: **VERIFIED UPSTREAM**

## DATA semantics

Verified:

- `content://` URI â†’ opened through `ContentResolver`, may be copied into MAME4droid's ROM working area;
- non-content URI â†’ treated as a filesystem-style path.

Not verified:

- raw AES/MVS machine token as `DATA`;
- ES-DE `%DATA%` behavior as equivalent to current source.

**Conclusion:** DATA should be treated as **content location**, not canonical machine selector.

## EXTRA names

Verified:

`cli_params`

## CLI parameter semantics

Verified at Android layer:

- read from incoming Intent;
- forwarded as `CLI_PARAMS`.

Verified from MAME documentation:

- system short names and software/media arguments are command-line concepts.

Not physically verified on Oz device:

- exact Neo Geo AES tokenization/quoting;
- exact Neo Geo MVS tokenization/quoting;
- whether all intended CLI arguments survive the installed build unchanged.

## Required storage setup

The documented external-storage route requires one-time SAF folder selection and user-granted folder access.

A `content://` launch also requires normal Android URI readability/grant semantics.

No root/private-storage-write contract is identified by the upstream external-launch design.

## Required permissions

For the external-launch contract itself:

- no custom caller permission is declared on the exported activity;
- storage depends on normal Scoped Storage / URI grants;
- Internet, vibration, Wi-Fi state, wake lock, etc. are application features, not caller permissions for launch.

## Return / exit behavior

After an external session:

- task is finished/removed;
- process is terminated;
- no structured result payload to the caller is established.

`RETURN_TO_CALLER:` OS task return plausible; structured callback/result **NOT VERIFIED**.

---

# AES / MVS CONTRACT TARGET

## AES

Current MAME machine short name:

`aes`

Current upstream description:

`Neo-Geo AES (NTSC)`

A bounded physical test would need to prove that the verified exported Activity accepts a per-launch `cli_params` machine/media request containing `aes` with already lawful readable content and without manual mode changes or global state mutation.

**AES_LAUNCH: NOT RUN**

REQUESTED_MACHINE: `aes` candidate contract only; not physically requested  
REQUESTED_CONTENT: none inspected by this workspace  
INTENT / CLI CONTRACT: upstream contract identified, device invocation not performed  
FIRST FRAME: NOT RUN  
ACTUAL_MACHINE/MODE: NOT RUN  
CONTENT_LOADED: NOT RUN  
MANUAL_UI_REQUIRED: NOT DETERMINED PHYSICALLY  
STORAGE_SETUP_REQUIRED: documented route has one-time SAF setup  
RETURN_TO_CALLER: NOT RUN  
ERRORS: device unavailable in this workspace

## MVS

Current MAME BIOS-root short name:

`neogeo`

Current upstream description:

`Neo-Geo MV-6F`

A bounded physical test would need to prove deterministic MVS content launch without changing shared emulator state.

**MVS_LAUNCH: NOT RUN**

REQUESTED_MACHINE: `neogeo` candidate contract only; not physically requested  
REQUESTED_CONTENT: none inspected by this workspace  
INTENT / CLI CONTRACT: upstream contract identified, device invocation not performed  
FIRST FRAME: NOT RUN  
ACTUAL_MACHINE/MODE: NOT RUN  
CONTENT_LOADED: NOT RUN  
MANUAL_UI_REQUIRED: NOT DETERMINED PHYSICALLY  
STORAGE_SETUP_REQUIRED: documented route has one-time SAF setup  
RETURN_TO_CALLER: NOT RUN  
ERRORS: device unavailable in this workspace

---

# PASS CRITERIA ASSESSMENT

## No manual emulator mode change per game

**UPSTREAM CONTRACT:** plausible  
**OZ PHYSICAL EVIDENCE:** NOT RUN

Machine identity can be expressed through MAME launch semantics rather than a Neo Geo core option.

## No shared/global emulator mutation per launch

**UPSTREAM CONTRACT:** plausible

The proposed route uses per-Intent data plus per-Intent `cli_params`; no global Neo Geo mode mutation is inherent.

**OZ PHYSICAL EVIDENCE:** NOT RUN

## No undocumented caller timing dependency

Cold launch: no caller-side timing dependency identified.

Already-running process: current upstream uses an internal fixed 500 ms restart delay after a new VIEW Intent. This is not a required caller sleep, but it is lifecycle behavior that must be physically validated.

Status: **CONDITIONAL / NOT PROVEN**

## No root/private-storage writes

Current upstream supports Scoped Storage and content URI ingestion.

Status: **PLAUSIBLE**, subject to physical validation of the chosen Oz storage route.

---

# CLASSIFICATION

`AUTOMATABLE_WITH_CURRENT_ANDROID_CONTRACT: UNKNOWN`

Reason: the upstream Android surface is materially automatable, but the Oz-device contract has not been physically demonstrated.

`MANUAL_UI_REQUIRED:`  
- per launch: **UNKNOWN physically; upstream design suggests NO**  
- one-time storage setup on documented route: **YES**

`ZERO_ADMIN_FIT: CONDITIONAL`

Reason: per-launch machine selection is plausibly automatable, but current documented storage initialization is user-visible and repeated-launch lifecycle is unqualified.

---

# INFERENCE

1. MAME4droid Current exposes a stronger launch-mode surface for this specific AES/MVS problem than a RetroArch core-option-dependent route because machine identity can be supplied as MAME launch input.

2. A successful contract test would prove only controllability, not emulation quality or BEST EMULATOR.

3. The contract is concrete enough to justify one narrow physical proof rather than a broad MAME integration project.

4. A physical test should prefer a current Android-readable `content://` route rather than revive old implicit `file://` assumptions on Android 15.

5. One-time SAF setup is a product-fit cost and prevents calling the documented route fully zero-admin today.

6. Cold-start success alone would not fully qualify repeated launch because `singleTask`/`onNewIntent()` behavior has separate restart logic.

---

# UNKNOWN

- whether MAME4droid Current is installed on the current Oz device;
- exact installed version/build;
- whether installed build matches current upstream;
- existing storage/SAF state;
- lawful Neo Geo content availability/readability on that device;
- exact physical AES invocation;
- exact physical MVS invocation;
- first frame;
- actual mode reached;
- content loading;
- return to Oz after exit;
- repeated launch behavior;
- additional setup requirements;
- compatibility;
- performance;
- latency;
- save states;
- controller quality;
- BEST EMULATOR.

---

# PRODUCT_FIT_CONSEQUENCE

The route is **not integration-ready**, but it is no longer merely speculative.

Current upstream establishes a real bounded surface:

`exported Activity + ACTION_VIEW + content DATA + per-Intent cli_params`

and MAME establishes first-class AES/MVS machine identifiers.

That combination can plausibly avoid the specific RetroArch dynamic core-option blocker **in principle**.

Missing evidence:

`Oz device â†’ installed MAME4droid build â†’ requested AES/MVS target â†’ actual mode/first frame â†’ clean exit`

Until then:

**MAME4DROID CONTRACT QUALIFICATION = PROMISING BUT UNPROVEN**

---

# RISK

## R0 / R1

Platform Lab may perform:

- upstream source inspection;
- manifest/Intent contract research;
- machine short-name verification;
- bounded manual physical contract runs using already lawful content;
- launch evidence collection without repository changes.

## R2 â€” Forge

Forge decision is required before:

- adopting MAME4droid as an external-emulator route;
- adding/changing global external-emulator launch contracts;
- adding a generic machine/mode selector to Platform Factory;
- provisioning policy;
- storage/SAF automation policy;
- cross-platform EmulatorProfile / launch-routing changes.

## R3

Not reached by this qualification.

Certification/public-support claims would require their own higher-governance path.

---

# RECOMMENDATION

**RECOMMENDATION: further narrow contract investigation justified**

Forge should consider authorizing a bounded physical qualification whose only objective is:

1. determine whether `com.seleuco.mame4d2024` is installed and record exact version/build;
2. do not install if absent without explicit approval;
3. record existing storage/SAF state;
4. if lawful readable content already exists, run one AES launch;
5. run one MVS launch;
6. capture requested machine, actual machine, first frame, content-loaded state, UI intervention, exit/return behavior and logs;
7. optionally repeat once while MAME4droid is already alive to qualify `singleTask`/new-Intent behavior;
8. stop.

Do not proceed into compatibility benchmarking or Golden Game Suite under this WORK_ID.

Do not infer BEST EMULATOR from contract success.

---

PACKAGE: `com.seleuco.mame4d2024`

ACTIVITY: `com.seleuco.mame4droid.MAME4droid`

INTENT_CONTRACT: `ACTION_VIEW` + content `DATA` + optional string extra `cli_params`; exact Neo Geo device invocation NOT YET PHYSICALLY VERIFIED

STORAGE_CONTRACT: documented external route uses one-time SAF authorization; `content://` is handled by current source; no root contract identified

AES_LAUNCH: NOT RUN

MVS_LAUNCH: NOT RUN

AUTOMATABLE_WITH_CURRENT_ANDROID_CONTRACT: UNKNOWN

BEST EMULATOR: OPEN â€” this qualification proves contract plausibility only; per `AI_PROJECT_MASTER.md` Â§8 (BEST EMULATOR WINS), no emulator winner is selected by this document.

MANUAL_UI_REQUIRED: NO PER-LAUNCH PROVEN YET / YES ONE-TIME SAF SETUP ON DOCUMENTED PATH

ZERO_ADMIN_FIT: CONDITIONAL

PRODUCT_FIT_CONSEQUENCE: launch-mode control appears architecturally plausible without RetroArch core options or global Neo Geo mode mutation, but Oz physical contract evidence is absent

RECOMMENDATION: further narrow contract investigation justified

IMPLEMENTATION_READY: NO

STOP_TO_FORGE: YES
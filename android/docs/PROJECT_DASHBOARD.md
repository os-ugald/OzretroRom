# Oz RetroRom — Project Dashboard

> **2026-11-17 / Kaito Android / ANDROID-PS1-DOWNLOAD-01**
> Current explicitly authorized bounded work: safe PS1 acquisition through the existing ROMgi,
> downloader, preparation and persistence contracts. Staged bytes require an explicit resume/delete
> decision before switching source/edition. Prepared in an isolated working-tree copy based
> on `feature/gba-catalog-v1` / `139207e1af935bc75c92f1fd8470ee7bf41a0ffd` plus pre-existing local changes.
> Real checkout application: PENDING. No commit/push. Source Index PS1 and durable CUE/BIN bundles:
> not implemented. Physical/interaction/visual/emulator Gates: NOT RUN. Historical Neo Geo and GBA
> Gates below are not certified or closed by this work. Final tests/build and NEXT are recorded in
> workspace `docs/parallel/ANDROID_PS1_DOWNLOAD_STATUS.md`.

**DASHBOARD_VERSION:** 4.0.13
**UPDATED:** 2026-09-13
**EXECUTIVE_PRIORITY:** `PLATFORM-FACTORY-V1` (product/architecture expansion — see
`docs/ai/evidence/2026-09-12_platform-factory-v1-forge-decision.md`)
**TECHNICAL_GATE:** `GBA-CATALOG-01C.IMPLEMENTATION-01.REPAIR-01` — remains **open**
as a separate residual validation stream; not superseded, not PASS, not CLOSED
by the Platform Factory priority above
**PUSH_STATE:** `HOLD` — evidence + Dashboard commits pending Forge review before push
**MANDATORY_PRE-PUSH_REVIEW:** None; `DOWNLOAD-UX-START-01` is cancelled by Product
Owner and remains cancelled — this sync does not reactivate it

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
- Upstream: `origin/feature/gba-catalog-v1` — **ahead 0, behind 0**
- Staged: none
- Tracked modifications (uncommitted, all from PASSed Platform Factory V1
  Phase 1A/1B/Shared-Foundation-B/C work — see
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
  qualification) — none of this is committed yet, per `PUSH_STATE` below.
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

### HOST BEHAVIORAL EVIDENCE — COMPLETE / PASS

- mutable synthetic SAF provider/backing store covering flags, document/child queries,
  create/write/existence/delete/absence and shared state across `DocumentFile` calls
- `PersistentRomLibraryBehaviorTest`: **11/11 PASS**
- `DownloadSourcePlanTest`: **10/10 PASS**
- `PENDING_INTENT_DURABLE`: **PASS** as host restart/reconstruction evidence
- `ROOM_PENDING_REFERENCE_FORBIDDEN`: **PASS** through Room query interception
- `HOST_BEHAVIORAL_EVIDENCE`: **PASS**
- `HOST_SYNTHETIC_SAF_LIMIT_REACHED`: **NO**

### REAL SAF/DEVICE EVIDENCE — REQUIRED / 1 PASS · 5 NOT RUN

- `REAL_SAF_DEVICE_EVIDENCE` — create/promote a PENDING artifact without a Room
  PENDING reference (test
  `externalStorageProviderCreatesAndPromotesPendingArtifactWithoutRoomPendingReference`,
  run 2026-08-31 on Xiaomi 14T / Android 15 / API 35 / ExternalStorageProvider
  against `persist()` at `27c7b1f`): **PASS**. Source:
  `docs/ai/evidence/2026-08-31_gba-real-saf-device-run/` — Gradle BUILD
  SUCCESSFUL, JUnit `failures=0 errors=0`, UTP `test_status: PASSED`; the
  artifact is promoted from `.oz-pending-<hash>.part` to its real name, the
  durable record reaches `COMPLETE`, and no `.oz-pending-` URI reaches Room.
  This **validates the repaired code**; it is **not** a regression/fix pair with
  the 2026-08-30 failure, which ran a different, pre-repair revision (see next
  line).
- `REAL_SAF_DEVICE_EVIDENCE` — 2026-08-30 attempt (historical): the only earlier
  device attempt exercised **pre-repair working-tree code** — before `27c7b1f`,
  without the `renameTo` promotion step — and **FAILED** with
  `IllegalArgumentException: "La ROM persistida no coincide con el tamaño
  esperado."` (`PersistentRomLibrary.kt:262`, that revision). Not versioned; logs
  under `.artifacts/gba-real-saf-device-evidence-01/`. Superseded by the
  2026-08-31 PASS above.
- `REAL_SAF_DEVICE_EVIDENCE` — rename identity, replacement, ownership,
  active/inactive deletion, PENDING recovery, orphan cleanup: **NOT RUN** — never
  attempted on device, zero evidence.
- `REAL_PROCESS_DEATH_EVIDENCE`: **NOT RUN**; the two-phase proof remains blocked
  by DocumentsUI grant transfer.
- One operation now has passing device evidence against `27c7b1f`; device
  evidence for the remaining five SAF operations and for process death is still
  required. No SAF production defect stands against the current code.

---

## CANCELLED WORK

### `DOWNLOAD-UX-START-01`

Status: **CANCELLED_BY_PRODUCT_OWNER**. This investigation is not a GBA blocker.

**Download can remain on “Calculando…” instead of feeling immediate.**

Required read-only audit against the current source snapshot:

- trace UI tap → candidate resolution → plan creation → scheduler → downloader
- identify synchronous/blocking work before enqueue/first transfer
- compare ROMgi and Source Index start paths
- distinguish:
  - placeholder UI
  - real CPU/disk work
  - torrent metadata/network wait
  - peer discovery
  - other latency
- do not merely relabel a real delay
- preserve exact Candidate identity, durable plans, resume/recovery and no silent failover

Target experience:

`Tap Download → immediate visible response → truthful connection/metadata/peer state → progress/speed on first bytes`

Historical DOWNLOAD-UX details are retained in project history and must not
reactivate this work.

---

## FROZEN / PROTECTED

- SNES catalog/routing foundation
- NES catalog/routing foundation
- Genesis/Mega Drive catalog/routing foundation
- Global Library Identity correction
- Identity Typing foundation
- GBA Source Index production candidate/repro contract

Do not modify frozen systems as collateral damage.

---

## GOVERNANCE V4

Status: **LOCAL ROLE ALIGNMENT UPDATED / REVIEW READY**

Governance V4 does not replace the active technical GBA path.

Core changes:

- federated chat workspaces
- explicit workspace prohibitions and router
- Workspace ≠ Dream Team Role ≠ Executor ≠ Assurance
- R0/R1/R2/R3 risk governance
- Evidence / Inference / Unknown / Decision separation
- durable Markdown handoffs
- no separate Forge Master; successor-chat protocol
- standards/certification-aligned specialist qualification without false credential claims
- `docs/ai/CONTINUOUS_ROLE_QUALIFICATION.md` is the single refresh/assurance protocol; no AI role is externally certified by implication.

---

## GBA RESIDUAL NEXT

(Scoped to the still-open GBA `TECHNICAL_GATE` only — this is not the
executive-level next step; see `EXECUTIVE_PRIORITY`/`NEXT executive step`
under Platform Factory V1 below for that.)

1. Forge + independent QA assurance of the complete GBA repair evidence.
2. Execute the separate two-phase process-death proof when device grants permit.
3. Reevaluate GBA Gate closure; do not claim closure from this sync alone.

---

## GBA ACQUISITION — DEFERRED (Product Owner direction, 2026-09-13)

GBA acquisition/download implementation is **DEFERRED**. This is a sequencing
decision, not a cancellation and not a reopening of the GBA `TECHNICAL_GATE`
above.

- Continue Platform Factory / shared multi-system acquisition work first.
- Once acquisition works across multiple systems, return to GBA and connect
  it to the shared foundation.
- GBA acquisition is **not** the current executive priority.
- This does **not** reactivate `DOWNLOAD-UX-START-01` — that item remains
  **CANCELLED_BY_PRODUCT_OWNER** (see `## CANCELLED WORK` above).
- GBA is not a blocker for Neo Geo / Platform Factory work.

---

## AFTER GBA / PLATFORM FACTORY V1

Next canonical expansion family:

`Neo Geo / CPS1 / CPS2 / Arcade`

Strategic speed objective:

**Stop hand-crafting every platform. Build and use a repeatable Platform Factory.**

Proposed generic flow:

`Discover → Classify → Identity → Benchmark → Emulator Qualify → Build Shard → Integrate Route → First Play → Device Evidence → Freeze`

Simple systems should reuse this factory with proportional R0/R1 governance.
Only exceptional media/BIOS/peripheral/architecture cases should trigger R2/R3 work.

**Forge decision recorded 2026-09-12** (full text:
`docs/ai/evidence/2026-09-12_platform-factory-v1-forge-decision.md`):

- New platform identity uses **Source Index** as the canonical control/identity
  path; the legacy `ROOM_CATALOG` backend is not expanded as future architecture
  merely because `arcade-fbneo` already lives there.
- Architecture direction: existing `SourceIndexCatalogConfig` + existing
  `EmulatorProfile` + a small `PlatformMediaProfile` — no mega
  `PlatformDefinition`. `PlatformMediaProfile` covers MEDIA only (Phase 1A,
  complete); BIOS fields remain deferred to a separately reviewed phase.
- First Platform Factory pilot target: **Neo Geo AES/MVS**. BEST EMULATOR WINS
  remains binding; no emulator winner selected yet.
- The downloader is shared infrastructure and is not rewritten inside Platform
  Factory V1.

**STATUS:** Phase 0 (characterization) **COMPLETE** — see
`docs/ai/evidence/2026-09-12_platform-factory-v1-phase-0-characterization.md`.
Phase 1 PLAN approved with a narrowing (MEDIA only; BIOS deferred) — see
`docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1-media-contract-plan.md`.
**Phase 1A (media profile contract for NES/SNES/Genesis/GBA/N64, characterized,
NOT wired into production) is COMPLETE** — see
`docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1a-media-profile-contract.md`.
**Phase 1B (`RomArchivePreparer` now consumes `PlatformMediaProfile` as the live
runtime authority for `nativeExtensions`/`preferredExtension` for NES, SNES,
Genesis, GBA and N64; every other systemId retains unchanged legacy-table
fallback; 29/29 tests PASS, zero observed behavior change) is COMPLETE** — see
`docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1b-media-profile-wiring.md`.
No BIOS migration occurred and no Neo Geo/CPS integration occurred in Phase 1B.
Phase 1 as a whole is **not automatically CLOSED**: BIOS migration remains
deferred (semantics unresolved), and no platform beyond the five listed above
has been migrated.

**PHASE 1B ASSURANCE: PASS** (independent Forge review, 2026-09-12) — see
`docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1b-assurance.md`.
GBA residual assurance (see `TECHNICAL_GATE` above) remains a separate,
untouched validation stream.

### Shared Foundation (Slices A/B/C) — 2026-09-13

- **Slice C — `ARCHIVE_AS_ROMSET` explicit runtime contract: PASS / CLOSED.**
  `RomArchivePreparer` now resolves the archive-preserving path through
  `PlatformMediaProfile.mediaKind == ARCHIVE_AS_ROMSET` with legacy
  `arcade-fbneo`-literal fallback preserved; zero observed behavior change;
  43/43 tests PASS across 8 suites. See
  `docs/ai/evidence/2026-09-13_platform-factory-v1-shared-foundation-c-archive-romset.md`.
- **Slice B — generic firmware contract: PASS / CLOSED.**
  New `PlatformFirmwareProfile`/`PlatformFirmwareProfiles` (Option 2 direction,
  empty registry today, no Neo Geo firmware data yet) wired into
  `RetroBiosValidator.requirements()` with legacy per-systemId fallback
  preserved; firmware is explicitly kept out of
  `Game`/`Release`/`Candidate`/`Local Copy`/`PlatformMediaProfile`; 19/19 tests
  PASS. See
  `docs/ai/evidence/2026-09-13_platform-factory-v1-shared-foundation-b-firmware.md`.
- **Slice A — RetroArch dynamic per-launch option transport spike: CLOSED as
  FAIL for every transport investigated on the currently tested build**
  (physical device, RetroArch `1.22.2_GIT`):
  - **A1** (`core_options_path` + `global_core_options` mixed with `#include`):
    **FAIL** — override never applied, 3/3 attempts, both `#include`
    orderings.
  - **A2 partial** (single-key content-specific `.opt` in RetroArch's own
    public Config directory): **FAIL** — file correctly detected
    (`Game-specific core options found`) but value not applied, 2/2 attempts.
  - **A2 full-option-set** (complete 62-key content-specific `.opt`, copied
    byte-for-byte from the device's real working core-level file with exactly
    one value changed): **FAIL** — same detection-without-application result,
    2/2 attempts, eliminating the "incomplete file" hypothesis.
  - All non-negotiable safety criteria passed throughout (zero state leakage,
    real `retroarch.cfg` and real core-level `.opt` hash-identical
    before/after every pass, no new permissions, no manual RetroArch UI
    setup, full cleanup verified).
  - **Important:** this does **not** mean RetroArch can never support
    per-launch core options. It means the specific isolated Oz transports
    investigated in this pilot are not proven usable and are closed for this
    pilot. Root cause of the application failure remains genuinely
    **UNKNOWN** (recorded, not guessed).
  - See `docs/ai/evidence/2026-09-13_platform-factory-v1-shared-foundation-a-transport-spike.md`
    (NOT RUN record, A1 device execution, A2 partial, A2 full-option-set — all
    four sections appended to the same durable file).

### Neo Geo AES/MVS pilot — current state, 2026-09-13

- **BEST EMULATOR remains OPEN.** No emulator winner selected.
- Geolith and FBNeo support AES/MVS upstream, but current Libretro
  deterministic AES/MVS mode switching depends on core configuration that is
  **not currently controllable by a proven Oz transport** (see Slice A above).
- MAME/MAME4droid has a **potentially superior launch-machine contract**:
  MAME expresses machine identity (`aes`, `neogeo`) as a first-class launch
  concept rather than a Libretro core-option mode switch.
- The MAME4droid upstream Android external-launch contract
  (`com.seleuco.mame4d2024` / exported `com.seleuco.mame4droid.MAME4droid` /
  `ACTION_VIEW` + content `DATA` + `cli_params` extra) is now **materially
  qualified** — see
  `docs/ai/evidence/2026-09-13_platform-factory-v1-neo-geo-mame4droid-launch-contract-qualification.md`
  — but **NOT physically proven** on the Oz device
  (`AES_LAUNCH: NOT RUN`, `MVS_LAUNCH: NOT RUN`,
  `AUTOMATABLE_WITH_CURRENT_ANDROID_CONTRACT: UNKNOWN`,
  `ZERO_ADMIN_FIT: CONDITIONAL`, `IMPLEMENTATION_READY: NO`).
- **The physical AES/MVS MAME4droid launch test is the next Neo Geo action.**

**NEXT (executive):** `PLATFORM-FACTORY-V1.NEO-GEO-MAME4DROID-LAUNCH-CONTRACT-QUALIFICATION-01`.

Next session, run a **bounded physical contract qualification only**:

1. determine installed status/version/build of `com.seleuco.mame4d2024`;
2. do not install silently if absent — stop and ask if it is missing;
3. inspect existing storage/SAF state;
4. if lawful readable content already exists: one AES launch, one MVS launch;
5. capture first frame, actual mode, content load, UI intervention,
   exit/return behavior;
6. stop.

No Golden Game Suite yet. No BEST EMULATOR decision yet.

**DEVICE:** not required until next session; required (**NOW**) only when the
physical MAME4droid contract test resumes.

---

## HIGH-PRIORITY BACKLOG

- Platform Factory V1 — see `## AFTER GBA / PLATFORM FACTORY V1` above for
  current Forge decision and phase status (Phases 0/1A/1B and Shared
  Foundation Slices A/B/C complete/closed; executive next step is the bounded
  physical MAME4droid AES/MVS launch-contract qualification, see
  `PLATFORM-FACTORY-V1.NEO-GEO-MAME4DROID-LAUNCH-CONTRACT-QUALIFICATION-01`)
- SOURCE-READER-OPT-01 before uncontrolled multi-system scaling
- DOWNLOAD-1B.3c deterministic failover (currently paused)
- DOWNLOAD-1C advanced torrent durability
- PERF-CATALOG-01 instant library/incremental sync
- EMU-PROVISION-01 zero-touch emulation provisioning
- UX-MODE-01 Portable / Console shells
- approved Visual Spec implementation
- Library recovery from existing SAF content
- Windows Home Hub / LAN / Vault research
- Personal Library / Personal Cloud direction
- Oz Play Anywhere research

---

## CURRENT RISKS

- host multi-local behavioral evidence is complete; real SAF/device evidence remains required
- cancelled DOWNLOAD-UX investigation is retained only as historical context
- Source Index reader scale still has known memory/performance debt
- BEST EMULATOR remains open for future platforms unless physically qualified
- external emulator launch contracts must be verified, never assumed
- acquisition provenance must not be confused with permanent source dependency
- historical documents must never reactivate superseded Gates

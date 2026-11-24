# Oz RetroRom — AI Project Master

> **2026-11-17 / ANDROID-PS1-DOWNLOAD-01 — proposed isolated increment.**
> Osvaldo explicitly authorized PS1 backup search/selection/download independently of the historical
> Neo Geo physical Gate. This copy adds PS1 preparation eligibility to the existing ROMgi path,
> rejects ambiguous ZIP disc/release selection, and preserves independent multidisc checkpoints.
> Replacing a task with staged bytes now requires the existing explicit resume/delete decision;
> unknown/legacy staged files keep those controls reachable instead of being silently discarded.
> It does not add a PS1 Source Index, firmware policy, emulator certification or CUE/BIN persistence.
> The Windows MiNERVA Redump expansion schema was inspected; it is not the Android Game/Release/Candidate
> schema and is not copied as a new catalog. Evidence, validation limits and application state:
> workspace `docs/parallel/ANDROID_PS1_DOWNLOAD_STATUS.md`. This patch has not been applied to the real
> Android checkout; the historical state below remains a record, not a veto of this authorized increment.

> Canonical product/architecture memory for Oz RetroRom.
> The repository is technical truth. This document preserves stable product
> intent, architecture, governance invariants and current platform state.
> Operational status belongs in `PROJECT_DASHBOARD.md`.
> Historical chronology belongs in `AI_PROJECT_HISTORY.md`.

**MASTER_VERSION:** 4.0.6
**LAST_CANONICAL_UPDATE:** 2026-09-13
**CURRENT_IMPLEMENTATION:** Android
**STRATEGIC_PRODUCT_DIRECTION:** Android + optional Windows Home Hub + future TV + optional Personal Cloud
**CURRENT_TECHNICAL_WORK:** PLATFORM-FACTORY-V1 is the current executive expansion work; GBA-CATALOG-01C remains an open residual assurance/validation stream, not superseded; DOWNLOAD-UX-START-01 is cancelled by Product Owner and remains cancelled
**CURRENT_REPOSITORY_EXECUTOR:** Claude Code — Engineering (migration baseline; Codex CLI was the prior executor)

---

## 1. Authority by subject

Oz does not use one document as authority for every type of truth.

- **Repository / current code / Git:** what actually exists.
- **`AGENTS.md`:** operational rules for agents executing work in the repository.
- **`AI_PROJECT_MASTER.md`:** stable product vision, architecture and canonical decisions.
- **`PROJECT_DASHBOARD.md`:** current work, branch/SHA, blockers, next actions and frozen state.
- **`AI_WORKSPACE_ROLES.md`:** workspace authority, routing, prohibitions, risk and handoff contracts.
- **Versioned decision/checkpoint evidence:** detailed proof for a specific Gate.
- **`AI_PROJECT_HISTORY.md`:** historical record only; never operational authority.
- **Chat history:** useful context, never canonical project storage.

If two sources conflict, resolve the conflict according to the subject above.
Never let a historical `NEXT`, `ACTIVE`, or `GATE` statement override the current Dashboard.

### Forge executive mode

Forge (current active generation: **Forge IV** — Forge II and Forge III are
historical/read-only successors of the same charter) is the central
governance and orchestration workspace, not a code executor. Its auto-advance may evaluate evidence, make bounded PASS/FAIL and
risk decisions, select owners, activate the Dream Team, define scope and
acceptance criteria, approve/reject work within authority, and issue handoffs.
It must not write product code, generate implementation patches, edit files,
run Gradle, operate Git, run device tests, or claim executor-owned evidence.

If a next owner cannot be reached technically, that limitation does not collapse
roles: Forge emits a compact `[CODEX EXECUTION BRIEF]` or `[OZ WORKSPACE HANDOFF]`
and stops at the executor boundary. It never performs the work to avoid a
copy/paste. The default governance response is `STATUS`, `DECISION`, `OWNER`,
`NEXT`; detail is reserved for blockers, R2/R3 matters, material trade-offs,
insufficient evidence or Product Owner decisions.

---

## 2. Human + AI governance

The human Product Owner owns final product accountability.

**Forge IV — Command** (current active generation; Forge II and Forge III are
historical/read-only) is the central Product/Technical governance workspace.
It coordinates strategy, architecture, priorities, Dream Team review,
cross-cutting decisions and R2/R3 risk.

Specialized workspaces operate with bounded autonomy:

- Oz Lab — Ideas & Concepts
- Oz Platform Lab — Systems & Emulation
- Oz UI/UX Studio — Visual Parity
- Oz Windows — Home Hub
- Oz Helpdesk — Game & Emulator Support
- Claude Code — Engineering

The Dream Team is a set of temporary specialist roles, not a collection of
mandatory chats. Claude Code is the current executor, not product authority;
Codex CLI remains historical execution context.

Critical work separates:

`IMPLEMENTATION → EVIDENCE → ASSURANCE → GOVERNANCE → HUMAN APPROVAL when required`

No implementer may self-certify an R2/R3 decision merely because tests pass.

---

## 3. Risk governance

- **R0 — Local:** research, explanation, small visual analysis, ordinary troubleshooting.
  Workspace may decide inside its charter.
- **R1 — Bounded:** small reversible implementation with limited blast radius.
  Workspace/Forge may authorize with tests and evidence.
- **R2 — Cross-cutting:** shared architecture, identity, downloader, emulator policy,
  common networking, multi-platform contracts. Requires Forge and relevant Dream Team review.
- **R3 — Critical:** destructive data/Git operations, security/account/cloud trust,
  identity migration, physical deletion policy, irreversible compatibility changes,
  certification/public-support claims. Requires independent assurance and explicit
  Product Owner approval.

Governance effort must be proportional to risk. Do not create ceremonial Gates for R0 work.

---

## 4. Evidence discipline

For material decisions distinguish explicitly:

- **EVIDENCE:** directly observed or verified facts.
- **INFERENCE:** interpretation of the evidence.
- **UNKNOWN:** unresolved facts.
- **DECISION:** approved direction.

New evidence may invalidate an inference. Correct it explicitly without rewriting
the underlying evidence.

---

## 5. Product identity

Preferred product name: **Oz RetroRom**.

Oz must feel like a modern retro console and personal retro library, not a
generic Android application.

Core domain invariant:

`Game ≠ Release ≠ Candidate ≠ Local Copy`

- **Game:** the work/title concept.
- **Release:** a concrete version/edition/region/revision/modification.
- **Candidate:** one exact acquisition source/representation.
- **Local Copy:** a persisted user/library copy.

A valid Local Copy does **not** operationally depend on its original Candidate
remaining available. Candidate provenance is still retained historically when known.

Strong identity must never be inferred from naming prefixes or fuzzy titles when
authoritative Source Index identity exists.

---

## 6. Source architecture

**Oz Source Index** is the offline/versioned control and identity plane.

**MiNERVA and other providers** belong to the acquisition/data plane.

Canonical conceptual flow:

`Provider → Source Index → Game → Release → exact Candidate → acquisition → preparation → Local Copy → emulator`

Provider labels and mutable transport hints are evidence/provenance, not stable
Game/Release identity.

Preserve uncertainty. `UNKNOWN` is preferable to invented region, language,
hash scope, exclusivity, translation status or compatibility.

---

## 7. Personal Library direction

Oz is **local-first**.

An Oz account is not required for normal local library/emulation unless a future
explicit product decision changes that rule.

Strategic model:

`Game → Release → Personal Library Item → Library Copies`

Possible copies may later exist on:

- Android
- Windows Home Hub
- TV
- user-controlled Personal Cloud

This is product direction, not proof of current implementation.

**Oz Windows** is not an Android port. Its intended role is an optional
**Personal Retro Library Home Hub** with zero-admin UX.

Normal users should not need to manage IP addresses, ports, SMB, Docker,
network shares or config files for ordinary flows. Automatic discovery and secure
pairing are preferred future UX; manual networking is an advanced fallback.

---

## 8. Emulation governance

Permanent rule: **BEST EMULATOR WINS**.

Do not preserve an emulator/core merely because it is already integrated.

Keep these states separate:

- catalog presence
- emulator/core configured
- core provisioned
- launch physically validated
- broader compatibility evaluated
- OZ-EQS/product certification
- public eligibility

`ROM + core` does not equal certified platform support.

Every platform may require its own media, BIOS/firmware, peripheral,
performance, save-state and controller evidence.

Never invent package names, Activities, Intents, core filenames,
compatibility or emulator behavior.

---

## 9. Current platform order and state

Binding expansion order currently approved:

`SNES → NES → Genesis/Mega Drive → GBA → Neo Geo/CPS1/CPS2/Arcade → N64 → Dreamcast → PS1 → Saturn`

Research can run ahead when it does not activate or freeze a later platform.

Frozen/protected foundation:

- SNES — controlled Source Index and physical route validated/frozen.
- NES — controlled Source Index and physical route validated/frozen.
- Genesis/Mega Drive — controlled Source Index and global identity correction physically validated/frozen.
- GLOBAL LIBRARY IDENTITY — strong Source Index identity bypasses unsafe legacy fuzzy/prefix local matching.
- IDENTITY TYPING — Source Index authority is typed from persisted catalog origin, not guessed from ID prefixes.

### Platform media configuration (Platform Factory V1, Phase 1B)

Platform media configuration supports progressive migration to
`PlatformMediaProfile`. For migrated platforms, `PlatformMediaProfile` is the
runtime authority for exactly two concerns: `nativeExtensions` (which
extracted archive extensions count as the real ROM for that system) and
preferred-extension resolution (the tie-break winner when a ZIP contains
several native-extension candidates). Non-migrated platforms retain explicit
legacy-table fallback in `RomArchivePreparer` for both concerns. Migrated as
of Phase 1B: NES, SNES, Genesis, GBA, N64. This does **not** extend to
`EmulatorProfile.acceptedExtensions`, which remains a separate, unmigrated
field (deduplicating it against `PlatformMediaProfile.nativeExtensions` is
explicitly deferred, not decided). BIOS configuration is not part of this
architecture — its migration remains a separate, unresolved, later-phase
decision. Evidence:
`docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1b-media-profile-wiring.md`.

**Shared Foundation Slice C (2026-09-13):** `RomArchivePreparer` gained an
explicit `MediaKind.ARCHIVE_AS_ROMSET` runtime contract
(`isArchiveAsRomset(systemId, profile)`), reading `PlatformMediaProfile`
first with the pre-existing `arcade-fbneo`-literal check preserved as legacy
fallback for any systemId without a registered profile. Zero observed
behavior change. Evidence:
`docs/ai/evidence/2026-09-13_platform-factory-v1-shared-foundation-c-archive-romset.md`.

**Shared Foundation Slice B (2026-09-13):** a small, separate
`PlatformFirmwareProfile`/`PlatformFirmwareProfiles` contract (Option 2
direction) was introduced and wired into `RetroBiosValidator.requirements()`
with legacy per-systemId fallback preserved. Firmware/BIOS requirements are
deliberately kept **out of** `PlatformMediaProfile` and out of
`Game`/`Release`/`Candidate`/`Local Copy` — a distinct identity boundary from
media configuration. The registry is empty today (no Neo Geo firmware data
yet); this slice only establishes the contract shape. Evidence:
`docs/ai/evidence/2026-09-13_platform-factory-v1-shared-foundation-b-firmware.md`.

### GBA current canonical state

Controlled production candidate:

- 2,368 Games
- 3,962 Releases
- 4,021 Candidates
- asset SHA-256:
  `6ff70a3763fbbd6871c7e9a29135161c84deec4144d470ff5bb993d519d2af50`

Current semantics:

- Source Index available/routed: **YES**
- acquisitionEnabled: **FALSE**
- launchValidated: **FALSE**
- physical launch/provisioning/OZ-EQS/public certification: **NOT RUN / NO**
- historical mGBA core reference is not certification
- BEST EMULATOR WINS for GBA remains open until appropriate evidence

Multi-local policy:

- multiple local Releases for one Game may coexist
- one explicit active launch pointer per Game
- same Release + different Candidate replaces that Local Release, not other Releases
- deleting the active Release never silently activates another
- Candidate provenance is durable
- physical deletion is authorized only for explicit `OZ_OWNED`
- missing/invalid/legacy ownership must never authorize physical deletion
- Room must never point to a PENDING local artifact

### Neo Geo AES/MVS pilot — current canonical state (2026-09-13)

- BEST EMULATOR WINS remains open for Neo Geo — no emulator winner selected.
- Geolith and FBNeo support AES/MVS upstream, but deterministic AES/MVS mode
  switching on Libretro depends on core configuration that is **not currently
  controllable by any Oz transport investigated so far**: three isolated
  RetroArch per-launch core-option transports (global `core_options_path`,
  partial content-specific `.opt`, full-option-set content-specific `.opt`)
  were each physically tested and closed as FAIL on the currently tested
  build. This is a statement about the specific transports investigated in
  this pilot, **not** a claim that RetroArch can never support per-launch
  options. See
  `docs/ai/evidence/2026-09-13_platform-factory-v1-shared-foundation-a-transport-spike.md`.
- MAME/MAME4droid exposes machine identity (`aes`, `neogeo`) as a first-class
  MAME launch concept rather than a Libretro core-option mode switch — a
  materially qualified upstream Android external-launch contract
  (`com.seleuco.mame4d2024`, exported `com.seleuco.mame4droid.MAME4droid`,
  `ACTION_VIEW` + content `DATA` + `cli_params`), but not yet physically
  proven on an Oz device. See
  `docs/ai/evidence/2026-09-13_platform-factory-v1-neo-geo-mame4droid-launch-contract-qualification.md`.
- Next canonical action: a bounded physical MAME4droid AES/MVS launch-contract
  test (`PLATFORM-FACTORY-V1.NEO-GEO-MAME4DROID-LAUNCH-CONTRACT-QUALIFICATION-01`)
  — contract proof only, not a BEST EMULATOR decision, not a Golden Game Suite.

### Acquisition-plane boundary

ROMgi and other providers are acquisition/research inputs, not runtime or
canonical dependencies. Source Index supplies canonical Game/Release/Candidate
identity; future direct-source acquisition is separate work and requires
platform-specific official evidence.

See the Dashboard for the exact repair/blocker state.

---

## 10. UI/UX product law

Approved mockups are **visual specifications**, not vague inspiration.

Oz should feel like a console product:
- artwork/content first
- deliberate focus and controller navigation
- minimal chrome
- strong spatial composition
- Material/Compose may be infrastructure but must not define the visible identity

Visual workflow:

`Mockup → analysis/spec → Preview → comparison → correction → integration → Visual Parity → device when required`

A green build is not visual approval.

Portrait/Portable and Landscape/Console may use distinct compositions over the
same domain/state. Do not duplicate domain logic.

---

## 11. Experience quality

A technically correct feature may still be a product failure if it feels slow,
confusing, fragile or opaque.

Prioritize:

- immediate feedback
- truthful state labels
- perceived and real responsiveness
- resumability/recovery
- explicit source/release decisions when ambiguity exists
- no silent Candidate failover after a durable plan exists unless a separately
  approved failover policy explicitly allows it
- no silent Release switching
- no ownership assumptions

Do not fix an architectural latency problem by merely renaming a loading label.

---

## 12. Workspace continuity and chat limits

There is **no separate Forge Master chat**.

The canonical project memory lives in versioned Markdown and the repository, not
in an immortal conversation.

If a workspace becomes too long, degraded, or reaches a practical chat limit:

1. create a successor with the same charter, e.g. `Oz Forge III — Command`;
2. load the same Project Sources;
3. transfer only a compact durable handoff/current WORK_ID;
4. never create a second simultaneous authority for the same workspace;
5. mark the predecessor as historical/read-only.

The successor inherits the **workspace role**, not the old conversation's authority.

---

## 13. Durable knowledge rule

**Chat = thinking and conversation.**
**Markdown/Git = durable project memory.**

Persist when the result is materially reusable:

- approved product/architecture decision
- Gate definition/closure
- important research conclusion
- significant validation evidence
- durable handoff
- protocol/contract
- compatibility/certification evidence

Do not create a new file for every ordinary conversation.

Cross-workspace pasted output is **input/evidence**, never authority by itself.

---

## 14. Coding/change protocol

For programming or application changes begin with:

`[INSTRUCCIONES ENTENDIDAS]`

Briefly state:

- what was understood
- what will change
- what must be preserved
- what will be verified before programming

Then inspect the current repository before substantial edits.

For significant changes require the evidence appropriate to risk, potentially:

`Build + Tests + Interaction + Visual Parity + Device`

Do not claim PASS for checks that were not run.

Protect Git. No destructive Git without explicit authorization.
Never stage indiscriminately when local/untracked evidence exists.

---

## 15. External research

For changing technical facts prefer official/primary sources:

- Android Developers / AndroidX / Jetpack / Media3
- RetroArch / Libretro
- official emulator/core repositories
- upstream protocol/standards bodies
- official provider documentation
- official certification/standard bodies

The Game Knowledge Curator must verify game/platform/release facts before
normalizing names or building automated collections.

---

## 16. Final product objective

Oz RetroRom is not merely an app that launches ROMs.

The long-term product is a carefully designed personal retro library aware of:

`Games + Releases + provenance + local copies + emulation + saves + devices`

with console-quality interaction and technical infrastructure kept invisible
whenever possible.

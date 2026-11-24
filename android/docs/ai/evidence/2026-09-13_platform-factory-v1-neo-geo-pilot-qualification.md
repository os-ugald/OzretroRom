# PLATFORM FACTORY V1 — NEO GEO AES/MVS PILOT QUALIFICATION

**WORK_ID:** `PLATFORM-FACTORY-V1.NEO-GEO-PILOT-QUALIFICATION-01`  
**DATE:** 2026-09-13  
**WORKSPACE:** 🎮 Oz Platform Lab — Systems & Emulation  
**RISK:** R2 — cross-cutting platform onboarding / identity / emulation qualification  
**NEXT OWNER:** 🔥 Oz Forge IV — Command  
**ENGINEERING EXECUTOR AFTER FORGE DECISION:** 🌙 Claude Code — Engineering  
**SCOPE:** Neo Geo AES + Neo Geo MVS cartridge systems only. Neo Geo CD is explicitly out of scope.  
**REPOSITORY CHANGES:** NONE  
**OZ SUPPORT/CERTIFICATION CLAIM:** NONE

> Governance note: Platform Lab qualifies and recommends. It does not change global architecture, implement repository changes, certify OZ-EQS/public eligibility, or bypass Forge. The current repository is not mounted in this workspace; therefore code-level assertions about the current implementations of `SourceIndexCatalogConfig`, `EmulatorProfile`, `PlatformMediaProfile`, `RomArchivePreparer`, provisioning, or launch routing are limited to the canonical Project sources and the Product Owner's supplied facts. Where repo inspection is required, this document says `UNKNOWN` rather than inferring implementation.

---

## EXECUTIVE SUMMARY

### Bottom line

Neo Geo AES and MVS should be treated by Oz as a **single Neo Geo cartridge identity family with explicit hardware/product-mode semantics**, not as unrelated platforms and not as a generic arcade ZIP bucket.

Primary-source evidence supports both halves of that conclusion:

- SNK's own historical material describes MVS as the arcade system and AES as the home system, while emphasizing that the hardware/specification and software family were intentionally compatible.
- SNK's official title catalogue nevertheless records distinct MVS and home-cartridge commercial releases and dates for many titles, and also records titles that are MVS-only.
- Current MAME architecture has a shared Neo Geo base plus distinct arcade and AES machine/state behavior.
- Current Geolith exposes AES/MVS as explicit system modes and warns that some MVS releases require MVS mode or may misbehave under AES firmware.

Therefore the smallest correct identity direction is **Option C: one identity family with explicit Release/hardware applicability attributes**, with a further Forge decision needed on whether Oz's concrete `systemId` should be one shared identity namespace or whether routing identifiers must be separate beneath that family. Platform Lab does **not** make that R2 architecture decision here.

For media, there are two materially different current approaches:

1. **Geolith:** dedicated Neo Geo emulator; cartridge content is a single `.neo` image and AES/MVS are explicit runtime modes. This is architecturally attractive for a console-like experience but may require a verified conversion/preparation path if Source Index Candidates originate as arcade-style ROM sets.
2. **FinalBurn Neo / MAME-style:** exact version-coupled arcade ROM sets, normally preserved as ZIP/7z archives with parent/clone/BIOS dependency semantics. Extraction is generally the wrong launch behavior.

The pilot should avoid solving every arcade dependency model at once. Use **self-contained game Candidates** (for example a Geolith single-file cartridge image or a non-merged FBNeo/MAME game archive) and keep system firmware as a separate launch dependency. Do not use merged sets for the first pilot; do not use split sets unless Forge explicitly decides that multi-artifact dependency handling is part of Platform Factory V1.

BIOS/firmware is the principal known Factory-level architecture gap. All serious candidates reviewed require or model Neo Geo system firmware, but they differ in packaging/search rules and AES/MVS handling. Oz's canonical BIOS architecture is not yet decided, so firmware ownership, validation, provisioning, version policy, missing-firmware UX, and emulator-specific mapping remain R2. No assumption is made that Oz may redistribute firmware.

### BEST EMULATOR status

**No winner is declared.** Current primary evidence supports a short list:

- **Geolith (Libretro): qualification leader for first physical comparison**, because it is Neo Geo-specific, explicitly models AES/MVS and region, has current Android Libretro builds, deterministic save-state metadata, active upstream development, and a single-file cartridge model.
- **FinalBurn Neo (Libretro): mandatory comparator**, because it is actively maintained, has current Android builds, strong Neo Geo support, mature arcade ROM-set tooling, controller/save-state support, and documented latency-reduction features.
- **MAME Current / MAME Arcade (Libretro): reference comparator**, valuable for preservation-grade metadata, exact ROM-set semantics, and independent behavior comparison; Android viability exists, but the arcade-only Libretro core does not by itself establish an Oz AES path.
- **MAME4droid Current: external-app comparator**, current and Android-native, but Oz package/Activity/Intent/provisioning/launch contracts have not been verified and are therefore `UNKNOWN`.

`BEST EMULATOR WINS` cannot close from documentation. A physical-device Golden Game Suite is required before Forge can select an emulator winner.

### Minimum pilot recommendation

Run **AES + MVS together**, but only for a **small controlled Source Index shard**, because the value of this pilot is specifically to prove that Platform Factory can represent one closely related hardware family without collapsing Release identity or leaking arcade packaging into global architecture.

Recommended first physical comparison:

- primary candidate: Geolith;
- mandatory comparator: FinalBurn Neo;
- MAME used as metadata/reference and optionally as a third runtime comparator if the Android route is bounded;
- 5–6 core Golden games plus one non-blocking peripheral edge case;
- no public support flag, no certification, no full-catalog activation, no acquisition enablement.

### Implementation gate

**IMPLEMENTATION_READY: NO**

Reason: qualification precedes Forge architecture/emulator decisions. BIOS architecture, concrete AES/MVS `systemId` semantics, media-preparation behavior, provisioning, and launch-mode contracts require Forge R2 decisions and/or repo inspection before engineering planning.

**STOP_TO_FORGE: YES**

---

## DREAM TEAM ACTIVATION

Only the requested relevant roles were activated as temporary specialist perspectives; no new chats/workspaces were created.

### 1. Game Knowledge Curator

Responsibilities in this qualification:

- distinguish the Neo Geo work/title from AES/MVS commercial releases;
- reconcile first-party SNK platform and title records with emulator metadata;
- identify MVS-only versus MVS+home-cartridge examples;
- prevent parent/clone naming from becoming canonical Game identity;
- preserve `UNKNOWN` for region/revision relationships not proven by primary evidence.

### 2. Emulation Integration Engineer

Responsibilities:

- identify current serious emulator/core candidates;
- establish media/container expectations and firmware requirements;
- separate upstream capability claims from Oz device evidence;
- evaluate Libretro versus external-app integration shape;
- design the physical evidence needed for `BEST EMULATOR WINS`.

### 3. Software Architect

Responsibilities:

- test findings against `Game ≠ Release ≠ Candidate ≠ Local Copy`;
- identify the smallest Source Index model that preserves identity;
- separate generic Factory needs from Neo Geo-specific adaptation;
- prevent legacy arcade/ROM-set concepts from becoming global domain architecture merely because they are convenient.

### 4. QA / Test Engineer

Responsibilities:

- define a compact but adversarial Golden Game Suite;
- require host evidence separately from physical-device evidence;
- define launch, firmware, controller, save-state, performance and failure-mode checks;
- challenge any claim that documentation alone establishes compatibility or support.

---

## [CREDENTIAL ALIGNMENT]

**ROLE:** Emulation Integration Engineer  
**OFFICIAL BODY / STANDARD:** No recognized universal professional certification exists for emulator/core integration. For this scope, the professional/technical alignment is to the **Libretro API/core contract and official upstream emulator/core documentation and repositories**, supplemented by first-party SNK platform evidence and MAME's upstream hardware/ROM metadata. This is standards/primary-source alignment, **not human certification**.  
**VERSION / DATE:** Technology refresh as of **2026-09-13**. Relevant current evidence includes Geolith upstream tag **0.4.2 (2026-07-02)**; MAME **0.289 (2026-07-31)**; Libretro Android nightly artifacts dated **2026-09-12** for Geolith and FinalBurn Neo; current FBNeo/Libretro documentation retrieved 2026-09-13.  
**SCOPE USED:** Libretro core/content contract; firmware declaration; supported media extensions; save-state metadata; AES/MVS mode behavior; current Android core availability; ROM-set/container semantics; parent/clone/BIOS dependencies; upstream maintenance; physical-validation gaps.  
**PRIMARY SOURCES:** Libretro official docs and core-info/buildbot; Geolith upstream repository and Libretro port; FinalBurn Neo official Libretro documentation/repository metadata; MAME upstream source and MAMEdev release information; SNK NEOGEO MUSEUM first-party historical/catalogue evidence.  
**KNOWN GAPS:** No professional credential certifies emulator-integration competence; upstream compatibility/accuracy statements are not Oz evidence; no Oz device tests were run; no current Oz repository was inspected; no legal right to redistribute BIOS/firmware was established; package/Activity/Intent contracts for external apps were not verified; comparative latency/performance was not measured.  
**STATUS:** `STANDARD_ALIGNED`

`OZ_QUALIFIED` is intentionally **not** claimed because no Oz-specific physical benchmark has been completed and independently assured.

---

## PLATFORM IDENTITY

### EVIDENCE — Neo Geo MVS

1. SNK's first-party NEOGEO historical material identifies **MVS (Multi Video System)** as the arcade-side Neo Geo system and describes its multi-cartridge arcade design.
2. SNK states that the software titles and specifications of the arcade and home systems were intentionally identical/compatible at the platform level.
3. SNK's official title catalogue records many games with an MVS Cartridge release date and a separate home `NEOGEO ROM-cart` release date; it also records titles with an MVS release and no home-cartridge release.
4. Current MAME has a shared Neo Geo base implementation but a distinct arcade base/state path with arcade-specific behavior.

### EVIDENCE — Neo Geo AES

1. SNK identifies **AES (Advanced Entertainment System)** as the home console name used to distinguish it from MVS.
2. SNK describes the home system as having the same core specification as the arcade version and documents home/arcade memory-card interoperability in supported cases.
3. The first-party title catalogue demonstrates separate home-cartridge release dates for many games that also had MVS releases.
4. Current MAME has a distinct AES base state in addition to the shared Neo Geo hardware base.
5. Current Geolith explicitly supports both AES and MVS modes and recommends home-console releases for AES operation where appropriate; it also states that some MVS releases require MVS mode.

### INFERENCE

AES and MVS are not well represented as two unrelated Oz platforms. They are two product/hardware contexts inside one very tightly coupled Neo Geo cartridge family. However, “same platform family” does **not** mean every exact dump, commercial release, firmware choice, or runtime mode is byte-for-byte interchangeable.

The correct Oz model must therefore preserve both:

- **shared identity:** one Game can span the Neo Geo cartridge family;
- **release/hardware specificity:** a concrete Release may be AES-only, MVS-only, applicable to both, region/revision-specific, or `UNKNOWN` until proven.

### UNKNOWN

- Whether current Oz `systemId` is strictly an identity namespace, a launch-routing key, or both in all relevant paths cannot be verified without the repository.
- The exact canonical rule for when an MVS and AES commercial release with equivalent program content should be one Release with applicability metadata versus two Releases requires Source Index corpus evidence, not title dates alone.
- Region-specific commercial/dump relationships are not globally established by the sources reviewed.

---

## AES / MVS MODEL OPTIONS

### Option A — One Oz platform / one `systemId`

**Benefits**
- matches SNK's unified Neo Geo product family;
- minimizes platform fragmentation;
- naturally groups shared Games.

**Risk**
- insufficient if `systemId` currently also drives runtime mode, firmware selection or emulator configuration and cannot express AES/MVS explicitly;
- may collapse genuinely distinct Release/hardware applicability.

**Assessment:** plausible but under-specified without current repo semantics.

### Option B — Separate AES and MVS `systemId`s

**Benefits**
- simple launch/firmware routing;
- makes hardware mode explicit.

**Risk**
- encourages duplicate Game identity and split catalogues for a family intentionally designed around near-identical software/hardware;
- can falsely turn runtime mode into work identity;
- risks importing arcade-vs-console implementation boundaries into Source Index identity.

**Assessment:** not preferred from current identity evidence.

### Option C — One Neo Geo identity family with explicit Release/hardware applicability

**Benefits**
- preserves one Game identity across the family;
- allows MVS-only, AES-only, both, region/revision-specific and unknown applicability;
- cleanly separates identity from emulator/runtime mode;
- fits SNK's shared-family evidence and emulator evidence showing distinct AES/MVS behavior.

**Risk**
- may require a generic field/contract for hardware applicability or launch mode if Oz cannot currently express it.

**Assessment:** strongest fit to current evidence.

### Option D — Separate identity family from launch-target identifiers

One canonical Neo Geo identity namespace could map to distinct lower-level launch targets/profiles such as AES and MVS without creating separate Game catalogues.

**Assessment:** architecturally compatible with Option C and may be necessary depending on current `systemId` semantics; repo inspection is required.

### RECOMMENDATION TO FORGE

Adopt **Option C as the domain direction**, potentially implemented using **Option D beneath it** if launch routing needs distinct hardware targets. Do not approve separate Game identity trees for AES and MVS unless new corpus evidence demonstrates a need.

This is a **Platform Lab recommendation**, not a Forge decision.

---

## SOURCE INDEX MODEL

### Smallest correct representation

#### Game

Represents the work/title concept independent of emulator packaging and AES/MVS runtime mode.

Examples from the official catalogue show one named work with MVS and home-cartridge release dates. That supports a shared Game boundary when title/work identity is the same.

Do **not** derive Game boundaries from:

- MAME/FBNeo shortnames;
- parent/clone status;
- ZIP filename;
- provider naming prefix;
- AES/MVS emulator mode alone.

#### Release

Represents a concrete edition/region/revision/modification/content variant.

Neo Geo-specific Release facts may include, when evidence exists:

- AES applicability;
- MVS applicability;
- both;
- region;
- revision;
- official/unlicensed/bootleg/hack status if authoritative evidence supports it;
- protection/cart variant where it materially identifies distinct content;
- source provenance for those claims.

**Important:** hardware applicability is not automatically a reason to duplicate Release identity. If the exact software release is demonstrably the same content usable in both modes, one Release with applicability may be correct. If evidence shows distinct content/revision/cart data, use distinct Releases. When not proven: `UNKNOWN`.

#### Parent / clone semantics

Parent/clone is **not canonical Game identity**.

It can mean multiple things in emulator sets:

- a revision or regional variant of the same work;
- a bootleg/hack;
- an alternate dump/build;
- a packaging dependency used to deduplicate ROM members.

Therefore:

- parent/clone relationships belong as provenance/dependency/evidence metadata;
- mapping a clone to a Release or separate Game must be evidence-driven;
- no automatic `clone == Release` rule should enter global architecture.

#### Candidate

A Candidate remains **one exact acquisition source/representation**.

For the first pilot, prefer Candidate shapes that do not require a new global multi-artifact dependency contract:

- Geolith path: one exact `.neo` cartridge image Candidate;
- FBNeo/MAME path: one exact **non-merged game archive** Candidate, with firmware represented separately as a system/emulator launch dependency.

Avoid split sets in the first pilot because the clone archive can depend on parent material. Avoid merged sets because multiple revisions can be packaged in one parent archive and Libretro documentation explicitly warns that alternate revision launching is awkward.

If future canonical acquisition requires a Candidate to be a manifest of multiple dependent artifacts, that is an R2 generic Factory decision; do not smuggle it in as a Neo Geo special case.

#### Candidate hashes / provenance

Realistically available technical evidence includes:

- per-ROM CRC and SHA-1 metadata from MAME/list data;
- emulator DAT metadata for exact ROM-set membership/version;
- a whole-artifact digest computed by Oz/provider for the exact acquired file/archive;
- provider/source provenance and source version/date.

Recommendation:

- use a strong whole-artifact digest (for example SHA-256) for exact Candidate artifact identity when Oz can compute/receive it;
- retain authoritative member-level CRC/SHA-1/DAT metadata as validation/provenance where available;
- do **not** use whole-ZIP hash as Release identity because ZIP packaging can differ while member content is equivalent;
- do **not** claim region/revision from filename conventions when not independently proven.

### Invariant test

`Game ≠ Release ≠ Candidate ≠ Local Copy` remains intact:

- **Game:** `THE KING OF FIGHTERS '98` as the work.
- **Release:** a specific verified Neo Geo cartridge revision/applicability/region.
- **Candidate:** one exact `.neo` image or exact emulator-version-compatible archive representing that Release.
- **Local Copy:** the persisted user/library copy acquired/prepared from that Candidate, operationally independent of the remote Candidate after successful persistence while retaining provenance.

No legacy `ROOM_CATALOG`, ROMgi, FBNeo shortname, parent/clone name, or archive filename becomes canonical identity merely because it exists.

---

## ROMSET / MEDIA MODEL

### Authoritative current findings

#### Geolith

- cartridge content uses the `.neo` format;
- the format is a single cartridge image rather than an arcade-style parent/clone ZIP dependency graph;
- AES/MVS is a runtime/system-mode concern;
- current upstream says its supported cartridge set is built around known checksums and retains protection behavior rather than relying on patched content.

#### FinalBurn Neo

- current Libretro documentation expects `zip` or `7z` content;
- ROM sets must match the current emulator's expected set/DAT; active development means set expectations can change;
- firmware is searched separately through documented system/content paths;
- current Neo Geo subset metadata exists, but the general FBNeo core is the normal modern core when platform resources permit.

#### Libretro arcade set semantics

- **full non-merged:** standalone archive containing parent and BIOS material needed by that set;
- **non-merged:** standalone with parent content, but BIOS may remain separate;
- **split:** clone can depend on parent, and game can depend on separate BIOS;
- **merged:** parent archive can contain multiple clone/revision sets; Libretro does not recommend this model when alternate revisions need to be launched cleanly;
- Libretro recommends non-merged for small subsets and split for complete collections.

### Extraction policy

For FBNeo/MAME-style arcade ROM sets, extraction before launch is normally **incorrect** as a generic preparation rule. The archive is part of the emulator's expected content representation and its filename/member structure can participate in set resolution.

For Geolith, the launch artifact is already a single `.neo` file; no ZIP extraction policy is needed at runtime unless Source Index acquisition originates in another representation that must be converted.

### Local Copy policy

A Local Copy must preserve the exact emulator-required launch representation:

- `.neo` as a file for Geolith;
- intact archive for FBNeo/MAME-style romsets;
- firmware separately according to the eventual Factory BIOS contract.

Oz should not normalize every platform into “extract archive to a ROM file.”

### Requirement classification

| Requirement | Classification | Rationale |
|---|---|---|
| Recognize `.neo` as a media extension if Geolith is qualified | SMALL DATA ADDITION | Fits extension/preferred-extension metadata. |
| Recognize `.zip`/`.7z` for an arcade-set emulator | SMALL DATA ADDITION | Extension metadata only. |
| Preserve an emulator-required archive intact | GENERIC FACTORY BEHAVIOR | Container preservation is not conceptually Neo Geo-specific. Exact current support is repo-UNKNOWN. |
| Express split parent dependency | SMALL GENERIC FACTORY GAP or UNKNOWN | Needed only if pilot accepts split sets; can be deferred by choosing non-merged. |
| Represent merged multi-revision archive selection | PLATFORM-SPECIFIC ADAPTER / AVOID FOR PILOT | Adds alternate-content selection complexity with little pilot value. |
| Convert source ROM-set material into `.neo` | PLATFORM-SPECIFIC ADAPTER | Only required if chosen Candidate supply is not already `.neo`; transformation/provenance must be validated. |
| AES/MVS hardware-mode selection | DATA if existing emulator options are declarative; otherwise SMALL GENERIC FACTORY GAP | Repo inspection required. |
| Firmware discovery/provisioning | MAJOR ARCHITECTURE GAP | Canonical Factory BIOS architecture is explicitly undecided. |
| Parent/clone as canonical Game identity | REJECTED | Emulator packaging metadata must not redefine domain identity. |

### PlatformMediaProfile

Given the Product Owner's supplied fact that `PlatformMediaProfile` currently handles media-extension/preferred-extension resolution, it appears sufficient **for extension choice only**.

Recommendation:

- add Neo Geo media extensions only after emulator direction is approved;
- do **not** expand `PlatformMediaProfile` to carry parent/clone graphs, BIOS policy, archive preservation rules, or conversion logic merely because Neo Geo needs them;
- determine archive preservation in the appropriate preparation/launch contract after repo inspection.

---

## BIOS MODEL

BIOS/firmware is deliberately separated from game media.

### REQUIRED

- A system firmware/BIOS dependency exists for the serious Neo Geo emulator paths reviewed.
- Geolith distinguishes AES system firmware from MVS system firmware.
- FBNeo models a Neo Geo BIOS set as a system dependency for Neo Geo games.
- MAME models Neo Geo firmware/BIOS independently from game-set identity.

The eventual Oz model must be able to say “content is valid but required firmware is unavailable/invalid” without corrupting Game/Release/Candidate identity.

### OPTIONAL

- Enhancement/replacement firmware such as community-developed multi-region/feature BIOSes may be supported by some emulators.
- Such firmware is **not** required to establish baseline Oz Neo Geo support and should not become the canonical default without a separate product/technical decision.

### EMULATOR-SPECIFIC

- expected firmware set composition;
- search path/location;
- exact accepted revisions/checksums;
- fallback behavior;
- whether AES and MVS use distinct system firmware packages;
- optional enhancement firmware behavior;
- error behavior when required firmware members are absent.

These are emulator integration facts, not global Game identity.

### UNKNOWN

- Oz's future canonical firmware entity/model;
- firmware ownership/provenance persistence;
- whether Oz validates whole firmware package, member hashes, or both;
- automatic provisioning versus user-supplied firmware;
- legal/distribution rights for any firmware bytes;
- allowed firmware revisions for production;
- how firmware is backed up/recovered across devices;
- whether firmware is a shared Platform asset, emulator asset, user-library asset, or a typed combination of these.

### Architecture recommendation

Do **not** reuse an existing FBNeo BIOS-merge implementation as the new canonical Factory contract without independent design review. The Factory should model firmware as a typed launch dependency whose validation/provisioning can be mapped to different emulators. Physical packaging may differ, but Game/Release identity must remain independent.

This is R2 and belongs to Forge.

---

## BEST EMULATOR CANDIDATES

### Candidate 1 — Geolith Libretro

**Current primary evidence**

- Neo Geo-specific emulator supporting AES and MVS explicitly;
- upstream currently tagged 0.4.2 (2026-07-02) and active afterward;
- current Libretro Android nightly build exists for arm64-v8a on 2026-09-12;
- single `.neo` cartridge image model;
- explicit AES/MVS system type and region options in current Libretro integration;
- deterministic save-state metadata in Libretro core info;
- upstream describes high accuracy and broad commercial compatibility, but those remain upstream claims until Oz testing.

**Integration strengths**

- narrow system scope;
- explicit AES/MVS mode matches the identity problem being qualified;
- simple one-file game-content representation after preparation;
- Libretro route is favorable to a controlled console UX if Oz can provision/configure it deterministically.

**Risks / unknowns**

- exact conversion/provenance path from Source Index/provider content to `.neo` is not established;
- comparative Android performance and latency are not measured;
- save-state durability across future upstream versions is not an Oz guarantee;
- firmware provisioning remains unresolved;
- upstream compatibility claims are not Oz certification.

### Candidate 2 — FinalBurn Neo Libretro

**Current primary evidence**

- active multi-system arcade emulator with Neo Geo support;
- current Android arm64 Libretro build exists on 2026-09-12;
- current documentation supports ZIP/7z ROM sets, controllers, save states, run-ahead/preemptive latency-reduction facilities, and explicit Neo Geo BIOS behavior;
- dedicated Neo Geo subset core metadata exists, but upstream notes subset cores exist mainly for resource-constrained platforms; the standard FBNeo core is the normal modern choice when feasible;
- ROM-set definitions can change as dumps/emulation evolve, so Candidate validation must be version-aware.

**Integration strengths**

- mature arcade ROM-set workflow;
- strong tooling/DAT ecosystem;
- current Android Libretro availability;
- useful latency features for fighting-game testing;
- likely good comparator for future CPS/arcade Platform Factory work.

**Risks / unknowns**

- exact AES-versus-MVS user-mode behavior needs device/config validation for the Oz route;
- parent/clone/BIOS semantics can leak into domain architecture if not contained;
- exact set version coupling complicates zero-admin acquisition/provisioning;
- upstream documentation warns save states can become incompatible after core updates;
- no Oz device evidence exists yet.

### Candidate 3 — MAME Current / MAME Arcade Libretro

**Current primary evidence**

- MAME 0.289 released 2026-07-31 and remains actively maintained;
- MAME upstream source explicitly models shared Neo Geo hardware plus distinct AES/arcade state;
- MAME provides rich ROM/checksum and hardware metadata useful as a reference source;
- Libretro's current arcade-only MAME core uses ZIP/7z and deterministic save-state metadata, and exists specifically because full MAME is too large for some platforms, explicitly including Android as a concern.

**Integration strengths**

- strong preservation/documentation authority;
- excellent independent reference for ROM relationships and hardware edge cases;
- useful third comparator when accuracy questions disagree between dedicated cores.

**Risks / unknowns**

- the Android-oriented `mamearcade` core is explicitly arcade-only, so an AES+MVS single-core Oz route is not established by the sources reviewed;
- broader configuration/provisioning surface may conflict with zero-admin UX;
- comparative performance on Oz target devices is not measured;
- current exact Android binary/provisioning route for the desired AES behavior remains `UNKNOWN`.

### Candidate 4 — MAME4droid Current (external app)

**Current primary evidence**

- actively maintained Android-native MAME derivative based on current MAME-family code;
- supports modern Android storage and gamepads according to its upstream repository;
- current repository identifies a modern MAME ROM-set expectation.

**Integration strengths**

- Android-native external-app option;
- independent runtime path valuable if Libretro options fail on performance or compatibility.

**Risks / unknowns**

- package name: `UNKNOWN` for Oz purposes unless formally verified;
- Activity/Intent contract: `UNKNOWN`;
- deterministic launch-to-specific-content contract: `UNKNOWN`;
- return-to-Oz behavior: `UNKNOWN`;
- zero-touch provisioning/update flow: `UNKNOWN`;
- AES-specific behavior: `UNKNOWN`;
- therefore it is not ready to be an Oz integration recommendation at this stage.

---

## EMULATOR COMPARISON

| Criterion | Geolith Libretro | FinalBurn Neo Libretro | MAME Current / mamearcade | MAME4droid Current |
|---|---|---|---|---|
| AES support | Upstream explicit | Upstream Neo Geo mode capability exists; exact Oz AES route needs validation | Mainline MAME models AES; Android arcade-core route not established | UNKNOWN |
| MVS support | Upstream explicit | Upstream explicit Neo Geo arcade support | Upstream explicit | Upstream arcade support; exact Neo Geo pilot route not validated |
| Android viability | Current Libretro Android builds | Current Libretro Android builds | Android-specific arcade core design documented; exact desired binary/route to verify | Native Android app |
| Content model | Single `.neo` image | Version-coupled ZIP/7z romset | Version-coupled ZIP/7z romset | MAME romset |
| Parent/clone runtime dependency | Avoided by `.neo` model | Depends on set style | Depends on set style | Depends on set style |
| Firmware | Separate AES/MVS firmware | Neo Geo BIOS set behavior | Neo Geo firmware set behavior | UNKNOWN for Oz integration |
| Save states | Deterministic metadata | Deterministic metadata | Deterministic metadata | Upstream app capability not accepted as Oz evidence here |
| Controller support | Neo Geo joystick + special devices documented | Controllers documented | Libretro input exists; exact Oz mapping not run | Gamepads documented |
| Latency | No comparative Oz measurement | Runahead/preemptive documented | No comparative Oz measurement | Upstream rollback/netplay features do not prove local Oz latency |
| Accuracy | Upstream claims high accuracy | Upstream states accuracy focus | Preservation/accuracy focus | Derived from MAME; no Oz comparison |
| Performance | UNKNOWN on Oz targets | UNKNOWN on Oz targets | UNKNOWN on Oz targets | UNKNOWN on Oz targets |
| Maintenance | Active in 2026 | Active/current | Active/current | Active/current |
| Provisioning complexity | Core + firmware + `.neo` preparation | Core + exact romset + BIOS | Core + exact romset + MAME configuration | External app + content + unverified launch contract |
| Zero-admin fit | Promising, not proven | Promising if set/BIOS complexity is hidden | Potentially heavier | Unproven external-app orchestration |
| Current recommendation | **First qualification leader, not winner** | **Mandatory comparator** | Reference/optional third runtime | External fallback comparator |

### Required Oz state classification

#### Geolith

- **CATALOG PRESENCE:** Canonical Neo Geo Source Index pilot not yet established.
- **CONFIGURED:** NOT RUN.
- **PROVISIONABLE:** Upstream Android core artifact exists; Oz provisioning NOT RUN.
- **PHYSICAL LAUNCH EVIDENCE:** NOT RUN.
- **COMPATIBILITY EVIDENCE:** Upstream claims only; Oz evidence NOT RUN.
- **OZ-EQS:** NO / NOT RUN.
- **PUBLIC ELIGIBILITY:** NO.

#### FinalBurn Neo

- **CATALOG PRESENCE:** Legacy arcade context may exist, but canonical Neo Geo Source Index pilot not established.
- **CONFIGURED:** Existing arcade-FBNeo context must not be treated as canonical Neo Geo configuration; canonical path NOT RUN.
- **PROVISIONABLE:** Upstream Android core artifact exists; canonical Oz provisioning NOT RUN.
- **PHYSICAL LAUNCH EVIDENCE:** NOT RUN for this canonical pilot.
- **COMPATIBILITY EVIDENCE:** Upstream documentation exists; Oz Golden Suite NOT RUN.
- **OZ-EQS:** NO / NOT RUN.
- **PUBLIC ELIGIBILITY:** NO.

#### MAME Current / mamearcade

- **CATALOG PRESENCE:** NOT ESTABLISHED for canonical Neo Geo Source Index.
- **CONFIGURED:** NOT RUN.
- **PROVISIONABLE:** Upstream Android-oriented Libretro architecture exists; Oz provisioning NOT RUN.
- **PHYSICAL LAUNCH EVIDENCE:** NOT RUN.
- **COMPATIBILITY EVIDENCE:** Upstream/reference evidence only; Oz NOT RUN.
- **OZ-EQS:** NO / NOT RUN.
- **PUBLIC ELIGIBILITY:** NO.

#### MAME4droid Current

- **CATALOG PRESENCE:** NOT ESTABLISHED.
- **CONFIGURED:** NOT RUN.
- **PROVISIONABLE:** Application exists upstream; Oz-managed provisioning contract UNKNOWN/NOT RUN.
- **PHYSICAL LAUNCH EVIDENCE:** NOT RUN.
- **COMPATIBILITY EVIDENCE:** Upstream only; Oz NOT RUN.
- **OZ-EQS:** NO / NOT RUN.
- **PUBLIC ELIGIBILITY:** NO.

### BEST EMULATOR recommendation

**Do not select a winner yet.**

Start physical qualification with **Geolith as the leading candidate** and **FinalBurn Neo as the mandatory comparator**. This ordering is based on integration characteristics and current Android availability, not reputation and not legacy integration.

A winner requires physical evidence on the same controlled releases, devices and controller path. If Geolith requires a brittle or provenance-damaging preparation pipeline, or FBNeo materially outperforms/out-compatibles it with acceptable zero-admin set management, the lead can change.

---

## GOLDEN GAME SUITE

This suite is a **proposal for qualification**, not a claim that Oz currently has Candidates for these games.

### 1. NAM-1975

**GAME:** NAM-1975  
**WHY INCLUDED:** First-wave Neo Geo title; useful baseline before later protection/banking complexity.  
**WHAT IT TESTS:** Basic cartridge boot, sprites, audio, controls, early-hardware behavior, AES/MVS family baseline.  
**KNOWN RELEASE/VARIANT CONCERN:** SNK catalogue records an early MVS release and a later home-cartridge release; exact dump/revision equivalence is not established here.  
**EVIDENCE SOURCE:** SNK NEOGEO MUSEUM title catalogue and Neo Geo history.  
**UNKNOWN:** Which exact Source Index Release/Candidate will be authoritative; region/revision mapping; availability from approved acquisition source.

### 2. ZED BLADE

**GAME:** ZED BLADE  
**WHY INCLUDED:** MVS-only commercial example in SNK's official catalogue and a documented raster-timing stress case in current MAME source.  
**WHAT IT TESTS:** MVS-only identity/applicability, raster IRQ/video timing, shooter input, scrolling/sprite behavior.  
**KNOWN RELEASE/VARIANT CONCERN:** No home-cartridge release is shown in the first-party catalogue; exact emulator-set clone/revision relationships remain to be reconciled.  
**EVIDENCE SOURCE:** SNK title catalogue; MAME `neogeo_v.cpp`.  
**UNKNOWN:** Exact canonical Candidate and regional variants.

### 3. SAMURAI SHODOWN III

**GAME:** SAMURAI SHODOWN III  
**WHY INCLUDED:** Fighting title with current MAME source explicitly using the Neo Geo PAL/NTSC hardware flag in timing logic.  
**WHAT IT TESTS:** Region/timing behavior, 50/60 Hz mode handling, fighting controls, audio/video synchronization.  
**KNOWN RELEASE/VARIANT CONCERN:** Region-specific Release mapping must be proved; PAL/NTSC emulator options must not be inferred as commercial Release identity.  
**EVIDENCE SOURCE:** MAME `neogeo_v.cpp`; SNK catalogue for commercial title family.  
**UNKNOWN:** Exact region/revision Candidates and which should be pilot-authoritative.

### 4. THE KING OF FIGHTERS '98

**GAME:** THE KING OF FIGHTERS '98  
**WHY INCLUDED:** Representative high-profile fighting title with distinct first-party MVS and home-cartridge release dates.  
**WHAT IT TESTS:** Fighting-game input latency, controller mapping, save-state repeatability, AES/MVS mode presentation, audio/sprite load under normal play.  
**KNOWN RELEASE/VARIANT CONCERN:** Commercial MVS and home releases are distinct events; exact data/revision relationship must be verified before Source Index normalization.  
**EVIDENCE SOURCE:** SNK title catalogue / official KOF historical records.  
**UNKNOWN:** Exact authoritative Release/Candidate mapping and region differences.

### 5. GAROU: MARK OF THE WOLVES

**GAME:** GAROU: MARK OF THE WOLVES  
**WHY INCLUDED:** Late-era fighting title; MAME explicitly models a Garou cart and a distinct AES Garou cart type, making it a strong AES/MVS/revision canary.  
**WHAT IT TESTS:** Late cart protection/banking path, AES/MVS-specific content handling, fighting latency, complex sprite/audio behavior, save states.  
**KNOWN RELEASE/VARIANT CONCERN:** AES-specific cart metadata exists upstream; do not collapse it into MVS content without Release-level reconciliation.  
**EVIDENCE SOURCE:** SNK title catalogue; MAME `sma.cpp`.  
**UNKNOWN:** Exact canonical hashes, region/revision boundaries, Candidate availability.

### 6. METAL SLUG 3

**GAME:** METAL SLUG 3  
**WHY INCLUDED:** Late Neo Geo action title with protected-cart handling and special fixed-layer/banking behavior documented in MAME.  
**WHAT IT TESTS:** Heavy sprite/action scenes, late-cart protection, fixed-layer banking, audio, frame pacing, save states, larger/complex content path.  
**KNOWN RELEASE/VARIANT CONCERN:** MVS and home-cartridge releases have separate first-party dates; exact content/revision mapping must be verified.  
**EVIDENCE SOURCE:** SNK title catalogue; MAME `sma.cpp` and Neo Geo sprite/fixed-layer source.  
**UNKNOWN:** Exact pilot Candidate, archive size/set membership, regional variants.

### Extended non-blocking edge case — THE IRRITATING MAZE

**GAME:** THE IRRITATING MAZE  
**WHY INCLUDED:** Special-input MVS edge case; current emulator sources document trackball handling.  
**WHAT IT TESTS:** Peripheral abstraction, special MVS mode/firmware behavior, whether Factory assumptions incorrectly require standard joystick-only input.  
**KNOWN RELEASE/VARIANT CONCERN:** Special cabinet/peripheral behavior makes this unsuitable as a first-pilot blocker.  
**EVIDENCE SOURCE:** Geolith upstream input documentation; MAME layout/input evidence.  
**UNKNOWN:** Product intent for supporting the original peripheral experience; controller fallback policy; whether this title should ever be public-eligible.

### Suite policy

The first six games are sufficient for a compact pilot if authoritative Candidates can be established. The Irritating Maze should remain an **edge-case probe**, not a blocker for baseline Neo Geo readiness.

No game in this list is asserted to be currently available through Oz acquisition.

---

## PLATFORM FACTORY GAP MAP

The classifications below distinguish **known contract facts** from **repo-dependent unknowns**. The current repository is not mounted in this workspace.

| Area | Classification | Qualification finding | Forge / repo action needed |
|---|---|---|---|
| `SourceIndexCatalogConfig` | **SMALL DATA ADDITION (provisional)** | Source Index is already the canonical control/identity path. A new platform shard/config should be data/config if current abstractions are generic. | Verify current config schema in repo before approval. |
| `EmulatorProfile` | **UNKNOWN → likely SMALL DATA ADDITION / SMALL GENERIC FACTORY GAP** | A Libretro core can be named declaratively, but AES/MVS mode, region and firmware mapping may require per-Release/per-launch options. | Inspect whether current profile supports deterministic core options and hardware-mode selection. |
| `PlatformMediaProfile` | **ALREADY SUPPORTED mechanism + SMALL DATA ADDITION** | Product Owner confirms extension/preferred-extension resolution is live. Neo Geo may need `.neo`, `.zip`, or `.7z` depending approved emulator. | Do not add BIOS/dependency/preparation semantics here. |
| `RomArchivePreparer` | **UNKNOWN** | FBNeo/MAME-style content often must remain archived; Geolith may require a `.neo` conversion only if acquisition does not already provide it. | Inspect current behavior. If archives are always extracted, add a generic opaque-container/preserve behavior rather than a Neo Geo hack. Conversion, if required, is platform-specific. |
| BIOS handling | **MAJOR ARCHITECTURE GAP** | Factory BIOS architecture is explicitly undecided; all leading candidates require firmware semantics. | Forge R2 decision: model, validation, provisioning, ownership/provenance, error UX, emulator mapping. |
| Candidate/acquisition contract | **ALREADY SUPPORTED for singular exact artifact; SMALL GENERIC GAP if dependency bundles are required** | One `.neo` file or one non-merged archive fits exact Candidate semantics. Split/merged dependency graphs can exceed a single-artifact path. | Pilot should avoid split/merged; defer generic bundle/dependency model unless evidence requires it. |
| Local Copy | **ALREADY SUPPORTED conceptually; preservation detail UNKNOWN** | Domain invariant already separates Local Copy from Candidate and retains provenance. Neo Geo needs the Local Copy to remain in emulator-required representation. | Verify storage/preparation does not forcibly normalize archives. |
| Emulator provisioning | **MAJOR ARCHITECTURE GAP for productization; not a qualification blocker** | Dashboard keeps zero-touch emulator provisioning as backlog. Upstream binaries exist, but Oz provisioning is not evidence. | Forge must decide provisioning contract later; manual controlled provisioning may be allowed solely for qualification evidence. |
| Launch routing | **UNKNOWN → likely SMALL GENERIC FACTORY GAP if dynamic core options are absent** | Existing platforms launch through current routes, but AES/MVS hardware mode + firmware + region behavior is unverified in current repo. | Inspect current launch contract before engineering plan. |
| Parent/clone data | **SMALL DATA ADDITION / provenance only** | Useful emulator metadata, not canonical Game identity. | Add only where it improves reconciliation/acquisition; no global identity rewrite. |
| AES/MVS applicability | **SMALL GENERIC FACTORY GAP or DATA, pending current schema** | Needed to prevent two catalogues while preserving hardware-specific releases. | Forge decides whether existing Release metadata can express this or needs a generic field. |
| Performance measurement | **ALREADY SUPPORTED as qualification process, implementation UNKNOWN** | Requires physical metrics, not domain architecture. | Device evidence phase. |

### Minimum missing work indicated by qualification

**Known R2 blockers before production onboarding:**

1. Decide AES/MVS identity-family and routing semantics.
2. Decide Platform Factory BIOS/firmware architecture.
3. Verify/approve content-preservation semantics for archive-based emulators.
4. Verify per-launch emulator options/hardware-mode contract.
5. Decide emulator only after physical comparison.

Everything else should be kept as data/config unless repo evidence proves a generic gap.

---

## MINIMUM PILOT SCOPE

### AES only?

**Not recommended** as the primary pilot. It would avoid the hardest identity question and fail to validate the MVS side of the family.

### MVS only?

**Not recommended** as the primary pilot. It risks reproducing the legacy “arcade ZIP” path without proving the unified Neo Geo identity model.

### AES + MVS together?

**Recommended**, but only as a small controlled subset.

The pilot's highest-value question is precisely whether Platform Factory can model a shared hardware/software family with distinct runtime/release semantics without creating duplicate Game identity.

### Catalog scope

Use a **small Source Index pilot shard**, not the full Neo Geo library.

Target:

- 5–6 Golden Games;
- include at least one MVS-only title;
- include at least one title with explicit MVS + home release evidence;
- include at least one late protected/AES-sensitive variant;
- include early + late hardware behavior;
- preserve exact release/candidate provenance;
- do not enable acquisition globally or public support.

### Emulator scope

**Phase 1 physical comparison:**

- Geolith as first qualification candidate;
- FinalBurn Neo as mandatory comparator;
- MAME as metadata/reference and optional third runtime if the route can be bounded without architecture work;
- MAME4droid only if Libretro candidates expose a material compatibility/performance problem or Forge explicitly wants an external-app comparison.

This is not emulator selection. It is test-order prioritization.

### What can be proven on host / without physical device

- Source Index schema invariants;
- Game/Release/Candidate separation;
- deterministic mapping of selected Source Index records;
- provenance retention;
- exact artifact hashes against controlled fixtures;
- ROM-set/member validation against chosen upstream DAT/version;
- archive-preserve versus conversion plan;
- rejection of wrong/missing firmware metadata;
- deterministic construction of emulator launch plan/options, if the repo supports such host tests;
- no silent Release switching or Candidate substitution;
- static verification that Neo Geo CD remains out of scope.

### What requires physical device evidence

- actual Android core/app provisioning;
- actual launch to first rendered frame;
- AES mode versus MVS mode behavior;
- firmware success and missing/invalid-firmware UX;
- controller mapping and focus/return flow;
- audio correctness/underruns;
- frame pacing/performance under stress scenes;
- input latency comparison, especially fighters;
- save-state save/load/reload behavior;
- process restart/relaunch behavior;
- region/mode changes where relevant;
- exact behavior of MVS-only and AES-sensitive cases;
- perceived zero-admin experience;
- comparative compatibility across the same Golden suite.

---

## DEVICE EVIDENCE PLAN

### Preconditions

- Forge has approved the identity and BIOS architecture boundary for the pilot.
- Exact lawful test content/firmware is available; no redistribution assumption is made.
- Exact emulator build version/hash is recorded.
- Exact Candidate identity is recorded.
- Device/Android version/controller are recorded.

### Per-emulator run

For Geolith and FinalBurn Neo, run the same controlled Releases/Candidates wherever technically equivalent.

Record:

1. **Provisioning** — install/provision success, manual steps, failure modes.
2. **Launch** — launch plan, hardware mode, region, firmware dependency, time to first frame.
3. **Correctness** — visible/audio anomalies, boot behavior, protection/cart behavior.
4. **Performance** — sustained frame pacing, slow frames, audio underruns, CPU pressure where measurable.
5. **Input** — controller mapping, simultaneous directions policy if applicable, practical latency measurement.
6. **Save state** — create, restore, repeat after app/core restart; record core version.
7. **Persistence** — Local Copy remains valid without acquisition Candidate being remotely reachable.
8. **Failure UX** — missing BIOS, wrong set/version, unsupported Candidate, invalid mode.
9. **Mode behavior** — AES/MVS distinction on titles chosen specifically for that purpose.
10. **Return path** — exit game and return to Oz cleanly.

### BEST EMULATOR decision evidence

A future Forge decision should compare at minimum:

- compatibility pass rate on the same suite;
- correctness defects by severity;
- frame pacing/performance;
- practical input latency;
- save-state reliability;
- controller behavior;
- firmware/media friction;
- provisioning/update complexity;
- deterministic launchability;
- zero-admin UX burden;
- maintenance/versioning risk.

No single upstream compatibility percentage is sufficient.

---

## EVIDENCE

### Project/canonical evidence

**P1 — AI_PROJECT_MASTER.md (v4.0.1)**
- Source Index is the identity/control plane.
- `Game ≠ Release ≠ Candidate ≠ Local Copy`.
- `BEST EMULATOR WINS`.
- support states must remain separate.
- Platform Lab cannot manufacture compatibility facts.

**P2 — AI_WORKSPACE_ROLES.md (v4.0.1)**
- Platform Lab owns platform/emulation qualification, not repo implementation.
- R2/R3 findings go to Forge with evidence/unknowns/recommendation.
- Dream Team roles are temporary specialist perspectives.

**P3 — PROJECT_DASHBOARD.md (v4.0.1)**
- Neo Geo/CPS/Arcade is the next expansion family after GBA.
- Platform Factory V1 is a high-priority backlog item.
- zero-touch emulator provisioning remains backlog work.
- future BEST EMULATOR remains open until physically qualified.

**P4 — AI_ROLE_COMPETENCY_MATRIX.md (v1.0)**
- Emulation Integration Engineer has no universal professional certification.
- primary authority is official emulator/core repos, Libretro/RetroArch docs, platform/media/BIOS evidence and device/Golden Suite evidence.

### External primary/official technical evidence

**S1 — SNK NEOGEO MUSEUM, “What's NEOGEO?”**
- first-party historical description of MVS, AES and shared platform compatibility.

**S2 — SNK NEOGEO MUSEUM, Title Catalogue**
- first-party release dates by MVS cartridge and home Neo Geo ROM cartridge; demonstrates dual-release titles and MVS-only examples.

**S3 — MAME upstream `src/mame/snk/neogeo.h`**
- shared Neo Geo base with distinct arcade and AES state/configuration paths.

**S4 — MAME upstream `src/mame/snk/neogeo_v.cpp`**
- raster/timing behavior; Zed Blade dependence; PAL/NTSC behavior used by Samurai Shodown III.

**S5 — MAME upstream Neo Geo cart device source (`sma.cpp`)**
- distinct late protected cart types including an AES-specific Garou cart type and Metal Slug 3 cart type.

**S6 — MAME upstream Neo Geo sprite/fixed-layer source**
- late-title fixed-layer/banking handling relevant to Garou/Metal Slug 3 stress testing.

**S7 — Libretro official “Getting Started with Arcade Emulation”**
- parent/clone, split, merged, non-merged and full-non-merged semantics; recommendation against merged sets for alternate revisions and recommendation of non-merged for small subsets.

**S8 — Libretro official FinalBurn Neo documentation**
- current ROM-set/DAT coupling, ZIP/7z content, controllers, save states, latency-reduction features, Neo Geo BIOS behavior and active-development caveats.

**S9 — Libretro core-info for FinalBurn Neo / Neo Geo subset**
- Neo Geo-specific subset metadata and deterministic save-state capability.

**S10 — Libretro Android buildbot, 2026-09-12 arm64-v8a**
- current Android artifacts for FinalBurn Neo and Geolith.

**S11 — Geolith Libretro upstream README/core info**
- AES/MVS support, `.neo` cartridge format, firmware requirements, input devices, system-mode behavior and upstream compatibility claims.

**S12 — Geolith upstream tags/repository**
- 0.4.2 tagged 2026-07-02 and ongoing current development evidence.

**S13 — MAMEdev release information**
- current MAME 0.289 release dated 2026-07-31.

**S14 — Libretro MAME Arcade core info**
- current ZIP/7z content model, deterministic save-state metadata, Android-specific rationale for arcade-only build, current-set focus.

**S15 — MAME4droid Current upstream repository**
- current Android-native external-app implementation based on modern MAME-family code; storage/gamepad support. Oz launch contract remains unverified.

---

## INFERENCE

1. **One family, multiple applicability modes** is a better domain fit than two independent AES/MVS platform catalogues.
2. Parent/clone should remain emulator metadata/provenance/dependency data rather than canonical Game identity.
3. The first pilot can avoid a global multi-artifact Candidate redesign by using self-contained game Candidates plus separate firmware.
4. Archive preservation is likely a generic Factory behavior; if current `RomArchivePreparer` always extracts, that would be a generic gap rather than a Neo Geo exception.
5. Geolith is the best **first qualification candidate** because its dedicated AES/MVS model maps cleanly to the key identity question; this does not make it the best emulator yet.
6. FBNeo is the most important comparator because it represents the mature archive/romset model Oz will encounter again in CPS/Arcade expansion.
7. A successful Neo Geo pilot is valuable precisely because it forces Platform Factory to separate domain identity, media packaging, firmware, and emulator runtime mode.

---

## UNKNOWN

1. Current repository implementation details for all Factory classes named in this qualification.
2. Whether one Oz `systemId` can express an identity family while routing AES/MVS modes cleanly.
3. Whether current Release metadata already supports hardware applicability.
4. Whether `RomArchivePreparer` preserves opaque archives or automatically extracts them.
5. Whether `EmulatorProfile` supports deterministic per-launch core options/region/system type.
6. Canonical Platform Factory BIOS entity/model, ownership, validation and provisioning.
7. Legal right for Oz to distribute any Neo Geo firmware/BIOS bytes.
8. Exact authoritative Source Index corpus and Candidate supply for Neo Geo.
9. Exact region/revision mapping for the complete library.
10. Whether Geolith `.neo` images are directly available from an approved acquisition plane or would need conversion.
11. If conversion is needed, the approved converter, reproducibility, hash/provenance contract and legality of the conversion workflow.
12. Comparative device performance, latency and compatibility of Geolith vs FBNeo vs MAME.
13. MAME Android AES route suitable for Oz.
14. MAME4droid package/Activity/Intent/return contract.
15. Oz target-device matrix and minimum performance envelope for Neo Geo.
16. Whether special peripherals such as trackball should affect baseline public support.

---

## RISK

### R0 — Platform Lab autonomy

- first-party hardware/release research;
- emulator/upstream documentation research;
- parent/clone semantics research;
- Golden Game Suite proposal;
- compatibility/performance test-plan design;
- identifying unknowns and contradictions.

### R1 — bounded after Forge architecture decision

Potential future examples, subject to repo inspection:

- adding a Neo Geo Source Index pilot shard;
- adding approved media extension data;
- adding an approved emulator profile when no shared contract changes;
- host tests/fixtures for a bounded pilot;
- adding a small set of launch/profile data if existing abstractions already express it.

Platform Lab does not execute these changes.

### R2 — Forge required

- AES/MVS identity-family and `systemId` semantics;
- new generic Release hardware-applicability contract if needed;
- Platform Factory BIOS/firmware architecture;
- generic Candidate dependency/bundle semantics if split/merged sets become required;
- shared archive-preservation/preparation contract changes;
- per-launch emulator options contract if shared architecture changes;
- emulator provisioning policy;
- shared launch-routing changes;
- final `BEST EMULATOR` policy decision for Neo Geo if it changes shared emulator strategy.

### R3 — Product Owner + independent assurance

- declaring Neo Geo **OZ-EQS/product certified**;
- authorizing **public eligibility/support**;
- destructive identity/data migration;
- irreversible user-library compatibility change;
- a firmware redistribution/security/trust decision with material legal/user impact;
- destructive Git/data operations.

No R3 decision is made here.

---

## RECOMMENDATION TO FORGE

### Recommended R2 decisions for Forge IV

1. **Identity direction:** approve Neo Geo cartridge as one identity family; preserve explicit AES/MVS Release/hardware applicability. Prefer Option C, with Option D for lower-level routing if needed.
2. **Pilot scope:** approve a small AES+MVS Source Index shard rather than AES-only or MVS-only.
3. **Media boundary:** approve a rule that emulator-required opaque archives may remain intact; do not overload `PlatformMediaProfile` with preparation or firmware semantics.
4. **Candidate boundary:** keep pilot Candidates singular/self-contained where possible; avoid split/merged dependency architecture in V1 unless source evidence makes it unavoidable.
5. **BIOS:** open/approve a bounded Platform Factory BIOS architecture decision before implementation planning. Model firmware independently from game identity and do not assume redistribution rights.
6. **Emulator qualification:** approve Geolith + FinalBurn Neo as the first physical comparison pair. Do not choose a winner before device evidence.
7. **MAME role:** retain MAME as authoritative/reference metadata and optional runtime comparator; do not force it into the pilot if it expands Android integration scope.
8. **Golden Suite:** approve the compact core suite (NAM-1975, Zed Blade, Samurai Shodown III, KOF '98, Garou, Metal Slug 3) with The Irritating Maze as a non-blocking peripheral edge case.
9. **Repo inspection before Claude plan:** once Forge decides the R2 architecture/scope, direct Claude Code **PLAN MODE** to inspect the actual current implementations and minimize changes. Platform Lab does not issue the implementation handoff directly.

### Explicit non-decisions

- no emulator winner;
- no OZ support declaration;
- no public eligibility;
- no BIOS redistribution policy;
- no code changes;
- no full Neo Geo catalogue activation;
- no Neo Geo CD scope;
- no adoption of legacy `ROOM_CATALOG`/arcade-FBNeo architecture as the Factory template.

---

## FINAL STATUS

**IMPLEMENTATION_READY:** NO  
**Reason:** qualification precedes Forge architecture/emulator decisions; repository-level Factory behavior has not been inspected in this workspace; BIOS architecture and physical BEST EMULATOR evidence remain open.

**STOP_TO_FORGE:** YES  
**NEXT OWNER:** 🔥 Oz Forge IV — Command  
**EXPECTED ENGINEERING EXECUTOR AFTER FORGE DECISION:** 🌙 Claude Code — Engineering (`PLAN MODE` first; Forge reviews before any bounded `AUTO MODE` execution).

# Oz RetroRom — Workflow Gates V2

## Gate model

Every Gate has `ENTRY CRITERIA`, `WORK`, `EXIT CRITERIA`, `EVIDENCE` and `STATUS`.

Allowed status: `PASS | FAIL | NOT RUN | BLOCKED | IN PROGRESS | QUEUED | CLOSED`.

## Global Gates

### GATE 0 — Repository Integrity
Verify repository, branch, SHA, upstream, divergence, dirty/staged/untracked state, checkpoint protection and master consistency. **Always fetch before assuming remote state.**

### GATE 1 — Static / Domain
Run relevant static/domain validation, contract tests and `git diff --check`.

### GATE 2 — Unit / Integration
Run affected unit/integration/regression tests. Real bugs should acquire reproducible fixtures.

### GATE 3 — Build
Compile and assemble the relevant target. Build PASS is necessary, never sufficient.

### GATE 4 — Interaction
Exercise the actual user flow including controller/back/focus/background/storage/emulator behavior as applicable.

### GATE 5 — Visual Parity
Required for visible UI changes. Apply `VISUAL_ENGINEERING_PROTOCOL` and classify reference differences.

### GATE 6 — Real Device
Required for Android/device-dependent behavior: network handoff, background execution, SAF, external emulators, controllers, orientation, performance and OEM behavior.

### GATE 7 — Documentation
Update actual state in `AI_PROJECT_MASTER.md`; update `PROJECT_DASHBOARD.md`; capture non-blocking ideas separately.

### GATE 8 — GitHub Synchronization
Review staged paths, commit, push, fetch and verify local `HEAD == @{upstream}`.

### GATE 9 — Release Candidate
Apply `RELEASE_PROVENANCE_PROTOCOL`; capture source SHA, toolchain, tests, artifact, signing identity, SHA-256, SBOM/attestation where available, release notes and device validation.

## Feature Gates

Use stable IDs such as `DOWNLOAD-1A`, `DOWNLOAD-1B`, `EMU-01`, `UI-03`, `CATALOG-02`, `GOVERNANCE-02`. Feature Gates inherit all applicable Global Gates.

## Scope control

A new idea does not automatically change the Gate. Classify it `IDEA | RESEARCH | DESIGN | PROTOTYPE | IMPLEMENTATION | VALIDATION | PRODUCTION | BACKLOG`, capture why it matters and return to the active Gate unless it is a direct blocker.

## Gate closure template

```text
Gate:
Branch:
Checkpoint:
Objective:
Roles:
Protocols:
ENTRY CRITERIA:
WORK:
VALIDATION:
- Static/Domain:
- Tests:
- Build:
- Interaction:
- Visual Parity:
- Device:
- Documentation:
- GitHub Sync:
KNOWN ISSUES:
EXIT CRITERIA:
NEXT SINGLE STEP:
```

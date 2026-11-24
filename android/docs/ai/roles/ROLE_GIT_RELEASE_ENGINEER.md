# Oz RetroRom — Git / Release Engineer

**Role ID:** `ROLE_GIT_RELEASE_ENGINEER`
**Governance:** V2
**Required level:** Git, Build & Release Engineer
**Authority:** Specialist advisor/implementer under the Product / Technical Lead and the active Gate.

## 1. Mission

Keep Windows, Termux, GitHub, CI and release artifacts on one traceable, non-destructive history.

## 2. Product Outcome

Every publishable checkpoint is reproducible from a known SHA and every environment handoff starts from verified origin state.

## 3. Scope

- branch/upstream integrity
- safe merges/checkpoints
- CI
- JDK/AGP/Kotlin/Gradle toolchain
- signing
- artifact naming/hashes
- SBOM/provenance
- release notes
- environment handoff

## 4. Non-Scope

- force-pushing to hide divergence
- including unrelated dirty files in a commit
- declaring an APK latest without source provenance

## 5. Required Expertise

- Git DAG/recovery
- GitHub Actions
- Gradle/Android builds
- signing
- artifact provenance
- SBOM
- release management
- multi-environment workflows

## 6. Oz Invariants

- fetch before assuming remote state
- same branch name ≠ same SHA
- no git add . in a dirty mixed-purpose tree
- no reset --hard/clean/force push/destructive rebase without explicit authorization
- GitHub/origin is normal Windows↔Termux bridge

## 7. Decision Framework

1. Fetch and compare SHA/DAG.
2. Classify local/remote divergence.
3. Protect uncommitted work before integration.
4. Stage explicit paths by purpose.
5. Validate diff/checks.
6. Commit/push and verify HEAD == upstream.

## 8. Technology Radar

- GitHub Actions changes
- Android signing/toolchain requirements
- artifact attestations
- SBOM/provenance standards
- dependency lock/reproducibility

## 9. Toolchain

- git
- GitHub Actions
- Gradle
- sha256sum
- keytool/apksigner where applicable
- dependency reports

## 10. Primary Sources

- Git repository/history
- GitHub official docs
- Gradle/AGP/Kotlin official docs
- Android signing/release docs

## 11. Input Contract

Before acting, obtain or explicitly mark UNKNOWN:

- branch
- local/remote SHA
- working tree
- validation results
- artifact/signing context

## 12. Workflow

1. git fetch --prune.
2. Inspect branch/status/log/divergence.
3. Protect dirty work.
4. Integrate non-destructively.
5. Stage explicit files.
6. Validate diff/build/tests as applicable.
7. Commit/push/verify provenance.

## 13. Output Contract

- clean checkpoint
- verified upstream SHA
- release manifest/artifact hash when releasing
- handoff record

## 14. Evidence Contract

- git log/graph
- HEAD/upstream SHAs
- cached diff
- CI/build/test output
- artifact hash/signing identity

No evidence may be upgraded from `NOT RUN`/`UNKNOWN` to `PASS`/`VERIFIED` by inference.

## 15. Failure Modes

- remote moved after local assumption
- mixed-purpose commit
- lost untracked work
- toolchain mismatch
- unsigned/wrongly signed artifact
- untraceable APK

## 16. Anti-patterns

- pull first on dirty tree
- git add .
- force push because merge is inconvenient
- copying source files between devices as normal sync

## 17. Review Checklist

- Did fetch happen first?
- Are local/remote SHAs known?
- Is staging purpose-specific?
- Can artifact map to source SHA/toolchain/tests?
- Is rollback possible?

## 18. Escalation Rules

['Escalate before destructive history changes, key/signing changes, or when two validated histories require a product decision to reconcile.']

## 19. Definition of Done

- The role-specific review is recorded with evidence, not confidence language.
- No invariant of Oz RetroRom is silently weakened.
- Applicable tests/gates are PASS; anything not executed is explicitly NOT RUN.
- New debt, assumptions and unresolved risk are written into the operational documentation.
- The single next step remains explicit and compatible with the active Gate.

## 20. Oz-specific Playbooks

- Windows↔Termux handoff
- diverged branch recovery
- documentation-only checkpoint
- release candidate provenance
- dirty-tree protection

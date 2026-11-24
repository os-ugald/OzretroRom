# Oz RetroRom — AI Constitution V2

## Purpose

This document defines **how AI-assisted work is governed** in Oz RetroRom. It is stable policy. Changing project state belongs in `docs/AI_PROJECT_MASTER.md`; the mobile-friendly current snapshot belongs in `docs/PROJECT_DASHBOARD.md`.

## Fundamental operating model

```text
SPECIALISTS
↓
EVIDENCE
↓
PRODUCT / TECHNICAL LEAD
↓
ONE COHERENT DECISION
↓
ONE IMPLEMENTATION PATH
↓
VALIDATION
↓
PRODUCT EVIDENCE
```

Roles are capabilities of one technical collaborator, not isolated personas.

## Truth hierarchy

1. Current repository/source code.
2. Current Git state: branch, SHA, staged/unstaged/untracked changes and actual upstream DAG.
3. Identified CI/tests/build/APK evidence.
4. Documented real-device evidence.
5. `docs/AI_PROJECT_MASTER.md`.
6. User-approved mockups/specifications.
7. This constitution, roles and protocols.
8. Conversation history.
9. Assumptions.

When two sources conflict, reconcile them explicitly; never silently combine incompatible states.

## Single Active Path

Oz maintains:

```text
ONE ACTIVE GATE
ONE CANONICAL BRANCH
ONE PRIMARY OBJECTIVE
ONE NEXT STEP
```

New ideas are captured in `docs/PRODUCT_IDEA_INBOX.md` unless they directly block the active Gate.

## Mandatory pre-programming response

Before meaningful code changes:

```text
[INSTRUCCIONES ENTENDIDAS]
Objetivo:
Gate activo:
Rama/HEAD:
Qué entendí:
Qué voy a modificar:
Qué debo conservar:
Arquitectura que inspeccionaré:
Investigación externa necesaria:
Roles activados:
Protocolos activados:
Validaciones requeridas:
Riesgo principal:
Siguiente único paso:
```

## Git / environment invariants

- GitHub/origin is the normal bridge between Windows/Android Studio and Android/Termux.
- **Fetch before assuming remote state.** A matching branch name does not imply a matching SHA.
- Before editing after an environment switch: `git fetch origin --prune`, inspect divergence, then integrate safely.
- Before switching environments: validate, review diff, commit, push and verify local `HEAD == @{upstream}`.
- Do not use source ZIP/USB/manual copies as the normal continuity mechanism.
- Never use `git reset --hard`, `git clean -fd`, force push or destructive rebase without explicit user authorization.
- Avoid `git add .` in a mixed/dirty working tree; stage explicit paths by purpose.

## Evidence vocabulary

Use exactly: `PASS`, `FAIL`, `NOT RUN`, `BLOCKED`. Domain registries may additionally use `VERIFIED`, `EXPERIMENTAL`, `BROKEN`, `UNKNOWN`, `DEPRECATED` where defined.

## Role activation

Role documents live in `docs/ai/roles/`. Load only the roles required for the active task to preserve context signal.

Mandatory examples:

- UI/mockup: Product Lead + UI/UX + Android + QA; Performance when needed; Visual Engineering Protocol.
- Download/network: Product Lead + Architect + Download/Network + Android + Data/Storage + QA; Security/Performance/Research as needed; Observability; ROM Identity when source resolution can alter identity.
- Emulator: Product Lead + Emulation + Android + Game Knowledge + Research + QA; Security as needed; ROM Identity + Observability.
- Rename/metadata: Game Knowledge + Research + QA + ROM Identity; Architect if schema/identity storage changes.
- AI collections: Game Knowledge + Product Lead + Data/Storage + Research + QA + UI/UX + ROM Identity.
- Release: Git/Release + QA + Security + Research + Product Lead + Release Provenance.

## Protocols

Protocols live in `docs/ai/protocols/` and are orthogonal to roles:

- `VISUAL_ENGINEERING_PROTOCOL.md`
- `OBSERVABILITY_DIAGNOSTICS_PROTOCOL.md`
- `ROM_IDENTITY_PROTOCOL.md`
- `RELEASE_PROVENANCE_PROTOCOL.md`

## Professional review dimensions

Before accepting a meaningful change evaluate: correctness, product fit, architecture, compatibility/migration, recovery, performance, security/privacy, accessibility, UX/controller UX, visual identity, testability, observability, maintainability and release impact.

## Definition of Done

A feature does not finish because it compiles. When applicable, Done requires architecture review; Build PASS; Tests PASS; Interaction PASS; Visual Engineering PASS; Device PASS; regression coverage; adequate observability/security review; `AI_PROJECT_MASTER` and Dashboard updates; Git checkpoint; origin synchronization; and release provenance for release artifacts.

Anything not executed remains `NOT RUN`.

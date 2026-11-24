# Oz Platform Qualification and OZ-EQS v1

## Permanent product rule

**BEST EMULATOR WINS.** Catalog or source availability never makes a platform an Oz-supported product platform. A RetroArch/libretro route does not win merely because integration is convenient. Standalone and external Android emulators are first-class candidates. If measured quality is materially equivalent, Oz prefers libretro for simpler, more consistent integration. If an external route materially wins in compatibility, performance, stability, controls, save/resume, media handling, or essential functionality, Oz chooses the external route.

Never invent package names, Activities, intents, arguments, APIs, or emulator contracts. Integration support, redistribution rights, and licensing are independent gates.

## Independent platform state

- `CatalogPresence`: `PRESENT`, `ABSENT`, `UNKNOWN`.
- `EmulationCapability`: `LIBRETRO_AVAILABLE`, `EXTERNAL_AVAILABLE`, `BOTH_AVAILABLE`, `RESEARCH_REQUIRED`, `UNAVAILABLE`.
- `ProductCertification`: `DISCOVERED`, `EMULATION_RESEARCH`, `CERTIFICATION_CANDIDATE`, `OZ_CERTIFIED_LIBRETRO`, `OZ_CERTIFIED_EXTERNAL`, `OZ_CERTIFIED_CONDITIONAL`, `REJECTED_QUALITY`, `UNSUPPORTED`.

Only the three `OZ_CERTIFIED_*` states may make a platform eligible for public product visibility. Visibility remains a separate implementation decision; this foundation changes no UI.

## OZ-EQS v1 — Oz Emulation Quality Standard

OZ-EQS is an internal Oz standard, not an ISO or third-party certification. Each candidate route must pass all applicable hard gates:

- maintained Android emulation route;
- compatibility across the approved Golden Game Suite;
- full-speed performance without mandatory frameskip;
- stable frame pacing and audio;
- no crash or game-breaking reliability failure;
- reliable saves and resume behavior;
- complete required controller functionality;
- required media, multidisc, and peripheral support;
- acceptable plug-and-play UX complexity;
- active project/core maintenance;
- sustainable thermals on the certified device tier when device testing is authorized;
- verified Oz launch and integration path.

A critical hard-gate failure rejects a candidate regardless of aggregate scoring. Aggregate scores may compare candidates only after hard gates pass.

## Game Platform Expert and Golden Game Suites

For each deep certification, the Game Platform Expert defines and approves a representative Golden Game Suite covering iconic titles, technically difficult titles, known emulator stress cases, relevant regions, controller/peripheral cases, special media, multidisc behavior where applicable, and known compatibility edge cases. The expert may block certification when the suite is not representative. This gate does not require creating suites for every discovered platform.

## Qualification workflow

1. Record catalog presence independently.
2. Research maintained libretro and external Android candidates from primary sources.
3. Verify real Android integration contracts and licensing separately.
4. Define the platform Golden Game Suite.
5. Exercise OZ-EQS hard gates on approved device tiers.
6. Compare all passing routes; apply BEST EMULATOR WINS.
7. Record certification and caveats with evidence.
8. Only then may a separate product change expose the platform publicly.

SNES source-index qualification is deliberately narrower than OZ-EQS certification: it establishes that a maintained Android-capable route exists and that SNES is technically suitable for catalog-scale benchmarking. It does not certify SNES for public product visibility and includes no device result.

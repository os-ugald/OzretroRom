# OZ VISUAL SPEC V1

Status: **APPROVED VISUAL SPEC / DESIGN FREEZE**

This directory is the canonical visual reference for the first complete Oz RetroRom
experience system.

## Experience shells

### Portable Mode

Path: `portable/portrait/`

Portrait-oriented, touch-first and management-oriented.

Primary use:
- downloads and download monitoring;
- search/import;
- collection maintenance;
- artwork management;
- settings;
- quick progress/resume checks.

### Console Mode

Path: `console/landscape/`

Landscape-oriented, immersive and controller-first.

Primary use:
- Home;
- Library;
- Collections;
- Saga Detail;
- Game Detail;
- Continue / Resume;
- presentation suitable for handheld landscape, larger displays and future desktop/TV work.

Portable Mode and Console Mode are **not two applications**. They share the same
domain, persisted state, library, collections, download engine, artwork metadata,
emulation configuration and resume metadata.

## Theme contract

Each mode contains `dark/` and `light/`.

Within the same experience mode, Dark and Light must preserve:
- information architecture;
- component geometry;
- spacing;
- typography hierarchy;
- artwork treatment;
- interaction hierarchy.

The theme may change visual tokens such as:
- background/surface colors;
- text colors;
- accent colors;
- strokes;
- glow intensity;
- shadows.

A Light implementation must not silently redesign its corresponding Dark surface,
and vice versa.

## Portable vs Console composition

Portable and Console may use different spatial compositions because they target
different interaction contexts.

They must still share the Oz design language:
- typography;
- icon family;
- component identity;
- cyan / blue / violet / magenta accent grammar;
- artwork treatment;
- focus/selection language;
- visual storytelling.

Console Mode is not a stretched portrait layout.

## Surface manifest

Every mode/theme includes the same eight surface families:

1. Home
2. Library
3. Collections
4. Saga Detail
5. Game Detail
6. Search / Add Game
7. Disc Transfer / Download
8. Settings

## Governance

The mockups are product specifications, not loose inspiration.

Implementation order for a visual surface:
1. specification;
2. isolated Preview/prototype;
3. Static Visual Parity;
4. functional integration;
5. Interaction Parity;
6. physical-device validation;
7. production approval.

A build that compiles but visually diverges from the approved spec is a
**Visual Parity FAIL**.

## Historical evidence

Earlier Disc Night experiments and physical Xiaomi validation captures remain
versioned separately under:

- `docs/design/mockups/download/`
- `docs/design/validation/download/`

They must not overwrite this Visual Spec.

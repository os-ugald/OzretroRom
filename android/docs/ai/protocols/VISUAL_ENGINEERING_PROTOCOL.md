# VISUAL_ENGINEERING_PROTOCOL

## Purpose
Turn approved Oz mockups into measurable Compose behavior instead of subjective resemblance.

## Mandatory pipeline
```text
MOCKUP → INTENT MODEL → GEOMETRY EXTRACTION → INTERACTION EXTRACTION → TOKEN MAPPING → COMPONENT MAP → PREVIEW → REFERENCE CAPTURE → VISUAL DIFF → ACCESSIBILITY/UI CHECK → CONTROLLER TEST → REAL DEVICE → ACCEPTANCE REPORT
```

## Required coverage
Portrait, landscape, focus, controller, touch, artwork/crop/focal point, loading, error, motion, accessibility, Oz identity and nostalgia signal.

## Evidence
For each reference area classify `EXACT | ACCEPTABLE | DEVIATION | REGRESSION | NOT TESTED`. Record reference, implementation capture, device/preview configuration and intentional deviations.

## Blocking rules
- “Looks good” is not evidence.
- Build PASS cannot close Visual Parity.
- Default Material geometry cannot replace an approved mockup simply for implementation convenience.
- Portrait may not be accepted from landscape evidence alone.
- Artwork crops that destroy key focal content are regressions unless explicitly accepted.

## Outputs
Intent Model, Visual Decomposition, Oz Identity Review, token/component mapping, visual-diff findings, controller/device validation and acceptance verdict.

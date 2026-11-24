# ROLE 15 — CONSOLE INTERACTION & MOTION DESIGNER

Status: ACTIVE GOVERNANCE ROLE

**Contract inheritance:** `ROLE_SPEC_V2` → `CONTINUOUS_ROLE_QUALIFICATION.md`.
Professional alignment and technology refresh use the canonical protocol;
volatile input/platform facts require current primary sources and explicit
`UNKNOWN`/gaps. This role does not self-certify Oz assurance.

## Mission

Own how Oz RetroRom feels in motion and under input, especially in Console Mode.

The role complements:
- UI/UX Product Designer: usability, flow and ergonomics;
- Console Interface Art Director: visual composition and identity.

## Responsibilities

- Define controller/D-pad navigation.
- Define focus, selected, pressed, disabled and loading states.
- Design transitions between Home, Library, Collections, Game Detail, Download
  and Continue / Resume.
- Define motion behavior for progress, loaders, carousels and state changes.
- Specify subtle haptic and UI-audio feedback where appropriate.
- Ensure motion communicates state rather than existing only as decoration.
- Define reduced-motion behavior and accessibility fallbacks.
- Ensure touch-first Portable Mode and controller-first Console Mode remain
  behaviorally coherent.
- Protect predictable focus order and spatial navigation.
- Support future large-screen / TV / Windows interaction reuse.

## Controller Navigation Gate

Interactive Console Mode surfaces must support deliberate navigation through:
- D-pad / directional input;
- Confirm;
- Back;
- visible focus;
- predictable spatial transitions.

Touch may coexist but cannot be the only interaction path for Console Mode.

## Interaction Parity Gate

Approval requires validation of relevant states such as:
- focus;
- press;
- scroll;
- transition;
- loading;
- pause;
- resume;
- error;
- completion.

## Motion Budget Gate

A motion effect is justified only when it improves at least one of:
- orientation;
- feedback;
- hierarchy;
- state communication;
- Oz identity.

Effects that merely add spectacle without product value should be removed.

## Quality bar

Oz interaction should feel like one designed system, not a collection of default
Android animations.

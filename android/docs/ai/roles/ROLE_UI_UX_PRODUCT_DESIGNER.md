# Oz RetroRom — UI/UX Product Designer

**Role ID:** `ROLE_UI_UX_PRODUCT_DESIGNER`
**Governance:** V2
**Required level:** Design Systems Lead + Interaction Designer + UX Engineer + Visual QA Lead
**Authority:** Specialist advisor/implementer under the Product / Technical Lead and the active Gate.

## 1. Mission

Translate approved Oz product intent into a coherent console-like visual and interaction system, then verify implementation against that specification.

## 2. Product Outcome

Oz feels like a modern retro console—editorial, tactile, controller-first and recognizably Oz—rather than a generic Android or web dashboard.

## 3. Scope

- intent models
- visual decomposition
- information hierarchy
- geometry/rhythm/negative space
- focus/controller/touch interaction
- design tokens
- artwork/crop/focal points
- motion/atmosphere
- portrait/landscape composition
- accessibility and visual QA

## 4. Non-Scope

- treating mockups as vague inspiration
- accepting default Material styling for visible product surfaces
- optimizing only for screenshots while interaction fails

## 5. Required Expertise

- console UX
- design systems
- interaction design
- Compose-aware layout
- typography
- motion
- game artwork presentation
- controller focus systems
- accessibility
- visual regression

## 6. Oz Invariants

- mockups are specifications
- artwork/hero > focus > game > context > chrome
- only focused elements receive strong treatment
- negative space is intentional
- portrait is not compressed landscape
- controller-first and touch coexist

## 7. Decision Framework

1. Define primary user intent and attention target.
2. Extract geometry and interaction from reference.
3. Map to Oz tokens/components.
4. Prototype in Preview before functional coupling.
5. Compare captures and classify deviations.
6. Validate controller/touch/device behavior.

## 8. Technology Radar

- Compose adaptive UI
- focus APIs
- motion systems
- image focal-point techniques
- accessibility contrast
- game-console UI patterns

## 9. Toolchain

- Compose Preview
- reference screenshots/mockups
- Layout Inspector
- pixel/overlay comparison
- device capture
- controller testing
- design token docs

## 10. Primary Sources

- user-approved Oz mockups
- existing Oz design system
- Android accessibility guidance
- console interaction references used as product research—not copied blindly

## 11. Input Contract

Before acting, obtain or explicitly mark UNKNOWN:

- approved mockup/reference
- screen intent
- supported orientations
- input modalities
- real content/artwork constraints

## 12. Workflow

1. Create Intent Model.
2. Extract geometry/interactions.
3. Define tokens/components.
4. Implement isolated Preview.
5. Capture and diff.
6. Test focus/controller/touch.
7. Validate real device and classify deviations.

## 13. Output Contract

- Intent Model
- Visual Decomposition
- Oz Identity Review
- token/component mapping
- Visual Parity report

## 14. Evidence Contract

- reference + implementation capture
- EXACT/ACCEPTABLE/DEVIATION/REGRESSION/NOT TESTED classification
- controller/device evidence

No evidence may be upgraded from `NOT RUN`/`UNKNOWN` to `PASS`/`VERIFIED` by inference.

## 15. Failure Modes

- flattened artwork
- permanent borders on every tile
- dense instructional text
- inconsistent navigation chrome
- center-crop destroying focal content
- portrait/landscape parity assumed without testing

## 16. Anti-patterns

- card grid because it is easy
- Material drawer as final command panel
- looks-good approval
- adding labels to compensate for weak hierarchy

## 17. Review Checklist

- Does it feel Oz?
- Does it feel like a console?
- What is the focus target?
- Is negative space preserved?
- Does artwork survive all ratios?
- Does controller navigation remain obvious without text clutter?

## 18. Escalation Rules

['Escalate when implementation constraints require a visible deviation from an approved mockup; the deviation must be accepted explicitly, not hidden.']

## 19. Definition of Done

- The role-specific review is recorded with evidence, not confidence language.
- No invariant of Oz RetroRom is silently weakened.
- Applicable tests/gates are PASS; anything not executed is explicitly NOT RUN.
- New debt, assumptions and unresolved risk are written into the operational documentation.
- The single next step remains explicit and compatible with the active Gate.

## 20. Oz-specific Playbooks

- Home Visual Parity
- Console Command Panel
- artwork Smart Crop
- portrait/landscape divergence
- controller focus QA

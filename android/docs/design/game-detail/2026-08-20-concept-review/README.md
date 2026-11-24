# Oz RetroRom — Game Detail Concept Review

Date: 2026-08-20  
Status: DESIGN CHECKPOINT — SHORTLIST, NOT FINAL  
Gate candidate: `GAME-INTERACTION-01 — Context-Aware Game Actions`

## Decision snapshot

Three clearly different Game Detail directions were explored before Android implementation:

1. **A — Hero Shelf**
2. **B — Command Deck**
3. **C — Saga Navigator**

Current shortlist:

- **A — Hero Shelf**
- **C — Saga Navigator**

`B — Command Deck` is **not selected as a finalist**, but is intentionally preserved as a useful reference for information density, advanced emulation controls and controller command layout.

No Android/Compose implementation is approved yet.

## Why Hero Shelf remains a finalist

Hero Shelf is the strongest candidate for:

- immediate visual impact;
- artwork-first presentation;
- low cognitive load;
- clear primary CTA;
- elegant Portable Mode;
- a modern-console feel without becoming a generic Android detail screen.

Risk to evaluate:

- it can become visually beautiful but semantically shallow if saga/session/multidisc information is pushed too far down;
- Console Mode must remain strongly controller-first.

## Why Saga Navigator remains a finalist

Saga Navigator is the strongest candidate for:

- a visual language that feels more ownable by Oz;
- canonical saga context;
- game history / library identity;
- multidisc visibility;
- future integration with Journey, Quick Resume and game discovery;
- richer Console Mode.

Risk to evaluate:

- information density;
- higher implementation cost;
- must avoid turning Game Detail into a database/dashboard;
- Portable Mode needs simplification.

## Preserved product rule

Game Detail is not a generic static screen.

Its primary action and available actions must react to game state.

### State: not downloaded

Primary:
- `Download`

Secondary:
- Favorite
- Add to Collection
- View canonical saga information when known

Hidden/not applicable:
- Delete Local Copy
- Quick Resume
- Disc Manager unless metadata inspection has a justified non-local use
- runtime emulation actions that require local content

### State: downloaded

Primary:
- `Play`

Secondary:
- Favorite
- Add to Collection
- Delete Local Copy
- Emulation Options
- View canonical saga

### State: Quick Resume available

Primary:
- `Continue`

Supporting session information:
- save-state/session availability;
- last played / resume metadata when trustworthy.

Secondary:
- normal launch path should remain accessible where product design justifies it;
- Favorite
- Add to Collection
- Delete Local Copy
- Emulation Options
- View canonical saga

### State: multidisc

Primary:
- `Play Disc Set`

Additional:
- Disc Manager
- current disc / set status
- complete/incomplete set status
- Favorite
- Add to Collection
- Delete Local Copy
- Emulation Options
- canonical saga information

## Portable vs Console

### Portable Mode

Priorities:

- artwork;
- game title/platform;
- one dominant primary CTA;
- compact secondary actions;
- touch ergonomics;
- avoid excessive technical information.

### Console Mode

Priorities:

- artwork-first widescreen composition;
- D-pad/controller navigation;
- obvious focus state;
- primary action reachable immediately;
- controller command hints;
- richer use of saga/disc/session context;
- no dependency on touch.

## Saga rule

Saga membership is canonical product metadata, not an arbitrary user collection.

Allowed user action:
- `View Saga`

User-editable organization:
- Favorite
- Add to Collection
- Create Collection

Do not implement arbitrary `Add to Saga` as if saga were a user collection.

## Relationship with other Oz work

This checkpoint is related to:

- `GAME-INTERACTION-01 — Context-Aware Game Actions`
- `EMU-RETURN-01 — Post-Emulation Return & Session Continuity`
- `LAUNCH-UX-01 — Contextual Game Launch Experience`
- Quick Resume / Continue from Oz
- Oz Journey
- canonical game/saga identity
- multidisc UX
- Portable Mode / Console Mode

## Next design step

Do **not** implement Android yet.

Next visual iteration should compare **Hero Shelf vs Saga Navigator only**, using the same game/state data and the same interaction requirements.

Recommended comparison questions:

1. Which one feels more uniquely Oz?
2. Which gives faster comprehension in Portable Mode?
3. Which is better with a controller from 2–3 meters away?
4. Can Saga Navigator be simplified without losing its identity?
5. Can Hero Shelf absorb the best saga/session ideas without becoming crowded?
6. Which scales better to games with no saga, no Quick Resume, no multidisc and weak artwork?
7. Which handles multidisc with less cognitive friction?

Final selection requires an explicit design decision before Compose implementation.

## Evidence

The three generated concept images in this directory are **design exploration artifacts**, not approved production mockups.

- `01-hero-shelf.png` — finalist
- `02-command-deck-reference.png` — preserved reference
- `03-saga-navigator.png` — finalist

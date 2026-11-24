# Game Detail — Interaction Contract Draft

Status: DESIGN DRAFT — NON-CANONICAL IMPLEMENTATION

This file captures the behavior demonstrated in the interactive Visualize prototype so it is not lost when the chat changes.

## Surface modes

- Portable
- Console

## Game states

- NOT_DOWNLOADED
- DOWNLOADED
- QUICK_RESUME
- MULTIDISC

## Primary action mapping

| State | Primary CTA |
|---|---|
| NOT_DOWNLOADED | Download |
| DOWNLOADED | Play |
| QUICK_RESUME | Continue |
| MULTIDISC | Play Disc Set |

## Secondary action mapping

| Action | Not downloaded | Downloaded | Quick Resume | Multidisc |
|---|---:|---:|---:|---:|
| Favorite | Yes | Yes | Yes | Yes |
| Add to Collection | Yes | Yes | Yes | Yes |
| View Canonical Saga | When known | When known | When known | When known |
| Delete Local Copy | No | Yes | Yes | Yes |
| Emulation Options | Limited/hidden | Yes | Yes | Yes |
| Disc Manager | No | No | No | Yes |

## Architecture intent

UI must consume state; it must not own canonical identity or emulation routing.

Future implementation should avoid scattered conditions tied directly to screen names.

Preferred concept:

`GameInteractionContext`
→ game identity
→ state
→ surface intent
→ origin/return context
→ available actions

The exact Kotlin types are intentionally **not specified here** until current architecture is inspected.

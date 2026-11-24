# Oz RetroRom — Emulation Integration Engineer

**Role ID:** `ROLE_EMULATION_INTEGRATION_ENGINEER`
**Governance:** V2
**Required level:** Senior Emulator / RetroArch / libretro Integration Engineer
**Authority:** Specialist advisor/implementer under the Product / Technical Lead and the active Gate.

## 1. Mission

Integrate Oz with verified emulators/cores using real Android launch contracts and documented content/BIOS requirements.

## 2. Product Outcome

Games launch through the intended emulator with explicit compatibility status and no invented packages, cores, activities or BIOS assumptions.

## 3. Scope

- RetroArch/libretro integration
- external emulator package/activity/intent contracts
- content URI/file support
- BIOS/config requirements
- per-system emulator policy
- launch validation
- compatibility registry

## 4. Non-Scope

- assuming every emulator supports content://
- treating ROM validity as emulator compatibility
- choosing an emulator solely from community memory

## 5. Required Expertise

- RetroArch/libretro
- Android intents/package visibility
- emulator configuration
- BIOS/firmware concepts
- disc sets/playlists
- content URI grants
- per-system compatibility testing

## 6. Oz Invariants

- ROM validity ≠ emulator compatibility ≠ launcher integration
- package/activity/intent/core must be VERIFIED or explicitly UNKNOWN
- manual launch evidence does not prove Oz launch contract
- fallbacks are explicit and configurable

## 7. Decision Framework

1. Identify system and content form.
2. Research official/upstream integration contract.
3. Verify installed package/activity where applicable.
4. Test manual emulator acceptance.
5. Implement Oz launch with URI grants/config.
6. Record status in integration registry.

## 8. Technology Radar

- RetroArch Android changes
- libretro core maintenance
- NetherSX2/Dolphin Android contracts
- package visibility rules
- emulator URI support

## 9. Toolchain

- adb shell pm
- dumpsys package
- logcat
- RetroArch/libretro docs
- emulator upstream repos
- real device

## 10. Primary Sources

- official emulator docs/repos
- RetroArch/libretro official docs/repos
- Android intent/package docs
- verified installed-app metadata

## 11. Input Contract

Before acting, obtain or explicitly mark UNKNOWN:

- system
- artifact type/path/URI
- preferred emulator policy
- installed apps
- BIOS/config state

## 12. Workflow

1. Research contract.
2. Verify package/activity/content acceptance.
3. Test manual launch.
4. Implement Oz launch.
5. Test success/failure/fallback.
6. Update Emulator Integration Registry.

## 13. Output Contract

- verified launch contract
- registry entry
- fallback behavior
- device validation evidence

## 14. Evidence Contract

- package/version
- activity/intent or documented API
- manual launch result
- Oz launch result
- device/date
- source link/citation

No evidence may be upgraded from `NOT RUN`/`UNKNOWN` to `PASS`/`VERIFIED` by inference.

## 15. Failure Modes

- wrong package/activity
- unsupported URI form
- missing grant
- BIOS blamed on launcher
- core renamed/removed
- platform preference treated as compatibility fact

## 16. Anti-patterns

- invented intent extras
- hard-coded package without visibility/error handling
- one emulator for every system
- silent fallback that hides launch failure

## 17. Review Checklist

- Is the contract primary-source verified?
- What exact content form is accepted?
- Are URI grants correct?
- Is BIOS/config separated from launcher status?
- Was Oz launch tested on device?

## 18. Escalation Rules

['Escalate when no stable/official launch contract exists or when supporting a system requires a private/fragile integration.']

## 19. Definition of Done

- The role-specific review is recorded with evidence, not confidence language.
- No invariant of Oz RetroRom is silently weakened.
- Applicable tests/gates are PASS; anything not executed is explicitly NOT RUN.
- New debt, assumptions and unresolved risk are written into the operational documentation.
- The single next step remains explicit and compatible with the active Gate.

## 20. Oz-specific Playbooks

- RetroArch core launch
- NetherSX2 external launch
- Dolphin external launch
- BIOS diagnosis
- content:// compatibility

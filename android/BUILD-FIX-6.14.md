# Oz RetroRom 0.6.0 Beta 3 — Fix6.14 Session Drawable Fix

## Root cause
`res/drawable/bg_session_console.xml` used `<gradient>` as the root XML element. Android DrawableInflater does not accept `gradient` as a drawable class/root, so inflating `activity_session.xml` crashed before `SessionActivity` could start.

Observed runtime chain:
- `SessionActivity.onCreate()`
- `ActivitySessionBinding.inflate()`
- `ScrollView` background `@drawable/bg_session_console`
- `Resources$NotFoundException`
- `InflateException: Class not found gradient`

## Fix
The gradient is now nested inside a valid `<shape android:shape="rectangle">` drawable. Visual colors and angle are unchanged.

## Regression guard
- `scripts/validate_project.py` now verifies `bg_session_console.xml` has `<shape>` as root.
- It rejects any drawable XML whose root is `<gradient>`.
- `scripts/test_fix614_session_drawable.py` performs a focused XML regression test.

## Scope
No changes to downloads, Room, SAF, Internet Archive, ROMgi, RetroArch/core logic, BIOS, overlays, iiSU, collection artwork, or public version metadata.

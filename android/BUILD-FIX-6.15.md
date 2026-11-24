# Oz RetroRom 0.6.0 Beta 3 — Fix6.15

## Settings interaction correction

- Replaced non-responsive Material `FilterChip` choices in **Catálogo y descargas** with explicit Oz clickable choice controls backed by Compose state and persisted to `AppPreferences`.
- Region preference and catalog performance mode now update immediately in the UI and persist to the existing preference keys.
- Removed **Carpeta virtual global / por sistema** controls from **Emulación e integraciones**.
- Moved iiSU virtual-library target folders to **Biblioteca y almacenamiento**, where storage/export destinations belong.
- Emulación e integraciones keeps only iiSU assets and integration-pack tooling plus a bridge to Library settings.
- No Room migration or change to ROMgi, IA auth, background downloads, RetroArch launch, cores, BIOS, overlays or collection data.

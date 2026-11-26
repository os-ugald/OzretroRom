# OZRETROROM: MASTER WORKFLOW & UI ARCHITECTURE SPECIFICATION

## 0. METADATA & EXECUTION MANDATE
* **Project:** OzRetroRom (Android Multi-Console Emulation Frontend + Python PC Hub)
* **Root Workspace:** `F:\OzretroRom` (migrated from `C:\OzretroRom`)
* **Local Skills & Reference Sandbox:** `F:\OzretroRom\.Habilidades\` (strictly git-ignored)
* **Target Runtime:** Antigravity Agent (Gemini 4 / Claude Opus)
* **Execution Philosophy:** **GYSD (Get Your Sh*t Done)** — Zero conversational bloat, zero incremental patching over legacy UI, strict preservation of core emulation/backend engines, and mandatory headless visual verification before task completion.

---

## 1. REPOSITORY RELOCATION, `.Habilidades` SANDBOX & GIT HYGIENE

### 1.1 Pre-Move & Post-Move Integrity Checklist
1. **Verify Root Path:** Confirm active workspace is strictly `F:\OzretroRom`.
2. **Initialize Local `.Habilidades` Directory:**
   * Create `F:\OzretroRom\.Habilidades\` to store external agent workflows, reference repositories, and local helper scripts without polluting `android/` or `python-hub/`.
3. **Sanitize `.gitignore` (MANDATORY BEFORE ANY COMMIT):** Ensure all local caches, secrets, and external reference clones are excluded from GitHub:
   * `.Habilidades/`
   * `.agents/`
   * `.gradle/`, `**/build/`, `.cxx/`, `.idea/workspace.xml`, `local.properties`, `*.apk`
4. **Update Local Paths:** Check `android/local.properties` (Android SDK path) and any hardcoded `C:\OzretroRom` references in scripts or Gradle configs so they resolve dynamically or point to `F:\OzretroRom`.
5. **Git & GitHub Baseline:**
   * Run `git status` and `git remote -v` to verify intact Git history after moving from `C:` to `F:`.
   * Confirm `.Habilidades/` does NOT appear in `git status` untracked files.
   * Stage and commit the current Phase 1 + Phase 2 + Roborazzi baseline (`git commit -m "chore: migrate workspace to F:, setup .Habilidades sandbox and lock Roborazzi baseline"`).
   * Push to GitHub remote (`origin`) to establish a clean recovery checkpoint before UI rewrites.

---

## 2. REAL MCP SERVER CONFIGURATION (`.agents/mcp_config.json`)
To minimize context-window token consumption, configure standard local MCP servers so the agent interacts directly with the filesystem, `.Habilidades/`, and GitHub without dumping full directory trees into prompts.

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": [
        "-y",
        "@modelcontextprotocol/server-filesystem",
        "F:\\OzretroRom"
      ]
    },
    "github": {
      "command": "npx",
      "args": [
        "-y",
        "@modelcontextprotocol/server-github"
      ],
      "env": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "<YOUR_GITHUB_PAT>"
      }
    }
  }
}
```

### 2.1 Immutable Boundaries (DO NOT TOUCH)
Unless explicitly instructed in a dedicated backend task, the agent **MUST NOT** alter:
* `python-hub/` (PC Hub server, mDNS, catalog sync)
* `android/app/src/main/java/cl/gakuto/kizuna/catalog/` (ROM & metadata parsers)
* `android/app/src/main/java/cl/gakuto/kizuna/launch/` (RetroArch / standalone emulator intent launchers — *read-only inspection allowed to hook overlay triggers*)
* `android/app/src/main/java/cl/gakuto/kizuna/domain/` (Core business logic)

---

## 3. VISUAL DESIGN SYSTEM & `.Habilidades` REPOSITORY STACK

### 3.1 Local `.Habilidades` Installation & Verified GitHub Libraries
During initial bootstrap, the agent is authorized to clone the following repositories directly into `F:\OzretroRom\.Habilidades\` as local reference and orchestration engines (never tracked by Git):

* **Workflow Orchestration (GSD for Antigravity):**
  * `git clone https://github.com/toonight/get-shit-done-for-antigravity.git .Habilidades/gsd-antigravity` — Atomic XML planning, `/plan`, `/execute`, `/verify`, and PowerShell search scripts so the agent inspects only targeted code slices.
  * `git clone https://github.com/KhanXBT/get-shit-done-antigravity.git .Habilidades/gsd-multimodel` — Disk-persisted state and anti-hallucination gates across multi-model handoffs (Claude $\leftrightarrow$ Gemini).
* **Native Compose Motion & Shader Stack (Zero Web/JS Repos):**
  * **Reference Recipe Repo:** `git clone https://github.com/skydoves/compose-animations.git .Habilidades/compose-animations` — Local pattern library for `Spring.DampingRatioNoBouncy` focus scaling (`1.04f`), card morphing, and smooth `animateColorAsState` theme transitions.
  * **Hardware Glassmorphism (`styropyr0/PrismalAGSL`):** Optional JitPack dependency (`com.github.styropyr0:PrismalAGSL:1.0.0`) for AGSL Gaussian blur, specular rim highlights, and spring physics on floating overlays and the landscape rail — always paired with a clean translucent gradient + 1dp hairline fallback for API < 33 and headless Roborazzi tests.
  * **Shared Element Transitions (Native Compose BOM 2026.06.00):** Implement `SharedTransitionLayout` + `AnimatedContent` so game boxart morphs smoothly from `OzHomeScreen` / `OzLibraryScreen` grids into `GameDetailScreen`.

### 3.2 Eradicating the "Wireframe" Look (4 Console-Grade Visual Rules)
The current UI suffers from excessive idle borders (`CutCornerShape` + bright outlines on every card, chip, and button simultaneously), making it look like an amateur wireframe grid. All remaining UI work must enforce these **4 rules**:

1. **Zero Idle Borders (Surface Layering First):**
   * Unselected chips, search fields, and resting game cards **MUST NOT** render glowing or high-contrast borders. Separate them from `OzColors.Bg` purely through subtle tonal elevation (`OzColors.Surface` / `OzColors.SurfaceElevated`).
   * Reserve neon gradient borders and `ozNeonGlow` **strictly** for the currently **focused** (gamepad/D-pad) or **active/selected** element.
2. **Full-Bleed Cover Art:**
   * Game cards (`OzCoverImage.kt`) must use clean, softly rounded corners (`10.dp`, never 45° chamfered corners on artwork), edge-to-edge boxart, and floating vector favorite/status icons without boxed frames around them.
   * For ROMs missing cover art, render a sleek console-tinted gradient fallback (`ConsolePalette`) with subtle typography — remove internal blueprint/grid lines.
3. **Frameless Cinematic Hero:**
   * On `OzHomeScreen`, the Quick Resume Hero must not sit inside a bordered box. Render a full-bleed blurred/dimmed cover backdrop with a smooth vertical gradient scrim melting into `OzColors.Bg`, with sharp cover art, metadata, and the primary `CONTINUAR` CTA floating cleanly over it.
4. **Native 60fps State Transitions:**
   * Replace harsh layout jumps and static `drawBehind` outlines with `animateFloatAsState`, `AnimatedContent`, and `graphicsLayer` scale/alpha transitions.

---

## 4. PENDING UI REWRITE PHASES (CLEAN REWRITE, NO PATCHING)

### Phase 3: Game Detail, Download Bridge & Oz In-Game Overlay (RetroArch Visual Layer)
* **Target Files:**
  * `GameDetailScreen.kt`
  * `SessionActivity.kt` (Download / Launch bridge)
  * **New Module:** `ui/compose/overlay/OzInGameOverlayService.kt` + `OzInGameMenuContent.kt` (Android `TYPE_APPLICATION_OVERLAY` Compose HUD over RetroArch)
* **Directives:**
  * **Oz In-Game Overlay (Signature Console Feature):**
    * Build a native Android floating overlay window (`SYSTEM_ALERT_WINDOW` / `TYPE_APPLICATION_OVERLAY` with `ComposeView` + `LifecycleOwner`) that sits silently on top of RetroArch during gameplay.
    * **Trigger Mechanism:** Intercept physical gamepad hotkey combos (`L3 + R3`, `Select + Start`, or dedicated Guide/Home button via `KeyEvent` / Accessibility/Input hook, plus a subtle draggable translucent Oz emblem edge-pill fallback for touch).
    * **Interactive Console HUD:** When triggered, slide/fade in a dark glassmorphic Oz command deck over the paused/active game without killing the RetroArch process:
      1. **Reanudar Juego** (dismisses overlay and returns focus immediately to RetroArch).
      2. **Save States Visuales** (Quick Save / Quick Load slots with thumbnails & timestamps via RetroArch network/command interface or state directory sync).
      3. **Capturar Pantalla** (instant screenshot saved to Oz vault).
      4. **Guía / RetroAchievements / Controles** (quick reference sheet without leaving the session).
      5. **Cerrar Sesión y Volver a Oz** (cleanly closes RetroArch and brings `OzRetroRom` back to foreground).
  * **Game Detail:** Eliminate system emojis (`✨`, `📁`, etc.) and replace with `Icons.Rounded.*`. In `LANDSCAPE_HANDHELD`, use a 2-column console layout (left: clean boxart + primary `JUGAR`/`DESCARGAR` CTA; right: metadata, synopsis, savestates/achievements).
  * **Download / Bridge Screen:** Replace the legacy rounded modal (`OZ GAME VAULT / RETROARCH BRIDGE`) and truncated labels (`Tamaño pen...`) with a clean, full-screen or sleek bottom-sheet progress view using `OzColors`, `OzTypography`, real progress bar, transfer speed, and clear cancel/retry actions.

### Phase 4: Settings, Sagas & Search Polish
* **Target Files:**
  * `HylianSettingsScreen.kt`
  * `HylianExploreScreen.kt` & `SagaDetailScreen.kt`
  * `OzSearchAddScreen.kt`
* **Directives:**
  * **Settings:** Remove all redundant `"HUD"` badges on cards. Replace all Material 3 oval `Switch(` components with `OzHudToggle`. Group settings cleanly with breathing room. Add permission toggle for **Oz In-Game Overlay (`Mostrar sobre otras apps`)**.
  * **Sagas & Saga Detail:** Fix broken multi-line button wrapping (e.g., `Publ / icar / en / iiSU` must fit cleanly on a single line or icon+short label). Replace oval pills (`DESTACADA`, `2/52`) with subtle flat badges. Ensure section headers never clip at the top edge.
  * **Search & Add (`OzSearchAddScreen`):** In `LANDSCAPE_HANDHELD`, compact the giant `AÑADIR JUEGO / IMPORT ROM` block into a sleek top action bar (`44.dp`) so quick platform filters and search results remain visible without scrolling.
  * **Library & Home Refinement:** Apply Section 3 ("Zero Idle Borders" + frameless Hero) to `OzHomeScreen.kt`, `OzLibraryScreen.kt`, and `OzCoverImage.kt`, and fix the clipped `CONSO` tab label in `OzLibraryScreen`.

---

## 5. HEADLESS VISUAL QA LOOP (ROBORAZZI)
Every UI task is **incomplete** until verified visually via Roborazzi.

1. **Expand `OzUiCaptureTest.kt`:** Include captures for `home`, `library`, `game_detail`, `ingame_overlay`, `saga_detail`, `search`, and `settings` in both `PORTRAIT_PHONE` (`w411dp-h915dp-xxhdpi`) and `LANDSCAPE_HANDHELD` (`w915dp-h411dp-xxhdpi`).
2. **Execution Commands (in `F:\OzretroRom\android`):**
   ```powershell
   .\gradlew.bat testDebugUnitTest --no-daemon
   .\gradlew.bat recordRoborazziDebug --no-daemon
   ```
3. **Mandatory Self-Audit:** Open the generated PNGs in `F:\OzretroRom\docs\ui-captures\` and inspect them for:
   * Zero clipped/wrapped text (`CONSO`, `Publ/icar`, `Tamaño pen...`).
   * Zero idle wireframe boxes around cards and chips.
   * Proper landscape space utilization (>70% content area).
4. **Build Final Deliverable:** Only after visual verification passes, run `.\gradlew.bat assembleDebug --no-daemon` and report the APK path and visual audit results.
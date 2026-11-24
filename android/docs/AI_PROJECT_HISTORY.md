# Oz RetroRom — AI Project History

**STATUS:** HISTORICAL / NON-OPERATIONAL
**CREATED_BY_GOVERNANCE_V4:** 2026-08-29

> This archive preserves pre-V4 project memory and dashboard material so no
> historical evidence is lost during the Governance V4 refactor.
>
> **IMPORTANT:** Any `CURRENT`, `ACTIVE`, `NEXT`, `GATE`, branch, SHA, platform
> state or instruction contained below reflects the historical moment in which it
> was written. It MUST NOT override:
>
> 1. current repository/Git evidence,
> 2. current `AI_PROJECT_MASTER.md`,
> 3. current `PROJECT_DASHBOARD.md`,
> 4. current `AI_WORKSPACE_ROLES.md`.
>
> Historical prompts/instructions are evidence only and are never default commands
> for a new workspace.

---

# ARCHIVED PRE-V4 AI_PROJECT_MASTER

# Oz RetroRom — Prompt Maestro de Continuidad

> **Propósito:** permitir que cualquier chat, sesión o agente de IA continúe el desarrollo de **Oz RetroRom** sin perder contexto, decisiones ni metodología.
>
> **Ubicación recomendada:** `docs/AI_PROJECT_MASTER.md`
>
> **Regla principal:** el repositorio Git actual es la fuente de verdad. Este documento conserva intención, decisiones y estado conocido, pero si contradice al código actual, **manda el código actual**.
>
> **CONDICIÓN OPERATIVA OBLIGATORIA — GITHUB:** `origin` en GitHub es el único puente autorizado para transferir y sincronizar trabajo entre Termux y Android Studio. Ningún cambio local se considera disponible en el otro entorno hasta quedar guardado en una rama, un commit y un `push` verificables. Antes de continuar en el entorno receptor se debe ejecutar `fetch`, seleccionar la misma rama, hacer `pull --ff-only` y comprobar que el SHA local coincide con su upstream remoto.

---

> **MASTER_VERSION:** 3.0
> **LAST_CANONICAL_UPDATE:** 2026-08-29
> **CURRENT_MAJOR_GATE:** OZ-GOVERNANCE-UPDATE-01

## Governance operating model

- Forge II is the central Product/Technical Lead; the human Product Owner has final authority.
- Dream Team is an expert council for high-impact decisions; Gates are proportional to risk.
- Domain identity remains `Game ≠ Release ≠ Candidate ≠ Local Copy`.
- Source Index is the control and identity plane; local-first is the product principle.
- A Candidate is provenance, not a permanent dependency on a still-valid local copy.
- `BEST EMULATOR WINS` remains the emulation decision rule.
- Current direction: Android = portable console/library; Windows = Personal Retro Library Home Hub; TV = future console/receiver; Personal Cloud = optional.
- Oz Personal Library / Personal Vault is future vision, not current implementation.
- Zero-admin UX hides IP, ports, SMB, Docker and other technical configuration whenever possible.
- Codex prompts should be compact. The Product Owner must understand and participate in decisions, not merely transport prompts.

## 1. Instrucción obligatoria al iniciar un chat nuevo

Quiero que continúes el desarrollo de **Oz RetroRom** usando este documento como contexto maestro.

Antes de investigar, modificar archivos o escribir código, responde obligatoriamente con:

```text
[INSTRUCCIONES ENTENDIDAS]
- Resume exactamente qué entendiste.
- Indica qué partes del proyecto vas a tocar.
- Indica qué funcionalidades existentes debes conservar.
- Señala qué comprobarás primero en el repositorio real.
- Si hace falta investigación externa, indica qué investigarás y en qué fuentes oficiales.
- No programes hasta haber mostrado este bloque.
```

Después:

1. Revisa el estado real del repositorio.
2. No asumas que una función existe o falta sin mirar el código.
3. No reemplaces trabajo nuevo con ZIP o ramas antiguas.
4. No inventes packages, Activities, intents, endpoints, rutas, cores, APIs o compatibilidad.
5. Si algo no puede verificarse, dilo claramente.

---

## 2. Identidad del proyecto

**Nombre:** Oz RetroRom
**Plataforma:** Android
**Application ID conocido:** `cl.gakuto.kizuna`

Oz RetroRom debe sentirse como una **consola retro moderna**, no como una app Android genérica.

Referencias conceptuales:

- Nintendo 3DS / HOME Menu.
- iiSU y shells orientados a consola.
- interfaces de consola donde el contenido domina sobre el chrome.
- nostalgia noventera.
- identidad sutil chileno-japonesa.
- cyan / azul eléctrico como acento principal.
- mando como método de interacción de primera clase.

Evitar una apariencia de:

- dashboard web;
- app Material estándar;
- colección de `Card` + `Button` genéricos;
- simple `LazyVerticalGrid` con carátulas;
- interfaz saturada de texto y cajas.

---

## 3. Repositorio y jerarquía de fuentes

Repositorio:

```text
https://github.com/os-ugalde/gakuto-kizuna-android
```

Jerarquía de verdad:

```text
1. repositorio Git actual
2. código actual
3. tests/builds actuales
4. este documento
5. historial de conversación
6. ZIP antiguos
7. supuestos
```

Antes de cualquier cambio importante:

```bash
git status
git branch --show-current
git log --oneline -5
git remote -v
```

No modificar nada hasta conocer el estado real.

---

## 4. Puntos Git relevantes conocidos

Historial confirmado previamente:

```text
372c57a  Fix GitHub Release publishing
86240b6  Add GitHub APK pipeline and shared Oz Beta signing
cf28c9d  Merge Oz RetroRom 0.6.0 Beta 4 Fix 12
19745b6  Introduce Oz RetroRom branding and collections
0f7cf51  Checkpoint Surface 02 real device state
a334654  Surface 03B spatial home and contextual actions
```

Ramas conocidas:

```text
main
checkpoint/surface-02-real
feature/oz-next
feature/surface-03a
feature/oz-visual-parity
infra/github-apk-pipeline
release/0.6.0-beta4-fix12
```

La rama de trabajo visual más reciente conocida es:

```text
feature/oz-visual-parity
```

No asumir que está subida o limpia: comprobar Git.

---

## 5. Entorno de desarrollo actual

### Sincronización obligatoria entre entornos

Android Studio y Termux son entornos complementarios del **mismo repositorio GitHub**. No deben avanzar desde copias divergentes ni editar simultáneamente la misma rama.

Reglas obligatorias:

- antes de abandonar un entorno: revisar el árbol, ejecutar los validadores pertinentes, crear un commit explícito y publicarlo en GitHub;
- al entrar al otro entorno: ejecutar `git fetch origin --prune`, seleccionar la misma rama, ejecutar `git pull --ff-only` y verificar el SHA;
- comunicar siempre la rama y el SHA usados en cada traspaso;
- no usar ZIP, copias por USB, archivos sueltos, parches sin registrar o carpetas duplicadas como mecanismo de sincronización;
- no comenzar cambios nuevos si existen cambios locales sin confirmar o commits sin publicar en el otro entorno;
- si un trabajo no puede superar todavía sus validadores, no cambiar de entorno; si el traspaso es inevitable, aislarlo en una rama `wip/*`, publicarla explícitamente y no fusionarla hasta que pase sus validadores;
- nunca incluir secretos, claves, APKs locales ni archivos accidentales en un commit de sincronización.

Un cambio sólo queda traspasado cuando el entorno emisor y `origin/<rama>` apuntan al mismo commit.

### Principal

**Android Studio en Windows**.

Ruta usada recientemente:

```text
C:\Users\Osvaldo\StudioProjects\gakuto-kizuna-android
```

### Secundario

**Termux** en Android se mantiene como respaldo para:

- Git / SSH;
- scripts;
- Gradle;
- APK de emergencia;
- validadores;
- checkpoints.

Termux ya no debe ser el IDE principal para trabajo visual.

### Dispositivo real

Un Xiaomi está conectado a Android Studio y se utiliza como dispositivo físico de pruebas.

Existe un problema externo a Oz al ejecutar Preview en el teléfono:

```text
INSTALL_FAILED_USER_RESTRICTED
Installation via USB is disabled
```

Esto corresponde a permisos/seguridad de Xiaomi/HyperOS, no al código de Oz RetroRom.

---

## 6. Nueva metodología de UI/UX

Ciclo anterior, demasiado lento:

```text
editar → script → Termux → Gradle → APK → instalar → captura → corregir
```

Nuevo ciclo:

```text
mockup aprobado
→ Compose @Preview
→ comparación visual
→ ajuste
→ Preview
→ prueba en Xiaomi
→ integración funcional
→ build
```

Una iteración NO termina sólo con `BUILD SUCCESSFUL`.

Debe superar:

```text
1. BUILD PASS
2. INTERACTION PASS
3. VISUAL PARITY PASS
4. REAL DEVICE PASS (cuando corresponda)
```

---

## 7. Mockups aprobados

Existen mockups aprobados para:

1. Inicio.
2. Catálogo.
3. Biblioteca.
4. Colecciones.
5. Personalización.

Se consideran **especificaciones de producto**, no inspiración vaga.

Objetivo de trabajo:

```text
MOCKUP APROBADO  VS  COMPOSE PREVIEW
```

Reducir deliberadamente la diferencia visual antes de integrar todo a la app real.

---

## 8. Lenguaje visual aprobado

### Navegación

- rail lateral delgado en landscape;
- logo OZ arriba;
- iconos claros y grandes;
- sólo el elemento activo recibe glow/foco;
- texto mínimo;
- en portrait puede transformarse en dock inferior.

### Foco

- borde cyan;
- glow controlado;
- pequeña elevación;
- nada de bordes permanentes en todos los tiles.

### Controller hints

Barra discreta estilo consola:

```text
A      Abrir
B      Volver
Y      Buscar
X      Filtros / tamaño según contexto
START  Acciones
L/R    Página
```

### Espacio negativo

No llenar cada hueco con texto, botones, metadata o separadores.

Prioridad visual:

```text
ARTWORK / HERO
↓
FOCO
↓
JUEGO
↓
CONTEXTO
↓
CHROME
```

---

## 9. Objetivo actual: HOME / INICIO

No rehacer todas las pantallas al mismo tiempo.

**Inicio debe alcanzar un nivel visual alto antes de extender el sistema.**

Objetivo landscape:

- hero editorial superior;
- imagen principal + título + metadata;
- mosaico asimétrico;
- modo organizar flotante;
- page dots;
- controller hints;
- rail lateral fino.

Geometría conceptual útil:

```text
6 columnas × 3 filas

┌──────────────┬──────────────┬───────────────┐
│              │              │     SMALL     │
│    LARGE     │    LARGE     ├───────────────┤
│              │              │     SMALL     │
├──────────────┼──────────────┼───────┬───────┤
│     WIDE     │     WIDE     │ SMALL │ SMALL │
└──────────────┴──────────────┴───────┴───────┘
```

Portrait debe tener composición propia; no ser landscape comprimido.

---

## 10. Surface 02 / 03A / 03B — aprendizaje acumulado

### Surface 02

Introdujo:

- persistencia de posición;
- tamaños de tile;
- páginas;
- modo organizar;
- D-pad para mover;
- X para tamaño;
- A confirmar;
- B cancelar;
- L/R página.

### Surface 03A

Se pasó a un layout 2D real para resolver limitaciones de spans horizontales.

Conceptos:

```text
occupancy map
custom Layout
widthCells
heightCells
reflow
```

### Surface 03B

Añadió:

- `combinedClickable`;
- long press;
- menú contextual;
- selector de tamaños;
- menos bordes permanentes.

Validación real:

```text
long press → menú contextual
```

funcionó en dispositivo.

Problemas detectados:

1. Atrás/B no cerraba correctamente el menú.
2. El menú seguía pareciendo demasiado Material/Android.
3. La composición seguía demasiado llena.
4. Crops panorámicos podían quedar mal.
5. Seguía lejos del mockup.

03B no es diseño final.

---

## 11. Menú contextual objetivo

Abrir mediante:

```text
START
long press
```

Cerrar mediante:

```text
Android Back
B del mando
tocar fuera
```

Si existe submenú:

```text
Back/B → volver al nivel anterior
Back/B otra vez → cerrar
```

Visualmente debe ser un **Console Command Panel**, no un drawer Material.

Ejemplo:

```text
THE LEGEND OF ZELDA
Nintendo 64

▶  JUGAR
   Continuar

── ORGANIZAR ─────────
↔  Mover
▦  Tamaño          2×2

── PERSONALIZAR ──────
◉  Hero Media
▧  Artwork
♡  Colección

── SISTEMA ───────────
⚙  Emulador
ⓘ  Información

B  Cerrar
```

Sólo la opción enfocada necesita tratamiento fuerte.

---

## 12. Tamaños y organización

Tamaños conceptuales actuales:

```text
SMALL = 1×1
WIDE  = 2×1
LARGE = 2×2
```

Selector explícito:

```text
Pequeño
Horizontal
Destacado
```

Además:

```text
X → cambio rápido
```

Al cambiar tamaño:

1. actualizar tile;
2. reflow de vecinos;
3. evitar huérfanos/huecos absurdos;
4. persistir composición.

---

## 13. Artwork y Smart Crop

No confiar siempre en `ContentScale.Crop` centrado.

Problemas observados:

- personajes reducidos a ojos gigantes;
- rostros cortados;
- logos fuera de cuadro;
- contenido importante perdido al cambiar ratio.

Arquitectura futura recomendada:

```text
focalPointX
focalPointY
alignment / crop strategy
```

Un mismo artwork puede necesitar encuadres distintos para:

```text
1×1
2×1
2×2
hero
cover vertical
```

---

## 14. Hero Media / Wallpaper / Audio

### Hero Media por juego

Puede ser:

- imagen;
- WebP/GIF;
- video.

Sólo el juego enfocado debe activar su hero.

### Wallpaper global

Separado del Hero Media.

Puede ser:

- imagen;
- animación;
- video en loop.

Concepto aprobado: gameplay o escenas representativas detrás de la UI.

Ejemplos discutidos:

- Castlevania: Symphony of the Night;
- Mega Man;
- Donkey Kong;
- Kingdom Hearts;
- escenas tipo transformación Super Saiyan.

### Color adaptativo

Objetivo futuro: adaptar accent, focus, contornos y glow a colores del wallpaper/video sin sacrificar contraste.

### Soundtrack global

- loop;
- persistencia;
- si Hero Media usa audio, pausar soundtrack;
- al perder foco/terminar hero, reanudar donde estaba.

---

## 15. Preferencias existentes

Antes de crear nuevas claves revisar `AppPreferences.kt`.

Ya existe o ha existido soporte para:

- tema;
- orientación;
- densidad de grid;
- wallpaper;
- tipo de wallpaper;
- soundtrack;
- Hero Media;
- audio Hero;
- posición/tamaño Surface;
- artwork personalizado.

No duplicar preferencias.

---

## 16. Orientación

Objetivo:

```text
Automática
Vertical
Horizontal
```

Revisar siempre:

```text
AndroidManifest.xml
MainActivity
SessionActivity
requestedOrientation
SCREEN_ORIENTATION_*
```

No reintroducir bloqueo de orientación accidental.

---

## 17. ROM / región / discos

Bug real histórico: selección incorrecta de región/disco, por ejemplo Germany / Disc 2.

Reglas:

- no elegir silenciosamente región arbitraria;
- región preferida sólo ordena;
- si hay varias variantes, mostrar selector;
- distinguir discos;
- no descargar Disc 2 automáticamente.

Objetivo multidisc:

```text
un juego visible
→ varios discos internos
→ opción de descargar todos
→ cambiar disco desde acciones
```

---

## 18. Catálogo / ROMgi

Objetivos:

- búsqueda global;
- superar el antiguo límite ~192/207;
- filtros por plataforma, región, género;
- favoritos;
- recientes;
- catálogo amplio;
- descargas/importación coherentes.

ROMgi:

- no inventar rutas/endpoints;
- preservar identidad concreta del archivo;
- región no destructiva;
- confirmar alternativas ambiguas.

---

## 19. RetroArch / Libretro / emuladores

Conservar:

- integración RetroArch;
- detección de cores;
- emuladores externos cuando corresponda;
- iiSU cuando corresponda.

Sistemas históricos objetivo incluyen:

- SNES;
- Mega Drive;
- Neo Geo;
- CPS1/CPS2;
- Arcade;
- PS1;
- Saturn;
- N64;
- PS2;
- GameCube;
- Xbox cuando la integración real lo permita.

Antes de cambiar integración:

1. usar documentación oficial;
2. verificar package/activity/intent real;
3. no inventar identificadores;
4. comprobar compatibilidad Android actual.

---

## 20. Investigación externa

Prioridad:

1. Android Developers.
2. AndroidX / Jetpack Compose oficial.
3. Media3 oficial.
4. RetroArch oficial.
5. Libretro oficial.
6. repo oficial del core.
7. repo/documentación oficial del emulador.
8. ROMgi oficial.
9. iiSU oficial.

Evitar depender principalmente de blogs SEO, contenido generado o forks no oficiales cuando existe una fuente primaria.

---

## 21. ScreenScraper / SteamGridDB

No confundir funciones.

### SteamGridDB

Principalmente artwork:

- grids;
- heroes;
- logos;
- icons.

### ScreenScraper

Metadata y, según API disponible, gameplay/video.

No inventar credenciales ni endpoints.

Si requiere developer credentials, decirlo claramente.

---

## 22. Design System deseado

Migración gradual hacia componentes propios:

```text
ui/compose/
├── components/
│   ├── OzGameTile.kt
│   ├── OzNavigationDock.kt
│   ├── OzCommandPanel.kt
│   ├── OzControllerHints.kt
│   ├── OzFocusFrame.kt
│   ├── OzPageIndicator.kt
│   └── OzGlassPanel.kt
├── home/
│   ├── OzHomeScreen.kt
│   ├── OzHomeLandscape.kt
│   ├── OzHomePortrait.kt
│   └── OzHomePreviewData.kt
├── surface/
│   └── OzSurface.kt
├── visualparity/
│   └── OzHomeVisualParityLab.kt
└── theme/
    └── ...
```

No reorganizar todo de golpe.

---

## 23. Visual Parity Lab — estado conocido

Se creó localmente:

```text
app/src/main/java/cl/gakuto/kizuna/ui/compose/visualparity/OzHomeVisualParityLab.kt
```

Propósito:

- Preview aislada;
- sin Room;
- sin red;
- sin ArtworkResolver;
- sin emulación;
- evaluar composición pura.

Previews:

```text
Oz Home · Mockup Parity · Landscape
Oz Home · Mockup Parity · Portrait
```

Android Studio compiló y llegó a renderizar la Preview landscape.

No asumir que este archivo ya fue committed/pushed. Verificar Git.

---

## 24. Compose Preview

Las previews deben estar desacopladas de datos reales.

No depender directamente de:

- Room;
- internet;
- almacenamiento;
- emuladores;
- Services.

Después de aprobar visualmente:

```text
Preview model
→ adaptador
→ GameEntity real
→ ArtworkResolver
→ estado real
```

---

## 25. Adaptive UI

Landscape y portrait deben ser composiciones diferentes.

Evitar usar únicamente un breakpoint artesanal como solución total.

La arquitectura final debe considerar el espacio real disponible y APIs adaptativas/window size classes cuando corresponda.

---

## 26. Personalización

Mockup aprobado incluye:

- Tema.
- Fondo.
- Wallpaper animado/video.
- Música.
- Iconos.
- Grid/densidad.
- Mando.
- Preview en vivo.

Tema mínimo:

```text
Claro
Oscuro
```

Estilos de iconos discutidos:

```text
Clásico · Oz
Arcade · neón
```

---

## 27. Biblioteca

Objetivo visual:

- covers verticales;
- filtros arriba;
- panel contextual derecho;
- focus cyan;
- mínima metadata en grid;
- acciones vía START/context menu.

Filtros conceptuales:

```text
Todos
Descargados
Importados
Plataforma
Últimos jugados
```

---

## 28. Catálogo

Objetivo:

- búsqueda superior;
- filtro por sistema;
- mosaico editorial;
- selección destacada;
- ficha contextual integrada;
- page dots;
- controller hints.

Debe diferenciar remoto vs local sin duplicar patrones innecesariamente.

---

## 29. Colecciones

Objetivo:

- bloques editoriales grandes;
- identidad cromática propia por colección;
- previews de varios juegos;
- Favoritos de Oz destacado;
- colecciones temáticas.

Ideas históricas:

- Favoritos de Oz;
- Arcade esencial;
- Peleas noventeras;
- Aventuras 16-bit;
- RPG de bolsillo.

---

## 30. Controles

Asignación conceptual:

```text
A       abrir / confirmar
B       volver / cancelar
Y       buscar
X       filtros / tamaño según contexto
START   acciones del juego
SELECT  opciones
L/R     páginas / colecciones
D-pad   navegar / mover
```

No asumir HOME como botón fiable en Android.

---

## 31. Git — reglas de seguridad

Trabajar en ramas feature, no en `main` para experimentos.

### Gate obligatorio de sincronización GitHub

GitHub es siempre el punto de entrega entre Termux y Android Studio. Está prohibido asumir que una copia local está sincronizada sólo porque usa el mismo nombre de rama.

Antes de empezar a trabajar en cualquiera de los dos entornos:

```bash
git remote -v
git fetch origin --prune
git status --short --branch
git branch --show-current
git pull --ff-only
git rev-parse --short HEAD
git rev-parse --short '@{upstream}'
```

No programar hasta comprobar que:

- el remoto correcto es `git@github.com:os-ugalde/gakuto-kizuna-android.git` o su URL HTTPS equivalente;
- la rama local es la rama de trabajo acordada;
- no hay divergencia ni cambios locales ajenos;
- `HEAD` y `@{upstream}` corresponden al mismo SHA.

Antes de cambiar de Termux a Android Studio, o viceversa:

```bash
git status --short
git diff
git diff --check
# ejecutar aquí los tests/builds pertinentes
git add -- <rutas-exactas>
git commit -m "<tipo>: <descripción concreta>"
git push -u origin HEAD
git status --short --branch
git rev-parse --short HEAD
git rev-parse --short '@{upstream}'
```

El traspaso sólo está autorizado si el árbol queda limpio y ambos SHA coinciden. En el entorno receptor, repetir el bloque de inicio antes de editar.

Si una rama local nueva aún no tiene upstream, debe publicarse con `git push -u origin HEAD` después del primer checkpoint validado. No se permite continuar el mismo trabajo en otro entorno hasta completar ese `push`.

Antes de commit:

```bash
git status
git diff
git diff --check
```

No usar `git add .` a ciegas si pueden existir secretos o archivos locales.

Prohibido salvo instrucción explícita:

```text
git reset --hard
git clean -fd
force push
rebase destructivo
```

No borrar checkpoints.

---

## 32. Seguridad y secretos

Nunca pedir/compartir:

- contraseña GitHub;
- PAT;
- private SSH key;
- claves privadas de firma;
- `.env` con secretos.

La clave pública SSH sí puede compartirse.

Existe historial relacionado con una signing key beta compartida. No asumir que es de producción.

Claves de producción nunca deben estar en el repo.

---

## 33. CI / GitHub Actions

Existe:

```text
.github/workflows/android.yml
```

Históricamente ha manejado:

- validación/build de APK;
- publishing de release;
- signing beta;
- artifact naming;
- correcciones de release publishing.

No romper CI al cambiar UI.

---

## 34. 16 KB page size

Android Studio mostró advertencia de compatibilidad con páginas de 16 KB.

No fue la causa del fallo USB de Preview.

Queda como deuda técnica separada a auditar con herramientas oficiales antes de release moderna.

---

## 35. Validadores y build

Scripts conocidos históricamente:

```text
scripts/test_oz_surface_01.py
scripts/test_oz_surface_02.py
scripts/test_oz_surface_03a.py
scripts/test_oz_surface_03b.py
scripts/test_oz_next_01.py
scripts/test_oz_next_02a.py
scripts/validate_project.py
```

Comprobar que todavía existen antes de usarlos.

Para cambios significativos:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
git diff --check
```

---

## 36. Qué NO hacer

1. Reescribir toda la app porque una pantalla se ve mal.
2. Sustituir arquitectura funcional sin necesidad.
3. Añadir cinco features antes de validar la anterior.
4. Considerar build verde como aprobación visual.
5. Inventar APIs.
6. Volver a ZIP antiguos como fuente principal.
7. Usar componentes Material por comodidad si rompen el lenguaje visual.
8. Llenar la pantalla de texto.
9. Diseñar cada pantalla con un sistema diferente.
10. Hacer push antes de revisar cambios.
11. Afirmar que algo funciona sin evidencia.

---

## 37. Filosofía humano + IA

Este proyecto busca demostrar que humano e IA pueden producir software de alta calidad con:

- liderazgo;
- revisión crítica;
- investigación;
- pruebas reales;
- checkpoints;
- transparencia ante errores;
- rechazo de resultados mediocres.

La IA no debe elogiar automáticamente una implementación si las capturas muestran problemas.

Si algo falla:

```text
reconocer
→ diagnosticar
→ proteger estado
→ corregir
→ validar
```

---

## 38. Estado conocido al crear este archivo

Fecha de contexto:

```text
2026-08-14
```

Estado conocido:

- rama publicada y sincronizada: `fix/catalog-background-preparation`;
- HEAD de referencia al iniciar esta iteración: `25dda74`;
- el commit `02dda59` trasladó la preparación del catálogo desde `viewModelScope` a una tarea
  persistente con `JobService` iniciado por el usuario en Android 14+ y Foreground Service como
  fallback;
- tests unitarios y `assembleDebug` pasaron en Termux para `02dda59`;
- GitHub es el puente obligatorio entre Termux y Android Studio;
- Gate activo: validar en dispositivo que el catálogo alcance `44/44` aunque Oz sea minimizado;
- la reindexación segura debe conservar ROMs, favoritos, colecciones, login, sesiones y estado de
  juego; no se permite limpiar toda Room para repetir la prueba;
- Visual Parity Home queda pausado hasta cerrar el Gate A del catálogo.

No declarar cerrado el Gate A sólo por compilar: requiere prueba física, cronómetro, segundo plano
y confirmación final `44/44`.

---

## 39. Checklist para cualquier chat nuevo

### Paso 1

Responder con `[INSTRUCCIONES ENTENDIDAS]`.

### Paso 2

Sincronizar e inspeccionar GitHub antes de programar:

```bash
git remote -v
git fetch origin --prune
git status --short --branch
git branch --show-current
git pull --ff-only
git log --oneline -5
git rev-parse --short HEAD
git rev-parse --short '@{upstream}'
```

No continuar hasta que `HEAD` y `@{upstream}` coincidan o se haya resuelto explícitamente cualquier divergencia. Registrar en el chat la rama y el SHA verificados.

### Paso 3

Para el Gate A activo del catálogo revisar:

```text
catalog/background/*
LibretroCatalogRepository.kt
Daos.kt
OzViewModel.kt
OzApp.kt
CATALOG_COMPLETE_INDEXING_HOTFIX.md
```

### Paso 4

Si el usuario dice sólo:

```text
continuemos
vamos
sigamos
```

Y Git coincide con este documento, asumir:

> continuar el Gate A del catálogo en segundo plano desde el último estado verificable; volver a
> Visual Parity Home sólo después de documentar `44/44` en dispositivo.

---

## 40. Prompt corto para un chat nuevo

```text
Quiero continuar Oz RetroRom.

Lee primero `docs/AI_PROJECT_MASTER.md` y úsalo como contexto maestro.

Después revisa el estado Git real y los archivos implicados antes de asumir nada.

Sigue estrictamente estas reglas:
- `[INSTRUCCIONES ENTENDIDAS]` antes de programar;
- repositorio como fuente de verdad;
- GitHub como puente obligatorio entre Termux y Android Studio;
- `fetch` + misma rama + `pull --ff-only` + SHA verificado antes de editar;
- validación + commit + `push` antes de cambiar de entorno;
- fuentes oficiales al investigar;
- Build + Interaction + Visual Parity;
- no degradar funciones existentes;
- no inventar APIs;
- no trabajar directamente sobre main para experimentos.

Continúa desde el último punto verificable del proyecto.
```

---

## Regla final

Este documento conserva el **contexto**.

El repositorio conserva la **realidad**.

GitHub conserva la **continuidad entre Termux y Android Studio**.

Siempre comprobar el documento y el repositorio antes de programar. Todo traspaso entre entornos debe empezar desde una rama publicada y un SHA remoto verificado, y debe terminar con validación, commit y `push` a GitHub.

---

## 47. Estado de Iteración — 2026-08-16

**Gate trabajado:** Rediseño UI/UX Minimalista y Unificación de Menús.
**Rama:** `feature/visual-identity-and-collections`
**Estado:** **IN PROGRESS**.

### Causa raíz / Motivación
- La interfaz horizontal "aplastaba" el arte, rompiendo la estética.
- Falta de paridad de funciones entre Home y el resto de pantallas (Biblioteca/Colecciones).
- Exceso de texto instructivo ("Volver") que ensucia la visual minimalista.

### Cambios realizados (Pendientes de aplicar)
- **Proporción Real en Horizontal**: Los juegos en landscape ahora mantienen su aspecto (1:1 o vertical) en un carrusel fluido en lugar de un grid deformado.
- **Acciones Universales**: Menú contextual habilitado en Colecciones y Biblioteca.
- **Minimalismo UI**: Eliminación de etiquetas "Volver", nuevos iconos lineales y armonización de colores en Configuración.
- **Gestión de Colecciones**: Habilitada la funcionalidad para añadir juegos a listas desde el Command Panel.

### Validación
- **Static**: PASS
- **Assemble**: **AWAITING BUILD**.
- **Device**: **AWAITING RE-DEPLOYMENT**.

### Sincronización
- **Local SHA**: `910ae3c` (base)
- **GitHub SHA**: `910ae3c`

---

**Oz RetroRom — Human × AI Development Project**

---
<!-- OZ_AI_GOVERNANCE_V1 -->
## Sistema modular de gobernanza IA — 2026-08-17

**Objetivo:** instalar una capa estable de constitución, roles técnicos y Gates para que cualquier
agente continúe Oz RetroRom con el mismo estándar profesional sin duplicar el estado cambiante del
proyecto.

**Instalado:**
- `AGENTS.md`
- `docs/ai/AI_CONSTITUTION.md`
- `docs/ai/ROLE_*.md`
- `docs/ai/WORKFLOW_GATES.md`

**Jerarquía operativa:** repositorio/código → Git real → `AI_PROJECT_MASTER.md` → mockups aprobados
→ gobernanza IA → historial → supuestos.

**Sincronización obligatoria:** se conservan y refuerzan las reglas ya versionadas en este master:
GitHub/origin es el único puente normal entre Windows/Android Studio y Termux. Toda iteración
significativa debe actualizar este master en el mismo checkpoint que el código validado antes de
cambiar de entorno.

**Rama al instalar:** `feature/visual-identity-and-collections`
**Checkpoint previo a gobernanza:** `6131fb8023632218c93252af57533cd67d0a99aa`
**Código funcional modificado por esta iteración:** NO.
**Validación documental (`git diff --check`):** PASS

**Gate siguiente único:** `DOWNLOAD-1B`.

**Objetivo de DOWNLOAD-1B:** source failover, resiliencia de Internet Archive, metadata/resume
durable de MiNERVA/torrent, investigación de throughput foreground/background y handoff consistente
de red.

Nuevas ideas que no bloqueen DOWNLOAD-1B se registran como BACKLOG y no interrumpen el Gate activo.

## CIERRE DEVICE VALIDATION — DOWNLOAD-1A — 2026-08-17

### Resultado en dispositivo real

Dispositivo de validación:
- Xiaomi / Android 15.

Resultados:
- UI moderna conservada: PASS.
- SELECT -> Configuración: PASS.
- START -> acciones del juego: PASS.
- Move landscape: PASS.
- Resize landscape: PASS.
- Move + Resize conserva page/slot: PASS.
- PS2 ISO generado por Oz -> NetherSX2: PASS.
- GameCube artifact nativo generado por Oz: PASS.
- GameCube -> Dolphin externo manual: PASS.
- Oz -> lanzamiento directo GameCube: FAIL.

### Interpretación

El fallo de lanzamiento directo de GameCube no pertenece al pipeline DOWNLOAD-1A.

DOWNLOAD-1A queda validado respecto de:
- resolución ROMgi;
- separación container/payload;
- extracción de imágenes ópticas autocontenidas;
- persistencia;
- HTTP resume/retry;
- conservación de progreso;
- formato reproducible.

### Política de emulación objetivo

EMU-01:
- PS2: NetherSX2 preferido.
- GameCube: Dolphin externo preferido.
- Wii: Dolphin externo preferido.
- RetroArch: alternativa/fallback configurable según sistema.

Los package names, Activities e Intents externos deberán verificarse mediante fuentes oficiales y el
emulador realmente instalado antes de implementar.

### Gates

- Build: PASS.
- Tests: PASS.
- Interaction: PASS.
- Visual Parity / regresión visual: PASS.
- Device Validation DOWNLOAD-1A: PASS.
- Oz -> Dolphin direct launch: FAIL, trasladado a EMU-01.

### Estado

DOWNLOAD-1A: PASS / CLOSED.

### Siguiente Gate único

DOWNLOAD-1B

Objetivos:
1. múltiples source candidates persistentes;
2. source failover automático;
3. resiliencia Internet Archive;
4. MiNERVA/torrent metadata;
5. torrent resume durable;
6. network handoff;
7. medir y corregir throughput foreground/background.

---
<!-- OZ_AI_GOVERNANCE_V2 -->
## GOVERNANCE-02 · Professional Roles Upgrade — 2026-08-18

**Objective:** upgrade the existing modular AI governance from the initial V1 checkpoint to a professional V2 without modifying functional application code.

### Installed / upgraded

- `AGENTS.md` as concise agent entry point with mandatory fetch/SHA verification before assuming environment freshness.
- `docs/README.md` as documentation index.
- `docs/ai/AI_CONSTITUTION.md` → V2.
- `docs/ai/ROLE_SPEC_V2.md` with the mandatory 20-section professional role contract.
- `docs/ai/roles/` with 13 specialist roles:
  - Product / Technical Lead;
  - Software Architect;
  - Android Engineer;
  - UI/UX Product Designer;
  - Download / Network Engineer;
  - Emulation Integration Engineer;
  - Data / Storage Engineer;
  - QA / Test Engineer;
  - Performance Engineer;
  - Security / Privacy Engineer;
  - Git / Release Engineer;
  - Technical Researcher;
  - Game Knowledge Curator.
- `docs/ai/protocols/` with four cross-cutting protocols:
  - Visual Engineering;
  - Observability & Diagnostics;
  - ROM Identity;
  - Release Provenance.
- `docs/PROJECT_DASHBOARD.md` and `docs/PRODUCT_IDEA_INBOX.md`.
- Existing flat V1 role files migrated to `docs/ai/roles/` to avoid duplicate authorities.

### Governance incident incorporated

During Windows ↔ Termux reconciliation, the local branch appeared current by name while GitHub already contained later commits. Governance V2 therefore makes `git fetch origin --prune` + explicit local/upstream SHA/DAG comparison mandatory **before** creating checkpoints or assuming which environment is newest.

### Functional behavior

- Functional Kotlin/application changes in GOVERNANCE-02: **NONE**.
- Existing validated DOWNLOAD-1A/UI behavior must remain unchanged.

### Validation required for closure

- Documentation structure: PASS after file/link checks.
- `git diff --check`: required.
- Functional Build/Interaction/Visual/Device: `NOT RUN` for this documentation-only Gate unless code unexpectedly changes.
- GitHub synchronization: required before closure.

### Next single Gate after closure

`DOWNLOAD-1B`.

---

<!-- OZ_AI_GOVERNANCE_V2_CLOSED -->
## 50. GOVERNANCE-02 Closure - 2026-08-18

**Gate:** `GOVERNANCE-02 - Professional Roles Upgrade`
**Status:** `CLOSED`
**Governance V2 checkpoint:** `5716ea8`

### Evidence

- Repository integrity: `PASS`
- 13 specialist roles: `PASS`
- ROLE_SPEC_V2 contract: `PASS`
- 4 specialized protocols: `PASS`
- Game Knowledge Curator regression fixtures: `PASS`
- Documentation structure: `PASS`
- git diff --check: `PASS`
- GitHub synchronization: `PASS`
- Functional code changed by GOVERNANCE-02: `NONE`
- Build: `NOT RUN` - documentation-only Gate
- Interaction: `NOT RUN` - documentation-only Gate
- Visual Parity: `NOT RUN` - documentation-only Gate
- Device: `NOT RUN` - documentation-only Gate

### Governance lesson incorporated

A matching branch name is not proof that an environment is current.
Windows/Termux handoff requires fetch plus explicit SHA/DAG comparison before creating or integrating checkpoints.

### Next single Gate

`DOWNLOAD-1B`

---

<!-- OZ_GOVERNANCE_HARDENING_V3 -->
## Governance Hardening V3 - Continuity and Resilience

Status: ACTIVE.

Oz RetroRom must be portable across AI providers, chats, devices and
development environments.

Mandatory principles:

- significant work must reach a remote checkpoint;
- checkpoint commits may preserve coherent WIP without claiming closure;
- environment/provider/device handoff requires push + HEAD == upstream;
- approved visual artifacts belong in docs/design/;
- chat history is not a canonical project store;
- stash is not a remote backup;
- periodic portable Git backups are required;
- governance requires evidence and enforcement, not documentation alone.

Full amendment:

docs/ai/GOVERNANCE_HARDENING_V3.md


<!-- OZ_EXPERIENCE_MODES_DECISION_V1 -->
## OZ EXPERIENCE MODES — PRODUCT DECISION

Status: APPROVED / BACKLOG
Date: 2026-08-19

Oz RetroRom will support two complementary experience shells over the same
application domain, persisted data, download engine, library and game state.

### Portable Mode

Primary orientation: portrait.

Purpose:
- fast library management;
- game search/import;
- downloads and download monitoring;
- collection maintenance;
- artwork management;
- settings and administrative operations;
- quick resume/progress checks.

Portable Mode is optimized primarily for handheld touch interaction and
information-dense workflows.

### Console Mode

Primary orientation: landscape.

Purpose:
- immersive Home experience;
- library exploration;
- collection and saga browsing;
- game detail;
- Continue / Resume;
- controller-first navigation;
- presentation suitable for larger screens and future desktop/TV use.

Console Mode must feel like a dedicated game-console interface rather than a
portrait Android layout stretched horizontally.

### Shared Architecture Rule

Portable Mode and Console Mode MUST NOT implement duplicated domain logic.

Both modes share:
- game/library state;
- Room/database state;
- download tasks and checkpoints;
- collections;
- artwork metadata;
- preferences;
- emulation configuration;
- save/resume metadata.

Only presentation, spatial composition and interaction policy may differ.

### Orientation Activation Policy

When landscape orientation is first detected, Oz may offer Console Mode.

User preference must support:
1. Ask when entering landscape.
2. Automatically enable Console Mode in landscape.
3. Never automatically enable Console Mode.

The preference must be persisted and configurable from Settings.

A manual “Enter Console Mode” action should remain available for users with
system auto-rotation disabled.

### Mode Continuity Gate

Changing between Portable Mode and Console Mode must never reset or duplicate
active application operations.

Required validation includes:
- active downloads retain task ID, bytes and state;
- paused downloads retain their checkpoint;
- selected game remains selected;
- collection/navigation state is preserved where appropriate;
- no duplicate launch/download operation is triggered by configuration change;
- lifecycle behavior is validated on a physical device.

### Console Interaction Requirement

Console Mode must support deliberate focus navigation and be designed for:
- D-pad;
- controller confirm/back;
- visible focus state;
- predictable spatial navigation;
- reduced dependence on touch-only affordances.

### Visual Governance

Portrait and Console Mode mockups are separate approved specifications for
different usage contexts.

One is not considered a stretched version of the other.

Both must share the Oz visual system:
- typography;
- design tokens;
- color themes;
- iconography;
- component identity;
- artwork treatment;
- interaction language.

Implementation requires Build + Interaction + Visual Parity + physical-device
validation before production approval.

### Backlog

`UX-MODE-01 — Portable / Console Experience Shells`

Status: BACKLOG / PRODUCT DECISION APPROVED

Planned slices:
- UX-MODE-01A — mode preference + orientation policy
- UX-MODE-01B — shared navigation/state architecture
- UX-MODE-01C — Portable Shell
- UX-MODE-01D — Console Shell
- UX-MODE-01E — controller focus/navigation
- UX-MODE-01F — mode transition without state loss
- UX-MODE-01G — Console Mode Visual Parity
- UX-MODE-01H — tablet/external-display behavior
- UX-MODE-01I — Windows/TV reuse assessment


<!-- OZ_VISUAL_SPEC_V1_PORTABLE_CONSOLE -->
## OZ VISUAL SPEC V1 — PORTABLE + CONSOLE

Status: APPROVED VISUAL SPEC / DESIGN FREEZE

Canonical assets:
`docs/design/mockups/oz-visual-spec-v1/`

Experience mapping:
- `portable/portrait/` = Portable Mode
- `console/landscape/` = Console Mode

Each experience has:
- Dark theme
- Light theme
- Home
- Library
- Collections
- Saga Detail
- Game Detail
- Search / Add Game
- Disc Transfer / Download
- Settings

Portable and Console are separate presentation shells over the same application
domain and persisted state.

Within one experience mode, Dark and Light preserve structure/component geometry;
theme changes are token-level visual changes.

Portable and Console may use different spatial composition because their usage
contexts differ, but must share the Oz visual language.

Major visual implementation requires:
Specification -> Preview -> Static Visual Parity -> Integration ->
Interaction Parity -> Physical Device -> Production Approval.

A technically functional surface may still be rejected by the Console Interface
Art Director if it materially diverges from the approved visual specification.

Governance roles added:
- Role 14 — Console Interface Art Director
- Role 15 — Console Interaction & Motion Designer

<!-- OZ_PRODUCT_BACKLOG_20260820_ONBOARDING_BRAND_TASTE -->
## 2026-08-20 — Product backlog expansion: First Run, Taste, RetroArch and branding

Status of this section: **BACKLOG / PRODUCT DECISIONS**.
It does not replace the current active Gate. Single Active Path remains mandatory.

### Tooling decision — Gemini

Gemini Agent Mode is **not the primary implementation path for Oz RetroRom at this time**.
Reason: observed latency and insufficient confidence/efficiency for the current development workflow.
The governance work created for agents remains valuable and should be preserved.
Primary collaboration remains human + ChatGPT governance/review with implementation performed only through the active approved workflow.

### Product principle — hide technical choices, ask personal choices

Oz should automatically resolve technical decisions when it can do so reliably.
The user should mainly be asked about personal preferences.

Good questions:
- favorite consoles;
- favorite games;
- preferred play experiences;
- visual/tone preferences.

Avoid exposing unnecessary technical choices such as:
- 32-bit vs 64-bit RetroArch builds;
- ABI names;
- emulator package internals;
- filesystem implementation details.

### FIRST-RUN-01 — Setup & Taste Onboarding

Goal: turn first launch into a guided console setup plus personalized discovery flow, not a generic Android tutorial.

Proposed slices:

- **FIRST-RUN-01A — Onboarding shell/state machine**
  - skippable first-run flow;
  - resumable onboarding state;
  - no impact on existing library/download state.

- **FIRST-RUN-01B — Storage setup**
  - configure library/download locations with Android-supported storage APIs;
  - avoid manual path typing where possible;
  - persist authorized locations safely.

- **FIRST-RUN-01C — RetroArch readiness**
  - detect whether supported RetroArch installation is present;
  - if absent, show one clear primary install action;
  - Oz selects the appropriate supported official build automatically from device capability/ABI;
  - do **not** ask non-technical users to choose 32-bit/64-bit;
  - secondary action should re-check installation / continue after returning;
  - installation source and exact package/build must be verified from current official RetroArch/Libretro sources before implementation;
  - no silent installation and no invented package/intent behavior.

- **FIRST-RUN-01D — Favorite consoles and games**
  - console selection presented visually;
  - favorite-game selection similar to music-app artist onboarding;
  - searchable game picker so the user is not limited to a fixed curated list.

- **FIRST-RUN-01E — Taste Profile V1**
  - capture experiential preferences, not only genres:
    - art direction;
    - fluid character movement;
    - exploration;
    - story;
    - music;
    - puzzles;
    - technical combat;
    - fair challenge;
    - dark/colorful worlds;
    - unusual mechanics;
    - obscure/hidden games.

- **FIRST-RUN-01F — Initial Collection Seed Engine**
  - create useful collections before the first normal Home interaction;
  - use canonical game identity and curated experiential metadata;
  - deterministic/reproducible candidate scoring first;
  - AI may optionally rank/name/explain results;
  - AI must never become the source of canonical game identity.

- **FIRST-RUN-01G — First-run validation**
  - Build + Interaction + onboarding persistence + physical-device validation;
  - validate novice-user friction with at least one non-technical tester.

### RetroArch Quick Resume onboarding

Add an optional guided setup for RetroArch Auto Save State + Auto Load State.
Initial version should be tutorial/guidance unless a supported integration path is verified.
Future Oz Quick Resume should treat RetroArch as owner of binary save states and Oz as owner of metadata/reference state.

### Taste Engine / Game Knowledge Curator

The Game Knowledge Curator is a high-value product capability.
Recommendations should use canonical game identity plus experiential characteristics, not genre/platform alone.
The recommendation system should be able to explain why a game or collection was suggested.

Potential future signals:
- favorite games;
- accepted/rejected recommendations;
- play history;
- play time;
- favorites;
- completion/progress signals when trustworthy;
- selected consoles;
- experiential preference tags.

### Initial personalized collections

Examples of collection concepts generated from onboarding:
- Adventures with exceptional movement;
- Hidden PS1 gems;
- Worlds worth exploring;
- Games where art direction matters;
- Strange, brilliant and hard to classify;
- Console-specific starter collections;
- Saga/franchise collections when canonical identity is verified.

### BRAND-01 — Product naming exploration

Current project name remains **Oz RetroRom** until a formal product decision is approved.

Naming candidates to explore:
- **GameOZ**
- **OZ PLAY**
- **OZ/PLAY**

Current product assessment:
- GameOZ has a strong "Game OS" concept but higher naming/search ambiguity risk.
- OZ PLAY / OZ/PLAY better preserves the OZ identity while allowing the product to grow beyond ROM management.

No package, namespace, repository or application-id rename is authorized by this backlog item.

Required before any rename:
1. naming/brand research;
2. store/web collision review;
3. trademark-risk research appropriate to release scope;
4. logo/icon studies;
5. mockup comparison;
6. explicit final naming decision.

### BRAND-01 visual direction

Next logo exploration should activate:
- UI/UX Product Designer;
- Console Interface Art Director;
- Console Interaction & Motion Designer where motion identity is considered.

Direction:
- less generic "neon gaming" imagery;
- stronger ownable OZ mark;
- test **OZ PLAY** and **OZ/PLAY** separately;
- more vivid four-accent palette inspired by the colorful console-controller era;
- do not copy proprietary controller button symbols or console branding;
- retain modern retro-console identity rather than cyberpunk-template aesthetics.

### Backlog relationship

These items are intentionally **not active** while DOWNLOAD-1B remains the canonical active Gate.
They should be activated only through Single Active Path after Download closure or explicit reprioritization.

<!-- PERF_CATALOG_COLLECTIONS_01_20260820 -->
## PERF-CATALOG-01 — Catalog / Collections startup performance

Status: **BACKLOG — HIGH PRIORITY**.
This item does not replace the current active Gate unless explicitly reprioritized.

### User-observed problem

On routine application launch, catalog and collections appear to synchronize again and the application can remain effectively blocked/stalled for a couple of minutes.

This is not acceptable product behavior once catalog and collection state have already been loaded and persisted.

### Product requirement

A normal launch must prioritize immediate access to the last known valid library/catalog/collection state.

Routine startup must not require a full blocking catalog/collection synchronization before the application becomes usable.

Preferred behavior:

1. Render persisted valid state immediately.
2. Determine whether synchronization is actually required.
3. Perform refresh incrementally/background when possible.
4. Update only changed/invalidated catalog or collection data.
5. Never recalculate/reindex everything on every launch without evidence that it is necessary.
6. Preserve correctness: performance work must not trade away catalog identity, collection integrity or download/emulation behavior.

### Investigation scope before implementation

Do not assume the root cause.

Measure and inspect:
- application startup path;
- what triggers catalog synchronization/indexing;
- what triggers collection generation/recalculation;
- persistence and cache validity;
- invalidation/version checks;
- repeated network/disk/database work;
- main-thread/UI blocking;
- duplicated work between catalog and collections;
- whether already-synchronized state is being discarded or ignored;
- cold start vs warm start behavior;
- first launch vs routine launch behavior.

### Required governance

Activate:
- Product / Technical Lead;
- Performance Engineer;
- Software Architect;
- Android Engineer;
- Data / Storage Engineer;
- QA / Test Engineer.

Apply:
- OBSERVABILITY_DIAGNOSTICS_PROTOCOL;
- relevant data/persistence and release provenance checks.

Performance rule:

```text
MEASURE BASELINE
→ IDENTIFY BOTTLENECK
→ PROPOSE NARROW CHANGE
→ IMPLEMENT
→ MEASURE AGAIN
→ INTERACTION VALIDATION
→ PHYSICAL DEVICE VALIDATION
```

No speculative broad refactor is authorized.

### Gate proposal

`PERF-CATALOG-01 — Instant Library / Incremental Sync`

Potential slices:
- 01A — startup/sync instrumentation and baseline;
- 01B — persisted-state fast path;
- 01C — sync invalidation/version policy;
- 01D — incremental/background catalog refresh;
- 01E — incremental collection refresh;
- 01F — physical-device startup and interaction validation.

### Acceptance direction

Do not hard-code a performance target before baseline measurement.

Minimum product acceptance:
- routine launch is usable without waiting minutes for synchronization;
- previously valid catalog/collections are visible immediately or near-immediately;
- synchronization progress never blocks unrelated navigation unnecessarily;
- no duplicate full sync on unchanged state;
- refresh result remains correct after app restart;
- device evidence is required before PASS.

<!-- EMU_PROVISION_01_20260820 -->
## EMU-PROVISION-01 — Zero-Touch Emulation Provisioning

Status: **BACKLOG — P1**.
This item does not replace the current active Gate unless explicitly reprioritized.

### Tester evidence to preserve

A non-technical tester reported that the current Oz build already offered a way to download/install RetroArch.

The exact implementation path is currently **UNKNOWN** and must be audited in the current repository before any new RetroArch-install flow is added.

Mandatory rule:

> Do not duplicate or replace an existing working RetroArch installation handoff until its current implementation, package detection, source URL/intent behavior and user flow have been verified in code.

### Product problem

A fresh RetroArch installation normally has no useful cores provisioned for the user's selected systems.
A tester had to manually enter RetroArch and download a core before playing.

That breaks the plug-and-play goal of Oz.

### Product goal

A user should choose platforms, not emulator internals.

Oz should transform selected platforms into an **Emulation Provisioning Plan** and prepare the required engines/cores with the least possible user intervention.

Target experience:

```text
Choose favorite platforms
→ Oz resolves preferred engine
→ verify RetroArch / external emulator readiness
→ provision required approved cores when applicable
→ verify required firmware separately
→ first game launches without asking the user to understand "cores"
```

### FIRST-RUN integration

`EMU-PROVISION-01` integrates with `FIRST-RUN-01` but remains a reusable subsystem.

Suggested relationship:

- FIRST-RUN selects favorite systems and gathers taste.
- EMU-PROVISION resolves the engine/core requirements.
- Later, adding a new system to the library can trigger the same provisioning flow outside onboarding.

### Core policy

Create a versioned **OZ CORE POLICY**.

For every supported system define only after official/current verification:

- preferred engine;
- preferred RetroArch core when RetroArch is the approved engine;
- fallback core/engine where justified;
- architecture/ABI compatibility;
- firmware/BIOS requirement;
- known compatibility caveats;
- device-performance profile only when evidence supports it.

Do not expose core names to novice users unless advanced settings are opened.

### External emulator preservation

Do not download RetroArch cores for systems whose approved default route is an external emulator unless a real fallback requirement exists.

Existing external-emulator routing must be inspected and preserved.

Examples such as PS2/GameCube/Wii routing must be verified against current code before any policy is frozen.

### Core acquisition / installation boundary

Before implementation, verify the current official RetroArch/Libretro mechanism for:

- supported core distribution;
- Android ABI selection;
- core sideload/import;
- package/activity/intent contracts;
- whether final core installation requires RetroArch foreground participation.

Oz may own:
- platform decision;
- provisioning plan;
- approved-source download;
- progress/state;
- retry/error UX.

RetroArch should remain owner of its private core installation/runtime state unless an official supported integration says otherwise.

Never write directly into another app's private storage.

### Firmware / BIOS boundary

Core provisioning is not firmware piracy.

Oz may:
- detect whether required firmware is present;
- explain what is missing;
- guide the user to provide legally obtained firmware;
- validate known hashes when legally and technically appropriate.

Oz must not silently download proprietary BIOS/firmware from unverified sources.

### Persistence

Provisioning must be stateful.

Do not redownload or reprovision on every app launch.

Persist enough state to know:
- system;
- selected engine;
- core identity;
- architecture;
- provision status;
- last verification;
- source/build provenance;
- failure/retry state.

Re-run provisioning only when:
- selected systems change;
- core/engine is missing;
- compatibility is invalidated;
- explicit update policy requires it.

### Proposed slices

- **EMU-PROVISION-01A — Audit current RetroArch install/download flow**
- **EMU-PROVISION-01B — Engine/Core Policy V1**
- **EMU-PROVISION-01C — Provisioning state model**
- **EMU-PROVISION-01D — Core acquisition and verified handoff**
- **EMU-PROVISION-01E — First-run integration**
- **EMU-PROVISION-01F — Missing firmware guidance**
- **EMU-PROVISION-01G — Device validation with novice-user scenario**

### Acceptance direction

A novice tester selecting common supported platforms should be able to reach first successful launch without manually navigating RetroArch's Core Downloader.

PASS requires:
- Build;
- Interaction;
- no duplicate provisioning on restart;
- correct engine/core selection;
- failure recovery;
- physical-device validation;
- at least one novice-user validation scenario.

<!-- DOWNLOAD_1B_1C_SCOPE_SPLIT_20260820 -->
## DOWNLOAD-1B / DOWNLOAD-1C — Approved torrent durability boundary

Date: 2026-08-20
Decision authority: Product / Technical Lead
Status: APPROVED GOVERNANCE DECISION / DOCUMENTATION ONLY

`DOWNLOAD-1B` remains the single active Gate. This decision does not declare
implementation complete and does not change any existing validation status.

### DOWNLOAD-1B — Resilient Download Core

DOWNLOAD-1B retains:

- safe HTTP resume;
- persisted HTTP validators and If-Range safety;
- bounded retry semantics, including Retry-After handling;
- Wi-Fi ↔ cellular recovery;
- partial preservation;
- persistent ordered source candidates;
- deterministic automatic source failover;
- source-isolated partials;
- Internet Archive resilience;
- multidisc checkpoint/resume;
- aggregate progress truth;
- foreground/background survival;
- essential telemetry/diagnostics;
- Build, Test, Interaction and Device evidence required for closure.

DOWNLOAD-1B may persist torrent candidate identity and selected torrent-file
metadata. It may classify an interrupted or exhausted torrent candidate and fail
over to the next eligible candidate. DOWNLOAD-1B must not claim durable torrent
partial recovery or libtorrent fast-resume support.

### Approved remaining implementation sequence

#### 1B.3a — Persistent source-plan foundation

- transport-neutral logical-object/source-plan model;
- persistent ordered candidates;
- candidate-owned partial identity;
- schema-4 backward compatibility;
- no failover behavior yet.

Implementation checkpoint (2026-08-20, unstaged human-review state):

- added a transport-neutral logical payload identity plus deterministic candidate identity;
- persisted ordered source plans as task schema 5 while retaining the legacy selected `AuthorizedSource` execution adapter;
- schema-4 decoding wraps existing task and per-disc sources into safe one-candidate plans without replacing task, staging, progress or completed-disc state;
- ROMgi runtime resolution remains deliberately one-candidate-only; fuzzy alternatives were not promoted;
- multidisc ordering, set signatures and completed-disc source signatures retain their historical behavior;
- JVM source-plan/checkpoint tests pass, full debug unit tests pass, instrumented schema-4/schema-5 persistence tests compile, and `assembleDebug` passes;
- at this checkpoint instrumented persistence tests and device interaction remained NOT RUN; the final 2026-08-21 validation below supersedes that interim state.

Correction/validation checkpoint (2026-08-21, unstaged human-review state):

- added the official stable `androidx.test:runner:1.7.0` artifact required by the configured `AndroidJUnitRunner`;
- removed torrent file index/path from every production logical-object identity builder while retaining URL/magnet and torrent coordinates in candidate identity;
- added regression coverage proving HTTP and torrent candidates for the same represented payload can share one logical identity and that legacy `source` cannot diverge from `sourcePlan.selectedSource`;
- focused JVM tests, full debug unit tests, AndroidTest Kotlin compilation and `assembleDebug` pass;
- the first connected persistence attempt was blocked by Xiaomi `INSTALL_FAILED_USER_RESTRICTED`; that device restriction was subsequently corrected and the final validation below supersedes this interim result.

Final 1B.3a validation (2026-08-21): **PASS**

- physical target: Xiaomi 14T / Android 15 / API 35;
- filtered `RomDownloadTaskPersistenceTest`: 2 tests started, 2 passed, 0 failed; `connectedDebugAndroidTest` finished with `BUILD SUCCESSFUL`;
- D1 startup: PASS;
- D2 library: PASS; known slow startup/catalog behavior remains tracked as `PERF-CATALOG-01`;
- D3 existing local-game launch: PASS;
- D4 new download: PASS;
- D5 pause/process persistence: PASS;
- D6 resume from preserved bytes: PASS;
- D7 Internet Archive HTTP 500 exhausted the existing bounded same-source retries and then required manual Resume; the preserved 78.6 MB partial was reused and Silent Hill completed at 292.4 MB / 292.4 MB. This is not a 1B.3a regression and remains future 1B.3c retry/failover work;
- D8 existing multidisc entries were absent after reinstall, while ADB confirmed their M3U and CHD files remained in the external ROM directory. Read-only architecture triage established that visibility depends on app-private Room/PersistentRomLibrary metadata, reinstall removes that state, selecting the existing SAF tree does not rebuild it, no library-wide M3U reconstruction/rescan exists, and the 1B.3a diff does not modify discovery. D8 is not a 1B.3a regression;
- physically confirmed examples include `Final Fantasy IX.m3u` plus Disc 1–4 CHD, `Chrono Cross.m3u` plus Disc 1–2, `Resident Evil 2.m3u` plus Disc 1–2, `Final Fantasy VII International.m3u`, `Metal Gear Solid.m3u`, `Alone in the Dark - The New Nightmare.m3u`, and `D2.m3u`;
- 1B.3a implementation/validation status is PASS. DOWNLOAD-1B remains active and is not PASS; 1B.3b is the next implementation slice.

Separate future defects discovered during physical validation:

- `LIBRARY-RECOVERY-01` — reconstruct Room/PersistentRomLibrary state from an existing configured SAF ROM directory after clean install/reconfiguration, including parsing relative M3U manifests and validating/relinking their disc members;
- `ARTWORK-SETTINGS-01` — SteamGridDB API key persists successfully, but consumers do not observe the new value until settings is left and re-entered;
- `SETTINGS-UX-01` — artwork-mode selection can change or appear to change after `LazyColumn` scroll/disposal/recomposition because preference state and Compose selection state are not reliably synchronized.

#### 1B.3b — Safe candidate-specific HTTP resume

- HTTP validator persistence;
- If-Range safety;
- candidate-specific partial storage;
- typed transfer results/failures;
- preserve existing same-source retry behavior.

Implementation checkpoint (2026-08-21; superseded by the approved 2026-08-22 checkpoint below):

- task persistence advances to schema 6 with candidate-owned HTTP recovery records; schema 4 and schema 5 decode without invented validator evidence and retain legacy staging as unsafe/quarantined recovery data;
- strong ETag and RFC-qualified Last-Modified evidence can drive `Range` + `If-Range`; weak ETags are persisted for diagnostics but never used in `If-Range`;
- HTTP 206 requires an exact, internally consistent `Content-Range`; ranged HTTP 200 replaces only the active candidate representation; HTTP 416 is accepted as complete only with an unsatisfied byte range, exact local/remote length agreement and non-contradicted persisted evidence;
- HTTP partial paths are isolated by logical-object and selected-candidate identities, redirect destinations do not change candidate identity, and persisted effective URLs exclude query/fragment credentials or tokens;
- unsafe validator-less legacy/process-surviving partials are preserved but are not appended; replacement starts safely and old candidate bytes are quarantined until a verified replacement supersedes them;
- multidisc completed-disc checkpoint names/signatures remain unchanged; prepared discs are promoted to the historical checkpoint location and aggregate bytes exclude inactive/quarantined candidate partials;
- typed HTTP recovery failures are additive and retain the existing same-source retry loop; SourcePlan selection never advances in this slice;
- focused source/resume policy tests passed (18 tests), complete `testDebugUnitTest`, `compileDebugAndroidTestKotlin`, and `assembleDebug` passed at that implementation checkpoint;
- connected/device instrumentation and interaction validation were pending at that implementation checkpoint and are superseded by the final validation below;
- DOWNLOAD-1B remains active and is not PASS. Automatic failover, candidate advancement, Retry-After work and complete validation remain in 1B.3c.

Human-review correction and final validation checkpoint (2026-08-22): **PASS — APPROVED AND FROZEN**.

- corrected the HTTP 200 replacement ordering so old candidate bytes are first preserved under a separate same-directory generation, the active path is created and fsynced at zero length, and only then is new representation metadata durably committed before response-body writing;
- durable recovery commit failure now stops before new body bytes can be written, preventing new-validator metadata from ever identifying an old representation prefix;
- added a filesystem-backed crash-boundary regression covering process death after target reset/before metadata switch and after metadata switch/before body write;
- proved no standalone `RomDownloadTaskSnapshot` exists for synthetic `romgi-bios-*` transfer identities. BIOS SourcePlans remain BIOS-specific, but their HTTP recovery records and progress accounting now use the parent download task owner key;
- `unsafeLegacyPartial` is now conservative/sticky: persisting one safe candidate recovery cannot clear evidence while unrelated root, multidisc or BIOS legacy artifacts may remain. New quarantined generations mark the task unsafe before the filesystem/metadata transition; explicit successful staging deletion can clear the state;
- no candidate selection, failover, Retry-After, IA authentication, multidisc checkpoint or HTTP validator policy was expanded by this correction;
- Human Review Correction: PASS. Physical Device Validation: PASS on Xiaomi 14T / Android 15 / API 35: 3 tests executed, 3 passed, 0 failed and 0 skipped;
- final validation evidence: focused JVM 21/21, full JVM 100/100, AndroidTest compilation PASS, `assembleDebug` PASS and `git diff --check` PASS;
- DOWNLOAD-1B.3b is approved and frozen as a checkpoint. DOWNLOAD-1B remains active and is not PASS;
- DOWNLOAD-1B.3c is PAUSED. `SOURCE-MINERVA-01A` is the next planned engineering slice;
- `.unsafe-*` cleanup remains deferred technical debt and is not part of this closure.

#### 1B.3c — Deterministic failover and validation

- deterministic candidate failover;
- retry/failover classification;
- Retry-After handling;
- multidisc failover integration;
- diagnostics;
- complete applicable Build/Test/Interaction/Device validation.

### DOWNLOAD-1C — Advanced Torrent Durability

Status: BACKLOG / NOT ACTIVE

Scope moved from DOWNLOAD-1B:

- persistent torrent session storage;
- libtorrent fast-resume data;
- torrent process-death byte recovery;
- torrent-specific lifecycle hardening;
- torrent-specific advanced telemetry.

DOWNLOAD-1C must be activated separately through Single Active Path. Historical
DOWNLOAD-1B scope and evidence above remain preserved as history; this approved
decision governs the active scope from this point forward.

<!-- GAME_DETAIL_SHORTLIST_20260820 -->
## GAME DETAIL — Visual Concept Shortlist (2026-08-20)

Status: **DESIGN CHECKPOINT — NOT IMPLEMENTED**

Three Game Detail directions were explored:
- A — Hero Shelf
- B — Command Deck
- C — Saga Navigator

Current shortlist:
- **A — Hero Shelf**
- **C — Saga Navigator**

Command Deck is preserved as a reference, not a finalist.

No Android/Compose implementation is approved yet.

Preserved design evidence:
`docs/design/game-detail/2026-08-20-concept-review/`

Mandatory behavior preserved from the prototype:
- not downloaded → Download;
- downloaded → Play;
- Quick Resume → Continue;
- multidisc → Play Disc Set + Disc Manager;
- Favorite and Add to Collection remain user actions;
- saga information is canonical metadata, not an editable collection;
- Delete Local Copy only when local content exists;
- advanced emulation actions appear only when contextually valid;
- Portable and Console modes remain distinct;
- Console Mode is controller-first.

Next design step:
compare Hero Shelf vs Saga Navigator only and explicitly select/fuse the final Game Detail direction before Compose implementation.

## SOURCE-MINERVA-01A — Direct Nintendo Provider Feasibility Spike

Status (2026-08-22): **PASS / FROZEN — FEASIBILITY PASS — MINERVA_DIRECT_RECOMMENDED_WITH_CONDITIONS**. Human review approved this checkpoint. DOWNLOAD-1B.3b remains PASS/frozen and DOWNLOAD-1B.3c remains PAUSED.

Confirmed evidence:

- MiNERVA basic browse, search, ROM detail, torrent asset and dashboard GET surfaces required no API key or login; its API reference says basic GET requests require no key.
- SNES Aftermarket is `No-Intro/Nintendo - Super Nintendo Entertainment System (Aftermarket)`. The controlled GPL-2.0-or-later object is `240p Test Suite (World) (v1.061) (NTSC) (Program) (Aftermarket) (Unl).zip`, 113,992 bytes, CRC32 `fb442ac9`, MD5 `2c706355fc1d892fb887e45d43f065b1`, SHA-1 `ca2f3eb6b0fc21e7d71528cfaf64eff62d7496c6`, SHA-256 `b91452606f5959b55309343676e60e49ffbd1edc0c9679c1772f274b98460d5a`.
- Torrent artifact: 62,037 bytes / 1.366 s. Independent parse: infohash `811e19de482dc7769e1efdee9f04e3eef5f51ad5`, 232 files, 70,185,128 payload bytes, 51 tracker URLs, target index 6 and exact `Minerva_Myrient/No-Intro/Nintendo - Super Nintendo Entertainment System (Aftermarket)/240p Test Suite (World) (v1.061) (NTSC) (Program) (Aftermarket) (Unl).zip` path.
- Dashboard snapshot reported 44 seeders and 6 leechers at stale timestamp `2026-04-14T00:11:35Z`; these are not connected peers. Successful tracker-response count is UNKNOWN.
- Final Xiaomi 14T / Android 15 / API 35 run: catalog 51,101 ms, metadata 3,361 ms, first peer 2,046 ms, first byte 3,382 ms, maximum 11 connected peers, 113,992 bytes, transfer-path duration 4,049 ms (~28.2 KB/s), SHA-256 PASS.
- Diagnostics proved `target=6 active=1 ignored=231`; one file was retrieved, not the 70 MB set. The isolated session was deleted and nothing was registered in the ROM library.
- Exact API response was 2,147 bytes / 47.688 s; browse HTML 178,834 bytes / 2.073 s; detail HTML 7,034 bytes / 1.718 s. No separate hash database was exposed; Android peak-memory cost remains UNKNOWN.

The SNES-only resolver uses injected HTTP, exact collection/file/region/hash checks, typed failures, stable provider candidate identity independent of tracker churn and exact torrent coordinates. It emits the existing `DownloadSourceCandidate` and reuses libtorrent4j. It is not wired to production UI/default resolution, does not replace ROMgi and cannot advance candidates.

`SOURCE-MINERVA-01B` should build a normalized platform index in CI, sign/version it, retain last-known-good and record freshness. Its health gate should compare total/per-platform/provider counts, generation time, previous delta, critical-platform presence, hash coverage and torrent-coordinate coverage, rejecting suspicious regressions even when newer. Recheck upstream paths, hashes, coordinates, telemetry freshness and distribution evidence every generation.

Remaining risks: slow/variable search, stale telemetry, mutable HTML/API contracts, public-BitTorrent privacy and torrent lifecycle durability owned by DOWNLOAD-1C. The MiNERVA data plane is viable, while live MiNERVA lookup is unsuitable as the production Android control plane. Next approved slice is `SOURCE-MINERVA-01B.1` on a dedicated branch.

## SOURCE-MINERVA-01B.1 — Oz Source Index v1 Schema + CI Builder Architecture

Status (2026-08-23): **PASS / FROZEN** on `feature/oz-source-index-v1`. The reviewed Source Index v1 schema/builder foundation and stable ReleaseId correction are checkpoint-approved; SOURCE-MINERVA-01A remains PASS/frozen and DOWNLOAD-1B.3c remains PAUSED.

Implemented:

- A separate Source Index domain; Room `oz-retrorom.db`, PersistentRomLibrary, preferences and download task state are unchanged. GameId v1 is explicitly a `PROVISIONAL_TITLE_KEY`. Stable ReleaseId always derives from `GameId + canonicalReleaseKey`; provider filename/size/item ID, artifact hashes and the evolving set of ROM hashes never participate. Release status begins `PROVISIONAL_METADATA_KEY` and upgrades to `VERIFIED_CONTENT` when trusted `ROM_CONTENT` evidence arrives without changing ReleaseId. Different curated release keys cannot collapse; cryptographic fingerprints remain independent reconciliation/deduplication evidence.
- Hash scope and byte relation are independent. Current MiNERVA ZIP is `DISTRIBUTED_ARTIFACT` + `WRAPPED`, while canonical ROM content remains UNKNOWN. A validated raw-ROM artifact may be `ROM_CONTENT` + `CONTENT_IDENTICAL`, allowing the same verified bytes to establish canonical content. `UNKNOWN` never exact-matches.
- Python 3.12 CI builder under `tools/source_index`, with a provider-neutral adapter protocol and SNES-only `MinervaAdapter`. Identical inputs and builder version emit canonical sorted JSON and deterministic gzip. A GitHub workflow runs builder tests, generates the prototype and publishes it only as an unsigned review artifact.
- Format decision: canonical JSON per platform shard plus deterministic gzip, **PROVISIONAL FORMAT PENDING PRODUCTION-SCALE SNES BENCHMARK**. It is inspectable, additive and avoids main-Room migration; SQLite remains an evaluated future option if production-scale parsing proves too expensive. CBOR/protobuf are deferred to avoid new runtime/tooling dependencies.
- CandidateId uses provider + transport + immutable torrent infohash/index/exact path. Provider item ID, collection label, tracker URLs and torrent artifact strategy/path remain provenance or mutable transport hints and cannot churn identity. The reader preserves those hints and adds available trackers to the magnet used by the existing transport.
- Manifest v1 records generation/builder/provider freshness, per-platform paths, counts, compression, compressed/uncompressed hashes and starts truthfully at health `NOT_EVALUATED`; only an executed comparison with Last Known Good can return ACCEPT/QUARANTINE/REJECT. Ed25519 signature/key fields are scaffolded as `NOT_CONFIGURED`; private keys must live only in CI secrets and Android will later embed trusted public keys.
- Health gate foundation: missing or zero critical platform is REJECT; >20% release/global game/release/candidate drop or >15 percentage-point hash/coordinate coverage collapse is QUARANTINE. Healthy unchanged/growth is ACCEPT. Thresholds are configurable and must be calibrated against Last Known Good rather than eternal hardcoded catalog counts.
- Read-only Android gzip reader validates compressed SHA-256, schema, required fields, platform consistency and release references; exact SHA-256 + region lookup maps a stored candidate to the existing `DownloadSourceCandidate`. It is not wired into UI, ROMgi, failover or automatic resolution.

Release-ID-stable SNES prototype: one 240p Test Suite game/provisional release/MiNERVA torrent candidate, 2,023 uncompressed bytes, 977 compressed bytes, generation ID `6e42fbbf064f7a4ddd0b81cf2ad9b2c39f0fa02bbb30e7af9d5078df68917982`, shard SHA-256 `3f723ba7152f2b49ec03edcbb46df52886ab070b866f89dac4dc77102efbb142`, build 3.884 ms. MiNERVA hashes remain distributed-artifact evidence; canonical ROM-content hashes remain UNKNOWN. Validation: builder 16/16, reader 4/4, full JVM 112/112 with 0 failed/skipped, `compileDebugKotlin`, `compileDebugAndroidTestKotlin`, `assembleDebug` and `git diff --check` PASS. Peak memory remains UNKNOWN.

Proposed/deferred: CI promotion uses `staging/ → current/` only after hash/signature/schema/health PASS, retaining old `current/` as `previous/`; rejection leaves current untouched. Atomic publication and production Ed25519 signing are designed but not activated because CI keys/distribution are outside 01B.1. Public BitTorrent exposes the user's public IP to swarm peers; a disclosure belongs in a later source-enable/download consent surface.

Remaining risks: the one-record fixture does not establish production SNES memory/lookup characteristics; MiNERVA ingestion still needs robust CI extraction/freshness handling; signing and atomic Last Known Good promotion are not yet operational. Do not replace ROMgi, enable automatic MiNERVA resolution/failover, or start 01B.2 without review.

## SOURCE-MINERVA-01B.2A — Nintendo Coverage Census

Status (2026-08-23): **PASS / FROZEN** on `feature/minerva-nintendo-census`. The reviewed metadata-only Nintendo census is checkpoint-approved; SOURCE-MINERVA-01B.1 remains PASS/frozen and DOWNLOAD-1B.3c remains PAUSED. No ROM payload, torrent payload, device, Room, download-resolution or UI operation occurred.

Confirmed primary evidence: MiNERVA basic GET metadata requires no API key. The live `/v1/api/dashboard/latest` response exposed 1,050 torrent records and a stale dashboard timestamp of `2026-04-14T00:11:35Z`; `/browse/<category>/<collection>/` exposed filenames, provider item links, exact torrent file indexes and collection magnets. The reproducible census joined these surfaces dynamically, discovering 147 Nintendo-related collections across No-Intro, T-En Collection, RetroAchievements, Redump, TOSEC/TOSEC-PIX and other current groups. It made 148 metadata requests, transferred 105,981,268 metadata bytes and completed in 54,147 ms. It normalized 143,617 listed files and found 40,002 case-insensitive filenames present in more than one collection; filename overlap is evidence only and was not deduplicated.

Core platform totals (all current joined collections, including specialty/translation/RetroAchievements where present): NES 10 collections/23,813 files; FDS 5/833; SNES-family 13/9,111; N64 9/3,472; N64DD 2/47; GB 5/3,486; GBC 7/3,677; GBA 13/8,739; Virtual Boy 5/182; Nintendo DS 18/21,940; Pokémon Mini 4/107. The generated machine-readable artifacts retain the remaining 3DS, DSi/other, GameCube, Wii, Wii U and miscellaneous Nintendo collections, many classified unsupported or identity-research-only for current Oz.

Identity findings: NES headered/headerless and N64 BigEndian/ByteSwapped sets overlap substantially and require future byte-level normalization; they must not be treated blindly as distinct games. Aftermarket, private, translation, prototype/beta/demo tags are provider evidence, not canonical identity. Joined surfaces did not establish CRC32/MD5/SHA1/SHA256 field availability or byte scope, so all hash availability/scope remains `UNKNOWN`; dashboard payload byte counts and timestamps are retained separately from current browse record counts. ROMgi route code covers the principal current Oz Nintendo system IDs, but actual current ROMgi catalog presence is not proven by route mapping; “MiNERVA present / ROMgi missing” therefore remains UNKNOWN except for concepts with no route such as Wii U and separately modeled Satellaview/Sufami families.

Implemented under `tools/source_index/census`: separated metadata fetch, directory parse, normalization, classification, overlap reporting and deterministic IDs; generated outputs live under `.artifacts/minerva-nintendo-census/`. Pure census tests are 8/8 PASS. The next proposed slice, pending review, is `SOURCE-MINERVA-01B.2B — Production-scale SNES ingestion + physical-format benchmark`; do not start it automatically.

## SOURCE-MINERVA-01B.2A-GLOBAL — MiNERVA Global Coverage Census

Status (2026-08-23): **PASS / FROZEN** on `feature/minerva-global-census`. SOURCE-MINERVA-01B.1 and Nintendo Census 01B.2A remain PASS/frozen; DOWNLOAD-1B.3c remains PAUSED. This is a single-snapshot metadata inventory: no browse deep scan, ROM/torrent payload, provider activation, Room/UI/download change or device operation occurred.

Raw provider-discovery evidence: TLS-verified `https://minerva-archive.org/v1/api/dashboard/latest` captured as `.artifacts/minerva-global-census/raw/minerva-dashboard-2026-08-23T152343Z.json`, 260,854 bytes, SHA-256 `5c329ded8eed3c27dc7f99171ca0092b56838bb556c07c67a348f60d914eddc9`, HTTP retrieval 2,294 ms. JSON validation confirmed metadata total 1,050 equals the 1,050-element torrent array. Its internal timestamp remains stale at `2026-04-14T00:11:35Z`; seeders/leechers are retained only as `TELEMETRY_SNAPSHOT`, while current swarm health is `UNKNOWN`.

First pass retained all 1,050 torrent records and represents 416,271,444,782,693 payload bytes. Actual category groups: No-Intro 463, TOSEC 220, Redump 116, T-En Collection 61, TOSEC-PIX 54, RetroAchievements 53, Internet Archive 29, TOSEC-ISO 28, MAME 13 and Uncategorized 13. Snapshot-only normalization took 101 ms and produced 46 known platform-family rows plus one `UNKNOWN`; 311 collections map to known families and 739 broad/ambiguous aggregates remain `UNKNOWN`, not guessed.

Highest-value repository-supported opportunities: PRIORITY_A because Oz has a `libretro_systems.json` system but `GameDescriptor` has no ROMgi route—WonderSwan 3 collections/218,428,865 bytes; WonderSwan Color 3/223,074,057; Commodore 64 5/353,137,641; Amiga 8/400,939,758,383; MSX 7/12,795,968,999; MSX2 3/85,517,117; Neo Geo Pocket 1/33,365,688; Neo Geo Pocket Color 1/7,528,775. PRIORITY_B targeted comparison—FBNeo 1/30,043,296,235; SNES 13/119,662,189,532; Genesis 9/51,299,976,475; Saturn 4/983,708,572,056; PlayStation 18/12,140,551,666,947. ROMgi evidence means route presence only; actual ROMgi catalog coverage remains `UNKNOWN`.

Nintendo regression: the global snapshot filter still selects the exact prior 147 Nintendo-related dashboard records. Of these, 131 map to known Nintendo platform families and the same 16 broad “Nintendo other” records remain `UNKNOWN`. The earlier 143,617 file-record and 40,002-overlap totals came from 147 browse deep scans and are intentionally not recomputed by the one-request global pass.

Implemented: generic `parse_dashboard`/`discover_all_torrents` now precedes Nintendo filtering; `global_census.py` performs snapshot-only platform/support/ROMgi-route/priority/telemetry normalization and emits manifest, groups, platforms, collections, opportunities and a human report. Global + Nintendo tests are 16/16 PASS and Source Index builder regression is 16/16 PASS. The frozen `SUPPORTED_NOW` label records repository route/config awareness only and must not be treated as product certification; the next gate formalizes that distinction before any provider expansion.

## OZ Platform Qualification Gate v1 + SOURCE-MINERVA-01B.2B host benchmark

Status (2026-08-23): **HOST BENCHMARK PASS / FROZEN** on `feature/platform-qualification-v1`. Global Census remains PASS/frozen at `5204819207353d05a98cbf47385c8bba77e2c629`; DOWNLOAD-1B.3c remains PAUSED. No UI, Room, user/library state, production source resolution, download behavior, ROM payload, device command, or device test changed.

Permanent product rule: **BEST EMULATOR WINS**. Catalog presence is independent from emulation capability and product certification. `CatalogPresence` is `PRESENT/ABSENT/UNKNOWN`; `EmulationCapability` is `LIBRETRO_AVAILABLE/EXTERNAL_AVAILABLE/BOTH_AVAILABLE/RESEARCH_REQUIRED/UNAVAILABLE`; `ProductCertification` is `DISCOVERED/EMULATION_RESEARCH/CERTIFICATION_CANDIDATE/OZ_CERTIFIED_LIBRETRO/OZ_CERTIFIED_EXTERNAL/OZ_CERTIFIED_CONDITIONAL/REJECTED_QUALITY/UNSUPPORTED`. Only `OZ_CERTIFIED_*` can make a platform eligible for later public visibility. The global census now emits these independent fields instead of `SUPPORTED_NOW`.

OZ-EQS v1 is an internal Oz standard, not an ISO certification. Hard gates cover maintained Android route, representative compatibility, full-speed/no-mandatory-frameskip performance, frame pacing, audio, crash/game-breaking reliability, save reliability, controllers, required media/peripherals, plug-and-play complexity, maintenance, device-tier thermal sustainability, and verified Oz integration. A critical failure rejects regardless of aggregate score. For deep qualification, the Game Platform Expert owns a representative Golden Game Suite and may block certification if it omits iconic, difficult, stress, regional, peripheral, special-media, multidisc, or known edge cases.

SNES limited qualification: **SNES_QUALIFICATION_PASS for Source Index benchmarking only**, not full OZ-EQS/public certification. Repository evidence configures `snes9x_libretro_android.so` and records `launchValidated=true`. Current official evidence: Libretro describes the core as an up-to-date mainline Snes9x port and publishes its content/database/core behavior at `https://docs.libretro.com/library/snes9x/`; the official `libretro/snes9x` repository is not archived and showed recent 2026-08 activity; the official Android arm64 buildbot exposed `snes9x_libretro_android.so.zip` at verification time. These establish a maintained Android-capable route and no platform-level blocker to an SNES metadata benchmark. External candidates were not certified or ranked, and no device evidence was produced.

SOURCE-MINERVA-01B.2B host scope: include only the primary No-Intro `Nintendo - Super Nintendo Entertainment System` torrent (4,642 files, infohash `93e0d2060d1b05f5b19d0a9e40011b51a76a933c`) and its explicit Aftermarket torrent (232 files, infohash `811e19de482dc7769e1efdee9f04e3eef5f51ad5`). Exclude Private, RetroAchievements, T-En translations/enhancements/MSU1/speed hacks, Satellaview, Sufami Turbo, and source-code collections because their identity/representation semantics need separate normalization. Only the two `.torrent` metadata artifacts were fetched (634,499 + 62,037 bytes); no torrent session or payload transfer occurred.

Final hardened host result: 2 collections / 4,874 file records -> 2,505 provisional-title games, 4,764 provisional releases, 4,874 immutable torrent candidates. All hashes remain `UNKNOWN` and artifacts are `WRAPPED`; no ROM-content hash was fabricated. The same 110 explicit provisional-key overlap warnings remain visible and candidate coordinates remain distinct. First deterministic build took 1,326.26 ms. Canonical JSON is 20,247,331 bytes; deterministic gzip is 1,477,037 bytes; manifest is 988 bytes; repeated build SHA-256 is identical (`320e6f424f2ae5b5f01c58efc495d80f16a25d8106a74bbd3934cbc2935d897e`). Python host open/parse measured 896.454 ms, first exact candidate lookup 0.0038 ms, 10,000 repeated lookups 21.338 ms (0.002134 ms average), and `tracemalloc` peak 69,961,882 bytes. Methodology and generated evidence live under `.artifacts/source-index-snes-benchmark/`.

Format gate: **FORMAT_HOST_FAVORS_JSON_GZ**. The 1.48 MB shard, sub-second Python parse, deterministic output, and millisecond-scale exact lookup do not justify an SQLite prototype on host. JSON.gz remains provisional for production until a separately authorized Android memory/open benchmark; physical benchmark is **NOT AUTHORIZED / NOT RUN** in this slice.

## SOURCE-CLASSIFICATION-FOUNDATION-01

Status (2026-08-23): **PASS / FROZEN**. Provider metadata is evidence; Oz owns normalized classification for every platform. Ingestion preserves region, language, translation, homebrew, aftermarket, prototype, beta, demo, unreleased, modification, and provider provenance even when presentation later filters it.

`ReleaseFacets` keeps regions, languages, publication class, modification class, translation source/targets/status, regional exclusivity, classification evidence, and evidence status independent. Region does not imply exclusivity or language. Publication and modification remain orthogonal, so official regional releases, fan translations, homebrew, aftermarket commercial releases, prototypes, and demos remain representable without schema redesign. MiNERVA/No-Intro/T-En/RetroAchievements/specialty collection labels remain candidate provenance and never enter GameId, ReleaseId, or CandidateId. Facet enrichment cannot churn stable identity.

Hash evidence now fails closed: `UNKNOWN` with no digest is valid; `ROM_CONTENT` or `DISTRIBUTED_ARTIFACT` requires at least one actual digest; CRC32/MD5/SHA1/SHA256 require exact hexadecimal length; unscoped, malformed, missing claimed, cross-candidate contradictory, and contradictory `CONTENT_IDENTICAL` evidence is rejected. Content-hash enrichment remains separate from stable ReleaseId. Host gates: Platform Qualification 3/3, Global+Nintendo 17/17, and Builder+Classification+SNES 31/31 PASS; `git diff --check` PASS. Android/Gradle/device: NOT RUN because no Android/Kotlin source changed and device use was forbidden.

### GBA-CATALOG-01B.COVERAGE-05 — T-En Controlled Expansion Pilot

Status (2026-08-26): **PASS / EXPERIMENTAL**. Base + Aftermarket + T-En produce
2,368 Games / 3,962 Releases / 4,021 Candidates. The 97 translation Candidates
map to 70 Games (48 existing, 22 new) and 97 distinct Translation Releases;
49 Games have one translation and 21 have multiple. Base identities remain
stable, A/B determinism passes, and the shard is non-production/untracked.

### GBA-CATALOG-01B.REPRO-01 — Versioned Production Candidate Contract

Status (2026-08-26): **PASS / VERSIONED**. The portable contract
`tools/source_index/fixtures/gba_coverage05_contract.json` pins the three GBA
input identities, provider roles, infohashes, builder/schema versions and the
expected 2,368 / 3,962 / 4,021 output with SHA-256
`6ff70a3763fbbd6871c7e9a29135161c84deec4144d470ff5bb993d519d2af50`. It does
not track torrents, ROMs, shards or local paths. A host test validates the
contract and, when the authorized local artifact exists, its exact counts,
sizes and digest. Android integration remains pending.

### GBA-CATALOG-01B.IMPLEMENTATION-R1 — GBA Android Source Index Integration

Status (2026-08-26): **PASS / CONTROLLED**. GBA Source Index is integrated
from the versioned Base + Aftermarket + T-En candidate: 2,368 Games, 3,962
Releases and 4,021 Candidates; production asset SHA-256
`6ff70a3763fbbd6871c7e9a29135161c84deec4144d470ff5bb993d519d2af50`.
Routing is Source Index while acquisition and launch validation remain false.
Reader, search, pagination, exact identity, Translation/Prototype facets and
Room overlay-only behavior pass. Physical launch, provisioning, OZ-EQS and
public eligibility remain NOT RUN/NO; the asset is not yet remotely promoted.

## SOURCE-MINERVA-01B.2C — Android Source Index format benchmark

Status (2026-08-23): **METHODOLOGY SUPERSEDED FOR MEMORY DECISION**. The original timing/integrity evidence remains historical, but its memory/PSS/GC result combined a diagnostic ByteArray/JSONObject/HashMap with a second production-reader representation. SOURCE-CLASSIFICATION-FOUNDATION-01 and SOURCE-MINERVA-01B.2B remain PASS/frozen; DOWNLOAD-1B.3c remains PAUSED.

The isolated AndroidTest harness used the production `OzSourceIndexReader` for total cold load and the exact frozen SNES shard: 1,477,037 compressed bytes, SHA-256 `320e6f424f2ae5b5f01c58efc495d80f16a25d8106a74bbd3934cbc2935d897e`, 20,247,331 uncompressed bytes, 2,505 games, 4,764 releases, 4,874 candidates, and 110 preserved provisional overlaps. On Xiaomi 14T (`2406APNFAG`) / Android 15 / API 35, one warm-up plus five measured iterations produced: decompression min/median/max 77.541/79.051/81.062 ms; JSON parse 598.233/602.303/609.175 ms; androidTest-only CandidateId index construction 4.900/6.560/6.986 ms; production-reader total cold load 862.383/878.774/905.601 ms. First exact lookup was 0.004923/0.005847/0.006539 ms and 10,000 lookups 12.413/14.049/14.739 ms. First, middle, and last CandidateIds resolved exactly; record counts and shard digest matched.

Managed heap used `Runtime.totalMemory-freeMemory` sampled at phase boundaries: median start 4,149,840 bytes, observed peak 99,414,608 bytes, delta 95,227,808 bytes (~90.8 MiB, GREEN). `Debug.MemoryInfo.totalPss` median increased from 133,317 KiB to 287,302 KiB, delta 153,848 KiB (~150.2 MiB): below the RED threshold but above the GREEN budget. ART runtime stats reported a median 6 GC and 289 ms GC time per measured iteration; logcat showed bounded concurrent collections, with no OOM, ANR, crash, parse exception, bad count, or failed lookup. Total latency and lookup are GREEN; PSS/GC make the aggregate result YELLOW. The next reader slice should reduce duplicate full-tree/model allocation and GC pressure before format freeze; do not jump to SQLite without comparative evidence.

## SOURCE-MINERVA-01B.2C-R1 — isolated production-reader benchmark

Status (2026-08-23): **PASS / FROZEN — JSON_GZ_ANDROID_VIABLE_NEEDS_READER_OPTIMIZATION**. JSON.gz is **PROVISIONALLY ACCEPTED FOR THE SNES VERTICAL SLICE**, not universally certified or format-frozen. `SOURCE-READER-OPT-01` is BACKLOG before multi-system scale/format finalization. Six independent production invocations and six independent diagnostic invocations were executed; the first of each was excluded as warm-up, leaving five fresh-process samples per path. Production invoked only `AssetManager.open → OzSourceIndexReader.openGzip`, with no harness-owned compressed ByteArray, decompressed bytes, JSONObject tree, diagnostic HashMap, or second representation alive. The deliberate baseline GC preceded the GC baseline. Diagnostic memory is excluded from the decision.

## SNES-END-TO-END-01 — first playable SNES vertical slice

Status (2026-08-23): **FIRST_PLAY_PASS / FROZEN** on `feature/snes-first-play-v1`. Xiaomi 14T / Android 15 / API 35 completed the normal Oz product flow through the exact frozen Source Index candidate to a playable 240p Test Suite screen. DOWNLOAD-1B.3c remains PAUSED, candidate advancement remains disabled, and SNES is not yet OZ-EQS certified or globally production-enabled.

- Product path: the existing catalog/game dialog exposes one stable `240p Test Suite` entry, then `SessionActivity` resolves exact Oz `GameId → ReleaseId → CandidateId` records from the frozen SNES Source Index. Existing/user-owned rows are never replaced by startup registration.
- Exact Oz identities: Game `053ba9c1405a4e66c8fd5378a03b7db3661317ca3879d1803064ae7da1d7588e`; Release `fd32201cc2548a237c757121cf9d103dd4bc3d790b38beb8fb58a0aa037be15c`; Candidate `be16f73ff801e399fc22521ea18f9dca59365febcec0e99e4f48812ce2cd0052`.
- Exact candidate: MiNERVA torrent infohash `811e19de482dc7769e1efdee9f04e3eef5f51ad5`, file index 6, exact Aftermarket v1.061 path, expected artifact size 113,992 bytes. Current Source Index hash evidence remains `UNKNOWN`; the implementation does not fabricate the historical artifact digest.
- The query is a deterministic scan over the retained production-reader model, avoiding a second candidate HashMap. It returns exactly one candidate and threads the resulting SourcePlan through the existing durable task store/scheduler. The existing libtorrent selective-file transport, candidate partial ownership, bounded same-source retry, archive preparation, validation, PersistentRomLibrary registration and launch path remain authoritative.
- Existing preparation selects SNES ZIP payloads by repository native-extension rules. Existing launch configuration selects `snes9x_libretro_android.so`; no package, Activity, Intent, core, or emulator contract was invented.
- Validation: focused Source Index/preparation tests PASS; full JVM **116/116**, 0 failed/skipped; `compileDebugKotlin`, `compileDebugAndroidTestKotlin`, `assembleDebug`, frozen asset size/hash verification and `git diff --check` PASS. Physical evidence: `Fuente: Oz Source Index`, `Descargar y jugar`, exact Game/Release/Candidate IDs, exact infohash/index/path, 113,992-byte ZIP transfer, 524,288-byte `.sfc` preparation, PersistentRomLibrary registration, RetroArch open, `snes9x_libretro_android.so` load and user-confirmed playable screen. Durable schema-6 task status was SUCCESS; no failover, ROMgi substitution, OOM, ANR or crash occurred. Final `assembleDebug`, `adb install -r` and package verification PASS with application data preserved.

## SNES-CATALOG-01 — generic SNES Source Index discovery and play

Status (2026-08-24): **SNES_ROUTING_PHYSICAL_PASS / FROZEN** on `feature/snes-catalog-v1`. The frozen controlled SNES shard exposes exactly **2,505 logical games / 4,764 releases / 4,874 candidates** through the existing Global Catalog and CRT game-detail flow. This is the controlled primary No-Intro + Aftermarket population, not a claim of complete worldwide SNES coverage, universal downloadability, translations, global MiNERVA enablement, or full SNES OZ-EQS certification.

- Discovery Source Index and user library remain separate domains. The process-scoped reader opens the shard once off the main thread and provides deterministic scan-based paging/search over its retained model; it does not mirror the catalog into Room or allocate a second complete object graph. Room remains the durable owner of local/downloaded state, favorites, collections, play history, artwork overrides and launch records. A lightweight exact-ID projection is materialized only when a user opens a Source Index game, and exact existing Room state overlays it without fuzzy-title merging.
- Generic queries expose games, normalized title search, releases for a GameId and candidates for a ReleaseId. Release facets retain region, language evidence, publication/modification class, translation and exclusivity evidence without entering stable identity. Region is not exclusivity and provider labels remain provenance rather than canonical game identity.
- The temporary one-game `SnesFirstPlaySource` bridge and startup seed were retired. The historical 240p projection ID remains an explicit compatibility alias so the validated downloaded row and user state survive unchanged. Its exact Game/Release/Candidate identity remains a regression fixture.
- Multiple releases are shown through the existing Oz/CRT dialog surface with region, revision, evidenced NTSC/PAL and classification labels. One clear release proceeds directly; an explicit user release is carried to `SessionActivity`. Candidate selection is deterministic and produces a one-candidate SourcePlan only. Candidate advancement, ROMgi substitution and DOWNLOAD-1B.3c remain disabled/PAUSED.
- The existing scheduler, task store, candidate-safe recovery, selective libtorrent transport, SNES ZIP preparation, PersistentRomLibrary and `snes9x_libretro_android.so` launch route are unchanged. Artwork remains lazy through the existing adapter/resolver and deliberately falls back where unavailable.
- Host validation covers exact controlled counts, no duplicate GameIds, exact/normalized/empty search, real USA retail/Japan/multi-release records, identity stability under facet/provider churn, exact Room overlay behavior, one-candidate plans and the frozen 240p FIRST_PLAY identity. Physical routing and generic download/play validation passed in 01F. JSON.gz remains provisional for SNES; `SOURCE-READER-OPT-01` remains backlog before multi-system scale/format finalization.
- Pre-device semantic hardening: Source Index publication classes remain exclusively in `ReleaseFacets`; projections use the repository-neutral genre value `Sin clasificar` and never fabricate `Retail`, `Aftermarket` or another publication class as genre. In SNES Source Index mode, the existing filter sheet exposes only truthful region filters and name A–Z/Z–A sorting. Genre, year/history/favorite ordering, ROMgi/local/download/artwork status and redundant platform controls are hidden and removed from the applied filter contract rather than silently ignored. Exact Room overlays remain authoritative without bulk materialization.
- The current multi-release Android `AlertDialog` is **TEMPORARY_FUNCTIONAL_UI** for device validation, not final Oz visual parity. `SNES-RELEASE-SELECTOR-UI-01` is **BACKLOG BEFORE VISUAL FREEZE** for a CRT-native selector. No SNES certification or DOWNLOAD-1B.3c activation is implied.

### SNES-CATALOG-01D / 01E — physical routing finding and reachability correction

Physical validation 01D (2026-08-24): **BLOCKED_AT_PROJECTION**. The frozen Source Index asset/counts were valid, but the active launcher is Compose `MainActivity → OzApp → CatalogScreen`. Its visible “Plataformas” controls represented `PlatformFamily` only; the concrete-system selector lived exclusively in the legacy-entry `GlobalCatalogActivity`. Therefore no physical action could assign `selectedSystem.id == "snes"`; visible Room records retained their ROMgi behavior, `com.caprado.romgi/.MainActivity` appeared, and no `SOURCE_INDEX_CANDIDATE_SELECTED` event occurred. This was not a MiNERVA/download/preparation/emulation failure.

01E correction: a testable `CatalogSystemRouting` now separates qualified `CatalogSystem` selection from family filtering and maps SNES to `SNES_SOURCE_INDEX`, with other systems retaining `ROOM_CATALOG`. The active Compose catalog renders reusable family → eligible-system navigation; this slice exposes only qualified `Nintendo → Super Nintendo`. Selecting it opens the existing Global Catalog with the exact `snes` system preselected and its selector restricted to eligible Source Index systems. Consequently paging/search originate from `SnesSourceIndexCatalog`, Room supplies exact-ID overlays only, and the approved SNES filter mode becomes reachable. Session source routing is explicit and preserves local source first, then SNES Source Index, then ROMgi/source-required; non-SNES ROMgi behavior is unchanged. FIRST_PLAY continues to use `LEGACY_FIRST_PLAY_PROJECTION_ID`, with no reseed or state mutation.

01F physical revalidation (2026-08-24): **SNES_ROUTING_PHYSICAL_PASS** on Xiaomi 14T / Android 15 / API 35. The user reached the reusable `Nintendo → Super Nintendo` route and successfully downloaded and played generic catalog entries Donkey Kong and Yoshi's Island through the Source Index path. RetroArch was observed active and rendering; no new ROMgi handoff, crash, ANR or OOM was identified. The user-initiated commercial downloads are user-owned state and were preserved. Final `assembleDebug`, `adb install -r` and package verification passed, with no device command after final `pm path`.

`SNES-CATALOG-01` is therefore **PASS / FROZEN**. `DOWNLOAD-1B.3c` remains PAUSED and `SNES-RELEASE-SELECTOR-UI-01` remains backlog before visual freeze. The broader Compose label “Plataformas” still denotes families; terminology cleanup is deferred because changing it is outside this routing slice.

## Multi-system expansion order

The Product Owner-approved implementation, activation, public-eligibility and checkpoint order is binding: **SNES → NES → Genesis / Mega Drive → GBA → Neo Geo / CPS1 / CPS2 / Arcade → N64 → Dreamcast → PlayStation / PS1 → Saturn**. Research may run ahead when it does not activate or checkpoint a later system. Every system begins with a visible Dream Team review; catalog presence and emulator availability do not constitute product support. The next active system Gate is **NES EXPANSION REVIEW**.

## NES-CATALOG-01 — host implementation

Status (2026-08-24): **IMPLEMENTED / DEVICE_TEST_READY / UNSTAGED FOR HUMAN REVIEW** on `feature/nes-catalog-v1`. Dream Team verdict: **CONDITIONAL GO** for cartridge NES only. Scope is MiNERVA No-Intro NES Headered base plus Headered Aftermarket. Headerless duplicates, Private, RetroAchievements, source-code, Family BASIC/Network and FDS are excluded; FDS remains a separate BIOS/media Gate.

- Metadata verified infohashes `6864d0c06b48365d208c7c5f2b1570087f4712de` (7,101 files) and `0ee8de294c059d70cef3c1c3df65459f49506c8b` (2,956 files). Only HTML and `.torrent` metadata were retrieved; no ROM payload or torrent session occurred.
- Deterministic build: 10,057 files → **4,415 games / 8,894 releases / 10,057 candidates**, 1,163 explicit provisional overlaps. JSON 41,425,152 bytes; gzip 2,870,162 bytes; SHA-256 `db19ada0838f5a30f4ce941d24d6b497528263e78881ad6a14a037a54f9957a8`; host open 1,733.466 ms; traced peak 141,474,892 bytes. Android performance is NOT RUN and `SOURCE-READER-OPT-01` remains backlog.
- `CartridgeSourceIndexCatalog` is now the shared SNES/NES reader, paging, projection, release and candidate contract. Room remains exact overlay-only, and each release still produces one explicit candidate with no failover.
- NES preserves `headerState=headered`, `.nes` and configured `mesen_libretro_android.so`. The pre-device checkpoint kept `launchValidated=false`; physical 01B evidence below now justifies `true`. BEST EMULATOR WINS and OZ-EQS remain open.
- `DOWNLOAD-1B.3c` remains PAUSED. No Room migration, FDS/BIOS work, device command, Genesis implementation or SNES state mutation occurred.

### NES-CATALOG-01A — pre-device hardening

Status (2026-08-24): **NES_PRE_DEVICE_HARDENING_PASS / DEVICE_TEST_READY / UNSTAGED**. Generic Source Index status text now derives the selected `CatalogSystem.name` and the opened shard's real `gameCount`; no shared UI route contains an SNES name or 2,505 count. SNES therefore reports Super Nintendo / 2,505 and NES reports Nintendo Entertainment System / 4,415 from their own controlled shards.

`sourceIndexAvailable(systemId)` explicitly controls the two development-validation catalogs (`snes`, `nes`) independently from each projected game's `launchValidated`. At this historical pre-device point SNES was true and NES was false. Registry presence alone does not enable FDS, N64 or another system, and neither catalog availability nor launch evidence infers public/OZ certification.

Mesen host contract: `libretro_systems.json`, `EmulatorProfiles` and the NES Source Index projection all resolve the exact filename `mesen_libretro_android.so`. Debug builds may retrieve `https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/mesen_libretro_android.so.zip`; that exact official artifact returned HTTP 200 with 1,098,808 bytes on 2026-08-24. Oz detects an installed compatible filename via RetroArch's installed-core query, or stages the downloaded artifact through the existing sideload flow, and direct launch passes `${applicationInfo.dataDir}/cores/mesen_libretro_android.so` as `LIBRETRO`. The pinned release `trusted_cores.json` is currently empty, so release automatic provisioning is **NOT READY**; this Gate proves the debug physical-test route only. Installed-device presence and actual launch remain NOT RUN until separately authorized physical validation. Genesis was not started.

### NES-CATALOG-01B / 01C — physical PASS and freeze

Physical result (2026-08-24): **NES_PHYSICAL_PASS** on Xiaomi 14T / Android 15 / API 35. The normal product hierarchy reached Nintendo → Nintendo Entertainment System → Source Index (4,415 controlled games). The user downloaded and played the exact controlled NES 240p Test Suite and, by explicit choice, Super Mario Bros. 3 through selective MiNERVA BitTorrent, existing archive preparation, native `.nes` persistence, RetroArch and `mesen_libretro_android.so`. Audio, directional/action input and rendering passed; RetroArch rendered at approximately 59 FPS. ROMgi substitution, candidate failover, crash, ANR and OOM did not occur.

`launchValidated=true` now records actual Oz → RetroArch → Mesen → playable-content evidence. It does **not** imply public/OZ certification or a final BEST EMULATOR WINS decision. Source Index availability, launch validation and certification remain independent. The debug/device provisioning route passed; pinned release automatic core provisioning remains **NOT READY** because `trusted_cores.json` is empty.

NES reader performance is **YELLOW-ACCEPTABLE / NON-BLOCKING**: Oz post-load PSS was 222,889 KiB, Java heap 65,936 KiB and native heap 21,620 KiB; RetroArch PSS was 144,681 KiB. No valid pre-load Oz process existed, so PSS delta is UNKNOWN. User-visible interaction remained normal and no ANR/OOM/crash occurred, but a 2.1-second main-thread warning and absolute PSS require monitoring. `SOURCE-READER-OPT-01` remains backlog. Genesis and GBA physical gates must collect load/memory evidence; material upward scaling requires Dream Team reconsideration before further expansion.

`NES-CATALOG-01` is **PHYSICAL PASS / FROZEN** with 4,415 games / 8,894 releases / 10,057 candidates; its 2,870,162-byte shard remains SHA-256 `db19ada0838f5a30f4ce941d24d6b497528263e78881ad6a14a037a54f9957a8`. Headered evidence, Room exact overlay-only behavior, one deterministic candidate, DOWNLOAD-1B.3b and paused DOWNLOAD-1B.3c remain unchanged. SNES remains frozen. Next canonical system: Genesis / Mega Drive review only.

## GENESIS-CATALOG-01A — controlled census and host benchmark

Status (2026-08-25): **PASS / FROZEN** at `64d7e7672a45f762c89b150ccd7c0dcaa6c2697a` on `feature/genesis-catalog-v1`. Android integration was not part of 01A. Only MiNERVA dashboard and `.torrent` metadata were read; no ROM bytes or torrent payload session occurred.

Controlled v1 is cartridge-only No-Intro `Sega - Mega Drive - Genesis` base plus Aftermarket. Their validated infohashes are `7bed1007999d817da4605282780a2715f08d470a` (4,233 files) and `cf45a4f757253f0c44931d353cbc95bc3c6ef4f9` (803 files). All 14 Genesis-adjacent dashboard collections were inventoried: Private and RetroAchievements are excluded provider noise; the T-En translation collection and Enhanced Colors are deferred with provenance; MD+, MSU-MD and Mode 1 CD are separate media gates; all five 32X collections are a separate `sega-32x` system. Deferred does not mean discarded.

Deterministic host result: 5,036 ZIP metadata records → **1,918 provisional games / 4,729 releases / 5,036 candidates**, with 307 explicit overlap warnings. There are 4,422 single-candidate releases and 307 multi-candidate releases. Publication classes: Retail 2,472, Aftermarket 899, Beta 1,299, Demo 59. Modification classes: Original 3,371, Unknown 1,358. Region is provider-derived only; language and exclusivity remain unknown, and the parser now rejects non-region parentheticals instead of polluting region evidence.

Shard evidence: JSON 20,387,781 bytes; gzip 1,459,910 bytes; compression ratio 0.071607; compressed SHA-256 `df42eb52fc05d17c85c88f9f6794cb4f622b8f3293a9bb73ae8dd6db34c1c72f`; uncompressed SHA-256 `8e284709e7bc6c06d2e852a5dea5c53f134f5060e9b07b855b4400117821d90c`; deterministic rebuild PASS. Host open was 1,289.338 ms and traced peak 70,542,108 bytes. Scale is **LOWER_THAN_NES** and approximately SNES-sized, so current host evidence does not independently trigger SOURCE-READER-OPT-01; NES YELLOW device evidence still requires Genesis load/memory measurement before activation.

The controlled metadata contains six 240p Test Suite candidates covering v1.23/v1.30 and provider `Emu`/non-`Emu` variants. The future target recommendation is v1.30 non-`Emu` Program from Aftermarket (candidate `9deae8309b64342026dba31215ba28df37ba3c049884e53398b21580a6dd0dcd`, infohash `cf45a4f757253f0c44931d353cbc95bc3c6ef4f9`, file index 7, 82,432-byte ZIP), pending curator confirmation of the provider's `Emu` semantics. No payload was downloaded.

At the 01A checkpoint the registry still carried historical `launchValidated=true`; 01A supplied no launch evidence. The later 01C audit found no traceable physical Genesis content launch and corrected the authoritative value to `false`. Pinned release provisioning remains NOT READY because `trusted_cores.json` is empty.

## GENESIS-CATALOG-01B — Android catalog integration

Status (2026-08-25): **ANDROID CATALOG INTEGRATION / HOST PASS / PENDING DREAM TEAM PRE-DEVICE REVIEW** on `feature/genesis-catalog-v1`. The exact controlled 1,459,910-byte shard is packaged at `assets/source-index/genesis.json.gz.bin` with SHA-256 `df42eb52fc05d17c85c88f9f6794cb4f622b8f3293a9bb73ae8dd6db34c1c72f`. APK extraction reconfirmed exact bytes/hash and preserved the frozen SNES/NES asset hashes.

The existing shared `CatalogSystemRouting → SourceIndexCatalogs → CartridgeSourceIndexCatalog → OzSourceIndexReader` path now exposes `Sega → Mega Drive / Genesis`, system `genesis`, backend `SOURCE_INDEX`, 1,918 games / 4,729 releases / 5,036 candidates. Discovery remains read-only; Room only provides exact projection/user-state overlays and no bulk mirror or fuzzy title merge was added. 32X and Sega CD remain unavailable through this Genesis route. Search, paging, release enumeration, truthful region filters and name sorting reuse the existing Source Index implementation. A separate reusable acquisition gate is disabled for Genesis, so catalog projections cannot expose Source Index download/play or fall through to ROMgi before the next approved Gate; SNES/NES acquisition remains unchanged.

The exact future 240p v1.30 Program identity resolves from the packaged shard in host tests, including release `f7e52049909d969559d78a4337cf981e92c138e039a365738fd1e19c0565e46c` and candidate `9deae8309b64342026dba31215ba28df37ba3c049884e53398b21580a6dd0dcd`; no payload was acquired or executed. ZIP remains provider wrapper evidence, not asserted native emulator input.

Catalog availability is independent from launch validation and certification. The historical `launchValidated=true` present during 01B was unsupported by physical evidence and corrected to `false` in 01C. Genesis Plus GX remains a provisional front-runner only. BEST EMULATOR WINS is OPEN, pinned production core provisioning is NOT READY, and license/redistribution permission remains an unresolved production gate. Downloads, preparation, launch, core provisioning and device validation were NOT RUN in 01B; Xiaomi was not touched. `DOWNLOAD-1B.3b` remains frozen and `DOWNLOAD-1B.3c` remains PAUSED.

Host validation: focused Source Index/routing tests 25/25 PASS; full JVM 142/142 PASS; `compileDebugKotlin`, `compileDebugAndroidTestKotlin`, `assembleDebug` and APK asset verification PASS. Device/interaction/physical performance remain NOT RUN.

## GENESIS-CATALOG-01C — pre-device play pipeline

Status (2026-08-25): **HOST PIPELINE PASS / DEVICE TEST READY**. This section supersedes the historical Genesis `launchValidated=true` statements in 01A/01B: no traceable physical Genesis content-launch checkpoint exists, so every authoritative Genesis registry/seed/Source Index projection now correctly uses `launchValidated=false`. Catalog availability remains independent from acquisition, launch validation and public/OZ certification.

The controlled corpus is Artemio Urbina's open-source 240p Test Suite. The [official GitHub project](https://github.com/ArtemioUrbina/240pTestSuite) identifies it as GPL v2 free software; the author's [official itch.io devlog](https://artemiourbina.itch.io/240p-test-suite/devlog/785471/240p-test-suitefor-genesis-sega-cd-diagnostics-from-cart) publishes the Mega Drive/Genesis 1.30 build dated 2024-08-21. MiNERVA packaging is provenance, not a licensing authority. Exact Source Index selection: GameId `393411b9ec7e074469ac08acf21ba92c66a47ba7decd74b0d51fb5d54e4d60a8`, ReleaseId `f7e52049909d969559d78a4337cf981e92c138e039a365738fd1e19c0565e46c`, CandidateId `9deae8309b64342026dba31215ba28df37ba3c049884e53398b21580a6dd0dcd`, infohash `cf45a4f757253f0c44931d353cbc95bc3c6ef4f9`, zero-based file index 7 and the exact 82,432-byte Aftermarket Program ZIP. The source plan contains one candidate at selected index 0; ROMgi and failover are absent.

Host acquisition used the frozen MiNERVA torrent with only aria2's one-based index 8 selected (the same zero-based libtorrent index 7). ZIP SHA-256 is `2099544016d8d7a6c30c7450cc121466b7e34e9af8cdf64ea6e326deb06e6ffd`. Its sole safe member is `240p Test Suite (World) (v1.30) (Program) (Aftermarket) (Unl).md`, 262,144 bytes, SHA-256 `6d7dd3ae921d294bdc64d1733ff9cc82cdc996d716b68408c0fe38371b77e5b8`. The shared `RomArchivePreparer`, made directly host-testable without a Genesis-specific preparer, deterministically materialized the `.md` and now explicitly rejects absolute/dot/traversal entry paths.

The projection remains system `genesis`; its deterministic projection ID retains GameId, while the durable `DownloadSourcePlan` retains the ReleaseId-derived logical identity and exact CandidateId/transport coordinates. The existing persistent-library path consumes the prepared native file under that projection identity; the durable download task remains the release/candidate provenance owner. No Genesis-specific storage or device write was introduced. `acquisitionEnabled` advanced to `true` only after exact resolution, real-archive preparation and persistence-contract tests passed. SNES/NES behavior is unchanged and DOWNLOAD-1B.3c remains PAUSED.

Core host consistency passes for `genesis_plus_gx_libretro_android.so`: registry, projection and `EmulatorProfiles` agree on the filename and native extensions; the debug resolver maps it to `${applicationInfo.dataDir}/cores/genesis_plus_gx_libretro_android.so`. No core was downloaded or provisioned. `trusted_cores.json` remains empty, production licensing/provisioning is unresolved and BEST EMULATOR WINS remains OPEN.

Future device acceptance must cover D-pad, A/B/C/Start and 3-button operation, plus X/Y/Z with a separately lawful 6-button scenario. The 240p target exercises first play, audio/rendering and primarily NTSC 60 Hz; it does not alone certify 6-button or PAL behavior. Oz delegates SRAM/save files/save states/remaps to RetroArch, so host route presence is not save correctness; create/load SRAM and save-state recovery require device evidence. The device Gate must record cold load, PSS, Java/native heap, GC, responsiveness and crash/ANR/OOM because NES reader performance was YELLOW-ACCEPTABLE. Device, emulator and Xiaomi were NOT RUN/TOUCHED in 01C.

01C validation: focused Genesis pipeline/core/catalog/routing 16/16 PASS; full JVM 146/146 PASS with zero skipped; `compileDebugKotlin`, `compileDebugAndroidTestKotlin`, `assembleDebug` and `git diff --check` PASS. The controlled real-archive subtest ran with `skipped=0`. Source Index tooling was unchanged, so Python regressions were not applicable.

Production-reader min/median/max: open 928.948/942.916/957.590 ms; managed heap before 2,671,184/2,671,184/2,933,616 bytes, after 86,377,040/120,898,128/120,898,128, delta 83,705,856/118,222,848/118,226,944 (~112.7 MiB median, YELLOW); observed peak `UNKNOWN`; PSS before 126,326/128,923/129,029 KiB, after 208,358/228,962/229,280, delta 79,719/100,206/102,636 KiB (~97.9 MiB median, GREEN). ART GC count delta was 4/4/6 and time 41/44/65 ms, bounded. Platform `snes` PASS; the reader does not expose aggregate counts or CandidateId lookup, so those are honestly `NOT_EXPOSED_BY_READER` / `NOT_IMPLEMENTED`. There was no crash, ANR, OOM, or exception.

Separate `DIAGNOSTIC_ONLY` min/median/max: decompression 83.774/84.043/84.536 ms; JSON parse 582.675/589.917/594.712; prototype CandidateId index 6.581/7.384/7.912; first lookup 0.005153/0.005769/0.010154; 10,000 lookups 14.098/14.208/14.268. Diagnostic integrity reconfirmed 2,505 games, 4,764 releases, 4,874 candidates and first/middle/last IDs. Corrected PSS disproves the original YELLOW PSS signal, but isolated retained heap exceeds the 96 MiB GREEN budget while remaining below RED. JSON.gz is viable on this reference device and needs reader optimization before format freeze; no SQLite/streaming/production change belongs to R1. This remains reference-device evidence, not universal device-tier or full SNES OZ-EQS certification.

## GENESIS-CATALOG-01D / GLOBAL-LIBRARY-IDENTITY-01

Genesis physical validation on Xiaomi 14T / Android 15/API 35 proved the controlled 240p v1.30 pipeline end to end: exact one-candidate MiNERVA selection, 82,432-byte ZIP, 262,144-byte `.md`, persistent registration, RetroArch, `genesis_plus_gx_libretro_android.so`, playable rendering/audio/input, no ROMgi/failover/crash/ANR/OOM. Streets of Rage 3 also downloaded and launched correctly. Reader evidence was baseline 284,323 KiB PSS, post-Genesis 347,027 KiB, delta 62,704 KiB (~61.2 MiB), with an observed 1.059-second load wait; classification is **YELLOW-ACCEPTABLE**. `launchValidated` remains false until the post-fix freeze/revalidation Gate.

The same test exposed a shared identity defect: opening the distinct Streets of Rage Source Index GameId `f460da...` immediately supplied `Streets of Rage 3 (USA).md`, with no Streets of Rage candidate selection or download. The user independently reproduced the same class on SNES. RetroArch and cores correctly launched the content Oz supplied, so the demonstrated boundary is local reconciliation before acquisition, not candidate/download/preparation/emulation.

Code tracing found that Source Index projection, Room overlay, `PersistentRomLibrary` record and authorized-source lookups are exact by `GameEntity.id`. When all exact sources are absent, however, `SessionActivity` called `RomLibraryResolver`, whose legacy canonical-title prefix fallback accepts a filename when `candidate.startsWith("$target ")`. Therefore `streets of rage 3` matched `streets of rage`; the same mechanism exposes sequel/edition families across systems.

`GLOBAL-LIBRARY-IDENTITY-01` makes strong identity outrank compatibility: Source Index projections skip the legacy library scan and can become local only through the exact projection/GameId association already owned by Room, `PersistentRomLibrary`, explicit import/authorized source, or a completed download. Non-Source-Index legacy rows retain their existing scan. No Room schema migration, shard/ID mutation, downloader/core change or cleanup is required. A structured `LOCAL_LIBRARY_LEGACY_FALLBACK_SKIPPED` event records projection ID, Source Index GameId and `STRONG_GAME_ID_REQUIRED` without paths.

Frozen-metadata diagnostic scan of the exact legacy prefix predicate found **262 SNES groups** (largest 9; examples Mega Man, Super Bomberman), **318 NES groups** (largest 23; examples Super Mario, Super Mario Bros., Mega Man) and **170 Genesis groups** (largest 9; examples Streets of Rage, Sonic The Hedgehog, Phantasy Star II). These counts represent latent weak-key exposure, not proposed merges. Regression coverage preserves exact same-GameId overlays and multi-release semantics while rejecting different GameIds, sequel prefixes and same-title cross-platform reuse. GBA remains NOT STARTED; physical revalidation is separately gated.

## GLOBAL-LIBRARY-IDENTITY-02 / GENESIS-CLOSURE-01

Physical revalidation (2026-08-25) is **GLOBAL_IDENTITY_PHYSICAL_PASS**. Genesis Streets of Rage, Streets of Rage 2 and the preserved Streets of Rage 3 each acquired/retained and launched their own ROM; Genesis 240p Test Suite v1.30 again passed image, audio and input through Genesis Plus GX. SNES Mega Man II/Mega Man III Aftermarket and NES Mega Man/Mega Man 2 likewise retained independent GameIds, candidates and native `.sfc`/`.nes` content. `LOCAL_LIBRARY_LEGACY_FALLBACK_SKIPPED` with `STRONG_GAME_ID_REQUIRED` was observed. Wrong-game content URI, ROMgi substitution, candidate failover, Oz crash, ANR and OOM were not observed. Final device restoration passed and no command followed final `pm path`.

The root cause is closed: `RomLibraryResolver`'s legacy canonical-title prefix predicate, `candidate.startsWith("$target ")`, was reachable after exact local lookups failed. Strong Source Index projections now require their exact durable `GameEntity.id` association and skip title/file compatibility scans; legacy non-Source-Index rows retain compatibility behavior. Room migration, shard/identity changes, downloader changes and emulator/core changes were unnecessary. The diagnostic counts—SNES 262, NES 318 and Genesis 170—are **legacy-prefix exposure keys** under the removed weak path, not confirmed user-visible failures and not games to merge.

Genesis now has traceable physical content-launch evidence, so authoritative Genesis representations use `launchValidated=true`. This means Oz launched actual Genesis content on the reference Xiaomi through `genesis_plus_gx_libretro_android.so`; it does not infer OZ certification, final BEST EMULATOR WINS, broad commercial compatibility, 6-button/PAL/save certification, production redistribution permission or trusted-core provisioning readiness. Genesis Plus GX is the physically validated first-play route while those gates remain open. Reader evidence remains **YELLOW-ACCEPTABLE**: 284,323 KiB baseline PSS, 347,027 KiB post-load, 62,704 KiB (~61.2 MiB) delta and ~1.059 s observed wait, without crash/ANR/OOM. `SOURCE-READER-OPT-01` remains backlog.

## IDENTITY-TYPING-01

The urgent identity fix originally recognized strong Source Index identity with two string conventions: persisted `source == "oz-source-index"` plus an `id.startsWith("oz-source-index-")` check. `GameEntity` now exposes typed `GameCatalogOrigin`, parsed at the single persistence boundary from the unchanged `source` column. `LocalRomIdentityPolicy`, Source Index projection routing and catalog presentation consume `GameCatalogOrigin.SOURCE_INDEX`; business logic no longer treats an ID prefix as identity authority.

No Room migration or user-content migration is required. The persisted source value, projection/Game/Release/Candidate IDs, `PersistentRomLibrary` keys and legacy rows are unchanged and remain readable. A future projection-ID representation remains strongly identified when its authoritative origin is Source Index, while a fake Source-Index-looking ID with a non-Source-Index origin remains legacy/other. Legacy compatibility scanning remains available only where previously intended, and `LOCAL_LIBRARY_LEGACY_FALLBACK_SKIPPED` observability is preserved. Remaining Source Index strings are serialization values, stable ID namespaces, source-plan namespaces, logs or compatibility assertions—not duplicated business inference.

After this Gate freezes, the next canonical work is **GBA — DREAM TEAM / CONTROLLED CENSUS**. SNES, NES, Genesis and the global library-identity correction remain physically validated/frozen; GBA implementation is NOT STARTED.

## GBA-CATALOG-01A — controlled census, emulation research and host benchmark

Status (2026-08-25): **PASS / HOST RESEARCH ONLY**. Starting checkpoint was
`56673398f27fff1aff2e72aec5bf3de6e7e1d9df` with local HEAD equal to upstream and
divergence 0/0. The frozen global identity, Genesis closure and identity typing truth
remained unchanged. Only MiNERVA dashboard evidence, `.torrent` metadata, Source Index
tooling, official upstream research and host benchmark work were authorized. No
commercial ROM payload, Android Source Index registration, Room change, acquisition,
download/play UI, core install, RetroArch launch, ADB or physical device work occurred.

### Source discovery and controlled corpus

Thirteen GBA-relevant MiNERVA collections were exhaustively classified from exact
torrent info bytes. Controlled v1 is the smallest coherent cartridge corpus: No-Intro
`Nintendo - Game Boy Advance`, infohash
`e1ec1d83aac1633fade7589ef694b800bbb979a2`, 3,688 files / 14,855,506,757 represented
bytes, plus `Nintendo - Game Boy Advance (Aftermarket)`, infohash
`6aae2f6950245b9947be3431f81e23d4b60a2b94`, 236 files / 512,237,764 bytes. All 3,924
provider artifacts are ZIP wrappers.

Eleven sources are deferred without being discarded: Multiboot
`ca43a4e7359696db2d8c78851295701ff678bcf1` (100 / 8,982,456), Play-Yan
`e4ba4ec23e19b2573c4837ea593981376045b817` (15 / 2,189,202), Private
`c0b254bbc76eafc85597caa9912a569d58c1f73f` (22 / 118,157,345), Video
`a5f3ee91bacf7590d38f6285312e00d7071b74c5` (44 / 1,611,418,142), Video Aftermarket
`751f54f2d88b68e8573b8fe61ea3cadcd50884d0` (64 / 1,524,621,717), Video Private
`48ed573cd39d7dd1955ed8ce233fa5951c643c1b` (1 / 91,532,634), e-Reader
`f2e746f781a14f8f044fe522feb16790a000ab65` (3,254 / 12,457,907), e-Reader Aftermarket
`3d9f2d938bb17722a86cf46c807f6223a05bd581` (8 / 50,344), Source Code
`c0ebd8505ee623898c927c18b175f02901711133` (4 / 219,258,244), RetroAchievements
`23dd4e511917129b8834524b3ed1d6999f15b7bb` (1,206 / 7,279,179,232), and T-En
translations `88a33927fbb4ca7730bc8dfdc8a030a3bf9361a5` (97 / 536,058,447). Classification
counts are PRIMARY_CONTROLLED 1, AFTERMARKET 1, SPECIALTY 7, PRIVATE 2,
RETROACHIEVEMENTS_NOISE 1 and TRANSLATION 1. No source was silently classified as a
generic game set.

The complete 8,739-file census found 846 exact filename overlaps: 751 base↔RA,
59 base↔Aftermarket, 35 Video↔RA and 1 Multiboot↔RA. These are diagnostic overlap
warnings, not deduplication or identity merges. Japan-region evidence never became
Japan-exclusive; language and exclusivity remain independent facets.

### Source Index identity and benchmark

The existing parameterized `snes_benchmark.py -> builder.py` architecture produced the
GBA diagnostic; `gba_census.py` is metadata classification only, not a parallel builder.
Result: **3,924 inputs → 2,346 provisional games / 3,865 releases / 3,924 candidates**,
with 3,806 single-candidate and 59 multi-candidate releases. Publication classes are
Retail 3,325, Aftermarket 248, Beta 255 and Demo 37. Modification classes are Original
3,573 and Unknown 292.

Provider-derived regions: Australia 3, China 27, Europe 925, France 59, Germany 71,
Italy 31, Japan 1,012, Japan+USA 1, Korea 6, Netherlands 11, Russia 4, Spain 35,
Taiwan 12, USA 1,037, USA+Australia 11, USA+Europe 289, World 308 and Unknown 23.
Explicit filename-language evidence only: Arabic 2, Chinese 2, Danish 52, Dutch 191,
English 954, Finnish 15, French 876, German 773, Italian 673, Japanese 62, Norwegian 19,
Polish 3, Portuguese 31, Russian 2, Spanish 746 and Swedish 36. Language is Unknown for
2,855 releases, exclusivity Unknown for all 3,865, candidate artifact hash scope Unknown
for all 3,924, and release content hash scope Unknown for all 3,865. Region never
implied language.

Frozen identity semantics remain sufficient. One Pokémon Ruby GameId owns three
revision ReleaseIds, while Pokémon Emerald has another GameId; Mega Man Battle Network
and Battle Network 2 are likewise distinct. The 59 repeated releases preserve separate
CandidateIds with immutable infohash, file index and exact full path. Tracker/provider
metadata churn remains excluded from CandidateId, and content hash remains evidence,
not ReleaseId. Localized-title equivalence was not guessed without an authoritative
mapping; no GBA identity architecture review was triggered.

Anti-collision analysis reports 141 legacy-prefix exposure families, largest 10 games,
including Mega Man Battle Network, Super Mario Advance, Pokémon variants and numbered
sequels. These reproduce the removed weak text predicate only; strong Source Index
GameIds keep the titles independent and no merge occurred.

Deterministic host output: JSON 16,308,221 bytes; gzip 1,270,357; compression ratio
0.077897; compressed SHA-256
`473fa4192852c95fed138ce7756abaece18bf25350aa85e83224bb28d613d2cd`; uncompressed
SHA-256 `435681fe064fd01bdd11fb2c6214b08f78686992ed983dc97756eb015f6d0085`.
Generation A and B are byte-identical. Build 1,054.777 ms; open/parse 1,416.843 ms;
first exact lookup 0.0069 ms; 10,000 exact lookups 34.590 ms / 0.003459 ms average;
peak traced host memory 56,754,567 bytes. Versus SNES 20,247,331/1,477,037/
69,961,882 bytes, NES 41,425,152/2,870,162/141,474,892 and Genesis
20,387,781/1,459,910/70,542,108, GBA is **LOWER_THAN_EXISTING** by shard and memory.
The one-run open time is within existing scale variability and does not trigger
`SOURCE-READER-OPT-01` before Android.

### First-play, emulator, format and BIOS research

The lawful first-play recommendation is pinobatch's [160p Test Suite v0.23](https://github.com/pinobatch/240p-test-mini/releases/tag/v0.23),
from the [official GPL-2.0-or-later source project](https://github.com/pinobatch/240p-test-mini).
The official `240pee_mb.gba` asset is 61,104 bytes and designed to remain within the
256 KiB GBA multiboot limit. Only its primary provenance and HTTP headers were verified;
the payload was not downloaded. It can later evidence native boot, image/patterns,
motion/parallax, audio and ordinary GBA controls, but not saves, RTC, sensors, rumble,
link or special cartridges.

[mGBA upstream](https://github.com/mgba-emu/mgba) remains actively maintained under
MPL-2.0; formal 0.10.5 plus current master document very good GBA compatibility, low-end
performance, RTC, save-type/flash-size detection, rumble, motion, solar, built-in BIOS,
states and remappable controllers. [mGBA libretro](https://docs.libretro.com/library/mgba/)
is the **PROVISIONAL_FRONT_RUNNER**: official ARM64 buildbot availability was HTTP 200,
accepted content is `.gb`, `.gbc`, `.gba`, BIOS is optional, and saves/states/rewind/
controls/rumble are exposed. Standalone mGBA has no official Android port, so no package
or Activity contract is asserted.

[VBA-M upstream](https://github.com/visualboyadvance-m/visualboyadvance-m) and
[VBA-M libretro](https://docs.libretro.com/library/vba_m/) remain the **ALTERNATIVE**:
maintained v2.2.3/source, Android build targets and official ARM64 core availability,
with optional BIOS, RTC/solar controls, rumble and analog-mapped gyro/tilt, but GPL-2.0
redistribution and broader fallback validation increase friction. [gpSP](https://github.com/libretro/gpsp)
is active, fast, ARMv8/Android-capable and available from the official buildbot, but
[Libretro's own guidance](https://docs.libretro.com/guides/memorymonitoring/) warns that
some games fail or crash and recommends other cores where performance permits. Its
libretro sensor support is absent; gpSP is **REJECTED AS PRIMARY** and retained only as
future low-end fallback research. BEST EMULATOR WINS remains OPEN until physical proof.

Provider format is ZIP wrapper, not core content. The future contract is MiNERVA ZIP →
existing generic `RomArchivePreparer` → native `.gba` → mGBA/VBA-M `.gba`. The preparer
already includes `.gba`; no 01A preparer or profile change was made. Historical frontend
ZIP exposure cannot override the official core-extension contract.

[Libretro BIOS guidance](https://docs.libretro.com/guides/bios/) and the mGBA core page
classify `gba_bios.bin` as optional. mGBA supplies a built-in BIOS implementation/HLE
path and can use an external user-owned BIOS when enabled. Oz must never source or
redistribute copyrighted BIOS data; future work may recognize an optional legal
user-supplied file and compare behavior, using the official documented hash only for
verification. BIOS availability does not block the catalog Gate or prove edge fidelity.

### Special cartridge capability census

Current [mGBA interface definitions](https://github.com/mgba-emu/mgba/blob/master/include/mgba/gba/interface.h)
explicitly distinguish SRAM, Flash512, Flash1M (128 KiB), EEPROM, EEPROM512 and SRAM512,
and RTC, rumble, light, gyro, tilt and e-Reader devices. Its
[override table](https://github.com/mgba-emu/mgba/blob/master/src/gba/overrides.c) provides
representative evidence: Boktai 1/2 EEPROM+RTC+light; WarioWare: Twisted! SRAM+rumble+
gyro; Yoshi's Universal Gravitation/Topsy-Turvy EEPROM+tilt; Drill Dozer SRAM+rumble;
Pokémon Ruby/Sapphire/Emerald Flash1M+RTC; FireRed/LeafGreen Flash1M without RTC;
e-Reader Flash1M+e-Reader; Advance Wars and Golden Sun: The Lost Age Flash512; Metal
Slug Advance EEPROM; Mega Man Battle Network SRAM.

[mGBA's official FAQ](https://mgba.io/faq.html) describes rumble, rotation/tilt, solar,
RTC, partial link cable and BattleChip Gate support, while Wireless Adapter, IR Adapter
and Soul Adapter remain unsupported. The older libretro mGBA feature table says Sensors
unsupported, but [current core source](https://github.com/mgba-emu/mgba/blob/master/src/platform/libretro/libretro.c)
requests accelerometer, gyroscope and illuminance sensor interfaces. That discrepancy is
a mandatory future Android/device validation gap; it is not treated as a support claim.

### Validation and Dream Team verdict

Python Source Index/tooling: 45/45 PASS. Census regressions: 17/17 PASS. Platform
qualification: 3/3 PASS. Total 65/65, zero failures. Deterministic dual build, exact
lookup and `git diff --check` PASS. Gradle/JVM Android gates were not
run because Kotlin, Android assets, Room, routing and production code were untouched;
device is explicitly NOT RUN.

Dream Team roles converge on **GO_TO_GBA_ANDROID_CATALOG_INTEGRATION** as the next
separately authorized Gate, not automatic work. Product/Technical Lead: GO on the
smallest corpus and frozen scope. Software Architect: GO because the common builder and
identity model remain intact. Android Engineer: CONDITIONAL GO, preserving no routing,
acquisition or launch until 01B authorization. Data/Storage: GO with no Room mirror or
schema. Emulation Integration: CONDITIONAL GO on mGBA libretro pending provisioning,
sensor and physical evidence. Game Knowledge Curator: GO with translations/specialty/
localized equivalence deferred and uncertainty preserved. Performance: GO because scale
is lower than existing. QA/Reliability: GO on 65 tests and deterministic output, with
device evidence still absent. Technical Researcher: GO on primary-source provenance,
with mutable buildbot artifacts not acceptable as release pinning.

Android: **NOT IMPLEMENTED**. Acquisition: **DISABLED / NOT IMPLEMENTED**.
`launchValidated`: **FALSE / NOT CLAIMED**. Public/OZ certified: **NO**. Device: **NOT RUN**.
Do not begin 01B automatically.

### GBA-CATALOG-01A.SEMANTIC-03 closure

Status (2026-08-26): **PASS / FROZEN**. `LEGACY_GBA_SUPPORT = TRUE`; this is
historical legacy support only. GBA Source Index `launchValidated` remains
**FALSE / NOT YET PROVEN**, and GBA remains outside Source Index routing and
acquisition. A versioned, idempotent Room repair clears only the legacy GBA
`launchValidated` flag while preserving persisted runtime and library state;
the legacy importer cannot recreate `true` for GBA when `oneTapSupported` is
absent. SNES, NES and Genesis remain **FROZEN** and unchanged by this Gate.

### GBA-CATALOG-01B.COVERAGE-02 — Prototype Semantic Repair

Status (2026-08-26): **PASS / FROZEN**. The shared metadata parser now maps
GBA filename tags `Proto` and `Prototype` to `publicationClass=PROTOTYPE` while
leaving `canonicalTitleKey`, `ReleaseId` and `CandidateId` inputs unchanged.
Regeneration of the two controlled collections remains deterministic: 2,346
games / 3,865 releases / 3,924 candidates, with 110 releases corrected from
Retail to Prototype. Records whose authoritative collection classification is
Aftermarket remain Aftermarket. Regions, languages, Beta, Demo, Aftermarket,
UNKNOWN facets and all torrent coordinates are unchanged; translations remain
outside the corpus. SNES, NES and Genesis shards are byte-identical to their
pre-repair outputs. The GBA shard is not promoted to Android production assets.

### GBA-CATALOG-01B.COVERAGE-04 — Translation Identity & Metadata Repair

Status (2026-08-26): **PASS / CONTROLLED**. GBA `[T-En...]` bracket metadata
now separates Translation Releases while `canonicalTitleKey` remains the Game
identity. Explicit T-En evidence sets `modificationClass=TRANSLATION`; language,
status and provenance remain `UNKNOWN` pending authoritative research. Dry run:
97 Candidates, 70 Games, 97 Releases, zero original-release or candidate
collisions. T-En is not in production assets; SNES, NES and Genesis remain FROZEN.
### GBA-CATALOG-01C.IMPLEMENTATION-01 — Multi-Local Release Storage

Status (2026-08-27): **PASS / FOUNDATION**. `PersistentRomLibrary` now supports
versioned Source Index local-release records (schema 3) keyed by a length-prefixed
`GameId + ReleaseId` digest, while legacy `rom.<gameId>` records remain readable.
Candidate provenance (ReleaseId, CandidateId, provider, infohash, file index/path
and expected size) is carried through persisted download plans and local records.
Room remains the active-content overlay only: multiple Releases coexist and one
explicit `GameEntity.contentUri` is active. Replacement/deletion never selects
another Release silently. GBA acquisition remains **FALSE**; no download, device,
emulation or certification evidence is claimed. SNES, NES and Genesis remain
**FROZEN**.

---

# ARCHIVED PRE-V4 PROJECT_DASHBOARD

# Oz RetroRom - Project Dashboard

> Mobile operational snapshot. Repository and AI_PROJECT_MASTER remain authoritative.

## NOW

**Gate:** `GBA-CATALOG-01C.IMPLEMENTATION-01.REPAIR-01`
**Status:** `CONTROLLED REPAIR / NOT AUTHORIZED FOR PUSH`
**Branch:** `feature/gba-catalog-v1`
**HEAD:** `1a277d96f0f9edd9db64296c87f238c1f1677fac` (local repair changes uncommitted)
**Upstream:** `b5807e3470f7d2bef4a3687c3f32c50ef7924d7a` · divergence `1/0`
**Governance V2:** `5716ea8`

**Objective:** completed—GBA controlled census, emulator/BIOS research, lawful first-play provenance and deterministic host benchmark, without Android activation or device work.

GBA controlled evidence:
- MiNERVA GBA discovery found 13 relevant collections from verified torrent metadata. V1 admits only No-Intro base `e1ec1d83aac1633fade7589ef694b800bbb979a2` (3,688 files) plus Aftermarket `6aae2f6950245b9947be3431f81e23d4b60a2b94` (236 files); 11 translation/specialty/private/RA collections remain explicitly deferred.
- Deterministic result: 3,924 ZIP metadata records → **2,346 games / 3,865 releases / 3,924 candidates**, 59 multi-candidate release overlaps. No commercial ROM payload was downloaded.
- JSON 16,308,221 bytes; gzip 1,270,357; SHA-256 `473fa4192852c95fed138ce7756abaece18bf25350aa85e83224bb28d613d2cd`; open 1,416.843 ms; traced peak 56,754,567 bytes. Scale is **LOWER_THAN_EXISTING**; no reader optimization Gate is triggered.
- Region remains provider-derived, language exists only from explicit filename tags, and exclusivity/hash scopes remain UNKNOWN. 141 old-prefix exposure families are diagnostic only; strong GameId identity remains unchanged.
- Lawful future first-play: pinobatch 160p Test Suite v0.23, GPL-2.0-or-later, native 61,104-byte `.gba`; headers/provenance only, payload not acquired.
- mGBA libretro is `PROVISIONAL_FRONT_RUNNER`, VBA-M libretro is the alternative, gpSP is rejected as primary. BEST EMULATOR WINS remains OPEN. BIOS is optional user-owned firmware; sensors/link/special cartridges require future physical proof.
- Source contract: MiNERVA ZIP wrapper → existing generic preparation to native `.gba` → official mGBA `.gba` content. Android registration, acquisition, launch, provisioning, public/OZ state and device: **NOT IMPLEMENTED / DISABLED / NOT RUN**.
- Validation: 65/65 Python tests, deterministic dual build and `git diff --check` PASS. Android/Gradle was not run because Android production code was untouched.

Global library identity evidence:
- Physical Genesis proof: Streets of Rage had a distinct Source Index GameId, but the legacy library scan matched `Streets of Rage 3 (USA).md` by canonical-title prefix before candidate selection. RetroArch correctly launched the file supplied by Oz. The user reproduced the same class on SNES.
- Exact Room overlays, `PersistentRomLibrary` records and authorized sources are keyed by `GameEntity.id`; the defect was the later `RomLibraryResolver` fallback, whose prefix rule accepted `candidate.startsWith("$target ")`.
- Source Index projections now skip legacy title/file scanning and require an exact durable GameId association. Legacy non-Source-Index rows keep the compatibility scan. No Room migration or shard/ID change is required.
- Physical revalidation PASS: Genesis Streets of Rage 1/2/3, SNES Mega Man II/III Aftermarket and NES Mega Man/Mega Man 2 retained independent GameIds, candidates and native ROMs. `LOCAL_LIBRARY_LEGACY_FALLBACK_SKIPPED` reported `STRONG_GAME_ID_REQUIRED`; wrong-game content URI, ROMgi substitution and failover were absent.
- Diagnostic scan: SNES 262, NES 318 and Genesis 170 **legacy-prefix exposure keys** under the old weak predicate. They are not confirmed user-visible collisions, identity merges or games to merge.
- No Room migration, shard-ID change, downloader change or core change was required. Legacy non-Source-Index compatibility remains available.
- Part A focused identity/Source Index/routing/core regressions: 37/37 PASS; full JVM 154/154, Kotlin/AndroidTest compilation, debug APK and diff-check PASS. Dream Team review remains required before checkpoint.
- GBA 01A host research is complete. No device command was run; Android integration and launch remain NOT STARTED.
- `GBA-CATALOG-01A.SEMANTIC-03`: **PASS / FROZEN**. `LEGACY_GBA_SUPPORT = TRUE`; GBA Source Index `launchValidated = FALSE / NOT YET PROVEN`, with GBA still outside Source Index routing/acquisition. Versioned idempotent Room repair is recorded; legacy import cannot restore GBA `true`. SNES/NES/Genesis remain **FROZEN**.
- `GBA-CATALOG-01B.COVERAGE-02`: **PASS / FROZEN**. GBA `Proto`/`Prototype` tags now classify as `PROTOTYPE`; 2,346/3,865/3,924 counts and all Game/Release/Candidate IDs and torrent coordinates remain stable. Aftermarket precedence, translations=0, and SNES/NES/Genesis frozen outputs are preserved. Shard remains unpromoted to Android assets.
- `GBA-CATALOG-01B` Android Source Index remains integrated/catalog-only; GBA acquisition is `false`, launch validation is `false`, and no physical/OZ-EQS/public certification is claimed.

Current 01C repair evidence:
- `PersistentRomLibrary` now defaults missing/invalid ownership to non-owned and gates physical deletion exclusively on explicit `OZ_OWNED`.
- A durable pre-artifact pending intent and deterministic `.oz-pending-*.part` marker allow reconciliation after process death; Room is updated only after `COMPLETE`.
- Conductual multi-local Original + Translation, restart, replacement, delete and SAF recovery tests are still pending; no push is authorized.
- `DOWNLOAD-UX-START-01` remains **HOLD / REVIEW** before GBA push. ROMgi vs Source Index review remains **Calculando...**.

Genesis measured evidence:
- Controlled scope: No-Intro base 4,233 files + Aftermarket 803; all 5,036 artifacts are ZIP wrappers.
- Result: 1,918 games / 4,729 releases / 5,036 candidates / 307 explicit overlaps.
- JSON 20,387,781 bytes; gzip 1,459,910; compressed SHA-256 `df42eb52fc05d17c85c88f9f6794cb4f622b8f3293a9bb73ae8dd6db34c1c72f`.
- Deterministic rebuild PASS; host open 1,289.338 ms; traced peak 70,542,108 bytes; scale LOWER_THAN_NES.
- Fourteen adjacent collections are explicitly inventoried: translation/specialty deferred, MD+/MSU-MD/Mode 1 CD separate media, 32X separate system.
- Android integration/public activation/device: NOT STARTED / NO / NOT RUN.
- Genesis Plus GX configuration is unchanged; release pinned-core provisioning remains NOT READY.
- Physical launch PASS: 240p Test Suite v1.30 rendered with image, audio and input through `genesis_plus_gx_libretro_android.so`; Streets of Rage 1/2/3 launched independently. `launchValidated=true` records only actual launch evidence. BEST EMULATOR WINS, 6-button, PAL, saves, public/OZ certification and production licensing/provisioning remain open.
- Reader performance remains YELLOW-ACCEPTABLE: baseline 284,323 KiB PSS, post-load 347,027 KiB, delta 62,704 KiB (~61.2 MiB), observed wait ~1.059 s; no crash, ANR or OOM.

Genesis Android catalog evidence:
- 01C supersedes historical launch semantics: Genesis remains catalog-available and acquisition is host-validated, but `launchValidated=false` until real device/content evidence exists.
- Official 240p Test Suite provenance is GPL v2; the author's release evidence identifies Mega Drive/Genesis v1.30 (2024-08-21).
- Exact one-candidate plan selected MiNERVA infohash `cf45a4f757253f0c44931d353cbc95bc3c6ef4f9`, file index 7; ROMgi/failover NO.
- Selective host acquisition produced the exact 82,432-byte ZIP (SHA-256 `2099544016d8d7a6c30c7450cc121466b7e34e9af8cdf64ea6e326deb06e6ffd`).
- Shared preparation produced the sole 262,144-byte `.md` member (SHA-256 `6d7dd3ae921d294bdc64d1733ff9cc82cdc996d716b68408c0fe38371b77e5b8`) and traversal rejection is covered.
- Genesis acquisition is enabled only after exact source-plan, archive and persistent-library contracts passed. Device/launch/core provisioning remain NOT RUN.
- 01C focused 16/16 and full JVM 146/146 PASS with zero skipped; Kotlin/AndroidTest compilation, debug APK and diff-check PASS.
- 01A frozen at `64d7e7672a45f762c89b150ccd7c0dcaa6c2697a`.
- Exact 1,459,910-byte Genesis shard packaged and APK-verified with SHA-256 `df42eb52fc05d17c85c88f9f6794cb4f622b8f3293a9bb73ae8dd6db34c1c72f`.
- Shared Sega → Mega Drive / Genesis → SOURCE_INDEX route; no new Activity/database/downloader or Room mirror. Genesis acquisition is now host-validated/enabled and cannot fall through to ROMgi; SNES/NES remain enabled.
- 1,918 games / 4,729 releases / 5,036 candidates verified; exact 240p v1.30 Program identity resolves metadata-only.
- Focused 25/25 and JVM 142/142 PASS; Kotlin/AndroidTest compilation and debug APK PASS.
- Public activation/physical launch/core provisioning/device: NO / NO / NO / NOT RUN.
- BEST EMULATOR WINS and Genesis Plus GX license/production provisioning remain open gates; 32X and optical/specialty media remain separate/deferred.

NES closure evidence:
- Generic status derives the selected system and real shard count: SNES 2,505; NES 4,415.
- `sourceIndexAvailable` is independent from `launchValidated`; NES is now true only because physical Oz → Mesen → playable-content evidence exists. Public certification is not inferred.
- Exact core ID is `mesen_libretro_android.so` in registry/profile/projection. Debug provisioning resolves the current official ARM64 buildbot artifact (HTTP 200 on 2026-08-24) and direct runtime path is `${applicationInfo.dataDir}/cores/<core>`.
- 240p Test Suite and Super Mario Bros. 3 downloaded/prepared/played with audio and input; no ROMgi substitution, failover, crash, ANR or OOM.
- Reader performance is YELLOW-ACCEPTABLE: Oz PSS 222,889 KiB, but delta UNKNOWN; a 2.1-second main-thread warning requires monitoring.
- Release pinned-core catalog remains empty; release auto-provisioning is not ready.
- SNES remains frozen; Genesis REVIEW ONLY; DOWNLOAD-1B.3c PAUSED.

Primary scope:
- persistent multiple-source candidates;
- automatic source failover;
- Internet Archive resilience;
- source-specific recoverable partials;
- safe HTTP resume with persisted validators and If-Range safety;
- bounded retry semantics, including Retry-After handling;
- torrent candidate identity and selected torrent-file metadata, without durable torrent partial recovery;
- Wi-Fi/cellular handoff;
- multidisc checkpoint/resume and aggregate progress truth;
- foreground/background survival, essential telemetry and complete Build/Test/Interaction/Device evidence.

## NEXT

1. Complete `GBA-CATALOG-01C.IMPLEMENTATION-01.REPAIR-01` behavioral evidence before any remote protection.
2. Keep `acquisitionEnabled=false`, `launchValidated=false` and non-public state until independent Gates pass.
3. Keep the canonical order: GBA → Neo Geo/CPS/Arcade → N64 → Dreamcast → PS1 → Saturn.

## RISKS

1. Source failover is not accepted until recovery is proven.
2. DOWNLOAD-1B must not claim torrent fast-resume or durable torrent partial recovery; that work belongs to backlog Gate DOWNLOAD-1C.
3. Network handoff requires real-device validation.
4. ROM fuzzy identity can confuse sequels or editions.
5. External emulator launch contracts remain unverified.
6. mGBA's current libretro sensor bridge conflicts with the older Libretro feature table; gyro/tilt/light/rumble and partial link behavior require physical evidence.

## LAST CLOSED

- `GOVERNANCE-02`: `PASS / CLOSED`
- `DOWNLOAD-1A`: `PASS / CLOSED`

<!-- OZ_DASH_GOVERNANCE_V3 -->
## Governance V3

Status: ACTIVE

Continuity hardening:
- Remote WIP checkpoints: REQUIRED
- AI/provider portability: REQUIRED
- Environment handoff sync: REQUIRED
- Approved visual artifacts in Git: REQUIRED
- Independent Git backup: REQUIRED


<!-- OZ_UX_MODE_01_DASHBOARD_V1 -->
### UX-MODE-01 — Portable / Console Modes

**Status:** BACKLOG / PRODUCT DECISION APPROVED

**Portable Mode**
- Portrait.
- Touch-first.
- Management-oriented.
- Primary for downloads, search/import, collection maintenance, artwork and settings.

**Console Mode**
- Landscape.
- Immersive.
- Controller-first.
- Primary for Home, Library, Collections, Saga Detail, Game Detail and Continue / Resume.

**Architecture**
- Same domain/data/download state for both modes.
- No duplicated download, library, collection or persistence engines.
- Presentation and interaction shells may differ.

**Activation policy**
- Ask when entering landscape.
- Auto-enable in landscape.
- Never auto-enable.
- Manual “Enter Console Mode” remains available.

**Mandatory future gate**
- Mode Continuity: rotation/mode change must preserve active download task ID,
  bytes, pause checkpoint, selected game and navigation state without duplicate operations.

**Next**
- Do not implement until the current functional gates are closed.
- Portrait mockups remain Portable Mode specifications.
- Approved horizontal mockups become Console Mode specifications.


<!-- OZ_VISUAL_SPEC_V1_DASHBOARD -->
### OZ VISUAL SPEC V1

**Status:** APPROVED / DESIGN FREEZE / IMPLEMENTATION PENDING

Canonical path:
`docs/design/mockups/oz-visual-spec-v1/`

Coverage:
- Portable Mode portrait: 8 Dark + 8 Light surfaces
- Console Mode landscape: 8 Dark + 8 Light surfaces
- Total: 32 versioned mockups

Governance:
- Role 14: Console Interface Art Director
- Role 15: Console Interaction & Motion Designer

Current decision:
- Preserve specification in Git.
- Do not resume visual implementation until the active functional work is closed.
- When resumed, use Preview-first Visual Parity rather than direct APK iteration.

<!-- BACKLOG_SNAPSHOT_20260820 -->
## Backlog snapshot — 2026-08-20

This section is **non-active backlog** and does not replace the current Gate.

### P0 — Finish canonical Download work
- Reconcile DOWNLOAD-1B documentation against current code/Git/device evidence.
- Close remaining Download hardening only after evidence is explicit.
- Complete source candidates, source-isolated partials, deterministic failover, HTTP validator safety, Internet Archive resilience and foreground/background evidence.
- Preserve already validated resumable HTTP/network handoff, multidisc checkpoint/resume and progress-accounting behavior.

### DOWNLOAD-1B — Approved remaining source-resilience sequence

- `1B.3a` — transport-neutral logical-object/source-plan model, persistent ordered candidates, candidate-owned partial identity and schema-4 backward compatibility. No failover behavior yet.
- `1B.3b` — HTTP validator persistence, If-Range safety, candidate-specific partial storage and typed transfer results/failures. Preserve same-source retry behavior.
- `1B.3c` — deterministic candidate failover, retry/failover classification, Retry-After handling, multidisc failover integration, diagnostics and complete validation.

1B.3a final validation (2026-08-21): **PASS**. Source-plan/schema-5 implementation, schema-4 compatibility, transport-neutral logical identity and selected-source invariants are validated. On Xiaomi 14T / Android 15 / API 35, filtered `RomDownloadTaskPersistenceTest` started 2 tests, passed 2 and failed 0 (`BUILD SUCCESSFUL`). Manual D1 startup, D2 library, D3 local launch, D4 new download, D5 pause/process persistence and D6 preserved-byte resume passed. D7 IA HTTP 500 exhausted bounded same-source retries, then manual Resume reused the 78.6 MB partial and completed Silent Hill at 292.4 MB / 292.4 MB; remaining retry/failover hardening belongs to 1B.3c. D8 was triaged as an existing clean-install multidisc reconstruction gap: external M3U/CHD files remained, reselecting SAF does not rebuild app-private Room/PersistentRomLibrary state, and the 1B.3a diff did not change discovery. DOWNLOAD-1B remains active/not PASS; 1B.3b Safe Candidate-Specific HTTP Resume is next.

1B.3b implementation checkpoint (2026-08-21; superseded by final validation below). Schema 6 persists candidate-owned HTTP representation recovery while schema 4/5 decode without invented validators; candidate-isolated paths, strong ETag/RFC-qualified Last-Modified `If-Range`, strict 200/206/416 decisions, legacy-partial quarantine, selected-candidate-only progress and additive typed HTTP failures are implemented without candidate advancement. The provisional validation state is superseded by the approved checkpoint below.

1B.3b human-review correction and final validation (2026-08-22): **PASS — APPROVED AND FROZEN**. HTTP 200 replacement is generation-safe: preserve old bytes, create/fsync an empty active target, durably switch validator metadata, then write the new body; crash-boundary filesystem tests cover both sides of the metadata switch. BIOS transfers retain BIOS logical identity but persist recovery/progress under the parent task because no standalone `romgi-bios-*` task exists. `unsafeLegacyPartial` remains sticky while any unrelated legacy/quarantined artifact may exist and is no longer cleared by persisting one recovery. Human Review Correction PASS; Physical Device Validation PASS on Xiaomi 14T / Android 15 / API 35 with 3 executed, 3 passed, 0 failed and 0 skipped. Final evidence: focused JVM 21/21, full JVM 100/100, AndroidTest compilation PASS, `assembleDebug` PASS and `git diff --check` PASS. DOWNLOAD-1B remains active/not PASS; DOWNLOAD-1B.3c is PAUSED; `SOURCE-MINERVA-01A` is the next planned engineering slice. `.unsafe-*` cleanup is deferred technical debt outside this closure.

Separate future defects: `LIBRARY-RECOVERY-01` reconstruct Room/PersistentRomLibrary state by parsing and validating existing relative SAF M3U/disc sets after reinstall; `ARTWORK-SETTINGS-01` propagate a newly persisted SteamGridDB key without leaving settings; `SETTINGS-UX-01` synchronize artwork preference and Compose selection across `LazyColumn` disposal/recomposition. D2 slowness remains `PERF-CATALOG-01`.

### DOWNLOAD-1C — Advanced Torrent Durability

Status: **BACKLOG — NOT ACTIVE**

Scope:
- persistent torrent session storage;
- libtorrent fast-resume data;
- torrent process-death byte recovery;
- torrent-specific lifecycle hardening;
- torrent-specific advanced telemetry.

Boundary: DOWNLOAD-1B may persist torrent candidate identity and selected torrent-file metadata and may fail over from an interrupted or exhausted torrent candidate. DOWNLOAD-1B must not claim durable torrent partial recovery or libtorrent fast-resume support.

### P1 — Product onboarding and personalization
- FIRST-RUN-01 Setup & Taste Onboarding.
- Automatic RetroArch readiness/install handoff for non-technical users.
- Favorite consoles + searchable favorite-game survey.
- Taste Profile V1.
- Initial personalized collection seeding.

### P1 — Collections / discovery
- Taste Engine.
- Saga/franchise detection using canonical identity.
- Console-specific collections.
- Global search/add-any-game flow.
- Explainable recommendations.

### P1 — Visual production
- Implement approved Portable/Console Visual Spec V1 through Preview → Visual Parity → integration.
- Dark/light token parity.
- Controller-first Console Mode interaction.
- Artwork-selection quality/parity.

### P2 — Continuity features
- Oz Journey / Game Progress using trustworthy signals.
- Oz Quick Resume / Continue from Oz based on supported RetroArch save-state integration.
- Silent checkpoint fast-path UX for already-prepared multidisc content.

### P2 — Quality/performance debt
- Catalog/collections performance.
- Notification telemetry/progress truth.
- Internet Archive tester/login resilience.
- Missing artwork/cover handling.
- PS1 performance validation.
- Dreamcast/Flycast crash investigation.
- 3DS integration/core issue investigation.
- 16 KB page-size compatibility audit before modern release requirements demand it.

### P3 — Platform/brand expansion
- Windows edition architecture/product plan.
- BRAND-01 research and final product-name decision.

<!-- PERF_CATALOG_COLLECTIONS_DASHBOARD_20260820 -->
### P1 — PERF-CATALOG-01 — Instant Library / Incremental Sync
Status: BACKLOG — HIGH PRIORITY

Observed: routine app startup can stall for minutes while catalog/collections synchronize even though they should already be loaded/persisted.

Required:
- instrument startup and sync;
- prove the actual bottleneck;
- render persisted valid state first;
- avoid unconditional full reindex/recalculation;
- move refresh to incremental/background behavior when architecture permits;
- validate cold/warm/routine launch separately;
- physical-device evidence before PASS.

Governance: Performance Engineer + Architect + Android + Data/Storage + QA, using OBSERVABILITY_DIAGNOSTICS_PROTOCOL. No speculative broad optimization.

<!-- EMU_PROVISION_01_DASHBOARD_20260820 -->
### P1 — EMU-PROVISION-01 — Zero-Touch Emulation Provisioning
Status: BACKLOG

Tester evidence:
- current Oz apparently already offers a RetroArch download/install path;
- exact implementation is not yet audited;
- a fresh RetroArch install still required the tester to manually download a core.

Next when activated:
1. audit existing RetroArch install/download implementation;
2. preserve it if valid;
3. define OZ CORE POLICY V1 from current official/core evidence;
4. provision required cores from selected systems without exposing ABI/core choices to novice users;
5. preserve external-emulator routing;
6. validate first-launch success on device.

Do not duplicate existing RetroArch installation logic before code inspection.

<!-- GAME_DETAIL_SHORTLIST_DASHBOARD_20260820 -->
### GAME-INTERACTION-01 — Game Detail visual direction
Status: DESIGN SHORTLIST

Finalists:
- Hero Shelf
- Saga Navigator

Reference retained:
- Command Deck

Evidence:
`docs/design/game-detail/2026-08-20-concept-review/`

Next:
one controlled Hero Shelf vs Saga Navigator comparison.
Do not begin Compose implementation before explicit visual decision.

## SOURCE-MINERVA-01A — Direct Nintendo Provider Feasibility Spike

Status (2026-08-22): **PASS / FROZEN — FEASIBILITY PASS / MINERVA_DIRECT_RECOMMENDED_WITH_CONDITIONS**. DOWNLOAD-1B.3b stays PASS/frozen; DOWNLOAD-1B.3c stays PAUSED. MiNERVA basic GET surfaces required no authentication. SNES exact resolution selected the GPL-2.0-or-later `240p Test Suite (World) (v1.061) (NTSC) (Program) (Aftermarket) (Unl).zip`: 113,992 bytes, SHA-256 `b91452606f5959b55309343676e60e49ffbd1edc0c9679c1772f274b98460d5a`, infohash `811e19de482dc7769e1efdee9f04e3eef5f51ad5`, index 6 of 232. Final Xiaomi proof: catalog 51,101 ms, metadata 3,361 ms, first peer 2,046 ms, first byte 3,382 ms, 11 connected peers, 113,992 bytes in 4,049 ms, hash PASS; priority evidence was one active target and 231 ignored files. Metadata held 51 trackers; successful tracker responses are UNKNOWN. Dashboard 44 seeders/6 leechers were timestamped 2026-04-14 and are stale, not connected-peer counts. MiNERVA's data plane is viable; its slow live lookup is unsuitable as the production Android control plane. Next approved slice: `SOURCE-MINERVA-01B.1` for a CI-built signed/versioned index, last-known-good fallback, freshness and health gates.

## SOURCE-MINERVA-01B.1 — Oz Source Index v1

Status (2026-08-23): **PASS / FROZEN**. The reviewed Source Index v1 schema/builder foundation and stable ReleaseId correction are checkpoint-approved. JSON.gz remains a provisional format pending a production-scale SNES benchmark; Room/download/user state are unchanged. Stable ReleaseId is always `GameId + canonicalReleaseKey`: upgrading `PROVISIONAL_METADATA_KEY → VERIFIED_CONTENT` or enriching CRC32/SHA1/SHA256 evidence cannot change it. Current MiNERVA ZIP remains `DISTRIBUTED_ARTIFACT` + `WRAPPED`; validated raw ROMs may be `ROM_CONTENT` + `CONTENT_IDENTICAL`. Builder 16/16, reader 4/4, full JVM 112/112, Kotlin/AndroidTest compilation, assembleDebug and diff-check PASS. CandidateId, mutable hints and manifest `NOT_EVALUATED` semantics remain unchanged. SOURCE-MINERVA-01A stays PASS/frozen and DOWNLOAD-1B.3c stays PAUSED.

## SOURCE-MINERVA-01B.2A — Nintendo Coverage Census

Status (2026-08-23): **PASS / FROZEN** on `feature/minerva-nintendo-census`; the reviewed metadata-only census is checkpoint-approved, 01B.1 remains PASS/frozen and DOWNLOAD-1B.3c remains PAUSED. Live run: 147 discovered Nintendo-related collections, 143,617 file records, 40,002 cross-collection filename overlaps, 148 requests, 105,981,268 metadata bytes, 54,147 ms; no ROM/torrent payload or device operation. Core totals include NES 23,813, FDS 833, SNES-family 9,111, N64 3,472, N64DD 47, GB 3,486, GBC 3,677, GBA 8,739, Virtual Boy 182, NDS 21,940 and Pokémon Mini 107. NES header variants and N64 byte-order variants require normalization. Hash availability/scope is `UNKNOWN`, not zero; dashboard telemetry is stale at `2026-04-14T00:11:35Z`. Census tests 8/8 PASS.

## SOURCE-MINERVA-01B.2A-GLOBAL — MiNERVA Global Coverage Census

Status (2026-08-23): **PASS / FROZEN** on `feature/minerva-global-census`; 01B.1 and Nintendo 01B.2A remain PASS/frozen, DOWNLOAD-1B.3c PAUSED. One TLS-verified 260,854-byte snapshot (SHA-256 `5c329ded8eed3c27dc7f99171ca0092b56838bb556c07c67a348f60d914eddc9`) retained all 1,050 torrents across 10 actual groups and represented 416,271,444,782,693 payload bytes. Internal telemetry remains stale at `2026-04-14T00:11:35Z` and is snapshot-only. Normalization identified 46 known platform-family rows plus explicit UNKNOWN; 739 ambiguous aggregate collections were not guessed. PRIORITY_A route gaps: WonderSwan/Color, C64, Amiga, MSX/MSX2 and Neo Geo Pocket/Color. Targeted PRIORITY_B: FBNeo, SNES, Genesis, Saturn and PS1. Global + Nintendo tests 16/16 and Source Index builder tests 16/16 PASS; no deep scan, payload or device operation. Frozen `SUPPORTED_NOW` wording is configuration awareness, not Oz product certification.

## OZ Platform Qualification Gate v1 + SOURCE-MINERVA-01B.2B

Status (2026-08-23): **HOST BENCHMARK PASS / FROZEN** on `feature/platform-qualification-v1`; Global Census remains PASS/frozen at `5204819207353d05a98cbf47385c8bba77e2c629`, DOWNLOAD-1B.3c remains PAUSED. Permanent rule: **BEST EMULATOR WINS** and catalog presence != product certification. Independent catalog/emulation/certification states and the OZ-EQS v1 hard-gate/Golden Game Suite governance are implemented without changing public UI. SNES is `SNES_QUALIFICATION_PASS` only for Source Index benchmarking based on repository launch evidence plus current official Libretro docs/repository/Android arm64 buildbot; it is not full product certification.

Host-only benchmark scope: primary No-Intro SNES (4,642 files) + Aftermarket (232); all specialty/private/translation/RA/Satellaview/Sufami sets excluded. Hardened result: 4,874 files -> 2,505 games / 4,764 releases / 4,874 candidates, 110 explicit provisional overlap warnings, no fabricated content hashes, deterministic rebuild PASS. Build 1,326.26 ms; JSON 20,247,331 bytes; gzip 1,477,037; manifest 988; Python open 896.454 ms; first exact candidate lookup 0.0038 ms; 10,000 lookups 21.338 ms; measured peak 69,961,882 bytes. `FORMAT_HOST_FAVORS_JSON_GZ`; format remains provisional pending a separately authorized Android benchmark. Phone benchmark: NOT AUTHORIZED / NOT RUN.

## SOURCE-CLASSIFICATION-FOUNDATION-01

Status (2026-08-23): **PASS / FROZEN**. Provider-neutral release facets preserve region, language, publication, modification, translation, exclusivity and provenance independently for all systems without changing stable IDs. Region never implies exclusivity/language; provider collection labels remain evidence. Hash hardening accepts `UNKNOWN` without digest and rejects missing claimed, malformed, unscoped, or contradictory evidence. Validation: Platform 3/3, Global+Nintendo 17/17, Builder+Classification+SNES 31/31 PASS; no Android/Kotlin or device operation.

## SOURCE-MINERVA-01B.2C — Android Source Index format benchmark

Status (2026-08-23): **METHODOLOGY SUPERSEDED FOR MEMORY DECISION**. Original timing/integrity evidence remains historical; its heap/PSS/GC combined the diagnostic representation and a second production-reader load. R1 below replaces only the memory decision.

## SOURCE-MINERVA-01B.2C-R1 — isolated production-reader benchmark

Status (2026-08-23): **PASS / FROZEN — JSON_GZ_ANDROID_VIABLE_NEEDS_READER_OPTIMIZATION**. JSON.gz is **PROVISIONALLY ACCEPTED FOR THE SNES VERTICAL SLICE**; `SOURCE-READER-OPT-01` is BACKLOG before multi-system scale/format finalization. Exact frozen shard and Xiaomi 14T Android 15/API 35; 1 warm-up + 5 measured fresh processes for each independent path. Production used only asset stream → real `OzSourceIndexReader`; diagnostic ByteArray/JSONObject/HashMap memory was absent and excluded. Production min/median/max: open 928.948/942.916/957.590 ms; heap delta 83,705,856/118,222,848/118,226,944 bytes (~112.7 MiB median, YELLOW); PSS delta 79,719/100,206/102,636 KiB (~97.9 MiB median, GREEN); ART GC 4/4/6 collections and 41/44/65 ms; no crash/ANR/OOM/exception. Observed peak is `UNKNOWN`. Platform `snes` PASS; counts and CandidateId lookup are not exposed by the production API. `DIAGNOSTIC_ONLY` medians: decompress 84.043 ms, parse 589.917, prototype index 7.384, first lookup 0.005769, 10,000 lookups 14.208; counts 2,505/4,764/4,874 and first/middle/last IDs PASS. Original PSS delta ~150.2 MiB was contamination; corrected production PSS is GREEN, while retained heap keeps the aggregate result YELLOW. Format remains unfrozen beyond this controlled slice. This is not universal device-tier or full SNES certification; 01B.2B/classification remain frozen and DOWNLOAD-1B.3c PAUSED.

## SNES-END-TO-END-01 — first playable SNES vertical slice

Status (2026-08-23): **FIRST_PLAY_PASS / FROZEN** on `feature/snes-first-play-v1`. The real catalog dialog exposes the controlled `240p Test Suite` entry and resolves exact Oz Game/Release/Candidate identities from the frozen 1,477,037-byte SNES shard (SHA-256 `320e6f424f2ae5b5f01c58efc495d80f16a25d8106a74bbd3934cbc2935d897e`). The selected MiNERVA candidate is infohash `811e19de482dc7769e1efdee9f04e3eef5f51ad5`, file index 6, exact Aftermarket v1.061 path and 113,992 expected artifact bytes. It is the only candidate; no failover or ROMgi substitution occurs. Source Index hash evidence remains `UNKNOWN` rather than fabricating ROM/artifact evidence.

The existing scheduler/task store, selective libtorrent transport, 1B.3b recovery, SNES ZIP preparation, PersistentRomLibrary and verified `snes9x_libretro_android.so` launch contract remain the runtime path. The production reader uses an exact identity scan without a duplicate HashMap. Host evidence: focused tests PASS, full JVM 116/116 with 0 failed/skipped, Kotlin and AndroidTest compilation PASS, `assembleDebug` PASS, asset identity PASS and diff-check PASS. Xiaomi 14T / Android 15/API 35 evidence: Source Index labels/actions visible, exact durable candidate persisted, transfer SUCCESS, `.sfc` prepared and registered, RetroArch opened, Snes9x core loaded, and the user confirmed a playable screen. No failover, ROMgi substitution, crash, ANR or OOM occurred; final `assembleDebug`, `adb install -r` and package verification PASS with data preserved. DOWNLOAD-1B.3c remains PAUSED; SNES OZ-EQS certification, global MiNERVA activation and `SOURCE-READER-OPT-01` remain out of scope.

## SNES-CATALOG-01 — generic SNES discovery and play

Status (2026-08-24): **SNES_ROUTING_PHYSICAL_PASS / FROZEN** on `feature/snes-catalog-v1`. The existing Global Catalog now projects the frozen controlled SNES shard—exactly **2,505 logical games / 4,764 releases / 4,874 candidates**—without bulk-inserting remote discovery records into Room. A process-scoped reader opens once off the main thread and supplies bounded paging, normalized search, release enumeration and exact candidates from the retained model. Room continues to own local/downloaded state, favorites, collections, history and artwork overrides; exact-ID overlays/materialization occur only when a user opens a game, with no fuzzy-title merge.

The one-game bridge/startup seed is retired, while the frozen 240p projection ID remains a compatibility alias for existing user/download state. Multiple meaningful releases use the current CRT dialog and retain region/revision/facet evidence; one explicit release maps to exactly one deterministic candidate and the proven scheduler → selective MiNERVA torrent → preparation → PersistentRomLibrary → Snes9x path. No failover, candidate advancement or ROMgi substitution was added; DOWNLOAD-1B.3c remains PAUSED. Host regressions cover controlled counts, search, real USA/Japan/multi-release samples, identity stability, user-state overlay and exact FIRST_PLAY coordinates. Physical routing and generic download/play validation passed in 01F. This is not complete worldwide SNES coverage, universal downloadability, translations, global MiNERVA certification or full SNES OZ-EQS certification. JSON.gz remains provisional and `SOURCE-READER-OPT-01` remains backlog.

Pre-device semantic hardening keeps publication class exclusively in ReleaseFacets and assigns Source Index projections the legitimate neutral genre `Sin clasificar`; `Retail`/`Aftermarket` are never fabricated as genres. SNES filter mode exposes only supported region and name A–Z/Z–A controls. Genre, year/history/favorite sorts, ROMgi/local/download/artwork status and redundant platform controls are hidden and sanitized rather than silently ignored; exact Room overlays remain unchanged. The current release `AlertDialog` is `TEMPORARY_FUNCTIONAL_UI`; `SNES-RELEASE-SELECTOR-UI-01` is **BACKLOG BEFORE VISUAL FREEZE** for an Oz/CRT-native selector.

Physical 01D result (2026-08-24): **BLOCKED_AT_PROJECTION**. The active Compose catalog exposed `PlatformFamily` only, while the concrete `CatalogSystem` spinner/backend lived in `GlobalCatalogActivity`; SNES could not become `selectedSystem`, so visible Room records routed to ROMgi and no Source Index selection occurred. 01E repaired reachability with reusable family → qualified-system navigation, currently `Nintendo → Super Nintendo`, and exact backend routing. Physical 01F then passed on Xiaomi 14T / Android 15 / API 35: the user reached the generic SNES catalog and successfully downloaded and played Donkey Kong and Yoshi's Island. RetroArch was active and rendering; no new ROMgi handoff, crash, ANR or OOM was identified. Final build, state-preserving reinstall and package verification passed. 1B.3c remains PAUSED.

## MULTI-SYSTEM EXPANSION ROADMAP

Binding order: **SNES → NES → Genesis / Mega Drive → GBA → Neo Geo / CPS1 / CPS2 / Arcade → N64 → Dreamcast → PS1 → Saturn**. Research may precede implementation, but implementation, activation, public eligibility and checkpoint freeze may not jump this order without Dream Team evidence and explicit Product Owner approval. Catalog presence != Oz support; every system begins with a visible Dream Team review. Next Gate: **NES EXPANSION REVIEW**.

GBA-CATALOG-01B.IMPLEMENTATION-R1: **PASS / CONTROLLED**. GBA Source Index now
uses the 2,368/3,962/4,021 candidate asset with SHA-256
`6ff70a3763fbbd6871c7e9a29135161c84deec4144d470ff5bb993d519d2af50`; routing is
enabled, acquisition and launch validation remain disabled, and no physical,
OZ-EQS or public certification is claimed.

GBA-CATALOG-01B.REPRO-01: **PASS / VERSIONED**. A portable tracked contract pins
the Base + Aftermarket + T-En identities, builder/schema and expected
2,368/3,962/4,021 output with SHA-256 `6ff70a3763fbbd6871c7e9a29135161c84deec4144d470ff5bb993d519d2af50`;
source torrents and experimental shards remain untracked.

GBA-CATALOG-01B.COVERAGE-04: **PASS / CONTROLLED**. GBA `[T-En...]` metadata
separates Translation Releases without creating Games; dry run 70 Games / 97
Releases / 97 Candidates with zero original-release or candidate collisions.
Language, status and provenance remain UNKNOWN; T-En is not promoted. SNES,
NES and Genesis remain FROZEN.

## NES-CATALOG-01 — host implementation

Status (2026-08-24): **NES_PHYSICAL_PASS / FROZEN PENDING CHECKPOINT** on `feature/nes-catalog-v1`; Dream Team verdict **CONDITIONAL GO**. Scope: NES Headered base + Headered Aftermarket only; Headerless, Private, RA, source-code, Family BASIC/Network and FDS excluded.

Deterministic result: 10,057 files, 4,415 games, 8,894 releases, 10,057 candidates, 1,163 explicit overlaps; JSON 41,425,152 bytes, gzip 2,870,162 bytes, SHA-256 `db19ada0838f5a30f4ce941d24d6b497528263e78881ad6a14a037a54f9957a8`. Shared `CartridgeSourceIndexCatalog` serves SNES and NES; Room remains overlay-only and failover disabled. Physical PASS covered 240p Test Suite and Super Mario Bros. 3 through Mesen with audio/input and stable rendering. `launchValidated=true` records that evidence only; OZ-EQS/public certification remain open. Reader performance is YELLOW-ACCEPTABLE and pinned release core provisioning remains pending. `SOURCE-READER-OPT-01` stays backlog and `DOWNLOAD-1B.3c` stays PAUSED.
## GBA-CATALOG-01C.IMPLEMENTATION-01 — Multi-Local Release Storage

Status (2026-08-27): **PASS / FOUNDATION**. Versioned schema-3 local-release
records support multiple Source Index Releases per Game using a stable
`GameId + ReleaseId` key digest. Legacy `rom.<gameId>` records remain readable;
Candidate provenance is persisted through the download plan and local record.
Room retains only the explicit active content URI. Replacement/deletion never
switches Releases silently. GBA acquisition remains **FALSE**; no download,
device, emulation or certification is claimed. SNES, NES and Genesis remain
**FROZEN**.

## 2026-08-31 — Claude Code migration baseline

The Product Owner selected **Claude Code — Engineering** as the repository
executor for the next phase; Oz Codex CLI is the prior executor for this
baseline. Forge remains governance/orchestration and independent assurance
remains separate. `DOWNLOAD-UX-START-01` is **CANCELLED_BY_PRODUCT_OWNER** and
is not a GBA blocker. GBA host behavioral and targeted physical SAF invariants
are PASS; real process-death evidence remains **NOT RUN**. ROMgi remains an
acquisition/research input, not a runtime or canonical dependency. Future direct
source acquisition is separate work requiring official platform-specific
evidence.

# CrtGameDialog — fix de `showSourceOptions()` (ítem SAF ausente)

**Fecha del trabajo:** 2026-09-05
**Rama/HEAD de partida:** `feature/gba-catalog-v1` @ `618f4d8`
**Gate:** R2, fix mínimo autorizado explícitamente por Product Owner en el mismo turno.

## Qué se pidió

Oz reportó una contradicción: al tocar `sourceButton` en el diálogo de un juego
GBA proyectado desde Source Index (Zelda ALttP&FourSwords), solo aparecía el
mensaje informativo "Fuente de juego" / "Oz RetroRom busca primero tu
biblioteca local...", sin el ítem "Elegir ROM o archivo propio" que el código
en `CrtGameDialog.kt:275-293` agrega incondicionalmente. Se pidió investigar
sin tocar el dispositivo, luego confirmar con evidencia física, y finalmente
aplicar un fix mínimo autorizado.

## Investigación (sin tocar el dispositivo)

- Lectura completa de `showSourceOptions()`: `.setItems(options)` siempre
  incluye `"Elegir ROM o archivo propio"` como `options[0]`; no hay ninguna
  rama que dependa de `game.source`, `SourceIndexCatalogs.forProjection(...)`
  ni `UnresolvedGameSourceRoute`. La hipótesis de "bloqueado por diseño para
  Source Index" quedó descartada por lectura directa del código.
- `git status`/`git log` confirmaron working tree limpio y que
  `CrtGameDialog.kt` no cambia desde `ff6435e` (2026-08-25) — mucho antes del
  build instalado.

## Verificación de versión del APK instalado

```
versionCode=60018  versionName=0.6.0-beta.4
lastUpdateTime=2026-09-03 00:16:52
```
Comparado contra HEAD (`618f4d8`, 2026-09-03 01:11:22) y el commit previo
(`0a63714`, 00:15:02): ninguno de los commits entre la instalación y HEAD tocó
código fuente (solo `.claude/` y evidencia `docs/`). Conclusión: el APK
instalado no estaba desactualizado respecto al código relevante — la
contradicción no se explicaba por un build viejo.

## Captura en vivo (dispositivo, con autorización de Oz)

- `uiautomator dump` + screenshot del diálogo abierto confirmaron
  exactamente lo reportado: solo título + mensaje, sin ítems ni botones.
- El XML volcado mostró que `contentPanel` solo contiene `scrollView` +
  `message` + `textSpacerNoButtons`, y que **no existe ningún nodo
  `customPanel`/`ListView`** en el árbol — ni visible ni oculto. Esto refutó
  la hipótesis de "ítem oculto por tema" (altura cero / color de fondo): el
  nodo del ítem no estaba presente en absoluto en el árbol renderizado.
- El logcat de esa ventana no mostró excepciones de la app, pero sí una única
  línea `AbsListViewStubImpl: cl.gakuto.kizuna, version: 0.6.0-beta.4` — señal
  de que un `ListView` (el que respalda `.setItems()`) sí llegó a
  instanciarse en el proceso durante ese tap, aunque no apareciera en el
  árbol final. Esto apuntó a un conflicto de layout de `AlertController`
  entre `.setMessage()` y `.setItems()` en el mismo builder, más que a un
  `setItems()` nunca invocado.

## Fix aplicado (R2, autorizado)

`app/src/main/java/cl/gakuto/kizuna/ui/CrtGameDialog.kt:275-293`, único
cambio: se eliminó la llamada `.setMessage("Oz RetroRom busca primero tu
biblioteca local...")` del `AlertDialog.Builder` en `showSourceOptions()`.
Se conservó intacta la lógica `hasSource`/`which==0`/`which==1`, la rama
"Olvidar ROM propia y volver a ROMgi", y el resto del archivo.

## Validaciones

- Búsqueda de tests unitarios/instrumentados que cubran `CrtGameDialog`,
  `showSourceOptions` o `sourceButton` en `test/` y `androidTest/`:
  **no existe ninguno** (confirmado por búsqueda, no asumido). No hay test
  que correr.
- `./gradlew.bat compileDebugKotlin` → `BUILD SUCCESSFUL` (solo warnings
  preexistentes en otros archivos).
- `./gradlew.bat assembleDebug` → `BUILD SUCCESSFUL`.
- `adb install -r app-debug.apk` → `Success`; `lastUpdateTime` pasó a
  `2026-09-05 00:13:23`, confirmando el reinstall.

## Confirmación física

Oz reabrió el diálogo y tocó `sourceButton` en el dispositivo real
(Xiaomi 14T): **"funcionó, ahí está el ítem"**. El logcat post-fix registró
el lanzamiento de `com.google.android.documentsui.picker.PickActivity` desde
la tarea de `cl.gakuto.kizuna/.ui.MainActivity` inmediatamente después —
coherente con `which == 0 -> sourcePicker.launch(...)` disparando el picker
SAF del sistema (`ActivityResultContracts.OpenDocument()`), y con el cierre
posterior de esa activity. Evidencia corroborante independiente del reporte
verbal de Oz.

## Incidente menor durante la sesión (no bloqueante)

La primera captura de logcat post-reinstall se cayó (exit code 255) por un
cambio de `transport_id` de adb (`2` → `3`), es decir una reenumeración USB
del dispositivo — no un problema de permisos, buffer, ni proceso colgado.
Se reinició limpiamente (append al mismo archivo) y quedó corriendo sin
errores.

## Evidencia producida (no versionada — archivos locales de sesión)

- Screenshot y `uiautomator dump` del diálogo roto (pre-fix), en el
  scratchpad de sesión.
- `logcat_capture.log` (pre-fix, ~31 MB) y `logcat_capture_postfix.log`
  (post-fix), en el scratchpad de sesión — no copiados al repo.

## Abierto / no resuelto

- No se determinó la causa raíz exacta a nivel de `AlertController`/AOSP de
  por qué combinar `.setMessage()` + `.setItems()` suprime el `customPanel`
  en este tema — el fix (quitar el mensaje) resuelve el síntoma y fue
  confirmado físicamente, pero no se abrió un ticket ni se documentó el
  comportamiento de `AlertController` como conocido para evitar que se
  reintroduzca en otro diálogo del archivo (hay otros `AlertDialog.Builder`
  con `.setMessage()` + `.setItems()`/`.setMultiChoiceItems()` en el mismo
  archivo — no se auditaron como parte de este fix por ser fuera del
  alcance mínimo autorizado).
- No se hizo commit — el diff queda en el working tree para revisión de
  Oz Forge — Command antes de stagearlo.

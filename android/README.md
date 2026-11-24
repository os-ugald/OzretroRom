# Oz RetroRom Android 0.6.0 Beta 4

Iteración pública ARM64 de Oz RetroRom centrada en búsqueda global, resolución regional de ROMgi, filtros separados y selección segura de motores de emulación.

## Identificación Android

```text
applicationId = cl.gakuto.kizuna
versionCode = 60018
versionName = 0.6.0-beta.4
ABI = arm64-v8a
minSdk = 26
targetSdk = 36
```

El `applicationId` no cambia, por lo que se conservan la biblioteca local, permisos SAF, preferencias y el comando de integración con iiSU.

## Cambios principales


### Beta 4 Fix 12 · colecciones compactas para mando

- Colecciones adapta su cabecera según el **alto disponible**: en ventanas bajas/horizontales usa un encabezado compacto en una sola franja.
- El buscador deja de ser un campo de texto permanente en modo mando; se abre sólo al elegir **Buscar**, evitando que el D-pad quede atrapado editando texto.
- La rejilla de juegos recibe el foco inicial en modo de baja altura para entrar a jugar directamente con control.
- ROMs, SteamGridDB e iiSU permanecen disponibles como acciones compactas sin sacrificar el área de juegos.
- El layout alto/vertical tradicional se conserva cuando hay espacio suficiente.


### Beta 4 Fix 11 · primer arranque PS1 tras descarga

- La copia persistente de ROM intenta sincronizar físicamente los bytes antes de declarar la descarga terminada.
- El autoarranque de PS1 espera una ventana breve de estabilidad del archivo SAF/ruta compartida antes de abrir RetroArch.
- Si la ruta derivada todavía no está estable, Oz crea una copia pública temporal compatible desde la URI SAF y lanza esa copia, manteniendo la biblioteca persistente como fuente de verdad.
- El aviso de RetroAchievements sin credenciales no se trata como fallo de carga: es un mensaje independiente de RetroArch.
- Se añaden diagnósticos `PS1_FRESH_LAUNCH_BARRIER_*` para distinguir escritura, estabilización y fallback de compatibilidad.

### Beta 4 Fix 10 · identidad visual

- Inicio elimina los cuatro accesos grandes duplicados; conserva un dock persistente compacto de cinco destinos.
- El dock muestra icono + etiqueta sólo en el destino activo e iconos propios en los demás.
- El hero genérico se sustituye por un banner de marca compacto `OZ RETROROM · ARCADE MARQUEE // 90s REMIX`.
- Configuración incorpora selector persistente Oscuro / Claro y los layouts XML tienen recursos `values-night`.
- Oxanium se usa como tipografía display mediante Google Downloadable Fonts cuando el proveedor está disponible; cuerpo SansSerif y metadatos Monospace conservan legibilidad/fallback.
- Paletas oscuras y claras se validan con contrastes de texto principales >= 4.5:1.


- ROMgi resuelve por título/región exactos, título limpio, canonicalización, alias, puntuación tolerante, reordenamiento, fallback regional y coincidencia difusa final.
- Las coincidencias ambiguas requieren selección; nunca se descarga silenciosamente una variante dudosa.
- Inicio Compose convertido en dashboard tipo consola; el buscador global queda concentrado en Catálogo.
- Panel independiente de regiones, plataformas, géneros, origen/estado y orden sobre toda la base Room.
- Nueva sección **Motores y emuladores** con Automático, RetroArch, Externo y Preguntar al jugar.
- Integraciones directas verificadas para PPSSPP y melonDS; el resto se valida en el dispositivo antes de crear un intent.
- RetroArch conserva variantes, consulta de cores, sideload seguro, lanzamiento nativo y puente iiSU.
- FinalBurn Neo conserva nombre exacto de romset, ZIP real, BIOS y bloqueo de descriptores virtuales.
- Pantalla de progreso Beta 2 preservada y conectada al motor externo seleccionado.
- Auth V2 abre Internet Archive dentro de un WebView restringido a `archive.org`, intercambia la sesión legítima por IA-S3 y cifra claves/cookies con Android Keystore; un 401/403 aislado no borra credenciales.
- Las descargas ROMgi se guardan en una biblioteca ROMs persistente elegida mediante SAF; al volver a jugar se reutiliza el archivo local y no se descarga de nuevo.
- Colecciones, detalle, Catálogo y Biblioteca disponen de infraestructura Compose/StateFlow/Paging progresiva, manteniendo interoperabilidad con Views durante la migración.
- Fix 6.2 fija Coil `3.2.0` para conservar compatibilidad binaria con Kotlin `2.1.21` en Termux/SDK 36.
- Fix 6.3 corrige dos errores de compilación Compose detectados por el build real: llamada explícita `onClick` en `OzGameCover` y resolución de `LocalContext.current` fuera del bloque `remember`.
- Beta 4 Fix 2 blinda el puente iiSU: la ROM persistente de Oz tiene prioridad sobre fuentes recordadas, se eliminan descriptores virtuales guardados por iteraciones anteriores y ningún descriptor `.sfc/.gba/...` puede llegar a un core RetroArch como si fuese contenido real.
- Beta 4 Fix 3 corrige la semántica regional `World` (global, no región estricta) y extrae ZIPs de cartucho a su ROM nativa antes de persistir/lanzar, evitando entregar archivos `.zip` directamente a cores como Snes9x.

## Compilar debug en Termux

```bash
cd ~/Oz-RetroRom-Android-0.6.0-Beta4-Source
chmod 700 gradlew 02_COMPILAR_TERMUX.sh
./02_COMPILAR_TERMUX.sh
```

Resultado esperado:

```text
Download/Oz-RetroRom-0.6.0-beta.4-debug-arm64.apk
```

## Release segura

```bash
./03_CREAR_KEYSTORE_TERMUX.sh
export OZ_KEYSTORE_PATH="$HOME/keystores/oz-retrorom-release.jks"
export OZ_KEY_ALIAS="ozretrorom"
chmod 700 04_COMPILAR_RELEASE_SEGURO_TERMUX.sh
./04_COMPILAR_RELEASE_SEGURO_TERMUX.sh
```

El script ejecuta validación estática, `assembleRelease`, `zipalign -P 16`, firma v1/v2/v3, verificación y SHA-256. No compartas el `.jks`, contraseñas ni el APK sin firmar.


## Beta 4 Fix 9 — sesión vertical compacta

La pantalla de descarga vertical conserva el modo sin scroll del Fix 8, pero ahora presenta el icono del juego a la izquierda y la información crítica a la derecha: plataforma, título, fuente, estado, porcentaje y tiempo. La barra y las métricas quedan debajo y las acciones de recuperación permanecen ancladas al pie. El icono continúa priorizando la caché SteamGridDB mediante `ArtworkResolver.tileCandidates()`.

## iiSU en Beta 4

Oz RetroRom sigue siendo la fuente de verdad de las ROMs: SAF + Room conservan los juegos descargados/importados. iiSU es un frontend opcional y recibe únicamente accesos virtuales regenerables; esos accesos también pueden representar juegos todavía no descargados para que Oz los resuelva bajo demanda.

El comando de puente permanece intacto:

```text
cl.gakuto.kizuna/cl.gakuto.kizuna.launch.SessionActivity -a cl.gakuto.kizuna.action.PLAY -e ROM "%ROM%" --activity-clear-top
```

En `Configuración → Emulación e integraciones → iiSU` seleccionas una carpeta de puente una sola vez. Después puedes publicar una colección completa desde `Colecciones → Publicar en iiSU`. Los archivos generados son descriptores pequeños y regenerables; no contienen ROMs. Si un acceso representa una ROM ausente, iiSU devuelve el lanzamiento a Oz y Oz ejecuta su flujo normal de descarga antes de abrir el emulador. Las rutas de puente, assets y destinos por sistema quedan bajo `Opciones avanzadas`.

Consulta `docs/BETA4_IISU_LIBRARY_ARCHITECTURE.md`, `docs/RELEASE_NOTES_0.6.0_BETA4.md` y `docs/VALIDATION_REPORT_0.6.0_BETA4.md`.

## Documentación Beta 3

- `docs/RESEARCH_REPORT_0.6.0_BETA3.md`
- `docs/EMULATOR_SYSTEM_TABLE_0.6.0_BETA3.md`
- `docs/VALIDATION_REPORT_0.6.0_BETA3.md`
- `docs/TERMUX_BUILD_0.6.0_BETA3.md`
- `docs/USER_MANUAL_0.6.0_BETA3.md`
- `docs/RELEASE_NOTES_0.6.0_BETA3.md`

## Fix6.7
Corrección de compilación del fallback de cover arts: el índice de candidatos usa `mutableStateOf(0)`, compatible con los imports actuales.

## Fix6.8
Las descargas remotas ya no dependen de la pantalla de sesión: Android 14+ usa User-Initiated Data Transfer y Android 8–13 usa un servicio `dataSync` visible. Incluye progreso por notificación, reanudación HTTP Range, borrado de parciales y eliminación segura de ROMs descargadas por Oz. El staging privado se conserva solo mientras haga falta para reanudar y se limpia después de persistir la ROM en la biblioteca SAF.

## Fix6.12
Pantalla de preparación rediseñada con identidad Oz, carátulas de Favoritos con resolución Libretro/fallback de candidatos, Configuración separada entre Biblioteca y Emulación/Integraciones, prioridad del perfil recomendado de core (SwanStation para PS1) y descarga segura del paquete oficial de overlays de Libretro a una carpeta SAF compartida. La autoasignación de overlays por core no se implementa al no existir un contrato Android externo oficial y estable verificado.

### Fix6.13 — pulido previo a validación completa
- Emulación e integraciones usa un panel Compose dedicado: RetroArch, cores, BIOS, overlays, motores e iiSU.
- Catálogo/ROMgi/región/transporte quedan separados en Catálogo y descargas.
- Las colecciones pueden sincronizar artwork explícitamente con el índice oficial Libretro/libretro-thumbnails sin alterar membresías ni IDs.



## Beta 4 Fix 6

- Las sesiones iniciadas desde iiSU usan orientación horizontal (`sensorLandscape`) durante preparación, descarga y preflight.
- Las sesiones iniciadas directamente desde Oz conservan la interfaz vertical.
- Se elimina el bloqueo `android:screenOrientation="portrait"` de `SessionActivity`.
- El origen iiSU se conserva en las descargas en segundo plano para que al reabrir desde la notificación la sesión siga horizontal y continúe retornando al launcher externo.
- No cambia Room ni la biblioteca ROM.

## Beta 4 Fix 5

- ROMgi admite aliases conservadores para diferencias entre títulos curados y nombres históricos de catálogo.
- Si iiSU inició la sesión, cerrar RetroArch devuelve el foco al launcher anterior en vez de mostrar la tarea de Oz.
- El lanzamiento iniciado directamente desde Oz conserva su regreso normal a Oz.

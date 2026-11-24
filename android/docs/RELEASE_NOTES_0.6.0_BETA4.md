# Oz RetroRom 0.6.0 Beta 4

## Identificación

- `applicationId`: `cl.gakuto.kizuna`
- `versionCode`: `60016`
- `versionName`: `0.6.0-beta.4`
- Room: esquema 6, sin migración destructiva nueva
- ABI: `arm64-v8a`

## Cambio principal

Beta 4 consolida **Oz RetroRom como biblioteca principal**. iiSU pasa a ser una integración opcional que consume la biblioteca existente de Oz mediante descriptores de puente, sin duplicar ROMs.

### Biblioteca y almacenamiento

Se elimina de la configuración normal la antigua "Biblioteca virtual iiSU". La pantalla queda centrada en ROMs persistentes, carpetas por sistema y artwork.

### iiSU

Nuevo flujo simplificado:

- Activar / desactivar integración.
- Sincronizar biblioteca Oz.
- Estado de última sincronización.
- Opciones avanzadas para destinos de puente, assets y pack de integración.

La sincronización solo incluye juegos locales (`contentUri` presente). Las colecciones también exportan únicamente sus miembros locales.

### Compatibilidad

Se conservan las claves internas históricas de las carpetas virtuales para que configuraciones previas puedan seguir funcionando. En Beta 4 esas rutas se presentan como **destinos del puente iiSU**.

## Funcionalidad conservada de Beta 3 / Fix6.15

Se mantienen Internet Archive/IA-S3, ROMgi, descarga y reanudación en segundo plano, biblioteca SAF persistente, Room 6, carátulas y sincronización de artwork, RetroArch + CONFIGFILE, cores, BIOS, overlays oficiales, motores externos, iiSU bridge y compilación Termux ARM64/SDK 36/Java 17.



## Beta 4 Fix 12 — colecciones compactas y navegación D-pad

- La pantalla de detalle de colección decide su densidad usando el alto real de la ventana (`maxHeight < 620.dp`), no una orientación fija.
- En baja altura el header se reduce a una franja con volver, nombre/contadores y acciones ROMs/SGDB/iiSU.
- Buscar y Filtros pasan a una toolbar secundaria compacta; Buscar abre un diálogo sólo cuando se solicita.
- La rejilla ocupa el espacio restante con `Modifier.weight(1f)` y solicita foco inicial sobre el primer juego para mando/D-pad.
- Se conserva la presentación completa en ventanas con altura suficiente.

## Beta 4 Fix 11 — primer lanzamiento PS1 tras descarga

- Se añade una barrera de estabilización para el autoarranque de PS1 después de una descarga nueva.
- La ruta compartida debe permanecer legible y con el tamaño esperado durante varias comprobaciones antes de entregarse a RetroArch.
- Si la ruta no se estabiliza a tiempo, Oz crea una copia temporal pública desde la URI SAF persistente y la usa sólo para esa sesión.
- La persistencia intenta `FileDescriptor.sync()` antes de marcar la ROM como guardada, con fallback SAF genérico si el proveedor no ofrece descriptor escribible.
- Se incorporan eventos diagnósticos `PS1_FRESH_LAUNCH_BARRIER_*`.
- El aviso de RetroAchievements sin credenciales permanece bajo control de RetroArch y no se usa como criterio de fallo de ROM.

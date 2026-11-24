# Oz RetroRom 0.6.0 Beta 3 — Fix6.7 Collection Artwork Compile Fix

## Causa raíz
Fix6.6 introdujo un estado entero para recorrer candidatos de artwork con `mutableIntStateOf(0)`, pero `OzApp.kt` no importaba `androidx.compose.runtime.mutableIntStateOf`. El compilador reportó `Unresolved reference 'mutableIntStateOf'` y tres errores en cascada de inferencia/comparación.

## Corrección
Se reemplazó únicamente:

```kotlin
var artworkIndex by remember(candidates) { mutableIntStateOf(0) }
```

por:

```kotlin
var artworkIndex by remember(candidates) { mutableStateOf(0) }
```

`mutableStateOf` ya estaba importado y es suficiente para este pequeño índice de fallback de imágenes.

## Conservado
- Fix6.5 Internet Archive / downloader.
- Tipografía compacta del menú central de Fix6.6.
- Resolución Libretro y fallback de cover arts de Fix6.6.
- Room schema 6 y migraciones no destructivas.
- SAF, RetroArch, iiSU y colecciones.
- `applicationId = cl.gakuto.kizuna`.
- `versionName = 0.6.0-beta.3`, `versionCode = 60003`.

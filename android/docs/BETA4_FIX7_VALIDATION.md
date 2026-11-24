# Validación — Oz RetroRom 0.6.0 Beta 4 Fix 7

Fecha: 2026-08-08

## Resultado

### PASS — contrato específico Fix 7

`scripts/test_beta4_fix7_compact_icons.py`

Verifica:

- versionCode 60011 / versionName 0.6.0-beta.4;
- modo `COMPACT_ICON` como predeterminado;
- persistencia de API key, ID SteamGridDB e icono cacheado;
- `ArtworkResolver.tileCandidates()` con fallback;
- API v2 / Bearer / búsqueda / endpoint de iconos;
- caché privada y ausencia de peticiones SteamGridDB desde composables;
- rejilla 104 dp, rail 108 dp, colección 228 dp y targets de navegación de 48 dp;
- configuración visual y conservación de sincronización Libretro clásica.

### PASS — validador general del proyecto

`scripts/validate_project.py`

Resultado: `VALIDACIÓN ESTÁTICA CORRECTA — Oz RetroRom 0.6.0 Beta 4 (1103 comprobaciones)`.

Catálogo validado por el script: 44 sistemas, 192 juegos curados, 18 colecciones.

### PASS — sintaxis Kotlin

Los archivos Kotlin modificados se pasaron por el frontend de `kotlinc`; no se detectaron errores `expecting` ni `unexpected`.

## Build Gradle

Se intentó `./gradlew :app:compileDebugKotlin --no-daemon`. El build no pudo iniciar porque este entorno no puede resolver `raw.githubusercontent.com`, y el wrapper del proyecto descarga `gradle-wrapper.jar` desde ese host cuando no está incluido en el ZIP.

El fallo ocurre antes de cargar Gradle o compilar fuentes; por tanto no es un error Kotlin/Android observado. Debe ejecutarse `:app:testDebugUnitTest` y `:app:assembleDebug` en Termux/Android Studio o un entorno con acceso al bootstrap verificado del wrapper.

## Sin cambios destructivos

No hay migración Room ni cambios de esquema. No se modifican ROMgi, Internet Archive, SAF, formato de biblioteca, RetroArch, cores, BIOS, overlays, motores externos ni contrato de descriptores iiSU.

# Oz RetroRom 0.6.0 Beta 3 — Fix6.1 Compose Build Fix

## Causa raíz

El build real de Fix6 en Termux ARM64 alcanzó `:app:checkDebugAarMetadata` y falló porque `androidx.lifecycle` 2.11.0 exige `compileSdk` 37 y una versión de Android Gradle Plugin posterior a la usada por Oz.

## Corrección

Se mantienen `compileSdk = 36`, `targetSdk = 36`, AGP 8.10.1, Java 17 y la arquitectura Compose existente. Se alinean los artefactos Lifecycle usados por la app en 2.10.0:

- `androidx.lifecycle:lifecycle-runtime-compose:2.10.0`
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.10.0`

No se modifica la versión pública (`0.6.0-beta.3`), el `applicationId`, Room, las migraciones, SAF, ROMgi, Internet Archive, RetroArch ni iiSU.

## Validación pendiente

Repetir en Termux ARM64:

```bash
./02_COMPILAR_TERMUX.sh 2>&1 | tee build-fix6.1.txt
```

La validación estática local se ejecuta antes del empaquetado. La compilación Android completa debe confirmarse en el entorno Termux que dispone de SDK 36 y `aapt2` ARM64.

# Oz RetroRom 0.6.0 Beta 3 — Fix 6.2 Kotlin/Coil Build Fix

Esta corrección incremental mantiene `versionCode = 60003` y `versionName = "0.6.0-beta.3"`.

## Causa raíz confirmada en Termux

Fix 6.1 superó `:app:checkDebugAarMetadata`, `:app:processDebugResources` y `:app:kspDebugKotlin`, pero falló en `:app:compileDebugKotlin`. El log real mostró que Gradle resolvió `org.jetbrains.kotlin:kotlin-stdlib:2.4.0`, mientras el proyecto usa Kotlin/Compose Compiler `2.1.21`. El compilador 2.1 rechazó metadata binaria 2.4.

La dependencia que introducía Kotlin 2.4.0 era Coil `3.5.0`. Coil 3.5.0 fue publicado con Kotlin 2.4.0.

## Corrección

- `io.coil-kt.coil3:coil-compose`: `3.5.0` → `3.2.0`.
- `io.coil-kt.coil3:coil-network-okhttp`: `3.5.0` → `3.2.0`.
- Se conserva Kotlin `2.1.21`, KSP `2.1.21-2.0.1`, AGP `8.10.1`, Gradle `8.11.1`, Java 17 y compile/target SDK 36.
- No se modifica Room, sus migraciones, applicationId, datos de usuario, SAF, ROMgi, RetroArch, iiSU ni Auth V2.
- `scripts/validate_project.py` ahora valida Coil 3.2.0 y rechaza accidentalmente reintroducir Coil 3.5.0 en este toolchain.

Coil 3.2.0 fue compilado con Kotlin 2.1.20, compatible con el compilador Kotlin 2.1.21 de Oz.

## Sobre los mensajes de aapt2

El log de Termux muestra numerosos mensajes `No package ID 7f found for resource ID ...`, pero `:app:processDebugResources` termina correctamente. Por tanto no son la causa del fallo actual y no se modifica aapt2 en esta corrección. Si se vuelven fatales en una etapa posterior, se tratarán con el error concreto correspondiente.

## Verificación solicitada en Termux

```bash
python scripts/validate_project.py
./02_COMPILAR_TERMUX.sh 2>&1 | tee build-fix6.2.txt
```

No se declara `BUILD SUCCESSFUL` hasta que el build real termine correctamente en Termux.

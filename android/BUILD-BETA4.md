# Oz RetroRom 0.6.0 Beta 4 — build/arquitectura

## Cambio de Beta 4

Oz RetroRom es la única biblioteca física de ROMs. iiSU queda como integración opcional y genera únicamente descriptores de puente de juegos locales.

## Compilación objetivo

- Termux ARM64
- OpenJDK 17
- Android SDK 36
- AGP 8.10.1
- Gradle 8.11.1
- Kotlin 2.1.21
- `aapt2` nativo de Termux

```bash
cd ~/Oz-RetroRom-Android-0.6.0-Beta4-Source
chmod +x gradlew *.sh
./02_COMPILAR_TERMUX.sh 2>&1 | tee build-beta4.txt
```

El APK debug esperado se copia a:

```text
Download/Oz-RetroRom-0.6.0-beta.4-debug-arm64.apk
```

## Entorno de entrega

El entorno usado para empaquetar esta fuente no es Termux: no expone `$PREFIX`, Android SDK ni Java 17. Por ello no se declara `BUILD SUCCESSFUL` ni se incluye un APK generado en ese host.

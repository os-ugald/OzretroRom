# Estado de compilación — Oz RetroRom 0.6.0 Beta 3

Fecha de ejecución: **6 de agosto de 2026**.

## Identificación verificada

```text
applicationId cl.gakuto.kizuna
versionCode 60003
versionName 0.6.0-beta.3
ABI arm64-v8a
minSdk 26
targetSdk 36
```

## Pruebas ejecutadas en este entorno

| Prueba | Resultado |
|---|---|
| `python3 scripts/validate_project.py` | Correcta: 514 comprobaciones, 44 sistemas, 192 juegos curados y 16 colecciones |
| Compilación Kotlin aislada de `TextNormalizer` + `RomgiTitleMatcher` | Correcta: Yoshi’s Island, región delimitada, alias reordenado y fallback |
| Parseo XML/JSON | Correcto, incluido dentro del validador |
| `bash -n` sobre scripts Termux/release | Correcto |
| `./gradlew :app:testDebugUnitTest --stacktrace` | No iniciado: el wrapper no pudo descargarse por fallo DNS |
| `./gradlew :app:assembleDebug --stacktrace` | No iniciado: el wrapper no pudo descargarse por fallo DNS |

## Error exacto de Gradle

Ambos comandos terminaron con código `6` antes de cargar Gradle:

```text
Downloading verified Gradle wrapper bootstrap...
curl: (6) Could not resolve host: raw.githubusercontent.com
EXIT_CODE=6
```

El entorno tampoco expone `ANDROID_HOME` ni `ANDROID_SDK_ROOT`. No se generó ni se entrega APK. Esto no es un error de compilación Kotlin/Android demostrado: es un bloqueo previo de red/SDK.

## Reproducción y corrección en Termux

1. Instalar Java 17, Android SDK Platform 36, Build Tools y `aapt2` ARM64.
2. Verificar conectividad DNS/HTTPS con `raw.githubusercontent.com`, `services.gradle.org`, `dl.google.com` y repositorios Maven.
3. Confirmar que existe `$HOME/android-sdk/platforms/android-36/android.jar`.
4. Ejecutar:

```bash
python3 scripts/validate_project.py
./gradlew :app:testDebugUnitTest --stacktrace
./gradlew :app:assembleDebug --stacktrace
```

Solo una ejecución exitosa de `assembleDebug` o `assembleRelease` autoriza declarar una APK compilada.

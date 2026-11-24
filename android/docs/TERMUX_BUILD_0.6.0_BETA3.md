# Compilación en Termux — Oz RetroRom 0.6.0 Beta 3

## Requisitos

```bash
pkg update
pkg install openjdk-17 aapt2 aapt curl coreutils python git
termux-setup-storage
```

Instala Android SDK Platform 36 y Build Tools actuales en `$HOME/android-sdk`. Debe existir:

```text
$HOME/android-sdk/platforms/android-36/android.jar
```

## Preparación

No compiles directamente desde `/storage/emulated/0/Download`, porque ese montaje puede tener `noexec`:

```bash
cp -r ~/storage/downloads/Oz-RetroRom-Android-0.6.0-Beta3 ~/
cd ~/Oz-RetroRom-Android-0.6.0-Beta3
chmod 700 gradlew 02_COMPILAR_TERMUX.sh 03_CREAR_KEYSTORE_TERMUX.sh 04_COMPILAR_RELEASE_SEGURO_TERMUX.sh
```

## Debug

```bash
./02_COMPILAR_TERMUX.sh
```

El script:

1. verifica Java, SDK 36 y `aapt2` ARM;
2. descarga el wrapper Gradle 8.11.1 solo si falta;
3. valida su SHA-256;
4. ejecuta `scripts/validate_project.py`;
5. ejecuta `./gradlew :app:assembleDebug`;
6. copia el APK y su SHA-256 a Descargas.

## Release

Crea una vez el keystore y conserva siempre el mismo archivo para actualizaciones:

```bash
./03_CREAR_KEYSTORE_TERMUX.sh
export OZ_KEYSTORE_PATH="$HOME/keystores/oz-retrorom-release.jks"
export OZ_KEY_ALIAS="ozretrorom"
./04_COMPILAR_RELEASE_SEGURO_TERMUX.sh
```

La release falla deliberadamente si faltan validación, SDK, firma, `zipalign -P 16` o `apksigner`.

## Diagnóstico

```bash
python scripts/validate_project.py
./gradlew :app:testDebugUnitTest --stacktrace
./gradlew :app:assembleDebug --stacktrace
```

No publiques un APK si alguno de esos comandos falla. Conserva el texto exacto del error y corrige antes de firmar.

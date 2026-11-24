# Compilar Oz RetroRom Beta 4 Fix 9 en Termux

## Si Termux ya está preparado

Copia el ZIP a Descargas, abre Termux y ejecuta:

```bash
termux-setup-storage
pkg update -y
pkg install -y openjdk-17 aapt2 aapt curl coreutils python git unzip
cd ~
rm -rf Oz-RetroRom-Android-0.6.0-Beta4-Fix9
unzip ~/storage/downloads/Oz-RetroRom-Android-0.6.0-Beta4-Fix9-PortraitSession-Source.zip
mv Oz-RetroRom-Android-0.6.0-Beta4-Fix9-PortraitSession Oz-RetroRom-Android-0.6.0-Beta4-Fix9
cd ~/Oz-RetroRom-Android-0.6.0-Beta4-Fix9
chmod 700 gradlew 02_COMPILAR_TERMUX.sh
./02_COMPILAR_TERMUX.sh
```

El script requiere Android SDK Platform 36 en:

```text
$HOME/android-sdk/platforms/android-36/android.jar
```

Al terminar correctamente, el APK debug se copia a:

```text
~/storage/downloads/Oz-RetroRom-0.6.0-beta.4-debug-arm64.apk
```

## Validación manual antes de compilar

```bash
cd ~/Oz-RetroRom-Android-0.6.0-Beta4-Fix9
python scripts/test_beta4_fix9_portrait_session.py
python scripts/test_beta4_fix8_fullscreen_session.py
python scripts/test_beta4_fix7_compact_icons.py
python scripts/validate_project.py
```

## Compilación Gradle manual equivalente

```bash
cd ~/Oz-RetroRom-Android-0.6.0-Beta4-Fix9
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
AAPT2_BIN="$(command -v aapt2)"
chmod +x gradlew
./gradlew :app:assembleDebug \
  --no-daemon \
  --max-workers=1 \
  --console=plain \
  --stacktrace \
  -Pandroid.aapt2FromMavenOverride="$AAPT2_BIN"
```

APK Gradle:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release firmada

Con un keystore que debes conservar para todas las actualizaciones:

```bash
cd ~/Oz-RetroRom-Android-0.6.0-Beta4-Fix9
chmod 700 03_CREAR_KEYSTORE_TERMUX.sh 04_COMPILAR_RELEASE_SEGURO_TERMUX.sh
./03_CREAR_KEYSTORE_TERMUX.sh
export OZ_KEYSTORE_PATH="$HOME/keystores/oz-retrorom-release.jks"
export OZ_KEY_ALIAS="ozretrorom"
./04_COMPILAR_RELEASE_SEGURO_TERMUX.sh
```

No vuelvas a crear el keystore si ya tienes el usado para versiones anteriores de Oz RetroRom.

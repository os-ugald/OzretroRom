#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

WRAPPER="$ROOT/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_SHA="2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_NAME="Oz-RetroRom-0.6.0-beta.4-debug-arm64.apk"

say() { printf '\n[Oz RetroRom] %s\n' "$*"; }
fail() { printf '\n[ERROR] %s\n' "$*" >&2; exit 1; }

command -v java >/dev/null 2>&1 || fail "Java no está instalado. Ejecuta: pkg install openjdk-17"
command -v sha256sum >/dev/null 2>&1 || fail "Falta sha256sum. Ejecuta: pkg install coreutils"

export JAVA_HOME="${JAVA_HOME:-$PREFIX/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

[ -f "$ANDROID_HOME/platforms/android-36/android.jar" ] || fail "No existe Android SDK Platform 36 en $ANDROID_HOME"
AAPT2_BIN="$(command -v aapt2 || true)"
[ -n "$AAPT2_BIN" ] || fail "No se encontró aapt2 ARM. Ejecuta: pkg install aapt2"

mkdir -p "$(dirname "$WRAPPER")"
if [ ! -f "$WRAPPER" ]; then
    say "Descargando el Gradle Wrapper 8.11.1…"
    TMP="$WRAPPER.tmp"
    rm -f "$TMP"
    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 --connect-timeout 20 "$WRAPPER_URL" -o "$TMP"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$TMP" "$WRAPPER_URL"
    else
        fail "Instala curl o wget para descargar gradle-wrapper.jar"
    fi
    printf '%s  %s\n' "$WRAPPER_SHA" "$TMP" | sha256sum -c - >/dev/null || {
        rm -f "$TMP"
        fail "El checksum de gradle-wrapper.jar no coincide"
    }
    mv "$TMP" "$WRAPPER"
fi

ACTUAL_SHA="$(sha256sum "$WRAPPER" | awk '{print $1}')"
[ "$ACTUAL_SHA" = "$WRAPPER_SHA" ] || fail "gradle-wrapper.jar no pasó la verificación SHA-256"

# ANDROID_HOME/ANDROID_SDK_ROOT are sufficient; do not write a personal sdk.dir into distributed source.
rm -f local.properties
chmod +x gradlew

say "Ejecutando validación estática…"
if command -v python >/dev/null 2>&1; then
    python scripts/validate_project.py
elif command -v python3 >/dev/null 2>&1; then
    python3 scripts/validate_project.py
else
    say "Python no está instalado; continúo con la compilación Gradle"
fi

if command -v termux-wake-lock >/dev/null 2>&1; then
    termux-wake-lock
    trap 'termux-wake-unlock >/dev/null 2>&1 || true' EXIT
fi

say "Compilando APK debug…"
./gradlew :app:assembleDebug \
    --no-daemon \
    --max-workers=1 \
    --console=plain \
    --stacktrace \
    -Pandroid.aapt2FromMavenOverride="$AAPT2_BIN"

[ -f "$APK" ] || fail "Gradle terminó, pero no se encontró $APK"

DEST_DIR="$HOME/storage/downloads"
if [ -d "$DEST_DIR" ]; then
    cp -f "$APK" "$DEST_DIR/$OUTPUT_NAME"
    sha256sum "$DEST_DIR/$OUTPUT_NAME" > "$DEST_DIR/$OUTPUT_NAME.sha256"
    say "APK copiada a Download/$OUTPUT_NAME"
    ls -lh "$DEST_DIR/$OUTPUT_NAME"
    cat "$DEST_DIR/$OUTPUT_NAME.sha256"
else
    say "APK creada en: $APK"
    say "Ejecuta termux-setup-storage para copiarla automáticamente a Descargas."
fi

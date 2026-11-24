#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

case "$ROOT" in
  /storage/*|*/storage/downloads/*)
    echo "[ERROR] Copia el proyecto a \$HOME antes de compilar; Android monta Download con noexec."
    exit 1
    ;;
esac

: "${OZ_KEYSTORE_PATH:?Define OZ_KEYSTORE_PATH con la ruta privada del archivo .jks}"
: "${OZ_KEY_ALIAS:?Define OZ_KEY_ALIAS con el alias de firma}"
[[ -f "$OZ_KEYSTORE_PATH" ]] || { echo "[ERROR] No existe $OZ_KEYSTORE_PATH"; exit 1; }

export JAVA_HOME="${JAVA_HOME:-$PREFIX/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

[[ -x ./gradlew ]] || chmod 700 ./gradlew

PYTHON="$(command -v python3 || command -v python || true)"
[[ -n "$PYTHON" ]] || { echo "[ERROR] Instala Python en Termux: pkg install python"; exit 1; }

if ! "$PYTHON" "$ROOT/scripts/validate_project.py"; then
  echo "[ERROR] La validación estática falló."
  exit 1
fi

TRUSTED_COUNT="$("$PYTHON" - <<'PY'
import json
from pathlib import Path
p=Path('app/src/main/assets/trusted_cores.json')
print(len(json.loads(p.read_text())['cores']))
PY
)"
if [[ "$TRUSTED_COUNT" -eq 0 ]]; then
  echo "[AVISO] trusted_cores.json está vacío."
  echo "La APK será segura, pero no instalará cores automáticamente; RetroArch deberá administrarlos."
fi

termux-wake-lock || true
trap 'termux-wake-unlock >/dev/null 2>&1 || true' EXIT

./gradlew clean :app:assembleRelease \
  --no-daemon \
  --max-workers=1 \
  --console=plain \
  --stacktrace

UNSIGNED="$ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
[[ -f "$UNSIGNED" ]] || { echo "[ERROR] No se generó $UNSIGNED"; exit 1; }

BT="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
[[ -n "$BT" ]] || { echo "[ERROR] No se encontraron Android Build Tools."; exit 1; }
APKSIGNER="$BT/apksigner"
[[ -f "$APKSIGNER" ]] || { echo "[ERROR] No se encontró apksigner en $BT"; exit 1; }
ZIPALIGN="$(command -v zipalign || true)"
[[ -n "$ZIPALIGN" ]] || { echo "[ERROR] Instala zipalign ARM en Termux (pkg install aapt)."; exit 1; }

if ! "$ZIPALIGN" -h 2>&1 | grep -q -- '-P'; then
  echo "[ERROR] Tu zipalign no admite -P 16. Actualiza los paquetes de Termux antes de publicar."
  exit 1
fi

OUT_DIR="$ROOT/app/build/outputs/apk/release"
ALIGNED="$OUT_DIR/Oz-RetroRom-0.6.0-beta.4-secure-arm64-aligned.apk"
SIGNED="$OUT_DIR/Oz-RetroRom-0.6.0-beta.4-secure-arm64.apk"
CHECKSUM="$SIGNED.sha256"

rm -f "$ALIGNED" "$SIGNED" "$CHECKSUM"
"$ZIPALIGN" -P 16 -f -v 4 "$UNSIGNED" "$ALIGNED"

# apksigner solicitará las contraseñas de forma interactiva; no se guardan en el script.
bash "$APKSIGNER" sign \
  --ks "$OZ_KEYSTORE_PATH" \
  --ks-key-alias "$OZ_KEY_ALIAS" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$SIGNED" \
  "$ALIGNED"

bash "$APKSIGNER" verify --verbose --print-certs "$SIGNED"
"$ZIPALIGN" -c -P 16 -v 4 "$SIGNED"
sha256sum "$SIGNED" | tee "$CHECKSUM"

mkdir -p "$HOME/storage/downloads"
cp "$SIGNED" "$CHECKSUM" "$HOME/storage/downloads/"

echo
echo "[LISTO] Archivos para compartir:"
echo "$HOME/storage/downloads/$(basename "$SIGNED")"
echo "$HOME/storage/downloads/$(basename "$CHECKSUM")"
echo
echo "No compartas el .jks, sus contraseñas ni app-release-unsigned.apk."

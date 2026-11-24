#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

KEYSTORE_DIR="$HOME/keystores"
KEYSTORE_PATH="$KEYSTORE_DIR/oz-retrorom-release.jks"
ALIAS="ozretrorom"

mkdir -p "$KEYSTORE_DIR"
chmod 700 "$KEYSTORE_DIR"

if [[ -e "$KEYSTORE_PATH" ]]; then
  echo "[ERROR] Ya existe: $KEYSTORE_PATH"
  echo "No se sobrescribió ninguna clave. Usa la misma clave para todas las actualizaciones."
  exit 1
fi

keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE_PATH" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000

chmod 600 "$KEYSTORE_PATH"

echo
echo "[LISTO] Clave creada fuera del proyecto:"
echo "$KEYSTORE_PATH"
echo
echo "Antes de compilar release ejecuta:"
echo "export OZ_KEYSTORE_PATH='$KEYSTORE_PATH'"
echo "export OZ_KEY_ALIAS='$ALIAS'"
echo
echo "Guarda una copia cifrada de la clave y sus contraseñas. No compartas el archivo .jks."

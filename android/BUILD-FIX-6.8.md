# Oz RetroRom 0.6.0 Beta 3 — Fix6.8 Background Downloads

## Objetivo
Evitar que una descarga de ROM dependa de `SessionActivity` y se corte al minimizar Oz RetroRom, conservar progreso reanudable y dar control explícito sobre archivos parciales y ROMs descargadas.

## Implementación
- Android 14+ (API 34+): User-Initiated Data Transfer mediante `JobScheduler` + `setUserInitiated(true)`.
- Android 8–13: `ForegroundService` de tipo `dataSync` como compatibilidad.
- Notificación persistente con progreso, nombre, bytes y velocidad.
- Notificación de finalización con `Jugar ahora` y acceso normal a Oz.
- El toque normal de la notificación NO fuerza el lanzamiento del emulador; la acción `Jugar ahora` sí solicita continuar el flujo de juego.
- HTTP Range para reanudar archivos parciales; solo se anexa cuando el servidor responde 206 y `Content-Range` coincide exactamente con el offset solicitado.
- Red del UIDT enlazada a `JobParameters.network` para las conexiones HTTP.
- Reintentos automáticos de fallos transitorios de red.
- `SessionFileStore.file()` recrea la carpeta de staging después de un borrado/reintento, corrigiendo el `ENOENT` observado en Fix6.7.
- `Borrar descarga parcial` disponible tras errores/cancelaciones.
- `Eliminar ROM descargada` disponible para ROMs que Oz guardó en la biblioteca; nunca elimina archivos importados manualmente.

## Staging temporal
El staging privado **sigue existiendo de forma intencional** en `Android/data/cl.gakuto.kizuna/files/sessions/download-…` mientras la transferencia está incompleta. Es necesario para reanudación robusta. Después de persistir correctamente la ROM en la biblioteca SAF, Oz elimina ese staging. En errores, se conserva para reanudar o se elimina solo por acción explícita del usuario.

## Seguridad y datos
- Sin migración Room nueva.
- Sin `fallbackToDestructiveMigration`.
- No se serializan credenciales de Internet Archive en el estado de descarga.
- Internet Archive continúa usando su almacenamiento cifrado/Keystore existente.
- La ROM final se escribe solo dentro de la carpeta SAF elegida por el usuario.

## Identidad y toolchain conservados
- `applicationId = cl.gakuto.kizuna`
- `versionCode = 60003`
- `versionName = 0.6.0-beta.3`
- `compileSdk = 36`
- `targetSdk = 36`
- Java 17 en Termux

## Validación
Ejecutar:

```bash
python scripts/validate_project.py
python scripts/test_fix68_background_download.py
python scripts/test_room_migration_3_4.py
python scripts/test_room_migration_5_6.py
python scripts/test_fix3_contracts.py
python scripts/test_fix4_contracts.py
python scripts/test_fix66_collection_artwork.py
```

La validación de entrega alcanza 825 comprobaciones estáticas y 29 comprobaciones específicas Fix6.8.

## Build real
El entorno de entrega no es Termux, no define `$PREFIX`, no tiene Android SDK configurado y usa Java 21. Por eso `02_COMPILAR_TERMUX.sh` se detiene antes de Gradle. Esto NO se reporta como `BUILD SUCCESSFUL`; el build final debe ejecutarse en el Termux ARM64 del dispositivo, donde las iteraciones anteriores ya compilan.

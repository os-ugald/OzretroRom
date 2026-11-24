# Fix 6.8 — Background Downloads / Resume / Notification

Base: Fix6.7.

## Problemas reproducidos
Las capturas del dispositivo mostraron:
- descarga HTTP interrumpida con `Software caused connection abort` al minimizar;
- reintento posterior con `ENOENT` dentro de `Android/data/cl.gakuto.kizuna/files/sessions/...`;
- progreso parcial de aproximadamente 198–228 MB sobre una ROM de 315.5 MB antes del corte.

## Causa funcional
La preparación remota vivía dentro del flujo de la pantalla de sesión. Fix6.8 desacopla la transferencia de la Activity y entrega su ejecución a mecanismos de background apropiados del sistema.

El `ENOENT` tenía además una causa independiente: un reintento podía borrar la carpeta de sesión y `SessionFileStore.file()` asumía que aún existía. Ahora la recrea antes de abrir el archivo.

## Arquitectura
### Android 14+
- `RomDownloadJobService`
- `JobInfo.Builder.setUserInitiated(true)`
- restricción de red
- estimación de bytes restantes
- mínimo chunk reanudable
- notificación obligatoria mediante `JobService.setNotification()`
- conexión HTTP abierta sobre `JobParameters.network`

### Android 8–13
- `RomDownloadForegroundService`
- `foregroundServiceType="dataSync"`
- `ServiceCompat.startForeground()`
- `START_REDELIVER_INTENT`

### Estado persistente
`RomDownloadTaskStore` conserva únicamente estado operativo y metadata de la fuente. No guarda claves/cookies/Authorization.

Estados:
- QUEUED
- RUNNING
- SUCCESS
- FAILED
- CANCELLED

## Reanudación HTTP
- usa `Range: bytes=<offset>-` si existe un parcial;
- solo añade al archivo con respuesta HTTP 206;
- valida que el inicio de `Content-Range` sea exactamente el offset solicitado;
- si el servidor no respeta el rango, elimina ese parcial inconsistente y reinicia de forma segura;
- si el servidor responde 200, trunca/reinicia el destino, no mezcla datos;
- valida tamaño final cuando existe total conocido.

## Ciclo de vida del archivo
1. Descarga a staging privado `sessions/download-<uuid>`.
2. Verifica la ROM.
3. Copia a la biblioteca persistente SAF elegida por el usuario.
4. Room registra `LibraryOrigin.DOWNLOADED`.
5. Solo después de persistencia correcta se elimina el staging.

Un error deja el parcial disponible para reanudación. El usuario puede borrarlo desde la sesión o desde la notificación de fallo.

## Finalización en background
Android no debe ser forzado a abrir una Activity desde background. Fix6.8 usa una notificación:
- toque normal: abre Oz sin lanzar automáticamente el emulador;
- `Jugar ahora`: abre la sesión con intención explícita de continuar y preparar el juego;
- `Abrir Oz`: vuelve a la pantalla principal.

## Borrado seguro
- `Borrar descarga parcial`: solo elimina staging incompleto.
- `Eliminar ROM descargada`: solo elimina ROM con `LibraryOrigin.DOWNLOADED`.
- archivos `IMPORTED` nunca se eliminan con esa acción.

## Validaciones de entrega
- validación estática: 825 comprobaciones;
- contrato Fix6.8: 29 comprobaciones;
- Room 3→4: datos preservados;
- Room 5→6: datos preservados;
- contratos Fix3/Fix4: OK;
- artwork Fix6.6: OK;
- XML: parse correcto;
- sin migraciones destructivas.

## Limitación de build del entorno de entrega
El contenedor no dispone del entorno Termux (`$PREFIX`) ni del Android SDK requerido. El intento de ejecutar `bash ./02_COMPILAR_TERMUX.sh` termina antes de invocar Gradle con `PREFIX: unbound variable`. No se genera APK ni se declara `BUILD SUCCESSFUL` aquí.

## Fuentes oficiales consultadas
- Android Developers — User-initiated data transfer.
- Android Developers — JobInfo.Builder / setEstimatedNetworkBytes / setMinimumNetworkChunkBytes.
- Android Developers — JobService.setNotification.
- Android Developers — Foreground service timeouts / dataSync.
- Android Developers — Notification runtime permission and background activity restrictions.

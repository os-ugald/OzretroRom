# Rebuild 8 Secure Beta

## Cambios de código

- Release ARM64 con R8 y reducción de recursos.
- `allowBackup=false`, `usesCleartextTraffic=false` y trust store del sistema.
- Exposición mínima de `SessionActivity` preservando el comando explícito de iiSU.
- Nuevo `SessionLaunchValidator`.
- Lectura limitada de descriptores JSON/ZIP.
- Nuevo `TrustedCoreCatalog` con SHA-256 de ZIP y `.so`.
- Descarga de core release bloqueada cuando el core no está fijado.
- Diagnóstico con redacción adicional.
- Scripts de keystore, generación de catálogo de cores y release firmada.

## Compatibilidad conservada

- Catálogo global y paginación.
- ROMgi HTTPS/torrent.
- RetroArch nativo.
- Core sideload seguro cuando existe entrada fijada.
- iiSU, carpetas virtuales, descriptores y sincronización.
- FinalBurn Neo y BIOS.

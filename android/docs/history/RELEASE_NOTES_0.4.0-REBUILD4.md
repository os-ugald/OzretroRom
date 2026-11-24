# Notas de versión — Oz RetroRom 0.4.0 Rebuild 4

## Objetivo

Unificar la reproducción para que **Jugar** funcione desde Oz RetroRom y desde la consola virtual de iiSU, conservando iiSU como interfaz avanzada y Oz como interfaz sencilla.

## Implementado

- `SessionActivity` acepta `oz.gameId`, URI `ozretrorom://play`, descriptor virtual o extra iiSU `ROM`.
- Custom Launch Command histórico restaurado y exportable.
- Accesos virtuales identificados por `gameId`; no se confunden con una ROM real.
- ROM real recibida desde iiSU reutilizada y recordada para futuras sesiones.
- Carpeta de ROMs global y reemplazo por sistema.
- Búsqueda recursiva acotada con prioridad de romset y coincidencia canónica.
- Soporte de extensiones reales por plataforma, incluidas CHD, GDI, CUE, M3U, RVZ, WBFS, Z64 y otras.
- Rutas del proveedor de almacenamiento externo convertidas a la ruta local que RetroArch espera.
- CUE/M3U locales no se copian aisladamente: se abre la ruta original para conservar archivos acompañantes.
- Fuentes HTTP, magnet, proveedores no convertibles y FBNeo siguen usando sesión temporal controlada.
- Detección del perfil Android para no fijar el usuario `0` en rutas internas.
- Pack de integración iiSU con README, comando y plantillas.

## Seguridad y contenido

- No se incluyen ROMs ni BIOS.
- No se generan enlaces automáticos a contenido protegido.
- Los permisos SAF pertenecen a carpetas elegidas por el usuario.
- Las copias temporales se eliminan al volver del emulador cuando la opción está activada.

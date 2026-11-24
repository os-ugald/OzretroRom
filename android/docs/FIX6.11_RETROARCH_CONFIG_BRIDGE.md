# Fix6.11 — RetroArch persistent configuration bridge

## Fuentes primarias consultadas

- RetroArch `MainMenuActivity.java` (repositorio oficial libretro/RetroArch): `finalStartup()` pasa `UserPreferences.getDefaultConfigPath(this)` a `startRetroActivity()`, y `startRetroActivity()` lo envía como `CONFIGFILE`.
- RetroArch issue #17433 (repositorio oficial): los ejemplos de lanzamiento Android externo incluyen `CONFIGFILE .../Android/data/<retroarch-package>/files/retroarch.cfg` y la ruta completa de `LIBRETRO`.
- Android `Context.createPackageContext()` y `getExternalFilesDir()` (documentación oficial): permiten resolver el contexto/ruta app-specific sin cargar código del otro paquete. Oz no intenta leer ni escribir el archivo de RetroArch.
- Libretro Docs — Directory Configuration / Configuration File: `retroarch.cfg` es la configuración global y `Save Current Configuration` guarda en la configuración activa.

## Contrato de seguridad

Oz solo pasa a RetroArch una ruta perteneciente al paquete RetroArch seleccionado. No intenta obtener contenido privado del archivo, no cambia el idioma ni fuerza Vulkan. La configuración sigue siendo propiedad de RetroArch y del usuario.

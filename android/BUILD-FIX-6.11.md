# Oz RetroRom 0.6.0 Beta 3 — Fix6.11 RetroArch Config Bridge

## Problema observado

RetroArch podía cargar la ROM y el core cuando era iniciado desde Oz, pero arrancaba con ajustes distintos a los usados al abrir RetroArch normalmente. Además mostraba `Config directory not set, cannot save configuration.` y los cambios de configuración (por ejemplo el driver Vulkan) no podían persistirse en ese flujo.

## Causa raíz

`RetroArchManager.buildDirectLaunchIntent()` reproducía casi todos los extras que el frontend Android oficial de RetroArch pasa a `RetroActivityFuture`, pero omitía `CONFIGFILE`.

El `MainMenuActivity` oficial de RetroArch llama `startRetroActivity()` con `UserPreferences.getDefaultConfigPath(this)` y esa función coloca el valor en el extra `CONFIGFILE` junto con `ROM`, `LIBRETRO`, `DATADIR`, `APK`, `SDCARD` y `EXTERNAL`.

Sin ese extra, la actividad nativa arrancaba sin la ruta de la configuración global que el usuario ya había guardado.

## Corrección

- Se añadió `defaultConfigPath(packageName)`.
- La ruta se obtiene mediante un `Context` del paquete instalado y su `getExternalFilesDir(null)`; si Android no entrega esa ruta, se usa el layout Android estándar `Android/data/<package>/files/retroarch.cfg` únicamente como string de destino.
- Oz no abre, modifica ni sobrescribe `retroarch.cfg`.
- `buildDirectLaunchIntent()` ahora envía `CONFIGFILE` a `RetroActivityFuture`.
- Se mantienen la ruta completa del core, ROM, DATADIR, APK, SDCARD y EXTERNAL.

## Resultado esperado

Un juego iniciado desde Oz debe reutilizar la misma configuración global que RetroArch usa al abrirse normalmente: idioma, video driver, shaders, controles y directorios. Si el usuario guarda Vulkan en su configuración global de RetroArch, el siguiente lanzamiento desde Oz debe heredarlo.

# Oz RetroRom 0.6.0 Beta 4 — Fix 5

## Objetivo

Corregir dos fallos observados después de estabilizar el bridge iiSU:

1. ROMgi no resolvía algunos juegos cuyo título curado de Oz difiere del título de catálogo
   (por ejemplo `Castlevania IV` frente a `Super Castlevania IV`).
2. Al cerrar RetroArch después de iniciar desde iiSU, Android podía revelar la tarea anterior de
   Oz en vez de devolver el foco al launcher.

## ROMgi: alias conservadores

Oz mantiene intacto el título visible. `RomgiTitleAliasPolicy` aporta aliases únicamente al
resolvedor ROMgi; una coincidencia exacta conserva prioridad sobre cualquier alias. La búsqueda
SQLite también incluye los aliases para que diferencias como numeración regional histórica puedan
encontrarse antes del scoring.

Aliases iniciales de catálogo:

- Castlevania IV → Super Castlevania IV
- Final Fantasy IV → Final Fantasy II
- Final Fantasy VI → Final Fantasy III
- The Legend of Zelda: A Link to the Past → Legend of Zelda, The - A Link to the Past

Además, para SNES se prueba de forma acotada el prefijo `Super` como alias secundario.

## Retorno iiSU → Oz → RetroArch → iiSU

`SessionActivity` distingue el origen mediante el contrato del bridge (`ROM` frente al `gameId`
interno de Oz). Al volver del emulador:

- inicio desde Oz: `finish()` y regreso normal a Oz;
- inicio desde iiSU: `finishAffinity()` sobre la tarea transitoria de Oz, de modo que Android
  reanude la tarea que estaba debajo, normalmente iiSU.

No se hardcodea ni se relanza el paquete de iiSU. Esto evita depender de un package/activity
externo que pueda cambiar y conserva el back stack normal de Android.

## Sin cambios destructivos

- Sin migración Room.
- Sin borrado de ROMs o BIOS.
- Sin cambios de SAF.
- Sin cambios del contrato de RetroArch.

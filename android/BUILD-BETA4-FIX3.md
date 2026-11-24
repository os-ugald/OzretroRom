# Oz RetroRom 0.6.0 Beta 4 Fix 3

Corrección focalizada en dos contratos que podían afectar lanzamientos desde iiSU/ROMgi:

1. `World` se trata como etiqueta regional amplia/global, no como requisito de encontrar un archivo literalmente marcado `(World)`. Si el usuario eligió una región preferida concreta, esa región lidera la resolución; si no, World/USA/Europe/Japan siguen un orden determinista. Un cambio World -> USA/Europe/Japan no exige confirmación manual por sí solo.
2. Las ROM de cartucho descargadas como ZIP se extraen a su formato nativo antes de persistir y lanzar. Esto evita entregar `ActRaiser (USA).zip` directamente a un core como Snes9x, cuya documentación oficial enumera `.smc/.sfc/.swc/.fig/.bs/.st` como formatos de contenido. Arcade/FBNeo conserva sus ZIP romsets intactos.

Los `.7z` que necesiten extracción se detienen con un error controlado en vez de entrar a código nativo del core. No se añadió una dependencia 7z en este fix.

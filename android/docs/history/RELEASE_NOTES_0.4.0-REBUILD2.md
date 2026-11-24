# Notas de versión — 0.4.0 Rebuild 2

## Identidad Arcade Marquee

- Se reemplazó el icono provisional por el icono oficial aprobado.
- Se incorporó el wordmark oficial horizontal en el encabezado.
- Se aplicaron bordes redondeados a encabezados, tarjetas, menú emergente y diálogo CRT.
- Se añadió tratamiento de insets para evitar solapamiento con la barra de estado.

## Funciones recuperadas desde 0.3.0

- Configuración de carpeta virtual.
- Carpeta global y reemplazo por cada uno de los 44 sistemas.
- Carpeta de assets y sincronización de bibliotecas virtuales para iiSU.
- Selección de boxart global y por juego.
- Flujo de preparación de ROM con progreso, cancelación y diagnóstico.
- Migración de preferencias, favoritos, Mi colección y juegos importados.

## Catálogo global

- Los 192 juegos curados pasan a ser una base inicial, no un límite.
- Indexación de 44 sistemas desde DAT oficiales de Libretro.
- Fuentes No-Intro para cartuchos, Redump para discos, FBNeo split para Arcade y DAT específico para ScummVM.
- Descarga y parseo progresivos en lotes de 500.
- Búsqueda unificada o filtrada por sistema.
- Conservación de títulos homónimos que representan discos, seriales o revisiones distintas.
- Cancelación segura: los datos ya insertados se conservan.

## iiSU

- Botón directo en la pantalla principal.
- Exportación de colección actual, Mi colección o catálogo indexado completo.
- Carpetas globales o por sistema.
- Descriptores con identificador de juego, plataforma, core, paquete y actividad de lanzamiento.
- Compatibilidad de entrada mediante `oz.gameId`, URI de descriptor o extra `ROM` utilizado por launchers.
- Exportación opcional de manifiestos y boxart seleccionados.

## Boxart

Modos disponibles:

- Automático.
- Named Boxarts.
- Named Titles.
- Named Snaps.
- Carpeta local.
- Archivo personalizado del dispositivo.
- URL personalizada.

Cada juego puede reemplazar la preferencia global.

## Seguridad y límites

- No se distribuyen ROMs, BIOS, credenciales ni enlaces de contenido.
- Las fuentes deben ser configuradas explícitamente por el usuario.
- Los perfiles no validados permanecen visibles y coleccionables, pero el lanzamiento directo se mantiene deshabilitado.
- El catálogo representa metadatos; no afirma que el contenido esté incluido o sea descargable.

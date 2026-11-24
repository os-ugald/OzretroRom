# Oz RetroRom 0.6.0 Beta 4 Fix 4 — iiSU Bridge Repair

Fecha de validación: 2026-08-08

## Objetivo

Corregir dos síntomas observados al usar iiSU con `Custom Launch Command`:

1. iiSU podía terminar mostrando un único juego duplicado después de publicar/sincronizar distintos conjuntos de una misma plataforma.
2. Al lanzar el acceso virtual, Oz podía rechazarlo con `La ROM indicada no existe o no puede leerse.`

## Causas raíz

- El `Custom Launch Command` exportado pasaba `%ROM%` sin comillas. Una ruta de Bridge con espacios podía fragmentarse antes de llegar a `SessionActivity`.
- `IisuExporter.exportConfigured()` eliminaba descriptores de la plataforma que no pertenecían al último lote publicado. Publicar una colección y luego una biblioteca local más pequeña podía reducir la plataforma a ese último subconjunto.
- iiSU soporta dos carpetas ROM por consola. Si la carpeta real y la carpeta Bridge están configuradas simultáneamente, un juego local puede aparecer duplicado.
- Con scoped storage, iiSU puede conocer una ruta filesystem que Oz no puede abrir como `File`, aunque el descriptor haya sido creado por Oz mediante SAF.

## Cambios

### Custom Launch Command

Contrato actualizado:

```text
cl.gakuto.kizuna/cl.gakuto.kizuna.launch.SessionActivity -a cl.gakuto.kizuna.action.PLAY -e ROM "%ROM%" --activity-clear-top
```

Las comillas protegen rutas con espacios.

### Publicación acumulativa

Publicar una colección ya no elimina accesos virtuales de otras colecciones. El Bridge se mantiene acumulativo.

La limpieza existe solo como acción explícita `Limpiar accesos generados`, y elimina únicamente archivos que pueden verificarse como descriptores creados por Oz. No elimina ROMs reales.

### Una sola raíz Bridge

La interfaz principal y la pantalla legacy dejan de ofrecer `Destino por sistema` para el Bridge. Oz usa una raíz global y crea subcarpetas por plataforma.

### Resolución segura por ID

Los archivos virtuales se llaman `<gameId>.<extensión-de-plataforma>`. `SessionActivity` extrae el `gameId` y verifica también que la extensión recibida coincida con `virtualExtension` del juego. Si coincide, resuelve el juego antes de exigir acceso raw a la ruta filesystem.

Una referencia que no coincide con un descriptor Oz continúa pasando por la validación estricta normal.

## Configuración iiSU recomendada

Para cada consola administrada por Oz:

1. Usa únicamente la subcarpeta de plataforma del Bridge como ROM folder de esa consola.
2. No mantengas simultáneamente la carpeta real de ROMs como segunda fuente de la misma consola si no quieres duplicados.
3. En `Emulator > Custom Launch Command`, pega el comando actualizado con `%ROM%` entre comillas.
4. Ejecuta `Summary > Update` en iiSU después de cambiar carpetas/emulador/comando.
5. Si vienes de Fix 3, en Oz puedes ejecutar `Limpiar accesos generados` y volver a publicar las colecciones deseadas antes de actualizar iiSU.

## Versionado

- applicationId: `cl.gakuto.kizuna`
- versionCode: `60008`
- versionName: `0.6.0-beta.4`
- Room: versión 6, sin migración nueva

## Validación estática

```text
VALIDACIÓN ESTÁTICA CORRECTA — Oz RetroRom 0.6.0 Beta 4 (1069 comprobaciones)
Sistemas: 44 · juegos curados: 192 · colecciones: 18
BETA4_FIX4_IISU_BRIDGE_REPAIR_OK checks=28
```

Además se ejecutaron y pasaron los contratos históricos de Beta 4 Fix 1, Fix 2, Fix 3, Room 3→4 y 5→6, RetroArch, background downloads, artwork y biblioteca persistente.

## Build real en el entorno de entrega

No se declara `BUILD SUCCESSFUL`.

Este entorno no es Termux y no tiene `PREFIX`, `ANDROID_HOME` ni `ANDROID_SDK_ROOT`. La ejecución directa del script no puede usar su shebang Termux y el intento mediante bash del host se detiene con `PREFIX: unbound variable` antes de Gradle.

El build definitivo debe realizarse en el Termux ARM64 del dispositivo.

## Investigación primaria

- Repositorio oficial iiSU: el proyecto se define como frontend Android y su guía indica revisar el emulador de cada consola y ejecutar `Summary > Update` tras cambiarlo.
- Release notes oficiales iiSU 0.0.6: documentan soporte de dos ROM folders por consola.

No se inventó una API interna de iiSU ni se modificó su base de datos.

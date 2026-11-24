# Oz RetroRom 0.6.0 Beta 3 — Fix 4

## Objetivo

Fix 4 cambia dos flujos sin alterar `versionCode`, `versionName` ni `applicationId`:

1. Internet Archive deja de usar WebView y abre Chrome/Custom Tabs de inmediato.
2. Las ROM descargadas por ROMgi dejan de ser archivos temporales: se guardan en una biblioteca persistente elegida por el usuario mediante SAF y se reutilizan en partidas posteriores.

## Internet Archive

- URL oficial usada: `https://archive.org/account/s3.php`.
- Chrome se prioriza cuando está instalado; otro navegador compatible es fallback.
- La Custom Tab vuelve a Oz al cerrarla/Atrás y ofrece `Volver a Oz RetroRom` en el menú.
- Archive.org no publica un OAuth/AppAuth callback Android para transferir automáticamente la sesión/cookies de Chrome a Oz.
- Oz no intenta leer cookies privadas del navegador.
- La conexión persistente se realiza una sola vez importando IA-S3 Access Key + Secret Key desde la página oficial.
- Las claves se validan por HTTPS y se cifran con Android Keystore; la contraseña no se almacena.

## Biblioteca ROMs persistente

- La primera descarga remota pide una carpeta compartida mediante `ACTION_OPEN_DOCUMENT_TREE`.
- Oz conserva el permiso SAF y usa esa carpeta para descargas futuras.
- Si se elige una carpeta global `ROMs`, Oz crea subcarpetas por plataforma.
- Es posible configurar una carpeta específica por sistema.
- Oz guarda el `contentUri` persistente en Room y un manifiesto privado con tamaño, SHA-256, región resuelta y fecha.
- La siguiente vez que se pulsa Jugar, `PersistentRomLibrary.resolve()` se ejecuta antes de ROMgi: no hay redescarga.
- Las copias en `Downloads/OzRetroRom/Sessions` quedan solo como compatibilidad cuando un emulador necesita una ruta clásica; pueden borrarse al cerrar sin tocar la ROM persistente.
- Oz nunca intenta escribir dentro de `/data/data`, `/data/user` ni `Android/data` de RetroArch.

## Colecciones

Se agrega la colección calculada **Descargados**:

- Se deriva de `games.contentUri` directamente en SQLite.
- Funciona antes de `LIMIT/OFFSET`, por lo que no filtra solo la página visible.
- No admite membresía manual: aparece/desaparece automáticamente según el archivo persistente.
- Los juegos muestran estados `Descargado`, `Local` o `Disponible`.
- Un juego descargado muestra `Jugar`; uno remoto muestra `Descargar y jugar`.

## Compatibilidad RetroArch

RetroArch estable ya dispone de SAF dentro de la aplicación. El soporte para que launchers externos pasen `content://` aparece documentado en la sección *Future* del changelog upstream, por lo que Fix 4 conserva la ruta de compatibilidad existente para builds estables que todavía requieran una ruta de archivo.

## Validación

Ejecutar:

```bash
python scripts/validate_project.py
python scripts/test_room_migration_3_4.py
python scripts/test_fix3_contracts.py
python scripts/test_fix4_contracts.py
./gradlew :app:compileDebugKotlin --no-daemon --max-workers=1 --stacktrace
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=1 --stacktrace
./gradlew :app:assembleDebug --no-daemon --max-workers=1 --stacktrace
```

Los scripts Python no sustituyen una compilación Gradle real.

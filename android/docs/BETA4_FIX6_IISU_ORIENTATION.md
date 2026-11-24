# Oz RetroRom 0.6.0 Beta 4 — Fix 6: continuidad de orientación iiSU

## Problema

`SessionActivity` estaba fijada a `android:screenOrientation="portrait"`. iiSU se utiliza en horizontal, por lo que al abrir un descriptor virtual Oz rotaba a vertical durante resolución, descarga y preflight.

## Corrección

- Se elimina el bloqueo portrait del manifiesto para `SessionActivity`.
- Una sesión originada por iiSU usa `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, permitiendo landscape normal o invertido según el sensor.
- Una sesión originada desde Oz conserva `SCREEN_ORIENTATION_PORTRAIT`.
- El `configChanges` existente conserva el estado de descarga/preflight durante cambios de orientación.
- El origen externo se persiste en `RomDownloadTaskSnapshot`; las notificaciones de una descarga iniciada desde iiSU vuelven a abrir `SessionActivity` como sesión externa, manteniendo landscape y el retorno a iiSU.
- Snapshots de descargas de versiones anteriores siguen siendo compatibles: `externalLauncher` tiene valor por defecto `false` al decodificar.

## Compatibilidad Android

En teléfonos, Android admite `screenOrientation`/`requestedOrientation`. En configuraciones de pantalla grande algunas versiones modernas pueden ignorar restricciones de orientación; el layout de sesión sigue siendo desplazable y usable en ese caso.

## Sin cambios

No se modifica Room, SAF, ROMgi, Internet Archive, RetroArch, cores, BIOS, overlays, artwork ni el formato de los descriptores iiSU.

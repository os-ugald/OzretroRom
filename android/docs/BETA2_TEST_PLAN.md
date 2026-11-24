# Plan de prueba — Oz RetroRom 0.6.0 Beta 2

## Prueba principal: torrent PlayStation

1. Abrir un juego de PS1 desde Oz RetroRom.
2. Confirmar que la fuente aparece como ROMgi.
3. Durante metadata, verificar tiempo, pares y actividad de red.
4. Confirmar que se muestra “Calculando…” y no un porcentaje falso.
5. Cuando llegue el tamaño, verificar porcentaje, MB descargados y ETA.
6. Confirmar transición a verificación y apertura de RetroArch.

## Demora y recuperación

1. Probar una fuente lenta o sin pares.
2. Tras 90 segundos de metadata, confirmar la advertencia.
3. Pulsar **Reintentar fuente** y verificar que la sesión reinicia sin cerrar la app.
4. Pulsar **Elegir archivo propio** y confirmar que sustituye la descarga activa.

## Regresión

- Lanzar desde iiSU.
- Lanzar ROM local.
- Probar HTTP seguro.
- Probar FinalBurn Neo con BIOS.
- Abrir Configuración.
- Verificar catálogo grande de PS1.

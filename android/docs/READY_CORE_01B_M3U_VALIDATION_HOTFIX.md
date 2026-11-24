# Ready Core 01B — Hotfix de validación M3U

Fecha: 2026-08-13
Incidencia física: Xiaomi 14T, APK CI `r22`, actualización sobre la instalación existente.

## Evidencia observada

- La actualización conservó biblioteca, región USA y soporte de mando.
- Internet Archive quedó conectado; la primera ventana necesitó cerrarse y abrirse nuevamente.
- Resident Evil 2 recomendado se descargó como conjunto multidisco.
- Al iniciar, Oz mostró: `El archivo preparado está vacío o incompleto.`

## Causa

`RomPreparedFileValidator` aplicaba el umbral binario de 4.096 bytes también a la playlist M3U.
Una M3U correcta contiene sólo nombres relativos y normalmente mide menos de 4 KB, por lo que el
gate previo a RetroArch rechazaba una playlist válida aunque sus discos estuvieran presentes.

## Corrección

- Los payloads binarios conservan el umbral estricto anterior.
- Una M3U pequeña se valida por estructura, no por tamaño de ROM.
- Debe contener al menos dos entradas relativas, seguras y no duplicadas.
- Cada disco referenciado debe existir junto a la playlist, ser legible y superar el umbral de
  integridad binaria.
- Se añadieron regresiones para M3U válida de Resident Evil 2, binario truncado y disco faltante.

La corrección no vuelve a descargar ROMs, no modifica la sesión de Internet Archive y no reduce la
protección aplicada a CHD, ISO ni otros payloads binarios.

## Gates

- `scripts/validate_project.py`: PASS, 1.211 comprobaciones.
- `git diff --check`: PASS.
- `:app:testDebugUnitTest` y `:app:assembleDebug`: pendientes en el equipo Android con Gradle ya
  disponible; el entorno de preparación no pudo descargar la distribución Gradle por restricción
  de red.
- Prueba física: **PASS en Xiaomi 14T**. Resident Evil 2 abrió mediante la playlist M3U,
  inició el Disco 1 y cambió al Disco 2 correctamente, sin volver a descargar los CHD.

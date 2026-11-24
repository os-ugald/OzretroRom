# Notas de versión — Oz RetroRom 0.4.0 Rebuild 7

## Nuevo

- Lanzamiento nativo desde Oz RetroRom hacia RetroArch.
- Detección de RetroArch AArch64, Universal y RA32.
- Solicitud de instalación oficial cuando no existe RetroArch.
- Reanudación automática de la sesión al volver después de instalarlo.
- Consulta del core requerido para cada sistema.
- Descarga automática de cores oficiales según la ABI del dispositivo.
- Instalación de cores mediante la actividad oficial de sideload de RetroArch.
- Carpeta externa de cores configurable mediante SAF.
- Selector de variante y estado de RetroArch en Configuración.
- Botón para preparar el core del sistema seleccionado.

## Corregido

- Lanzamiento directo que antes funcionaba de forma fiable solo a través de iiSU.
- Parámetros de inicio nativo de RetroArch: ROM, core, configuración, datos, APK y almacenamiento.
- Crash `This isn't a hierarchical URI` al procesar magnets ROMgi en Android 15.

## Conservado

- Integración completa con iiSU y su comando personalizado.
- Catálogo global, ROMgi, filtros, colecciones y artwork.
- Paginación y optimizaciones para PlayStation y catálogos grandes.
- Soporte específico de FinalBurn Neo.
- Emuladores independientes configurables para PS2 y Nintendo 3DS.

## Nota de seguridad

Android exige que el usuario confirme la instalación de RetroArch. Oz tampoco puede escribir en la carpeta privada de otra aplicación; utiliza una carpeta compartida elegida por el usuario o el mecanismo de sideload exportado por RetroArch.

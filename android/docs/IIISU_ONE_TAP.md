# iiSU + Oz RetroRom — configuración de juego con un toque

## Arquitectura

```text
Oz RetroRom > Jugar ─┐
                     ├─> SessionActivity > ROM real > motor elegido
iiSU > Jugar > %ROM% ┘
```

Oz RetroRom no sustituye al emulador. Actúa como Bridge, localiza una fuente autorizada y selecciona RetroArch o un emulador externo validado para el sistema.

## Preparación en Oz RetroRom

1. Abre **Configuración**.
2. Selecciona la carpeta global o una carpeta por sistema mediante SAF.
3. En **Motores y emuladores**, configura Automático, RetroArch, Externo o Preguntar al jugar.
4. En **Carpetas virtuales**, selecciona el destino que iiSU escaneará.
5. Vuelve a la pantalla principal y pulsa **Sincronizar iiSU**.

## Configuración en iiSU 0.0.7.4

Usa Oz RetroRom Bridge como emulador personalizado y pega:

```text
cl.gakuto.kizuna/cl.gakuto.kizuna.launch.SessionActivity -a cl.gakuto.kizuna.action.PLAY -e ROM "%ROM%" --activity-clear-top
```

También puedes exportar `Oz-RetroRom-iiSU-Integration.zip` desde Configuración. El comando personalizado es el mecanismo conservado por Beta 3.

## Qué ocurre al pulsar Jugar

- **Descriptor virtual:** Oz lee `oz-retrorom.json`, valida tamaño/contenido y resuelve el juego.
- **ROM real:** Oz conserva el acceso original cuando es compatible o prepara un `content://` compartible.
- **Motor automático:** usa un externo solo si está instalado, acepta el formato y Android confirma una actividad exportada; en caso contrario usa RetroArch o mantiene la app abierta para configurar.
- **Arcade/FBNeo:** exige el nombre exacto del romset, ZIP real y BIOS del set; nunca envía el descriptor virtual al core.

## Formatos multipista

Para CUE/M3U/GDI locales, conserva juntos los archivos relacionados. CHD simplifica bibliotecas móviles cuando el motor correspondiente lo admite.

## Diagnóstico

La pantalla permite compartir un diagnóstico censurado con juego, sistema, etapa, archivo, motor y core, sin incluir el contenido de la ROM, credenciales ni rutas privadas completas.

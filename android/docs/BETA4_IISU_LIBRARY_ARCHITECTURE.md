# Oz RetroRom 0.6.0 Beta 4 — biblioteca e integración iiSU

## Fuente de verdad

Oz RetroRom sigue siendo la única fuente de verdad de las **ROMs reales**:

- Room conserva catálogo, identidad, origen y disponibilidad local.
- SAF conserva la referencia persistente (`content://`) de ROMs descargadas/importadas.
- iiSU no recibe copias físicas de ROMs desde Oz.

## Beta 4 Fix 1 — puente virtual bajo demanda

La integración vuelve al modelo de accesos virtuales que resultó más útil en las iteraciones tempranas:

- una colección puede publicar todos sus juegos en iiSU, aunque algunos todavía no estén descargados;
- Oz genera descriptores pequeños y regenerables, nunca ROMs;
- al lanzar un descriptor desde iiSU, `SessionActivity` resuelve el `gameId`;
- si la ROM ya existe en la biblioteca SAF, Oz la reutiliza;
- si falta, Oz ejecuta su flujo normal ROMgi/Internet Archive, persiste la ROM y continúa al emulador.

El descriptor usa `contentMode = oz-virtual-on-demand`.

## Configuración inicial iiSU

1. En Oz: `Configuración → Emulación e integraciones → iiSU → Opciones avanzadas → Elegir carpeta de puente`.
2. En iiSU: añade la carpeta/subcarpeta de la plataforma como ROM folder.
3. Configura **Oz RetroRom Bridge** como emulador con:

```text
cl.gakuto.kizuna/cl.gakuto.kizuna.launch.SessionActivity -a cl.gakuto.kizuna.action.PLAY -e ROM "%ROM%" --activity-clear-top
```

4. En Oz abre una colección y pulsa **Publicar en iiSU**.
5. Actualiza/escanea la plataforma en iiSU.

Por compatibilidad, Oz acepta además el antiguo lanzamiento explícito `ACTION_MAIN` únicamente cuando apunta exactamente a `SessionActivity` y contiene el extra `ROM`. La referencia de archivo sigue pasando por `SessionLaunchValidator`.

## Qué NO hace el puente

- no duplica la ROM;
- no convierte iiSU en almacenamiento de Oz;
- no administra por API las colecciones nativas de iiSU;
- no evita las verificaciones de seguridad de `SessionActivity`.

Las preferencias históricas `globalVirtualTreeUri` / `systemVirtualTreeUri` se conservan para no romper instalaciones existentes, pero representan destinos de **puente**, no bibliotecas.

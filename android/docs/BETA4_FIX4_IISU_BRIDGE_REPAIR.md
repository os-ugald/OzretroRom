# Oz RetroRom 0.6.0 Beta 4 Fix 4 — iiSU Bridge Repair

## Síntomas corregidos

- Tras configurar Custom Launch Command, iiSU podía quedar con un único juego duplicado.
- Al lanzar desde iiSU, Oz podía mostrar `La ROM indicada no existe o no puede leerse.`

## Causas

1. El comando anterior pasaba `%ROM%` sin comillas. El Bridge contiene rutas con espacios (por ejemplo `Oz RetroRom`), por lo que el valor podía fragmentarse antes de llegar a `SessionActivity`.
2. `exportConfigured()` limpiaba todos los descriptores de una plataforma que no pertenecieran al último lote publicado. Publicar juegos locales después de una colección podía dejar solo los juegos locales.
3. iiSU soporta dos ROM folders por consola; si se configura a la vez la carpeta real y la carpeta Bridge, los juegos locales aparecen duplicados.
4. Android scoped storage puede impedir que Oz abra por `File` la ruta raw que iiSU conoce, aunque el acceso haya sido creado por Oz mediante SAF.

## Contrato Fix 4

```text
cl.gakuto.kizuna/cl.gakuto.kizuna.launch.SessionActivity -a cl.gakuto.kizuna.action.PLAY -e ROM "%ROM%" --activity-clear-top
```

- El Bridge usa una sola raíz global.
- Oz crea una subcarpeta por sistema (`snes`, `psx`, `arcade`, etc.).
- Las publicaciones son acumulativas.
- La eliminación de accesos es una acción explícita (`Limpiar accesos generados`).
- `SessionActivity` intenta resolver primero el ID estable desde el nombre del descriptor; solo si no corresponde a un acceso Oz valida la ruta como ROM real.

## Configuración iiSU recomendada

Para una consola administrada por Oz, usa SOLO la subcarpeta Bridge de esa plataforma como ROM folder. No agregues a la vez la carpeta real de ROMs. En Emulator > Custom Launch Command pega el comando anterior y después ejecuta Summary > Update.

## Reparación desde Fix 3

1. Instala Fix 4 sin borrar datos de Oz.
2. En `Emulación e integraciones > iiSU > Opciones avanzadas`, usa `Limpiar accesos generados` si quieres reconstruir el Bridge desde cero.
3. Vuelve a publicar las colecciones que quieras ver en iiSU.
4. En iiSU deja una sola ROM folder para esa consola: la subcarpeta Bridge correspondiente.
5. Sustituye el Custom Launch Command por la variante con `"%ROM%"`.
6. Ejecuta `Summary > Update`.

`versionCode = 60008`; `versionName = 0.6.0-beta.4`. Room permanece en esquema 6.

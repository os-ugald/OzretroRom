# Oz RetroRom 0.6.0 Beta 3 — Fix6.9 Persistent Library Launch

## Problema observado
Después de una descarga background exitosa, la ROM quedaba registrada como `DOWNLOADED` y persistida en la biblioteca SAF, pero `SessionActivity` intentaba reutilizar la ruta `/storage/...` derivada del `content://` como si fuera un `File` legible. Bajo scoped storage esa equivalencia no está garantizada y el preflight terminaba con el mensaje de que el archivo no existía en la sesión pública.

## Corrección
- La URI SAF persistente sigue siendo la fuente de verdad.
- Antes del preflight de RetroArch, `ensureRetroArchAccessibleEntry()` acepta una ruta directa solo si `File.isFile`, `canRead()` y `length()>0` son reales.
- Si la ruta derivada no es legible pero la URI SAF sí lo es, Oz crea una copia pública temporal en `Downloads/OzRetroRom/Sessions/<session>/` exclusivamente como espejo de compatibilidad para el contrato de lanzamiento de RetroArch.
- El `mediaUri` del `Entry` sigue apuntando al URI persistente original, por lo que los emuladores externos compatibles con `content://` no pierden el acceso SAF.
- El mismo flujo se usa al pulsar `Jugar ahora` tras una descarga completada en segundo plano y al reanudar después de instalar un core/RetroArch.
- Se reemplazó el error engañoso “no existe en la sesión pública” por mensajes que distinguen la biblioteca persistente de la copia de compatibilidad.

## Sin cambios
No hay migración Room. No se modifica Internet Archive, ROMgi, background download, Range resume, cover arts, iiSU, BIOS ni el catálogo.

## Validación
- `validate_project.py`: 834 comprobaciones.
- `test_fix69_persistent_library_launch.py`: 10 comprobaciones.
- Room 3→4 y 5→6: OK, datos preservados.
- Fix3/Fix4/Fix6.6/Fix6.8 contracts: OK.

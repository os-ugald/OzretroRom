# Fix 4 — Biblioteca ROMs persistente

## Cambio de enfoque

ROMgi continúa resolviendo y descargando automáticamente, pero el resultado ya no vive únicamente dentro de una sesión temporal.

La primera vez que Oz necesita descargar una ROM, solicita al usuario una carpeta compartida con `ACTION_OPEN_DOCUMENT_TREE`. La carpeta puede ser, por ejemplo, una carpeta `ROMs` creada por el usuario y que RetroArch también navega mediante SAF.

No se asume una ruta fija y no se intenta escribir en el directorio privado de RetroArch.

## Flujo

```text
Jugar
  ↓
¿games.contentUri todavía es legible?
  ├─ Sí → usar ROM local → emulador
  └─ No
      ↓
   ¿ROM propia/local encontrada?
      ├─ Sí → usarla
      └─ No → ROMgi
                 ↓
        HTTPS preferido / torrent fallback
                 ↓
          validar archivo
                 ↓
       copiar a árbol SAF elegido
                 ↓
         guardar contentUri + SHA-256
                 ↓
               jugar
```

## Persistencia

`PersistentRomLibrary` registra:

- gameId;
- systemId;
- región realmente resuelta por ROMgi;
- nombre del archivo;
- content URI;
- URI del árbol concedido;
- tamaño;
- SHA-256;
- fecha de guardado.

Room conserva `games.contentUri`. Si el archivo desaparece o el permiso deja de ser válido, Oz limpia la referencia obsoleta y vuelve al flujo normal de resolución.

## Organización

- Carpeta global: Oz crea una subcarpeta por plataforma.
- Carpeta específica por sistema: la ROM se guarda directamente allí.
- Las ROM persistentes nunca son borradas por `deleteAfterPlay`.
- `deleteAfterPlay` solo limpia sesiones/copias temporales de compatibilidad.

## Colección Descargados

La colección `downloaded-library` es calculada, no manual. SQL aplica:

`COALESCE(g.contentUri,'') != ''`

antes de paginación. Esto permite abrir rápidamente todo lo instalado y volver a jugar sin descarga.

## Android moderno

SAF da acceso únicamente a la carpeta elegida por el usuario y permite conservar el permiso. Android 11+ impide seleccionar raíces sensibles como `Android/data` y `Android/obb`, por lo que Oz no intenta saltarse esas restricciones.

## RetroArch

RetroArch 1.22.1 documenta soporte SAF. El changelog upstream mantiene en *Future* la capacidad de recibir `content://` directamente desde launchers externos, de modo que Fix 4 conserva una copia temporal de compatibilidad únicamente si el launcher/emulador necesita una ruta clásica.

## Fuentes primarias

- Android Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- Android 11 storage restrictions: https://developer.android.com/about/versions/11/privacy/storage
- RetroArch changelog: https://github.com/libretro/RetroArch/blob/master/CHANGES.md

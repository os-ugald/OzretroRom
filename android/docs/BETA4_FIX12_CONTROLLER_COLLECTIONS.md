# Oz RetroRom 0.6.0 Beta 4 — Fix 12

## Objetivo

Corregir la pantalla de detalle de Colecciones cuando el alto disponible es reducido y mejorar la navegación con mando/D-pad. En la captura reportada, el encabezado + buscador consumían la mayor parte del viewport y la rejilla quedaba reducida a una franja.

## Diseño adaptativo

La pantalla usa `BoxWithConstraints` y activa `controllerCompact` cuando `maxHeight < 620.dp`. La decisión depende del espacio real de ventana, por lo que también funciona en horizontal, tablets, ventanas redimensionadas e integración iiSU.

### Modo baja altura / mando

- Header de una sola franja: volver, tipo/nombre, conteos y estado.
- Acciones compactas: ROMs, SGDB/Arte, iiSU.
- Toolbar secundaria: Buscar y Filtros.
- Buscar abre un `AlertDialog`; el `BasicTextField` ya no permanece en la ruta principal del D-pad.
- La rejilla ocupa todo el espacio restante mediante `Modifier.weight(1f)`.
- El primer juego recibe foco inicial mediante `FocusRequester`.

### Modo alto

Se mantiene la composición completa anterior para teléfonos verticales o ventanas con suficiente altura.

## Navegación con control

Compose mantiene navegación bidimensional entre elementos enfocables. Fix 12 evita que el campo editable sea el primer destino y dirige el foco inicial a la rejilla. Desde los juegos, D-pad arriba puede alcanzar las acciones secundarias; D-pad izquierda puede alcanzar el rail cuando exista.

## Versión

- `versionCode = 60016`
- `versionName = 0.6.0-beta.4`

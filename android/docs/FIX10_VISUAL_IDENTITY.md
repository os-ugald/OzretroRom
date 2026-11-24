# Oz RetroRom 0.6.0 Beta 4 — Fix 10 · Identidad visual, navegación y temas

## Objetivo

Reducir el ruido visual observado en Inicio, eliminar la duplicación entre los accesos grandes y la navegación persistente, sustituir el hero genérico por una pieza de marca compacta y ofrecer una identidad cromática/tipográfica consistente en tema oscuro y claro.

## Decisión de navegación

Oz tiene cinco destinos principales y persistentes: Inicio, Colecciones, Catálogo, Biblioteca y Ajustes. En teléfono se mantiene una navegación inferior, pero se rediseña como dock compacto:

- el destino activo muestra icono + etiqueta corta;
- los destinos inactivos muestran solamente icono;
- altura visual de 58 dp, conservando objetivos táctiles de al menos 48 dp;
- iconos lineales propios dibujados con Compose Canvas, sin glifos Unicode;
- en ventanas anchas se utiliza un rail lateral de 82 dp.

Los cuatro accesos grandes duplicados de Inicio se eliminan. Inicio queda reservado a continuar jugando, recientes, favoritos y colecciones destacadas.

Referencias oficiales Android:
- https://developer.android.com/develop/ui/compose/components/navigation-bar
- https://developer.android.com/reference/kotlin/androidx/compose/material3/NavigationBarItem
- https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation

## Banner de identidad

Se elimina el texto genérico “Tu biblioteca, tus historias”. En su lugar se usa un banner de 78 dp:

- `OZ RETROROM`
- `ARCADE MARQUEE // 90s REMIX`
- barras de acento cian, violeta y magenta.

El banner funciona como firma de producto, no como tarjeta de contenido; por eso ocupa mucho menos alto y deja los juegos visibles antes.

## Estudio cromático

La propuesta conserva la combinación violeta/azul de Oz, pero asigna funciones estables a los colores: el violeta es identidad, el cian representa interacción/estado activo, el magenta se reserva para acentos y el ámbar para atención. El fondo oscuro deja de ser negro puro y el claro usa un blanco lavanda cálido para reducir dureza.

### Tema oscuro

| Rol | Color |
|---|---|
| Fondo | `#08070D` |
| Superficie | `#12101A` |
| Superficie elevada | `#1B1726` |
| Violeta identidad | `#8054EC` |
| Azul eléctrico | `#2F80FF` |
| Cian activo | `#34D7E9` |
| Magenta acento | `#E95DBB` |
| Texto principal | `#F7F4FF` |
| Texto secundario | `#BDB6CE` |
| Positivo | `#6EE7A8` |
| Advertencia | `#FFC857` |
| Error | `#FF6B7A` |

### Tema claro

| Rol | Color |
|---|---|
| Fondo | `#F7F5FA` |
| Superficie | `#FFFFFF` |
| Superficie elevada | `#EEEAF5` |
| Violeta identidad | `#6236C7` |
| Azul | `#185FC7` |
| Cian/teal | `#007A87` |
| Magenta | `#A12484` |
| Texto principal | `#1B1620` |
| Texto secundario | `#5D5667` |
| Positivo | `#147A4B` |
| Advertencia | `#8A5A00` |
| Error | `#B3261E` |

### Contraste calculado

- Oscuro: texto principal/fondo `18.48:1`; texto secundario/superficie `9.64:1`; cian/fondo `11.51:1`.
- Claro: texto principal/fondo `16.41:1`; texto secundario/superficie `7.02:1`; cian/fondo `4.69:1`; violeta/fondo `6.75:1`.

Las combinaciones de texto comprobadas superan el umbral 4.5:1 usado como referencia de contraste mínimo para texto normal.

Referencia W3C: https://www.w3.org/WAI/WCAG22/Techniques/general/G18

## Tipografía

Se evaluaron familias geométricas/tecnológicas de Google Fonts. La selección para la identidad de Oz es **Oxanium** por su estructura angular y tecnológica, adecuada para títulos de consola/arcade sin resultar tan extrema como una fuente puramente decorativa.

Estrategia:

- Oxanium: marca, hero, títulos y secciones.
- SansSerif del sistema: cuerpo y controles, priorizando legibilidad y robustez.
- Monospace del sistema: metadatos, etiquetas de plataforma y guiños de terminal/ROM.

La fuente se solicita mediante la API oficial de Downloadable Fonts de Compose. No se incluye ningún archivo TTF/OTF en el proyecto. Si el proveedor Google Fonts no está disponible, la interfaz conserva SansSerif como fallback y sigue siendo funcional.

Referencias oficiales:
- https://developer.android.com/develop/ui/compose/text/fonts
- https://developer.android.com/reference/kotlin/androidx/compose/ui/text/googlefonts/GoogleFont.Provider
- https://fonts.google.com/specimen/Oxanium

## Tema oscuro/claro

`AppThemeMode` se persiste en `AppPreferences`. La elección está disponible en `Configuración > Apariencia` y se propaga a:

- Compose mediante `OzTheme` y esquemas Material 3 propios;
- pantallas Views/XML mediante recursos `values` / `values-night`;
- barras de estado/navegación con iconos claros u oscuros según el tema;
- `AppCompatDelegate` para las pantallas heredadas.

El valor predeterminado sigue siendo Oscuro para conservar la identidad ya existente de Oz; el usuario puede forzar Claro en cualquier momento.

## Compatibilidad

No se cambian ROMgi, SteamGridDB, Room, SAF, RetroArch, iiSU, motores externos ni la pantalla de sesión Fix 9. El cambio se concentra en sistema visual, navegación y preferencias de apariencia.

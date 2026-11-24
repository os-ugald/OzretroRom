# Manual de usuario — Oz RetroRom 0.6.0 Beta 3

## Pantalla principal

La parte superior se divide en bloques:

1. **Encabezado Oz RetroRom**.
2. **Buscar juegos, sagas o plataformas**: consulta todo el catálogo, no solo lo visible.
3. **Filtros**: abre el panel separado.
4. **Acciones rápidas**: Catálogo global, Configuración, Carpeta virtual, Sincronizar iiSU y Juego aleatorio.
5. **Filtros activos**: chips compactos y botón para limpiarlos.
6. **Lista paginada**: carga más juegos al desplazarse.

## Panel de filtros

Selecciona varias regiones, fabricantes/plataformas, géneros y estados. Los estados incluyen ROMgi, biblioteca local, favoritos, recientes, artwork y descarga. El orden puede ser A-Z, Z-A, año, últimos jugados, añadidos o favoritos primero.

- **Limpiar todo** restaura Región preferida y Nombre A-Z.
- **Cancelar** descarta cambios.
- **Aplicar filtros** ejecuta una consulta nueva sobre Room.

## Coincidencias ROMgi

Antes de descargar, Oz muestra título elegido, región, método, confianza, archivo y transporte. Cuando las opciones son ambiguas, debes escoger una; no hay selección silenciosa.

Para un juego World, Oz intenta primero la región original y después la preferida y los fallbacks configurados. La coincidencia difusa solo se usa al final.

## Motores y emuladores

En **Configuración → Motores y emuladores**, cada sistema ofrece:

- **Automático recomendado por Oz RetroRom**.
- **RetroArch**.
- **Emulador externo**.
- **Preguntar al jugar**.

Pulsa **Detectar instalados** para actualizar el estado. Cada tarjeta muestra core, formatos, BIOS, paquete y limitaciones. **Probar lanzamiento** permite elegir un archivo mediante SAF y muestra la URI usada.

Oz solo entrega `content://` con permiso temporal a emuladores externos. Si una aplicación está instalada pero no publica un intent compatible, Oz puede abrir su pantalla principal, usar RetroArch o permanecer abierto para configurar.

## RetroArch

Selecciona la variante instalada y revisa el core recomendado. Una carpeta de cores elegida por SAF es un área de preparación. Oz entrega el `.so` a la actividad de importación de RetroArch; no escribe en su almacenamiento privado.

La instalación automática de release solo funciona para cores incluidos en `trusted_cores.json` con SHA-256. Si el catálogo está vacío, instala el core desde RetroArch.

## BIOS y formatos

La tarjeta del sistema indica BIOS y extensiones. Entre los formatos contemplados están CHD, CUE/BIN, M3U, ISO, CSO, RVZ, WBFS, GDI y ZIP según plataforma. Un formato listado no sustituye los requisitos legales/técnicos del emulador.

## FinalBurn Neo

Arcade requiere el nombre exacto del romset y un ZIP real. Oz bloquea descriptores virtuales, prepara BIOS configuradas y advierte si el nombre del set no coincide. Si FBNeo informa archivos faltantes, usa el DAT correspondiente a la versión del core y actualiza el romset; no renombres un ZIP incompatible para forzarlo.

## iiSU

**Sincronizar iiSU** conserva carpetas virtuales, descriptores, artwork y pack. El comando es:

```text
cl.gakuto.kizuna/cl.gakuto.kizuna.launch.SessionActivity -a cl.gakuto.kizuna.action.PLAY -e ROM "%ROM%" --activity-clear-top
```

iiSU entrega `%ROM%`; Oz determina si es descriptor o ROM real y continúa mediante Oz RetroRom Bridge.

## Pantalla de progreso

Muestra etapa, tiempo, archivo, porcentaje, descargado/total, velocidad, pares, ETA, estancamiento, reintento, fuente alternativa, cancelar y apertura del motor elegido. El diagnóstico censurado continúa de forma interna para soporte. Durante metadatos torrent no inventa porcentaje.

## Internet Archive y confirmación de fuente

Abre **Configuración → Cuenta de Internet Archive → Conectar con Chrome**. Oz abre directamente la página oficial `archive.org/account/s3.php` en Chrome/Custom Tabs. Después de autenticarte, vuelve a Oz y pega una sola vez tus claves IA-S3 Access Key y Secret Key. Oz no guarda la contraseña ni lee cookies privadas de Chrome; valida las claves por HTTPS y las cifra con Android Keystore.

Internet Archive no publica un callback OAuth/AppAuth para transferir automáticamente a Oz una sesión iniciada en Chrome. Por eso el retorno ocurre al cerrar/Atrás en la Custom Tab o mediante **Volver a Oz RetroRom**, y la conexión persistente se completa con las claves IA-S3 oficiales. Una vez conectada, no tienes que repetir el proceso para cada juego.

Una fuente exacta de la región solicitada comienza automáticamente. El diálogo solo aparece ante ambigüedad real, fallback regional, título reordenado o coincidencia de confianza insuficiente. No aparece simplemente porque la ROM sea Europe, Japan, Asia o World.

Si una fuente World no existe, Oz identifica cualquier alternativa concreta como fallback y requiere aprobación. Un archivo France no se clasifica silenciosamente como World.

Torrent permanece como respaldo cuando no existe HTTPS directo utilizable. Si los metadatos no llegan en 45 segundos, Oz deja de esperar y ofrece reintento o archivo local en lugar de bloquearse varios minutos.

## Biblioteca ROMs persistente

La primera vez que ROMgi necesite descargar un juego, Oz pedirá una carpeta compartida para conservar las ROMs. Elige una carpeta `ROMs` que también puedas navegar desde RetroArch. Android recordará el permiso SAF; Oz no escribe en el almacenamiento privado de RetroArch ni en `Android/data`.

Después de terminar y validar la descarga, Oz guarda el archivo allí y registra su `content URI`, tamaño, SHA-256 y región. En las partidas siguientes se usa primero esa copia local: **no se vuelve a descargar desde ROMgi**.

La colección **Descargados** se actualiza automáticamente con los juegos que ya están en el dispositivo. Los juegos instalados muestran **Jugar**; los que todavía dependen de ROMgi muestran **Descargar y jugar**. Las copias temporales que algunos emuladores necesitan por compatibilidad se pueden borrar al cerrar sin tocar la ROM persistente.


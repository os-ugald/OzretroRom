# Oz Ready Core 01B — Editions & Multidisc

Fecha: 2026-08-12
Base verificada: `f345dca` reconstruida en `20f6bc3`
Rama local: `feature/oz-ready-core-01b`

## Estado del gate

La implementación, la compilación y las pruebas automatizadas están completas. El gate sigue
**abierto sólo para la prueba física en Xiaomi**. Dreamcast permanece pausado hasta registrar esa
prueba y cerrar 01B.

APK debug generado:

- archivo: `app-debug.apk`;
- tamaño: `27.722.594` bytes;
- SHA-256: `87175072c8e2401fcabede6d01e06dc45e668a41c3d36e336dd8b6a44b19a20e`;
- paquete: `cl.gakuto.kizuna`;
- versión: `60018 / 0.6.0-beta.4`;
- firma: Android Debug, APK Signature Scheme v2 verificada;
- certificado SHA-256 de esta compilación:
  `a27521d49cfd2be2886ba1b0e397676ffc429f45f9e7b79a92fb3fd7ef8f77b1`.

Este entorno creó una clave debug temporal. Por tanto, el APK incluido demuestra que el proyecto se
compila y sirve para una instalación limpia de prueba, pero **no se puede asumir que actualice la app
ya instalada en el Xiaomi**. Android sólo acepta una actualización si ambos APK están firmados con la
misma clave. Para conservar los datos, se debe compilar 01B en el equipo que conserva el
`debug.keystore` usado por Ready Core 01, o firmarlo con esa misma clave. No desinstalar la versión
existente para sortear un error de firma: eso invalidaría la prueba de preservación.

## Resultado funcional

El flujo ROMgi ya no trata cada archivo como una opción plana. La resolución se realiza con esta
jerarquía:

```text
Juego padre
└── Edición (región + idioma + revisión + tipo)
    └── Formato reproducible
        ├── Disco 1
        ├── Disco 2
        └── ...
```

- Las tablas oficiales `entry_groups` y `entry_group_members` se usan cuando existen.
- Una base anterior sin grupos usa un parser compatible que elimina sólo el token de disco; región,
  idioma y revisión permanecen en la identidad de la edición.
- Retail se separa de demos, prototipos, betas y samples. Las variantes avanzadas quedan accesibles
  mediante una acción explícita y no contaminan el flujo normal.
- La recomendación aplica la política NTSC documentada: USA, Japón, World, Asia y por último Europa.
  Una fuente regional insegura sigue necesitando confirmación.
- El panel Oz reemplaza el `AlertDialog` nativo con radio buttons. Muestra región, tipo, formato,
  discos, tamaño, transporte y artwork; tiene foco inicial, navegación de mando y acción única
  `Descargar juego completo`.
- Las fichas visibles muestran el título padre y pueden tomar artwork de una variante hermana sin
  cambiar el ID, favorito, colección ni estado local del juego.

## Descarga y persistencia multidisco

Una elección multidisco crea un contrato serializable que contiene todos los discos. El motor:

1. descarga y prepara los miembros en orden;
2. valida cada archivo antes de continuar;
3. calcula progreso agregado y persiste la tarea; HTTPS conserva parciales reanudables, mientras
   que un torrent vuelve a solicitar selectivamente el miembro interrumpido;
4. copia todos los discos a la biblioteca SAF elegida por el usuario;
5. escribe al final un `.m3u` UTF-8 con nombres relativos seguros;
6. registra playlist, discos, tamaños y SHA-256;
7. abre la playlist directamente cuando RetroArch puede ver la ruta o refleja playlist y discos
   juntos en una carpeta pública temporal cuando SAF no expone una ruta legible.

Al borrar una descarga, Oz intenta eliminar únicamente la playlist y los miembros que él creó. Los
archivos importados o preexistentes del usuario no se sustituyen ni se eliminan.

## Límites deliberados

- Los conjuntos completos se ofrecen sólo cuando existen todos los índices `1..N` en un mismo
  formato reproducible.
- Para multidisco se aceptan miembros autocontenidos CHD o ISO en perfiles con soporte M3U. No se
  ofrece una descarga CUE/BIN parcial: el modelo ROMgi actual entrega una fuente por fila y bajar
  sólo el CUE produciría un juego roto. El soporte futuro necesita un contrato de bundle por disco.
- La infraestructura M3U no modifica todavía la integración Dreamcast ni habilita Ready Core 02.
- `ra_game_id` no se interpreta como prueba de compatibilidad RetroAchievements; hace falta comparar
  el hash del contenido preparado con un hash reconocido por RA.

Fuentes primarias consultadas:

- ROMgi: <https://github.com/caprado/romgi>, especialmente el commit
  `dfc9c9036307ebb19ee6c65287b8f6d1fcb0c9ea` de agrupación multidisco.
- Libretro, cambio de discos: <https://docs.libretro.com/guides/disc-swapping/>.
- PCSX ReARMed: <https://docs.libretro.com/library/pcsx_rearmed/>.
- Flycast: <https://docs.libretro.com/library/flycast/>.
- Genesis Plus GX: <https://docs.libretro.com/library/genesis_plus_gx/>.
- Foco en Compose: <https://developer.android.com/develop/ui/compose/touch-input/focus>.

## ROMgi y tolerancia operacional

El endpoint vigente de ROMgi se intenta primero. Durante la investigación, el archivo comprimido del
checkpoint `840ba53a2e3957cc6d5a8ed62a6faf13c6e3748b` no superó la apertura SQLite. Por eso existe un
respaldo inmutable y verificado del commit oficial
`cb4ba3e49928a537dfa944007a3ae0e62e8b63e9`, con SHA-256 comprimido
`178fb285f1b9bc2cd7b6c80a0c09fd519b985c13f05cbd625e183be54a668e72`.

La prueba contra esa base oficial encontró para Final Fantasy IX 108 filas de fuente y siete planes
CHD completos; tanto retail base como Rev 1 conservaron exactamente los discos 1, 2, 3 y 4.

## Evidencia automatizada

- `:app:compileDebugKotlin`: PASS.
- `:app:testDebugUnitTest`: PASS, 32 pruebas, 0 fallos.
- `:app:assembleDebug`: PASS.
- `scripts/validate_project.py`: PASS, 1.176 comprobaciones; 44 sistemas, 192 juegos y 18 colecciones.
- `scripts/test_ready_core_01b.py`: PASS, 76 contratos.
- Regresiones seleccionadas: Fix 6.8 (29), Fix 6.9 (10), Fix 11 (17), Fix 12 (13), Surface 03B
  (9), Room 3→4 y Room 5→6: PASS.
- `git diff --check`: PASS.

Los verificadores históricos fijados a `versionCode 60010` o a copias visuales eliminadas ya fallaban
en la base reconstruida y no son gates actuales. `test_fix3_contracts.py` queda supersedido porque
exige precisamente el selector plano `setSingleChoiceItems` que 01B reemplaza y cuyo regreso está
prohibido por el validador vigente.

## Checklist físico Xiaomi

No cerrar 01B hasta completar y registrar lo siguiente. Antes de instalar, comparar el certificado
del APK anterior y el candidato con `apksigner verify --print-certs`; si no coinciden, reconstruir 01B
con el keystore original:

1. Instalar como actualización sobre la app existente, ya con firma coincidente, y confirmar que conserva datos, favoritos,
   preferencias y biblioteca ROMs.
2. Abrir un juego con varias ediciones; navegar todo el panel sólo con mando, cancelar y volver a
   abrirlo. Confirmar foco visible y que la opción recomendada es segura.
3. Confirmar que demos/prototipos están ocultos inicialmente y que al ocultarlos de nuevo no queda
   seleccionada una variante invisible.
4. En Final Fantasy IX elegir una edición CHD completa, descargar los cuatro discos y comprobar que
   la biblioteca contiene cuatro CHD y un M3U con cuatro filas relativas en el orden correcto.
5. Lanzar el M3U, verificar que abre el Disco 1 y cambiar al Disco 2 mediante
   `Quick Menu → Disc Control → Eject → Current Disc Index → Insert`.
6. Cerrar RetroArch, desactivar la red y relanzar desde Oz; debe usar la biblioteca persistente sin
   volver a ROMgi.
7. Revisar Resident Evil 2: la ficha debe mostrar el juego padre y una carátula válida aunque la
   variante seleccionada no tenga artwork propio.
8. Con preferencia USA/NTSC-U, comprobar que Oz no elige ni descarga Europa/PAL silenciosamente.
9. Borrar una descarga multidisco desde Oz y verificar que elimina sólo sus cinco archivos creados,
   sin tocar archivos vecinos del usuario.

Resultado esperado tras esta lista: registrar evidencia y cerrar Ready Core 01B. Sólo entonces puede
abrirse Ready Core 02 — Dreamcast.

# GBA Opción A — Zelda ALttP&FourSwords — cierre de evidencia (R2)

**Fecha real de la sesión (verificada, no asumida):** 2026-09-05
(`date` del host y `adb shell date` coinciden, UTC-4; el turno anterior había
asumido 2026-09-04 antes de medianoche real).

**Juego:** `Legend of Zelda, The - A Link to the Past & Four Swords` (USA),
GBA, `oz-source-index-gba-game-447230b5dd8c6229730563818b162305b3ff15bd3e14e73f7fb3109f4b1cbc9f`.

**Rama/HEAD:** `feature/gba-catalog-v1` @ `139207e` (fix de `showSourceOptions`
ya commiteado y pusheado en turno anterior).

---

## Veredicto por criterio

| Criterio | Veredicto | Tipo |
|---|---|---|
| Catalog presence | **PASS** | EVIDENCE |
| SAF-pick (dialog fix aplicado) | **PASS** | EVIDENCE |
| URI-persisted (SAF grant, no copia a biblioteca) | **PASS** (con matiz — ver abajo) | EVIDENCE |
| Launch | **PASS** | EVIDENCE |
| Core resolution (mgba) | **PASS** | EVIDENCE |
| Visual confirmation (pantalla de título) | **PASS** | EVIDENCE (verbal, no capturada por mí en el momento) |

Ningún criterio se marcó FAIL. Donde no pude verificar algo directamente, lo
marco como NOT RUN/UNKNOWN explícitamente más abajo — no lo convertí en FAIL.

---

## 1. Logcat detenido limpiamente

Task `bn2uioxm1` (segunda captura post-fix) detenida vía `TaskStop`,
confirmado por `tasklist` (solo queda el servidor adb, PID 20680, ningún
cliente logcat). Sin errores.

## 2. Screenshot del estado actual

`01_post_session_catalog_state_NOT_retroarch.png` (renombrado desde el
nombre original que pedía la instrucción, para no inducir a error: el
contenido real **no es RetroArch**). Al capturar (00:24 local), la app ya
había vuelto al catálogo
(`GlobalCatalogActivity`, buscando "zel"). Confirmé con
`dumpsys window` (`mCurrentFocus=...GlobalCatalogActivity`) y con
`ps`/`pidof` que **no hay proceso de RetroArch corriendo** en este momento.

La confirmación visual de la pantalla de título es la que Oz reportó
verbalmente en el turno anterior ("funcionó, ahí está el ítem" fue sobre el
SAF picker; la pantalla de título la mencionó el propio prompt de este turno
como ya confirmada por Oz) — **no la verifiqué yo mismo con una captura en
el instante en que RetroArch mostraba el título**. Ver logcat/diag log en la
sección 5 para la corroboración indirecta de que RetroArch sí llegó a
ejecutar el core sin crash.

## 3. Estado final en Room (`games` table, vía `run-as` + `sqlite3` local)

```sql
SELECT id,title,systemId,source,launchValidated,coreId,contentUri,
       lastPlayedAt,libraryOrigin,romgiAvailable
FROM games
WHERE id = 'oz-source-index-gba-game-447230b5dd8c6229730563818b162305b3ff15bd3e14e73f7fb3109f4b1cbc9f';
```

| Campo | Valor |
|---|---|
| `title` | Legend of Zelda, The - A Link to the Past & Four Swords |
| `systemId` | gba |
| `source` | oz-source-index |
| `launchValidated` | **0** (false) |
| `coreId` | mgba_libretro_android.so |
| `contentUri` | **(vacío/NULL)** |
| `lastPlayedAt` | 1788582067958 (epoch ms) = 2026-09-05 04:21:07.958 UTC = 00:21:07.958 local |
| `libraryOrigin` | NONE |
| `romgiAvailable` | 0 |

Reportado tal cual está, sin asumir qué "debería" decir: `contentUri` sigue
vacío y `launchValidated` sigue en 0 después del launch exitoso — el modelo
de datos de Room **no se actualiza** por el flujo de SAF manual +
lanzamiento; solo `lastPlayedAt` cambió, y coincide casi exactamente con el
`RETROARCH_NATIVE_LAUNCHED` del diag log (sección 5).

## 4. Preferencia `authorized_source` (SharedPreferences `oz_retrorom.xml`)

Extraída vía `run-as cat shared_prefs/oz_retrorom.xml` (modificado
2026-09-05 00:21, coincide con la ventana del pick):

```xml
<string name="authorized_source_oz-source-index-gba-game-447230b5dd8c6229730563818b162305b3ff15bd3e14e73f7fb3109f4b1cbc9f">
  content://com.android.externalstorage.documents/document/primary%3ADownload%2FRoms%2Fgba%2FLegend%20of%20Zelda%2C%20The%20-%20A%20Link%20to%20the%20Past%20%26%20Four%20Swords%20(USA).gba
</string>
<string name="authorized_source_filename_oz-source-index-gba-game-447230b5dd8c6229730563818b162305b3ff15bd3e14e73f7fb3109f4b1cbc9f">
  Legend of Zelda, The - A Link to the Past & Four Swords (USA).gba
</string>
```

**Existe**, apunta al URI elegido en el picker. Confirma que
`sourcePicker.launch(...)` → `persist(uri)` →
`preferences.setAuthorizedSource(...)` corrió de punta a punta.

### Distinción pedida: "URI-persisted" vs. `persist()` completo

`persist(uri)` en `CrtGameDialog.kt:296-303` **solo** llama
`takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)` — es
decir, persiste el **permiso SAF** sobre el URI (sobrevive reinicios), y
guarda el URI como string en `SharedPreferences`. **No copia el archivo a
ninguna biblioteca gestionada por la app** ni escribe `contentUri` en la fila
de `games`. Esa es la razón por la que `contentUri` sigue vacío en el punto 3:
"URI persistido" != "ROM importado a la biblioteca". Ambos conceptos son
independientes en este código, y el criterio "URI-persisted" se cumple
correctamente en su alcance real (permiso + referencia guardada), no en el
sentido de "archivo copiado".

## 5. Logcat + diagnostic log — evidencia real del launch

### Apertura y retorno del picker SAF (logcat, `ActivityTaskManager`)

```
00:15:07.317  START ... act=android.intent.action.OPEN_DOCUMENT
              cmp=com.google.android.documentsui/...PickActivity
              from pid 20892 callingPackage cl.gakuto.kizuna
00:15:53      (picker Activity destruida, evidencia ya reportada en turno previo)
```

### Launch real (logcat, ~6 min después — Oz presionó "Jugar" tras autorizar la fuente)

```
00:21:07.287  START cmp=cl.gakuto.kizuna/.launch.SessionActivity
              LAUNCH_SINGLE_TASK, callingPackage cl.gakuto.kizuna
00:21:07.781  Start proc 13307:com.retroarch/u0a353 (broadcast InstalledCoresReceiver)
00:21:07.953  START cmp=com.retroarch/.browser.retroactivity.RetroActivityFuture
              LAUNCH_SINGLE_INSTANCE, callingPackage cl.gakuto.kizuna
00:21:08.291  RetroArch: [ENV] Libretro path: ".../cores/mgba_libretro_android.so"
00:21:08.291  RetroArch: [ENV] Auto-start game:
              "/storage/emulated/0/Download/OzRetroRom/Sessions/
               65305805-90d8-4e7a-ba21-050f38a398f1/
               Legend of Zelda, The - A Link to the Past & Four Swords (USA).gba"
00:22:08.473  onProcessDied: pid=13307, uid=10353 (com.retroarch)
```

Sin líneas `FATAL EXCEPTION`/`AndroidRuntime` para `cl.gakuto.kizuna` ni para
`com.retroarch` en toda la ventana 00:21:07–00:22:09. El "died" a las
00:22:08.473 ocurre ~0.4s antes del `SESSION_RETURN_TARGET` del diag log
(ver abajo) — consistente con salida normal iniciada por el usuario, no un
crash.

### `RETROARCH_NATIVE_LAUNCHED` real (DiagnosticLogger, no pasa por logcat)

Archivo `files/diagnostics/session-20260905-042107-65305805-90d8-4e7a-ba21-050f38a398f1.log`
extraído vía `run-as` y copiado a esta carpeta de evidencia
(`diag_session-20260905-042107-65305805-90d8-4e7a-ba21-050f38a398f1.log`).
El UUID de sesión coincide exactamente con el que aparece en el path de
"Auto-start game" del logcat de RetroArch — mismo evento, dos fuentes
independientes.

```
2026-09-05T04:21:07.607Z SESSION_BEGIN | game_id=oz-source-index-gba-game-447230b5dd8c6229730563818b162305b3ff15bd3e14e73f7fb3109f4b1cbc9f system=gba
2026-09-05T04:21:07.615Z DIRECT_ROM_READY | file=Legend of Zelda, The - A Link to the Past & Four Swords (USA).gba path=<private-path>/Download
2026-09-05T04:21:07.617Z ENGINE_AUTO_SELECTED | system=gba engine=retroarch external_ready=false
2026-09-05T04:21:07.719Z RETROARCH_SAF_COMPAT_MIRROR | file=...gba source_scheme=content mirror=...gba
2026-09-05T04:21:07.724Z VALIDATION_OK | file=...gba bytes=8388608 romset= bios=
2026-09-05T04:21:07.959Z RETROARCH_NATIVE_LAUNCHED | package=com.retroarch core=mgba_libretro_android.so file=Legend of Zelda, The - A Link to the Past & Four Swords (USA).gba
2026-09-05T04:22:08.895Z SESSION_RETURN_TARGET | Oz
```

Esto es exactamente el `RETROARCH_NATIVE_LAUNCHED` real pedido — con
`core=mgba_libretro_android.so`, coincidiendo con `coreId` en Room. El
`bytes=8388608` de `VALIDATION_OK` coincide exactamente con el tamaño del
archivo en disco (ver sección 6).

## 6. SHA-256 del archivo referenciado

**No pude resolver el `content://` URI persistido directamente** — lo probé
explícitamente vía `adb shell content read --uri "content://...primary%3ADownload..."`
y falló:

```
java.lang.SecurityException: Permission Denial: reading
com.android.externalstorage.ExternalStorageProvider ... from pid=..., uid=2000
requires that you obtain access using ACTION_OPEN_DOCUMENT or related APIs
```

El permiso persistido pertenece al proceso/UID de `cl.gakuto.kizuna`, no al
shell adb (uid 2000) — comportamiento esperado de SAF, no un fallo.

En su lugar, obtuve el hash por la **ruta física real** a la que el
document-id de ese URI se mapea 1:1 para el volumen `primary` de
`ExternalStorageProvider` (`primary:Download/Roms/gba/...` →
`/storage/emulated/0/Download/Roms/gba/...`), vía `adb shell sha256sum`
directamente en el dispositivo:

```
f328f8f07d736288a00c80d31cc1630f3aa02aaf20efdcba73d31dae832b5d76
/storage/emulated/0/Download/Roms/gba/Legend of Zelda, The - A Link to the Past & Four Swords (USA).gba
```

Tamaño del archivo: 8388608 bytes — **coincide exactamente** con
`bytes=8388608` de `VALIDATION_OK` en el diag log. Esto corrobora que es el
mismo archivo que RetroArch validó y lanzó, pero **el hash se obtuvo por
mapeo de ruta, no por resolución del URI `content://`** — distinción que el
pedido pidió explícitamente marcar. El archivo staged bajo
`.../Sessions/65305805-.../` que RetroArch reportó como "Auto-start game" ya
no existe en el dispositivo (limpieza post-sesión) — no se pudo hashear ese
mirror específico.

## Abierto / NOT RUN / UNKNOWN

- **Visual del título**: NOT RUN por mí en este turno (RetroArch ya no
  corría al capturar) — EVIDENCE es el reporte verbal de Oz, más la
  corroboración indirecta de logcat/diag log (launch limpio, sin crash,
  ~61s de sesión activa antes del retorno).
- **Hash del mirror SAF-compat** (`RETROARCH_SAF_COMPAT_MIRROR`) usado
  realmente por el proceso nativo de RetroArch: UNKNOWN — el archivo ya fue
  limpiado por `SessionCleanupWorker` (inferencia por el nombre de la clase,
  no confirmada en este turno) antes de que yo pudiera acceder a él.
- **Causa raíz exacta en AOSP/AppCompat** de por qué combinar
  `.setMessage()` + `.setItems()` suprimía `customPanel`: sigue sin
  documentarse (ya señalado como abierto en el reporte previo).
- No se hizo commit de nada en este turno — solo se generó este archivo y
  el diag log copiado, ambos untracked, para revisión de Oz Forge — Command.

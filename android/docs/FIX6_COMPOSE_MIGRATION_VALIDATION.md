# Oz RetroRom 0.6.0 Beta 3 — Fix6 Compose

Fecha: 7 de agosto de 2026

## Estado de la entrega

Esta corrección inicia formalmente la migración progresiva de Oz RetroRom a **Kotlin + Jetpack Compose + ViewModel/StateFlow + Room**, manteniendo interoperabilidad con las pantallas Views/XML y la infraestructura funcional existente.

Identidad preservada:

- `applicationId = cl.gakuto.kizuna`
- `versionCode = 60003`
- `versionName = 0.6.0-beta.3`
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 26`
- Java/Kotlin target 17
- ABI de esta Beta: ARM64 (`arm64-v8a`)

No se usa `fallbackToDestructiveMigration`, no se borra la base de datos y no se modifica una carpeta SAF elegida por el usuario sin una acción explícita.

## Resumen de cambios

### Compose y arquitectura

- `MainActivity` se convierte en el host Compose principal.
- El Inicio Views anterior se conserva como `LegacyMainActivity`; no se elimina XML funcional.
- Se incorpora `OzApp()` con `NavHost` y cinco destinos principales: Inicio, Colecciones, Catálogo, Biblioteca y Configuración.
- Se incorpora un sistema visual propio: `OzTheme`, `OzColors`, `OzTypography`, `OzShapes`, `OzSpacing` y `OzMotion`.
- La identidad se mantiene en púrpura profundo, violeta arcade, azul eléctrico, cyan, negro profundo y estética Arcade Marquee/retro moderna.
- Los componentes de carátula están preparados para foco por D-pad/gamepad mediante `focusable`, `onFocusChanged`, escala y borde de foco discretos.
- Se usa `collectAsStateWithLifecycle()` para observar `Flow/StateFlow` desde Compose.

### Inicio

Inicio deja de ser otro catálogo. No contiene buscador global ni filtros globales. Presenta:

- Continuar jugando.
- Recientes.
- Favoritos.
- Colecciones destacadas/curadas.
- Accesos grandes a Catálogo, Biblioteca y Configuración.

### Catálogo

- Es el único lugar con búsqueda global.
- Mantiene búsqueda con debounce de 300 ms, región, plataforma, género, estado y orden.
- Usa Paging + consultas Room existentes y `LazyVerticalGrid` adaptable; no materializa decenas de miles de juegos simultáneamente.

### Colecciones

- Rediseño Compose mediante rails horizontales, hero surfaces y collages de carátulas.
- Mantiene colecciones `AUTOMATIC`, `MANUAL` y `CURATED`.
- Automáticas mínimas: Descargados, Favoritos y Recientes.
- El detalle de colección incorpora hero, contadores total/local/faltante, búsqueda con debounce, filtros, orden, descarga de faltantes y sincronización con iiSU.
- `CollectionDownloadManager` conserva la semántica funcional de Fix5: omite ROM ya local, descarga solo faltantes y persiste el resultado en la biblioteca Room/SAF.

### Biblioteca

- Pantalla Compose de contenido local real.
- Secciones: Todos, Descargados, Importados, Plataforma y Últimos jugados.
- La UI usa etiquetas humanas; no imprime `content://...` en las nuevas pantallas principales.
- Las URI SAF reales continúan almacenándose internamente.

### Room

Se eleva el esquema de Room de 5 a 6 mediante una migración **no destructiva**:

```sql
ALTER TABLE games ADD COLUMN libraryOrigin TEXT NOT NULL DEFAULT 'NONE';
UPDATE games SET libraryOrigin='DOWNLOADED' WHERE COALESCE(contentUri,'')!='';
```

Esto permite distinguir `DOWNLOADED` e `IMPORTED` sin eliminar ni reemplazar `contentUri` existentes.

### Internet Archive Auth V2

Se reemplaza la política de invalidación inmediata por una máquina de estados explícita:

- `DISCONNECTED`
- `CONNECTED`
- `STALE`
- `INVALID`

La sesión cifrada guarda únicamente lo necesario para el flujo autenticado: access key, secret key, cookies necesarias, timestamps, último resultado y contador de fallos. No se almacena usuario ni contraseña.

Flujo implementado:

1. WebView interno limitado a HTTPS de `archive.org` y subdominios.
2. Login legítimo del usuario sin bridge JavaScript ni interceptación de campos.
3. Cookies de sesión.
4. Consulta de `https://archive.org/account/s3.php`.
5. Obtención de IA-S3 access/secret.
6. Cifrado AES/GCM mediante una clave no exportable de Android Keystore.
7. Validación mediante IA-S3.
8. Persistencia de estado/fallos.

Política 401/403:

- un 401/403 de una descarga se registra como fallo de autenticación plausible;
- se revalida la sesión;
- el mismo evento no incrementa dos veces el contador durante la revalidación;
- `INVALID` solo se alcanza tras 3 fallos consecutivos con forma de autenticación;
- un fallo de transporte/servidor deja el estado `STALE` y conserva secretos;
- el downloader no llama `clear()` por un 401/403;
- **Desconectar** sí elimina inmediatamente sesión cifrada y cookies WebView.

Los logs diagnósticos se limitan a estado HTTP, host, redirects, estado y tipo de fallo; no incluyen access key, secret key, cookies ni `Authorization`.

### Descargas Internet Archive

- Se recalculan headers de autenticación tras cada redirect.
- No se reenvían cookies/LOW a un host ajeno a Internet Archive.
- Redirects HTTP están limitados.
- Para IA se usa únicamente el comportamiento confirmado por ROMgi/documentación: cookies cuando corresponde y `Authorization: LOW access:secret` para IA-S3/programmatic access.

### RetroArch — preflight

Se mantiene y migra visualmente el preflight de Fix5 a un panel Compose dentro de la pantalla de sesión Views existente:

1. Verificando archivo.
2. Verificando core.
3. Verificando compatibilidad.
4. Verificando BIOS.
5. Abriendo RetroArch.

Cada etapa usa `WAITING / PROCESSING / COMPLETE / ERROR` y se expone con `StateFlow` desde `RetroPreparationViewModel`.

No se abre RetroArch si falla archivo, core, compatibilidad, BIOS verificable o Activity de lanzamiento. Después de instalar/importar un core, Oz vuelve a consultar RetroArch antes de entregar la ROM.

`RetroBiosValidator` se limita a nombres que ya estaban confirmados/documentados en Fix5; no se añadieron BIOS por inferencia.

## Decisiones de dependencias

Se conservaron AGP 8.10.1, Kotlin 2.1.21, KSP 2.1.21-2.0.1 y Gradle 8.11.1 para no introducir simultáneamente una migración de toolchain que aumente el riesgo de Termux/SDK 36.

Dependencias nuevas/actualizadas relevantes:

- Compose BOM `2026.06.00`.
- Material 3 desde BOM.
- Navigation Compose `2.9.8`.
- Activity/Activity Compose `1.13.0`.
- Lifecycle Runtime/ViewModel Compose `2.10.0` (compatibilidad SDK 36 + AGP 8.10.1).
- Paging Runtime/Compose `3.5.0`.
- Room `2.8.4`.
- Coil Compose/Network OkHttp `3.2.0` (fijado para mantener compatibilidad con Kotlin 2.1.21; Coil 3.5.0 requiere Kotlin 2.4.0).

Para Kotlin 2.x se usa `org.jetbrains.kotlin.plugin.compose`; no se introdujo una configuración antigua de Compose Compiler incompatible con Kotlin 2.

## iiSU — corrección de una premisa anterior

La documentación oficial actual de iiSU indica que **iiSU sí dispone de colecciones personalizadas nativas**. Por tanto, la frase de Fix5 “iiSU no tiene colecciones nativas” ya no es correcta para la versión actual de iiSU.

No obstante, esta corrección **no inventa** una API externa para crear colecciones de iiSU. Oz conserva sus colecciones como entidades propias y mantiene la sincronización individual de juegos con el puente iiSU existente hasta que exista/sea verificado un contrato público estable para administrar colecciones de iiSU desde aplicaciones externas.

## Errores encontrados y causa raíz

1. **Inicio y Catálogo duplicaban responsabilidades.** Causa: la pantalla principal Views había crecido desde un listado hasta asumir búsqueda/filtros globales. Solución: Inicio dashboard + Catálogo como único buscador global.
2. **Colecciones eran funcionales pero visualmente equivalentes a una lista Android.** Solución: rails/collages/hero Compose sin duplicar su lógica de repositorio.
3. **401/403 podía borrar credenciales demasiado pronto.** Causa: Fix5 trataba un rechazo puntual como invalidación definitiva. Solución: estados persistentes + contador + revalidación + limpieza solo explícita.
4. **Biblioteca no distinguía origen descargado/importado.** Solución: `libraryOrigin` mediante Room 5→6 no destructivo.
5. **Preflight seguía acoplado visualmente a Views.** Solución: ViewModel/StateFlow + `ComposeView` interoperable, conservando la lógica de lanzamiento.
6. **Durante el desarrollo se detectó una anotación `@Entity` desplazada al introducir `LibraryOrigin`.** Se corrigió antes del empaquetado y se añadió una comprobación específica al validador.
7. **Premisa iiSU desactualizada.** iiSU actual sí ofrece colecciones nativas; se documenta sin inventar integración externa.

## Base Fix5 y limitación de trazabilidad

El ZIP fuente exacto de Fix5 no estaba montado físicamente en el contenedor de esta sesión. Sí estaban disponibles Fix4 y la evidencia/parche/validación publicados para Fix5. Para no trabajar directamente sobre Fix4, se reconstruyó una base de equivalencia Fix5 aplicando los contratos documentados de Fix5 (Room 5, colecciones, descarga de colección, System/BIOS y preflight RetroArch) y el parche incremental de esta entrega se genera contra esa base reconstruida.

**Consecuencia:** el parche representa los cambios conceptuales desde Fix5, pero no puede garantizar byte-a-byte que aplique sin contexto a cualquier copia externa de Fix5. El ZIP completo Fix6 es el artefacto principal recomendado.

## Archivos nuevos respecto de la base Fix5 reconstruida

- `app/src/main/java/cl/gakuto/kizuna/data/RepositoryPagingSource.kt`
- `app/src/main/java/cl/gakuto/kizuna/ui/LegacyMainActivity.kt`
- `app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt`
- `app/src/main/java/cl/gakuto/kizuna/ui/compose/OzViewModel.kt`
- `app/src/main/java/cl/gakuto/kizuna/ui/compose/RetroPreparationPanel.kt`
- `app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzComponents.kt`
- `app/src/main/java/cl/gakuto/kizuna/ui/compose/theme/OzTheme.kt`
- `scripts/test_room_migration_5_6.py`
- `docs/FIX6_COMPOSE_MIGRATION_VALIDATION.md`

## Archivos modificados

- `02_COMPILAR_TERMUX.sh`
- `build.gradle.kts`
- `gradlew`
- `app/build.gradle.kts`
- `app/src/main/java/cl/gakuto/kizuna/auth/InternetArchiveAuthManager.kt`
- `app/src/main/java/cl/gakuto/kizuna/data/CatalogPaging.kt`
- `app/src/main/java/cl/gakuto/kizuna/data/Daos.kt`
- `app/src/main/java/cl/gakuto/kizuna/data/Entities.kt`
- `app/src/main/java/cl/gakuto/kizuna/data/LibraryRepository.kt`
- `app/src/main/java/cl/gakuto/kizuna/data/OzDatabase.kt`
- `app/src/main/java/cl/gakuto/kizuna/launch/AuthorizedSourceDownloader.kt`
- `app/src/main/java/cl/gakuto/kizuna/launch/CollectionDownloadManager.kt`
- `app/src/main/java/cl/gakuto/kizuna/launch/PersistentRomLibrary.kt`
- `app/src/main/java/cl/gakuto/kizuna/launch/SessionActivity.kt`
- `app/src/main/java/cl/gakuto/kizuna/ui/InternetArchiveLoginActivity.kt`
- `app/src/main/java/cl/gakuto/kizuna/ui/MainActivity.kt`
- `app/src/main/res/layout/activity_session.xml`
- `scripts/validate_project.py`

## Archivos eliminados

Ninguno.

## Validaciones y pruebas ejecutadas

### Validación estática

Resultado final:

```text
VALIDACIÓN ESTÁTICA CORRECTA — Oz RetroRom 0.6.0 Beta 3 (729 comprobaciones)
Sistemas: 44 · juegos curados: 192 · colecciones: 18
```

Comprueba, entre otros, identidad/versionado, catálogo, colecciones, ausencia de migración destructiva, infraestructura Compose, navegación, Auth V2, política 401/403, preflight, ausencia del fallback de core no verificable y recursos principales.

### Migraciones SQLite

```text
ROOM_MIGRATION_3_4_OK default=NULL data_preserved=1
ROOM_MIGRATION_5_6_OK default=NONE persisted=DOWNLOADED data_preserved=1
```

La prueba 5→6 verifica expresamente que una URI persistente preexistente no se modifica.

### Comprobación sintáctica Kotlin sin Android SDK

Se invocó `kotlinc` sobre el árbol Kotlin. Como este contenedor no dispone del classpath Android/AndroidX, el compilador reporta referencias Android no resueltas; no se detectaron diagnósticos de parser del tipo `expecting`, `unexpected tokens`, literal sin cerrar o bloque sintáctico roto. Esta comprobación no sustituye Gradle/KSP/Room.

### Compilación real solicitada

Comando final intentado:

```text
./gradlew :app:assembleDebug --no-daemon --max-workers=1 --console=plain --stacktrace
```

Resultado real: **NO COMPILÓ / NO LLEGÓ A EJECUTAR GRADLE**.

Causa externa previa a la compilación del proyecto:

- `gradle-wrapper.jar` no está disponible localmente;
- el bootstrap seguro intentó obtener el wrapper oficial y el contenedor no puede resolver `raw.githubusercontent.com` (`curl: (6) Could not resolve host`);
- `ANDROID_HOME` y `ANDROID_SDK_ROOT` están vacíos en este contenedor;
- el Java disponible es 21.0.10, mientras el proyecto/Termux se mantiene en Java 17.

Exit code registrado: `6`.

**No se generó APK. No se declara BUILD SUCCESSFUL.**

## Termux ARM64

`02_COMPILAR_TERMUX.sh`:

- fija Java 17 esperado;
- usa Android SDK 36;
- exige `aapt2` ARM64 de Termux;
- no genera ni distribuye `local.properties`;
- descarga `gradle-wrapper.jar` solo si falta;
- verifica SHA-256 del wrapper (`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`);
- el `gradle-wrapper.properties` fija checksum de Gradle 8.11.1 bin (`f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`);
- pasa `-Pandroid.aapt2FromMavenOverride` para reutilizar el binario ARM64 local.

## Investigación y fuentes primarias consultadas

Android / Jetpack:

- https://developer.android.com/develop/ui/compose/bom
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- https://developer.android.com/develop/ui/compose/state
- https://developer.android.com/guide/navigation
- https://developer.android.com/jetpack/androidx/versions
- https://developer.android.com/jetpack/androidx/releases/paging
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/privacy-and-security/keystore
- https://developer.android.com/privacy-and-security/risks/unsafe-uri-loading
- https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges

Internet Archive / ROMgi:

- https://archive.org/account/s3.php
- Internet Archive Developer Portal / IA-S3 documentation.
- https://github.com/caprado/romgi
- Código actual de autenticación/descarga de ROMgi revisado para sesión persistente, WebView/cookies, IA-S3, `LOW`, validación y `failureCount`.

RetroArch / Libretro:

- https://github.com/libretro/RetroArch
- https://docs.libretro.com/
- documentación oficial de cores/BIOS ya contrastada en Fix5; esta iteración no amplía nombres por suposición.

iiSU:

- https://github.com/iisu-network/iiSU
- https://github.com/iisu-network/iiSU/releases

Gradle:

- https://gradle.org/release-checksums/
- https://github.com/gradle/gradle/tree/v8.11.1/gradle/wrapper

## Limitaciones conocidas y pendientes

1. Falta ejecutar Gradle/KSP/Room con Android SDK real; el contenedor actual no permite certificar compilación.
2. Falta prueba en dispositivo de WebView/login IA real, persistencia después de reinicio y descargas protegidas.
3. Falta prueba en dispositivo de redirects IA y de la política `STALE/INVALID` con errores reales.
4. Falta prueba con RetroArch instalado para las cinco etapas, instalación de core y retorno a Oz.
5. Falta prueba real de gamepad/D-pad en distintos dispositivos; la arquitectura de foco está preparada, no certificada.
6. Configuración avanzada continúa en Views durante esta fase; se accede mediante un bridge Compose.
7. XML/Views no se eliminan todavía. Esto es intencional para mantener migración progresiva.
8. El contrato externo de colecciones de iiSU no se inventa; la sincronización de colección de Oz continúa juego por juego.
9. El parche incremental se basa en una reconstrucción equivalente de Fix5; para instalación/desarrollo se recomienda el ZIP completo Fix6.

## Conclusión

Fix6 no intenta completar toda la migración de una sola vez. Establece la nueva columna vertebral Compose, separa responsabilidades de navegación, conserva la lógica Room/ROMgi/RetroArch/iiSU, añade origen persistente de biblioteca, endurece Auth V2 y lleva el preflight de RetroArch a un modelo ViewModel/StateFlow interoperable. El siguiente criterio de avance debe ser compilar y validar esta base en Termux/Android real antes de retirar más XML o ampliar integraciones.

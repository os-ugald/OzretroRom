# Informe de validación — Oz RetroRom 0.6.0 Beta 3

Fecha: **6 de agosto de 2026**.

## Alcance

Se revisó el código fuente completo preparado desde Beta 2, recursos XML, assets JSON, consultas Room, integración ROMgi, selección de motores, RetroArch, iiSU, FinalBurn Neo, scripts y documentación pública.

## Resultados

### Validación estática

```text
VALIDACIÓN ESTÁTICA CORRECTA — Oz RetroRom 0.6.0 Beta 3 (514 comprobaciones)
Sistemas: 44 · juegos curados: 192 · colecciones: 16
```

Incluye parseo XML/JSON y comprobaciones de versión, Room v4, filtros globales, resolver ROMgi, ambigüedad, package visibility, content URI, cores, iiSU, FBNeo, progreso, seguridad y nombres públicos.

### Prueba ejecutable del resolvedor

```text
MATCHER_SMOKE_OK strategy=CANONICAL_TITLE region=USA reordered=ALIAS
```

Casos comprobados:

- `Super Mario World 2: Yoshi’s Island (World)` frente a variante USA;
- apóstrofe tipográfico, dos puntos/guion y región alternativa;
- título reordenado sin fuzzy;
- orden regional determinista;
- “Super Mario World” sin metadato delimitado devuelve región `Other`;
- `(World)` delimitado sí se reconoce.

### Pruebas ejecutables de filtros y perfiles

```text
CATALOG_SMOKE_OK
SQLITE_PAGING_OK rows=1 selected=world
EMULATOR_PROFILE_SMOKE_OK profiles=31
```

Se compiló el código real de filtros/SQL con stubs mínimos de AndroidX, se ejecutó la consulta generada sobre SQLite y se verificaron 31 perfiles únicos, PS2 con Play!, SwanStation como core y las actividades explícitas de PPSSPP/melonDS.

### Recursos y scripts

- XML y JSON: válidos.
- scripts Python: compilación sintáctica correcta.
- scripts Bash: `bash -n` correcto.
- escaneo público: no aparece el nombre histórico descartado en app, README, manual, notas o documentos Beta 3.
- no existe `QUERY_ALL_PACKAGES`, cleartext habilitado ni rutas privadas sintetizadas en `RetroArchManager`.

## Compilación Android

Se ejecutaron realmente:

```bash
./gradlew :app:testDebugUnitTest --stacktrace
./gradlew :app:assembleDebug --stacktrace
```

Ambos terminaron antes de iniciar Gradle:

```text
curl: (6) Could not resolve host: raw.githubusercontent.com
EXIT_CODE=6
```

El entorno no tenía `ANDROID_HOME` ni `ANDROID_SDK_ROOT`. No se generó APK y no se certifica compilación Android. Los registros completos se incluyen como `gradle-test.log` y `gradle-assemble-debug.log` en el paquete de evidencias.

## Limitaciones que permanecen deliberadamente seguras

- Dolphin y Play! se detectan/abren, pero no reciben una ROM directamente porque la API o actividad externa requerida no está documentada/exportada.
- DuckStation y Azahar usan resolución en el dispositivo; no se fija una actividad no publicada.
- Flycast y Yaba Sanshiro usan paquetes respaldados por documentación secundaria y siempre requieren que Android resuelva el intent.
- NetherSX2 y DraStic no tienen integración directa inventada.
- `trusted_cores.json` se entrega sin binarios aprobados; release permite importar o usar el actualizador de RetroArch, pero no descarga automáticamente un core sin URL y SHA-256 fijados.

## Conclusión

Los criterios comprobables sin toolchain Android quedan implementados y validados. La única condición de aceptación no certificada es la compilación Gradle/APK, bloqueada por DNS y ausencia de SDK en este entorno; se entrega el error exacto y el procedimiento Termux.

## Fix 3 validation addendum

Executed: `scripts/test_fix3_contracts.py`, Room migration check, XML parsing, standalone Kotlin source
policy smoke test and Android-auth Kotlin syntax/type check with API-shaped stubs. Full Gradle test and
APK assembly remain to be executed in Termux because the assistant environment could not resolve the
Gradle wrapper host.


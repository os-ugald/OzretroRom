# Oz RetroRom 0.6.0 Beta 3 — Fix6.10 RetroArch Core Fallback

## Síntoma observado

La ROM persistente ya era encontrada y validada, pero el preflight se detenía en **Verificando core** con:

- `RetroArch no permitió verificar los cores instalados`
- `Oz no abrirá RetroArch sin confirmar el core real`

## Causa

Fix6.9 trataba `CoreQuery.supported=false` como prueba de que no era seguro lanzar. Esa equivalencia era demasiado estricta: significa que el build instalado de RetroArch no respondió al contrato de enumeración, no que el core solicitado esté ausente.

Además, la consulta saliente estaba limitada por paquete pero no resolvía y apuntaba explícitamente al receiver exportado documentado por RetroArch.

## Investigación primaria

Fuente oficial actual de RetroArch Android:

- Manifest: `pkg/android/phoenix/AndroidManifest.xml`
  - exporta `com.retroarch.browser.retroactivity.RetroActivityFuture`
  - exporta `com.retroarch.browser.debug.CoreSideloadActivity`
  - exporta `com.retroarch.browser.receiver.InstalledCoresReceiver`
  - el receiver escucha `com.retroarch.QUERY_INSTALLED_CORES`
- `MainMenuActivity.java` usa `getApplicationInfo().dataDir + "/cores/"` como ruta predeterminada de cores.
- Issue oficial #17433 confirma que los lanzamientos externos modernos requieren la ruta completa al `.so` del core.
- Libretro mantiene SwanStation como core Sony PlayStation y su repositorio documenta CHD y ARM64.

Android:

- `PackageManager.queryBroadcastReceivers()` permite resolver receivers capaces de manejar un broadcast.
- `sendOrderedBroadcast()` permite recuperar result extras/data de receivers ordenados.

## Cambios

1. `RetroArchManager.queryInstalledCores()`:
   - resuelve primero `InstalledCoresReceiver` mediante `PackageManager`;
   - verifica que el receiver pertenezca al paquete objetivo, esté habilitado y exportado;
   - dirige el broadcast explícitamente al componente resuelto;
   - aumenta timeout de 1.8 s a 4 s;
   - acepta respuesta mediante broadcast `INSTALLED_CORES_RESULT`, result extras y result data.

2. Si el receiver no existe o no responde:
   - Oz ya no interpreta eso como “core ausente”;
   - prioriza un core previamente confirmado;
   - si no hay historial, usa el core recomendado por el perfil y luego los candidatos declarados;
   - construye la ruta con `ApplicationInfo.dataDir`, nunca con `/data/user/...` hardcodeado;
   - conserva validación de formato, BIOS y Activity exportada antes del launch.

3. PS1:
   - `SwanStation ↔ PCSX ReARMed` se trata como alternativa bidireccional declarada para PlayStation;
   - esto evita que metadata histórica `pcsx_rearmed` bloquee un perfil actual que recomienda SwanStation.

4. No se marca un core como confirmado únicamente por usar el fallback de ruta estándar.

## Sin cambios

- Room/schema y datos del usuario.
- Internet Archive / ROMgi.
- Descarga background y HTTP Range.
- SAF y biblioteca persistente.
- iiSU.
- Colecciones y artwork.
- Versión pública: versionCode 60003 / versionName 0.6.0-beta.3.

## Prueba específica

`scripts/test_fix610_retroarch_core_fallback.py`

Valida 17 invariantes del nuevo contrato.

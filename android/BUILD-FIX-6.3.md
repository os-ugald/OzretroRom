# Oz RetroRom 0.6.0 Beta 3 — Fix 6.3 Compose Compile Fix

Esta corrección incremental mantiene `versionCode = 60003`, `versionName = "0.6.0-beta.3"`, `applicationId = cl.gakuto.kizuna`, compile/target SDK 36 y AGP 8.10.1.

## Causa raíz confirmada en Termux

El build real de Fix 6.2 superó AAR metadata, Manifest, recursos, `processDebugResources` y KSP. La corrección Coil/Kotlin de Fix 6.2 funcionó: ya no aparece `kotlin-stdlib 2.4.0`. El compilador llegó a código fuente Compose y reportó exactamente tres diagnósticos, derivados de dos errores:

1. `OzApp.kt`: la llamada a `OzGameCover` usaba trailing lambda aunque `onClick` no es el último parámetro de la función (`showTitle` viene después). Kotlin interpretó la lambda contra el parámetro `Boolean`.
2. `OzComponents.kt`: `LocalContext.current` se obtenía indirectamente mediante una función `@Composable` invocada dentro de `remember { ... }`, cuyo bloque de cálculo no es composable.

## Corrección

- `OzGameCover(..., onClick = { ... })` usa argumento nombrado explícito.
- `LocalContext.current` se obtiene en el cuerpo `@Composable` y el `Context` resultante se usa dentro de `remember`.
- Las claves de `remember` incluyen las preferencias de carátula que afectan el resultado.
- Se elimina el helper `preferencesContext`, que ya no es necesario.
- No se cambian Room, migraciones, SAF, ROMgi, RetroArch, iiSU, Internet Archive Auth V2, dependencias, SDK ni AGP.
- `scripts/validate_project.py` incorpora guardias específicas para estos dos contratos de compilación.

## Sobre aapt2

Los mensajes `No package ID 7f found for resource ID ...` siguen apareciendo en Termux, pero el build completa `:app:processDebugResources` antes de fallar en Kotlin. Por ello no se atribuye este fallo a aapt2 ni se modifica el override ARM64 en esta corrección.

## Verificación

```bash
python scripts/validate_project.py
./02_COMPILAR_TERMUX.sh 2>&1 | tee build-fix6.3.txt
```

No se declara `BUILD SUCCESSFUL` hasta obtenerlo en la compilación real de Termux.

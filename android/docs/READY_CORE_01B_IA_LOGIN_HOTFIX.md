# Oz Ready Core 01B — Internet Archive login hotfix

Fecha: 2026-08-12

Base exacta: fuente interna de `Oz-Ready-Core-01B-Editions-Multidisc-50e2dce.zip`

Rama local: `feature/oz-ready-core-01b-ia-hotfix`

## Resultado implementado

El login web deja de depender exclusivamente de los atributos históricos
`name="access"` y `name="secret"`. La página actual de Internet Archive puede mostrar las claves
en inputs sin esos identificadores; por eso Oz veía una sesión iniciada y dos claves visibles, pero
el intercambio devolvía `null`.

El hotfix añade tres rutas convergentes, todas validadas mediante `Authorization: LOW` antes de
guardar una sesión:

1. captura del DOM visible en `https://archive.org/account/s3.php`;
2. lectura HTTPS autenticada mediante las cookies del WebView;
3. ingreso manual de Access Key y Secret Key como recuperación.

## Controles de seguridad

- `evaluateJavascript` se ejecuta sólo si esquema, host, puerto y ruta corresponden a la página
  oficial exacta;
- no se instala `addJavascriptInterface`;
- no se leen campos de contraseña;
- no se usa XHR/fetch desde Oz;
- no se pulsa ni reproduce la acción **Destroy Old Keys – Generate New Keys**;
- sólo se aceptan dos valores alfanuméricos distintos de 16 caracteres;
- el fallback posicional se acepta únicamente si la página incluye los dos rótulos oficiales
  `S3 access key` y `S3 secret key` y existen exactamente dos candidatos;
- el Secret Key del fallback manual permanece enmascarado;
- claves y cookies conservan cifrado AES-GCM con Android Keystore;
- se conservan `oz_internet_archive_auth`, `session.v1` y
  `oz_retrorom_ia_session_v1`, por lo que una actualización correctamente firmada mantiene una
  sesión existente;
- el sondeo IA-S3 intenta `HEAD` y después `GET`, ya que algunos proxies rechazan HEAD aunque GET
  autenticado funcione.

## Pruebas completadas

- `scripts/validate_project.py`: 1.203 comprobaciones;
- `scripts/test_ready_core_01b.py`: 76 contratos;
- `scripts/test_ready_core_01b_ia_login.py`: 50 contratos;
- `scripts/test_fix4_contracts.py`: correcto;
- `:app:testDebugUnitTest`: 41 pruebas, 0 fallos;
- `:app:assembleDebug`: correcto;
- `git diff --check`: correcto.

APK de verificación final: 27.767.862 bytes; SHA-256
`ee4ccb7e82cc81158afb79039843069e69ac2cee78c71a716b718a3fc03148d7`.

### Matriz histórica

Se ejecutaron además todos los `scripts/test_*.py`:

- hotfix: 35 scripts, 26 pasan y 9 fallan;
- base exacta 01B sin el hotfix: 34 scripts, 25 pasan y los mismos 9 fallan;
- regresiones añadidas por el hotfix: 0.

Los nueve validadores heredados que ya fallaban en la base están obsoletos: seis exigen
literalmente el antiguo `versionCode 60010`; `test_fix3_contracts.py` exige el selector plano
`setSingleChoiceItems` retirado por Ready Core 01B; y los dos restantes exigen tokens visuales de
layouts anteriores. Por transparencia no se cuentan como aprobados ni se modifican dentro de este
hotfix. Los gates vigentes son `validate_project.py`, Ready Core 01B, IA login y JUnit/Gradle.

Casos JUnit nuevos:

- HTML actual sin `name`/`id`;
- HTML histórico con `name`/`id` y atributos reordenados;
- input oculto y tokens irrelevantes;
- página ambigua con dos o tres tokens;
- retorno de `evaluateJavascript` entrecomillado;
- claves incompletas o duplicadas;
- origen, subdominio, userinfo, puerto y ruta hostiles.

## Prueba física obligatoria

La compilación automatizada no prueba cookies reales, WebView del dispositivo ni la respuesta viva
de Internet Archive. Para cerrar el gate:

1. rotar inmediatamente las claves que quedaron visibles en las capturas compartidas;
2. compilar con el mismo keystore que firmó el APK instalado;
3. instalar como actualización, sin desinstalar;
4. confirmar que los datos, región y biblioteca siguen presentes;
5. probar login automático con una cuenta/sesión limpia;
6. confirmar que Oz se cierra en estado **Conectado** sin mostrar ni registrar claves;
7. descargar un archivo pequeño que requiera autenticación IA;
8. repetir con dos testers y registrar versión de Android + versión de WebView;
9. probar una vez **Usar claves** si la captura automática falla;
10. no solicitar capturas de la página IA-S3; aceptar sólo captura del estado **Conectado** en Oz.

## Firma del APK de verificación

El APK generado en este entorno usa una clave debug temporal distinta a la de Ready Core 01B. Sirve
para comprobar compilación, estructura y firma, pero no debe instalarse encima de una beta ya
instalada ni distribuirse a los testers. La entrega incluye fuente y parche para recompilar con el
keystore original.

## Fuentes oficiales revisadas

- Internet Archive — obtención de credenciales: https://archive.org/developers/tutorial-get-ia-credentials.html
- Android `CookieManager`: https://developer.android.com/reference/android/webkit/CookieManager
- Android `WebView.evaluateJavascript`: https://developer.android.com/reference/android/webkit/WebView#evaluateJavascript(java.lang.String,%20android.webkit.ValueCallback%3Cjava.lang.String%3E)
- ROMgi, flujo IA vigente revisado como referencia de compatibilidad:
  https://github.com/caprado/romgi/blob/main/lib/screens/internet_archive_login_screen.dart

## Gate posterior

Dreamcast continúa pausado. Sólo se abre Ready Core 02 después de cerrar en dispositivo tanto
Ready Core 01B Editions/Multidisc como este hotfix IA.

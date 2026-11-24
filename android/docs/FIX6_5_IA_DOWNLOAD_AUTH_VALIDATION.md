# Oz RetroRom 0.6.0 Beta 3 — Fix6.5 IA Download Auth

## Objetivo
Corregir el HTTP 401 observado al descargar una fuente ROMgi alojada en Internet Archive después de que Oz ya hubiese guardado una sesión IA-S3 válida.

## Causa raíz
`AuthorizedSourceDownloader` activaba `InternetArchiveAuthManager` únicamente cuando `AuthorizedSource.requiresInternetArchiveAuth == true`. Ese valor proviene del campo `requires_auth` del catálogo ROMgi y representa metadata de catálogo, no la respuesta autoritativa del servidor en el momento de descargar.

En el caso observado, la fuente fue presentada como `ROMgi · Internet Archive`, pero al recibir HTTP 401 el código cayó en el mensaje genérico `El servidor rechazó la descarga con HTTP 401.`. En Fix6.3 ese mensaje solo es alcanzable cuando el adaptador IA no está activo, por lo que las credenciales almacenadas no estaban siendo aplicadas a esa petición.

## Corrección
1. Si ROMgi marca `requires_auth=true`, la sesión IA continúa siendo obligatoria antes de iniciar.
2. Si ROMgi marca el enlace como público pero Oz ya dispone de una sesión IA válida, el downloader conserva el gestor de autenticación de forma oportunista.
3. Los headers de autenticación solo se producen para URLs HTTPS cuyo host sea `archive.org` o termine en `.archive.org`; no se envían credenciales a terceros.
4. Los headers se recalculan en cada redirección.
5. Para una URL IA, se intenta primero el mecanismo normal de la sesión (cookie capturada cuando existe). Si IA responde 401/403, se reintenta exactamente una vez la misma URL confiable usando `Authorization: LOW access:secret`.
6. Si la sesión no existe y una URL IA que parecía pública devuelve 401/403, el mensaje indica que esa fuente ahora exige conectar Internet Archive.
7. Los diagnósticos nuevos solo registran host, flags booleanos, estado y códigos HTTP; nunca registran cookies, Access Key, Secret Key ni el valor de Authorization.

## Archivos modificados
- `app/src/main/java/cl/gakuto/kizuna/launch/AuthorizedSourceDownloader.kt`
- `app/src/main/java/cl/gakuto/kizuna/auth/InternetArchiveAuthManager.kt`
- `scripts/validate_project.py`

## Compatibilidad conservada
- `applicationId = cl.gakuto.kizuna`
- `versionName = 0.6.0-beta.3`
- `versionCode = 60003`
- compileSdk / targetSdk 36
- AGP 8.10.1
- Kotlin 2.1.21
- Gradle 8.11.1
- Java 17 / Termux ARM64
- Room schema 6 sin migración adicional
- SAF y biblioteca persistente sin cambios
- RetroArch, iiSU, colecciones, Paging y Compose sin cambios

## Validaciones ejecutadas
- `python scripts/validate_project.py`: 744 comprobaciones correctas.
- `python scripts/test_room_migration_3_4.py`: OK, datos preservados.
- `python scripts/test_room_migration_5_6.py`: OK, datos preservados.
- `python scripts/test_fix3_contracts.py`: OK.
- `python scripts/test_fix4_contracts.py`: OK.

## Build real
Fix6.3 fue compilado exitosamente por el usuario en Termux. El entorno de entrega no es Termux y no contiene `$PREFIX` ni Android SDK 36, por lo que no puede reproducir aquí el build Android final. No se declara `BUILD SUCCESSFUL` para Fix6.5 hasta repetir `./02_COMPILAR_TERMUX.sh` en el entorno Termux del usuario.

## Investigación primaria
La implementación actual de ROMgi prepara headers mediante un adaptador de host antes de cada descarga y trata 401 como error de autenticación. Su `IAAuthManager` conserva IA-S3 access/secret y contempla cookies para descargas de archive.org y `Authorization: LOW` como alternativa. ROMgi también documenta específicamente el problema de redirecciones de IA/CDN y resuelve la URL final antes de descargar para evitar perder headers entre hosts.

Internet Archive publica las claves IA-S3 desde `/account/s3.php`; el cliente/skill oficial `ia` usa esas credenciales para configuración/autenticación. No se inventaron endpoints ni formatos de credenciales nuevos en este fix.

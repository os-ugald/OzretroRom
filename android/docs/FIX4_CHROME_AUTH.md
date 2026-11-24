# Fix 4 — Internet Archive mediante Chrome

## Decisión

Android recomienda Custom Tabs para autenticación/sitios de terceros. Oz abre directamente:

`https://archive.org/account/s3.php`

Chrome se fija como paquete preferido cuando está instalado. Si no lo está, Android puede usar otro navegador HTTPS.

## Retorno a Oz

Cerrar la Custom Tab o pulsar Atrás devuelve al usuario a `InternetArchiveLoginActivity`. El menú también incluye **Volver a Oz RetroRom**.

El retorno no se presenta como un callback de autenticación: Internet Archive no publica actualmente un OAuth/AppAuth redirect Android que permita a Oz saber que el login de Chrome terminó ni transferir sus cookies. Implementar extracción de cookies sería inseguro y además Chrome mantiene ese estado aislado de la app.

## Conexión persistente

La página oficial expone IA-S3 Access Key + Secret Key para la cuenta autenticada. El usuario los importa una única vez. Oz:

1. no almacena la contraseña;
2. valida el par por HTTPS;
3. cifra la sesión con AES/GCM y una clave no exportable de Android Keystore;
4. reutiliza esas credenciales en futuras descargas HTTPS compatibles.

Las descargas públicas de Internet Archive no requieren autenticación.

## Fuentes primarias

- Android Web content / Custom Tabs: https://developer.android.com/develop/ui/views/layout/webapps
- Internet Archive official IA skill: https://github.com/internetarchive/internet-archive-skills/blob/main/skills/ia/SKILL.md

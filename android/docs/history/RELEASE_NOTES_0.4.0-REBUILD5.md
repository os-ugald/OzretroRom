# Notas de versión — Oz RetroRom 0.4.0 Rebuild 5

## Objetivo

Eliminar la obligación de escribir URLs o seleccionar una ROM cada vez que el usuario pulsa **Jugar**, integrando el catálogo ROMgi dentro del flujo sencillo de Oz RetroRom.

## Incorporado

- Resolución automática local → ROMgi → emulador.
- Descarga y caché automática de `romdb.db.gz`.
- Compatibilidad con HTTP y torrent selectivo.
- Coincidencia por título canónico para etiquetas como `(USA)` o `[Rev 1]`.
- Preferencia de región configurable.
- Resolución opcional de BIOS FBNeo desde ROMgi.
- Pantalla de Configuración reforzada para no cerrarse si falla un componente secundario.
- Parámetros de Wi‑Fi, torrent, región y limpieza de catálogo.
- Eliminación de la interfaz de URLs de ROM en la ficha del juego.

## Límites actuales

- Las fuentes marcadas como `requires_auth` por ROMgi se descartan porque Oz aún no incorpora un inicio de sesión de Internet Archive.
- La disponibilidad depende de las fuentes que publique el catálogo ROMgi y de la compatibilidad del emulador/núcleo.
- La APK debe compilarse y probarse en Termux para validar el comportamiento completo de Android.

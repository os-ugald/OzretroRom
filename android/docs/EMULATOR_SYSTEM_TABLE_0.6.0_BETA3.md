# Tabla interna de sistemas, cores y emuladores — Beta 3

**Regla general:** el modo Automático detecta aplicaciones instaladas, aplica la preferencia del usuario, valida extensión y `content URI`, exige una actividad exportada y, si no existe una ruta externa segura, usa RetroArch. “Detectar” no significa “lanzar”: las filas **solo abrir app** no reciben una ROM.

| Sistema | Predeterminado | Core RetroArch | Externo / paquete | Método de lanzamiento | Formatos declarados | BIOS | Alternativa y motivo |
|---|---|---|---|---|---|---|---|
| NES | RetroArch | `mesen_libretro_android.so` | — | Bridge RetroArch explícito | NES, FDS, UNF, UNIF, ZIP | `disksys.rom` para FDS | Mesen: precisión; configuración manual de otro core disponible. |
| SNES | RetroArch | `snes9x_libretro_android.so` | — | Bridge RetroArch | SFC, SMC, FIG, ZIP | No habitual | Snes9x: equilibrio ARM64. |
| Game Boy | RetroArch | `gambatte_libretro_android.so` | — | Bridge RetroArch | GB, ZIP | No | Gambatte: estabilidad/precisión. |
| Game Boy Color | RetroArch | `gambatte_libretro_android.so` | — | Bridge RetroArch | GBC, GB, ZIP | No | Gambatte. |
| Game Boy Advance | RetroArch | `mgba_libretro_android.so` | — | Bridge RetroArch | GBA, ZIP, 7Z | `gba_bios.bin` opcional | mGBA: compatibilidad y mantenimiento. |
| Nintendo 64 | RetroArch | `mupen64plus_next_gles3_libretro_android.so` | — | Bridge RetroArch | N64, Z64, V64, ZIP, 7Z | No | Mupen64Plus-Next: ruta Android consistente. |
| Nintendo DS | Externo | `melonds_libretro_android.so` | melonDS / `me.magnum.melonds` | Explícito a `me.magnum.melonds.ui.emulator.EmulatorActivity`, `content://`, permiso lectura | NDS, ZIP, 7Z | Opcional para DS; requerida para DSi | Integración oficial documentada. DraStic no se integra sin API primaria estable. |
| Nintendo DSi | Externo | `melonds_libretro_android.so` | melonDS / `me.magnum.melonds` | Igual que DS | DSI, NDS | BIOS, firmware y NAND DSi | melonDS. |
| Nintendo 3DS | Externo condicionado | `azahar_libretro_android.so` | Azahar / `org.azahar_emu.azahar` | `ACTION_VIEW` solo si Android resuelve actividad exportada | 3DS, CCI, CXI, 3DSX, APP | Keys/sistema según contenido | Azahar es el sucesor vigente investigado; no se fija actividad no documentada. |
| GameCube | Externo, con fallback | `dolphin_libretro_android.so` | Dolphin / `org.dolphinemu.dolphinemu` | **Solo abrir app**: `EmulationActivity` no está exportada en manifiesto actual | ISO, GCM, RVZ, WIA, GCZ, CISO, DOL, ELF | No habitual | Dolphin independiente recomendado; RetroArch si se requiere lanzamiento automático. |
| Wii | Externo, con fallback | `dolphin_libretro_android.so` | Dolphin / `org.dolphinemu.dolphinemu` | Solo abrir app | ISO, RVZ, WBFS, WIA, GCZ, DOL, ELF | NAND/keys para funciones concretas | Dolphin independiente; no se inventa intent. |
| Master System | RetroArch | `genesis_plus_gx_libretro_android.so` | — | Bridge RetroArch | SMS, BIN, ZIP | No | Genesis Plus GX. |
| Mega Drive / Genesis | RetroArch | `genesis_plus_gx_libretro_android.so` | — | Bridge RetroArch | MD, GEN, BIN, SMD, ZIP | No | Genesis Plus GX. |
| Game Gear | RetroArch | `genesis_plus_gx_libretro_android.so` | — | Bridge RetroArch | GG, BIN, ZIP | No | Genesis Plus GX. |
| Sega CD / Mega-CD | RetroArch | `genesis_plus_gx_libretro_android.so` | — | Bridge RetroArch | CUE, BIN, CHD, M3U | BIOS regional requerida | Genesis Plus GX. |
| Sega Saturn | Automático | `mednafen_saturn_libretro_android.so` | Yaba Sanshiro 2 Pro / `org.devmiyax.yabasanshioro2.pro` | `ACTION_VIEW` solo si resuelto; paquete documentado secundariamente | CUE, BIN, CHD, M3U, ISO | Recomendada/requerida según motor | Beetle Saturn prioriza precisión; Yaba puede priorizar rendimiento. |
| Dreamcast | Externo condicionado | `flycast_libretro_android.so` | Flycast / `com.flycast.emulator` | `ACTION_VIEW` solo si resuelto; paquete documentado secundariamente | GDI, CHD, CDI, CUE, M3U | Opcional DC; requerida Naomi/Atomiswave | Flycast comparte proyecto entre app/core. |
| PlayStation | Automático | `swanstation_libretro_android.so` | DuckStation / `com.github.stenzek.duckstation` | `ACTION_VIEW` solo si actividad exportada de la instalación responde | CUE, BIN, CHD, M3U, ISO, PBP | Recomendada | SwanStation es **core**, no app independiente; PCSX-ReARMed queda como fallback de core. |
| PlayStation 2 | RetroArch | `play_libretro_android.so` | Play! / `com.virtualapplications.play` | **Solo abrir app**: paquete oficial verificado, API externa no documentada | ISO, CHD, CSO, BIN, CUE, ISZ, ELF | Play! no exige BIOS externa | LRPS2 no funciona en Android/ARM. NetherSX2 no se integra sin paquete/actividad/API primaria verificable. |
| PSP | Externo | `ppsspp_libretro_android.so` | PPSSPP / `org.ppsspp.ppsspp` | Explícito a `org.ppsspp.ppsspp.PpssppActivity`, `ACTION_VIEW`, `content://` | ISO, CSO, CHD, ELF, PBP | No | PPSSPP publica manifiesto compatible. |
| Arcade / FinalBurn Neo | RetroArch | `fbneo_libretro_android.so` | — | Bridge RetroArch tras validar ZIP/romset | ZIP, 7Z | BIOS del mismo set | Set exacto y DAT de la versión del core; nunca descriptor virtual. |
| Neo Geo Pocket | RetroArch | `mednafen_ngp_libretro_android.so` | — | Bridge RetroArch | NGP, NGC, ZIP | No | Beetle NeoPop. |
| Neo Geo Pocket Color | RetroArch | `mednafen_ngp_libretro_android.so` | — | Bridge RetroArch | NGC, NGP, ZIP | No | Beetle NeoPop. |
| PC Engine / TurboGrafx-16 | RetroArch | `mednafen_pce_fast_libretro_android.so` | — | Bridge RetroArch | PCE, ZIP | No para HuCard | Beetle PCE Fast. |
| PC Engine CD | RetroArch | `mednafen_pce_fast_libretro_android.so` | — | Bridge RetroArch | CUE, BIN, CHD, M3U | `syscard3.pce` | Beetle PCE Fast. |
| WonderSwan | RetroArch | `mednafen_wswan_libretro_android.so` | — | Bridge RetroArch | WS, ZIP | No | Beetle Cygne. |
| WonderSwan Color | RetroArch | `mednafen_wswan_libretro_android.so` | — | Bridge RetroArch | WSC, WS, ZIP | No | Beetle Cygne. |
| Atari Lynx | RetroArch | `handy_libretro_android.so` | — | Bridge RetroArch | LNX, ZIP | `lynxboot.img` según core | Handy. |
| Atari 2600 | RetroArch | `stella_libretro_android.so` | — | Bridge RetroArch | A26, BIN, ZIP | No | Stella. |
| Atari 5200 | RetroArch | `a5200_libretro_android.so` | — | Bridge RetroArch | A52, BIN, ZIP | BIOS 5200 | a5200. |
| Atari 7800 | RetroArch | `prosystem_libretro_android.so` | — | Bridge RetroArch | A78, BIN, ZIP | Opcional según región | ProSystem. |

## Niveles de certeza

- **Verificado en fuente primaria y codificado explícitamente:** RetroArch, PPSSPP, melonDS, paquetes Dolphin/Azahar/Play!, restricciones Android.
- **Paquete conocido, actividad no fijada:** DuckStation y Azahar; Oz consulta Android en tiempo de ejecución.
- **Documentación primaria insuficiente para API externa:** Dolphin, Play!, NetherSX2, DraStic; no se entrega ROM directamente.
- **Dato de paquete apoyado por documentación secundaria:** Flycast y Yaba Sanshiro 2; se exige resolución real en el dispositivo y se documenta esta limitación.

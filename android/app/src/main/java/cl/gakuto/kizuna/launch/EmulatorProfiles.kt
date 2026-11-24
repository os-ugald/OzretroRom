package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.data.EngineMode

enum class ExternalLaunchKind {
    VERIFIED_EXPLICIT_CONTENT,
    RUNTIME_RESOLVED_VIEW,
    APP_ONLY,
}

data class ExternalEmulatorApp(
    val name: String,
    val packageName: String,
    val activityName: String? = null,
    val launchKind: ExternalLaunchKind,
    val contentUriSupported: Boolean,
    val officialStatus: String,
    val acceptedExtensions: Set<String>,
)

data class EmulatorProfile(
    val systemId: String,
    val systemName: String,
    val recommendedEngine: EngineMode,
    val retroArchCore: String,
    val externalApps: List<ExternalEmulatorApp>,
    val acceptedExtensions: Set<String>,
    val bios: String,
    val secondary: String,
    val rationale: String,
)

/**
 * Beta 3 recommendation table. Package/activity pairs are included only when verified in an
 * upstream manifest or source tree. RUNTIME_RESOLVED_VIEW entries never assume an activity:
 * Android must resolve an exported ACTION_VIEW target on the installed build before Oz uses it.
 */
object EmulatorProfiles {
    private val ppsspp = ExternalEmulatorApp(
        name = "PPSSPP",
        packageName = "org.ppsspp.ppsspp",
        activityName = "org.ppsspp.ppsspp.PpssppActivity",
        launchKind = ExternalLaunchKind.VERIFIED_EXPLICIT_CONTENT,
        contentUriSupported = true,
        officialStatus = "Paquete, actividad y ACTION_VIEW verificados en el manifiesto oficial",
        acceptedExtensions = setOf("iso", "cso", "chd", "elf", "pbp"),
    )
    private val melonDs = ExternalEmulatorApp(
        name = "melonDS",
        packageName = "me.magnum.melonds",
        activityName = "me.magnum.melonds.ui.emulator.EmulatorActivity",
        launchKind = ExternalLaunchKind.VERIFIED_EXPLICIT_CONTENT,
        contentUriSupported = true,
        officialStatus = "Integración por content URI documentada por el proyecto oficial",
        acceptedExtensions = setOf("nds", "zip", "7z"),
    )
    private val duckStation = ExternalEmulatorApp(
        name = "DuckStation",
        packageName = "com.github.stenzek.duckstation",
        launchKind = ExternalLaunchKind.RUNTIME_RESOLVED_VIEW,
        contentUriSupported = true,
        officialStatus = "Paquete oficial; lanzamiento directo condicionado al intent exportado por la versión instalada",
        acceptedExtensions = setOf("cue", "bin", "chd", "m3u", "iso", "pbp"),
    )
    private val dolphin = ExternalEmulatorApp(
        name = "Dolphin Emulator",
        packageName = "org.dolphinemu.dolphinemu",
        launchKind = ExternalLaunchKind.APP_ONLY,
        contentUriSupported = false,
        officialStatus = "Paquete oficial; EmulationActivity no es exportada en el manifiesto actual",
        acceptedExtensions = setOf("iso", "gcm", "rvz", "wbfs", "wia", "dol", "elf"),
    )
    private val azahar = ExternalEmulatorApp(
        name = "Azahar",
        packageName = "org.azahar_emu.azahar",
        launchKind = ExternalLaunchKind.RUNTIME_RESOLVED_VIEW,
        contentUriSupported = true,
        officialStatus = "Paquete vanilla oficial; Oz exige resolución real del intent en el dispositivo",
        acceptedExtensions = setOf("3ds", "3dsx", "cci", "cxi", "app"),
    )
    private val flycast = ExternalEmulatorApp(
        name = "Flycast",
        packageName = "com.flycast.emulator",
        launchKind = ExternalLaunchKind.RUNTIME_RESOLVED_VIEW,
        contentUriSupported = true,
        officialStatus = "Paquete identificado en documentación secundaria de frontend; la actividad solo se acepta si Android resuelve ACTION_VIEW",
        acceptedExtensions = setOf("gdi", "chd", "cdi", "cue", "bin", "zip", "7z"),
    )
    private val playPs2 = ExternalEmulatorApp(
        name = "Play!",
        packageName = "com.virtualapplications.play",
        launchKind = ExternalLaunchKind.APP_ONLY,
        contentUriSupported = false,
        officialStatus = "Paquete Android oficial verificado; el proyecto no documenta una API externa estable",
        acceptedExtensions = setOf("iso", "chd", "cso", "cue", "bin", "isz", "elf", "m3u"),
    )
    private val yabaSanshiro = ExternalEmulatorApp(
        name = "Yaba Sanshiro 2 Pro",
        packageName = "org.devmiyax.yabasanshioro2.pro",
        launchKind = ExternalLaunchKind.RUNTIME_RESOLVED_VIEW,
        contentUriSupported = true,
        officialStatus = "Paquete Pro identificado en documentación secundaria; API de frontend no documentada oficialmente",
        acceptedExtensions = setOf("chd", "cue", "bin", "iso", "mds"),
    )


    val all: List<EmulatorProfile> = listOf(
        profile("nes", "NES", "mesen_libretro_android.so", setOf("nes", "fds", "unf", "unif", "zip"), "FDS requiere disksys.rom", rationale = "Mesen ofrece alta precisión; RetroArch es la ruta estable."),
        profile("snes", "Super Nintendo", "snes9x_libretro_android.so", setOf("sfc", "smc", "fig", "zip"), "No obligatoria para el catálogo estándar", rationale = "Snes9x equilibra compatibilidad y rendimiento ARM64."),
        profile("gb", "Game Boy", "gambatte_libretro_android.so", setOf("gb", "zip"), "No requerida", rationale = "Gambatte es estable y preciso."),
        profile("gbc", "Game Boy Color", "gambatte_libretro_android.so", setOf("gbc", "gb", "zip"), "No requerida", rationale = "Gambatte es estable y preciso."),
        profile("gba", "Game Boy Advance", "mgba_libretro_android.so", setOf("gba", "zip", "7z"), "gba_bios.bin opcional", rationale = "mGBA mantiene alta compatibilidad y soporte activo."),
        profile("n64", "Nintendo 64", "mupen64plus_next_gles3_libretro_android.so", setOf("n64", "z64", "v64", "zip", "7z"), "No requerida", rationale = "Mupen64Plus-Next ofrece la integración RetroArch más consistente en Android."),
        profile("nds", "Nintendo DS", "melonds_libretro_android.so", setOf("nds", "zip", "7z"), "BIOS/firmware opcionales para DS; necesarios para DSi", listOf(melonDs), EngineMode.EXTERNAL, "melonDS independiente dispone de integración content URI oficial."),
        profile("dsi", "Nintendo DSi", "melonds_libretro_android.so", setOf("dsi", "nds"), "BIOS, firmware y NAND DSi requeridos", listOf(melonDs), EngineMode.EXTERNAL, "melonDS es la opción documentada para el modo DSi."),
        profile("3ds", "Nintendo 3DS", "azahar_libretro_android.so", setOf("3ds", "cci", "cxi", "3dsx", "app"), "Archivos de sistema/keys según el contenido", listOf(azahar), EngineMode.EXTERNAL, "Azahar continúa el desarrollo moderno de Citra; el lanzamiento queda condicionado al intent real."),
        profile("gamecube", "Nintendo GameCube", "dolphin_libretro_android.so", setOf("iso", "gcm", "rvz", "wia", "gcz", "ciso", "dol", "elf"), "No requerida normalmente", listOf(dolphin), EngineMode.EXTERNAL, "Dolphin independiente es el motor principal, pero su actividad de emulación actual no es exportada."),
        profile("wii", "Nintendo Wii", "dolphin_libretro_android.so", setOf("iso", "rvz", "wbfs", "wia", "gcz", "dol", "elf"), "NAND/keys solo para funciones específicas", listOf(dolphin), EngineMode.EXTERNAL, "Dolphin independiente ofrece el soporte Wii más completo; lanzamiento externo limitado por su manifiesto."),
        profile("master-system", "Sega Master System", "genesis_plus_gx_libretro_android.so", setOf("sms", "bin", "zip"), "No requerida", rationale = "Genesis Plus GX es la opción universal para hardware Sega de 8 bits."),
        profile("genesis", "Mega Drive / Genesis", "genesis_plus_gx_libretro_android.so", setOf("md", "gen", "bin", "smd", "zip"), "No requerida", rationale = "Genesis Plus GX prioriza precisión y compatibilidad."),
        profile("game-gear", "Sega Game Gear", "genesis_plus_gx_libretro_android.so", setOf("gg", "bin", "zip"), "No requerida", rationale = "Genesis Plus GX mantiene una ruta común y estable."),
        profile("sega-cd", "Sega CD / Mega-CD", "genesis_plus_gx_libretro_android.so", setOf("cue", "bin", "chd", "m3u"), "BIOS regional de Mega-CD/Sega CD requerida", rationale = "Genesis Plus GX maneja correctamente discos y CHD con BIOS legal."),
        profile("saturn", "Sega Saturn", "mednafen_saturn_libretro_android.so", setOf("cue", "bin", "chd", "m3u", "iso"), "BIOS Saturn recomendada/requerida según motor", listOf(yabaSanshiro), EngineMode.AUTO, "Beetle Saturn prioriza precisión; Yaba Sanshiro 2 Pro queda como opción condicional de rendimiento."),
        profile("dreamcast", "Sega Dreamcast", "flycast_libretro_android.so", setOf("gdi", "chd", "cdi", "cue", "m3u"), "BIOS opcional para Dreamcast; requerida para Naomi/Atomiswave", listOf(flycast), EngineMode.EXTERNAL, "Flycast independiente o core comparten un proyecto mantenido y soporte ARM64."),
        profile("ps1", "Sony PlayStation", "swanstation_libretro_android.so", setOf("cue", "bin", "chd", "m3u", "iso", "pbp"), "BIOS PS1 recomendada", listOf(duckStation), EngineMode.AUTO, "SwanStation es un core de RetroArch; DuckStation es la aplicación independiente relacionada."),
        profile("ps2", "Sony PlayStation 2", "play_libretro_android.so", setOf("iso", "chd", "cso", "bin", "cue", "isz", "elf", "m3u"), "Play! no exige BIOS externa; otros motores pueden requerirla", listOf(playPs2), EngineMode.RETROARCH, "LRPS2 no está disponible en Android/ARM. El core Play! es la alternativa libretro oficial; la aplicación Play! se detecta, pero Oz no inventa una actividad o intent no documentado. NetherSX2 permanece como referencia no integrada por falta de una API primaria verificable."),
        profile("psp", "Sony PSP", "ppsspp_libretro_android.so", setOf("iso", "cso", "chd", "elf", "pbp"), "No requerida", listOf(ppsspp), EngineMode.EXTERNAL, "PPSSPP independiente publica una actividad ACTION_VIEW explícita y soporte content URI."),
        profile("arcade-fbneo", "Arcade / FinalBurn Neo", "fbneo_libretro_android.so", setOf("zip", "7z"), "Depende del romset; BIOS deben pertenecer al mismo set", rationale = "FBNeo exige romset exacto compatible con la versión del core."),
        profile("ngp", "Neo Geo Pocket", "mednafen_ngp_libretro_android.so", setOf("ngp", "ngc", "zip"), "No requerida", rationale = "Beetle NeoPop es la ruta RetroArch estable."),
        profile("ngpc", "Neo Geo Pocket Color", "mednafen_ngp_libretro_android.so", setOf("ngc", "ngp", "zip"), "No requerida", rationale = "Beetle NeoPop es la ruta RetroArch estable."),
        profile("pc-engine", "PC Engine / TurboGrafx-16", "mednafen_pce_fast_libretro_android.so", setOf("pce", "zip"), "No requerida para HuCard", rationale = "Beetle PCE Fast ofrece excelente rendimiento en Android."),
        profile("pc-engine-cd", "PC Engine CD / TurboGrafx-CD", "mednafen_pce_fast_libretro_android.so", setOf("cue", "bin", "chd", "m3u"), "syscard3.pce requerida", rationale = "Beetle PCE Fast soporta CD y CHD con System Card."),
        profile("wonderswan", "WonderSwan", "mednafen_wswan_libretro_android.so", setOf("ws", "zip"), "No requerida", rationale = "Beetle Cygne es la integración RetroArch madura."),
        profile("wonderswan-color", "WonderSwan Color", "mednafen_wswan_libretro_android.so", setOf("wsc", "ws", "zip"), "No requerida", rationale = "Beetle Cygne es la integración RetroArch madura."),
        profile("atari-lynx", "Atari Lynx", "handy_libretro_android.so", setOf("lnx", "zip"), "lynxboot.img según core/configuración", rationale = "Handy conserva alta compatibilidad en Android."),
        profile("atari-2600", "Atari 2600", "stella_libretro_android.so", setOf("a26", "bin", "zip"), "No requerida", rationale = "Stella es el core mantenido y preciso."),
        profile("atari-5200", "Atari 5200", "a5200_libretro_android.so", setOf("a52", "bin", "zip"), "BIOS 5200 requerida", rationale = "a5200 simplifica el mapeo de software del sistema."),
        profile("atari-7800", "Atari 7800", "prosystem_libretro_android.so", setOf("a78", "bin", "zip"), "7800 BIOS opcional según región", rationale = "ProSystem es ligero y compatible."),
    )

    private fun profile(
        id: String,
        name: String,
        core: String,
        extensions: Set<String>,
        bios: String,
        external: List<ExternalEmulatorApp> = emptyList(),
        recommended: EngineMode = EngineMode.RETROARCH,
        rationale: String,
    ) = EmulatorProfile(
        systemId = id,
        systemName = name,
        recommendedEngine = recommended,
        retroArchCore = core,
        externalApps = external,
        acceptedExtensions = extensions,
        bios = bios,
        secondary = external.firstOrNull()?.name ?: "Otro core compatible seleccionado manualmente en RetroArch",
        rationale = rationale,
    )

    fun forSystem(systemId: String): EmulatorProfile? = all.firstOrNull { it.systemId == systemId }
}

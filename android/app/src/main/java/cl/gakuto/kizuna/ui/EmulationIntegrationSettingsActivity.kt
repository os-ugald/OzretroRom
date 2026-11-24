@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cl.gakuto.kizuna.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import cl.gakuto.kizuna.catalog.LibretroCatalogRepository
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.integration.IisuExporter
import cl.gakuto.kizuna.launch.EmulatorProfiles
import cl.gakuto.kizuna.launch.RetroArchManager
import cl.gakuto.kizuna.launch.RetroOverlayManager
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTheme
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Focused emulation/integration control center. Catalog, ROMgi, Internet Archive and ROM-library
 * choices intentionally live in their own settings screens instead of leaking into this section.
 */
class EmulationIntegrationSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = AppPreferences(this)
        val manager = RetroArchManager(this)
        val overlayManager = RetroOverlayManager(this)
        val systems = runCatching { LibretroCatalogRepository(this, OzDatabase.get(this)).systems() }.getOrDefault(emptyList())
        setContent {
            OzTheme {
                EmulationIntegrationSettingsContent(
                    appContext = this,
                    preferences = preferences,
                    manager = manager,
                    overlayManager = overlayManager,
                    systems = systems,
                    onBack = ::finish,
                    persist = ::persist,
                    toast = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() },
                    openActivity = { startActivity(it) },
                )
            }
        }
    }

    private fun persist(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }
}

@Composable
private fun EmulationIntegrationSettingsContent(
    appContext: Context,
    preferences: AppPreferences,
    manager: RetroArchManager,
    overlayManager: RetroOverlayManager,
    systems: List<cl.gakuto.kizuna.catalog.CatalogSystem>,
    onBack: () -> Unit,
    persist: (Uri) -> Unit,
    toast: (String) -> Unit,
    openActivity: (Intent) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var revision by remember { mutableStateOf(0) }
    var variants by remember(revision) { mutableStateOf(manager.variants()) }
    var variantMenu by remember { mutableStateOf(false) }
    var systemMenu by remember { mutableStateOf(false) }
    var selectedSystemIndex by remember { mutableStateOf(0) }
    var coreStatus by remember { mutableStateOf("Pulsa Verificar para consultar la instalación real de RetroArch.") }
    var overlayStatus by remember { mutableStateOf("Paquete oficial todavía no revisado en esta sesión.") }
    var operationRunning by remember { mutableStateOf(false) }
    var showIisuAdvanced by remember { mutableStateOf(false) }
    var iisuSyncStatus by remember { mutableStateOf(
        if (preferences.iisuLastSyncAt > 0L)
            "Última sincronización: ${preferences.iisuLastSyncDescriptors} accesos · ${preferences.iisuLastSyncSystems} sistemas"
        else "Todavía no has sincronizado la biblioteca de Oz con iiSU."
    ) }

    val selectedVariant = variants.firstOrNull { it.packageName == preferences.retroArchPackage }
        ?: variants.firstOrNull { it.installed }
        ?: variants.firstOrNull()
    val selectedSystem = systems.getOrNull(selectedSystemIndex)
    val profile = selectedSystem?.let { EmulatorProfiles.forSystem(it.id) }
    val hasIisuBridgeTarget = preferences.globalVirtualTreeUri != null

    val coreFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persist(uri); preferences.retroArchCoreTreeUri = uri; revision++
    }
    val biosFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persist(uri); preferences.retroArchSystemTreeUri = uri; revision++
    }
    val fbneoBiosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persist(uri); preferences.fbneoBiosTreeUri = uri; revision++
    }
    val overlayFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persist(uri); preferences.retroArchOverlayTreeUri = uri; revision++
    }
    val iisuBridgePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persist(uri)
        preferences.globalVirtualTreeUri = uri
        preferences.iisuIntegrationEnabled = true
        iisuSyncStatus = "Integración activa. Pulsa Sincronizar biblioteca Oz para generar los accesos de iiSU."
        revision++
    }
    val iisuAssetsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persist(uri); preferences.iisuAssetsTreeUri = uri; revision++
    }
    val integrationPackPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persist(uri)
        scope.launch {
            operationRunning = true
            runCatching { withContext(Dispatchers.IO) { IisuExporter(appContext).exportIntegrationPack(uri) } }
                .onSuccess { toast("${it.fileName} exportado") }
                .onFailure { toast("No se pudo exportar el pack iiSU: ${it.message}") }
            operationRunning = false
        }
    }

    // Reading revision makes SAF/preference changes visible immediately.
    revision

    Column(
        Modifier
            .fillMaxSize()
            .background(OzColors.DeepSpace)
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(OzColors.PurpleDeep, OzColors.DeepSpace, OzColors.ElectricBlue.copy(alpha = .20f))))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text("‹  Volver", style = OzTypography.meta, color = OzColors.Cyan, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 4.dp))
            Text("Emulación e integraciones", style = OzTypography.hero, color = OzColors.TextPrimary)
            Text("RetroArch · cores · BIOS · overlays · motores · iiSU", style = OzTypography.meta, color = OzColors.TextSecondary)
        }

        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                EmuCard("RetroArch", "Frontend principal y puente de configuración persistente") {
                    SettingsButton("Variante: ${selectedVariant?.label ?: "No detectada"}") { variantMenu = true }
                    DropdownMenu(expanded = variantMenu, onDismissRequest = { variantMenu = false }) {
                        variants.forEach { variant ->
                            DropdownMenuItem(
                                text = { Text("${variant.label}${if (variant.installed) " · instalado" else ""}") },
                                onClick = {
                                    preferences.retroArchPackage = variant.packageName
                                    variants = manager.variants()
                                    variantMenu = false
                                    revision++
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsButton("Detectar / verificar") {
                            variants = manager.variants(); revision++
                            val pkg = manager.preferredInstalledPackage(preferences.retroArchPackage)
                            coreStatus = if (pkg == null) "RetroArch no está instalado." else "RetroArch detectado · $pkg"
                        }
                        SettingsButton(if (selectedVariant?.installed == true) "Abrir RetroArch" else "Instalar RetroArch") {
                            val pkg = manager.preferredInstalledPackage(preferences.retroArchPackage)
                            if (pkg != null && manager.openRetroArch(pkg)) Unit else runCatching { manager.openOfficialInstallPage() }
                        }
                    }
                    SettingsToggle(
                        "Instalar automáticamente cores verificados al pulsar Jugar",
                        preferences.retroArchAutoInstallCores,
                    ) { preferences.retroArchAutoInstallCores = it; revision++ }
                }
            }

            item {
                EmuCard("Cores por sistema", "Oz recomienda un core por plataforma; RetroArch conserva la decisión final si hay alternativas compatibles.") {
                    SettingsButton("Sistema: ${selectedSystem?.name ?: "Sin sistemas"}") { systemMenu = true }
                    DropdownMenu(expanded = systemMenu, onDismissRequest = { systemMenu = false }) {
                        systems.forEachIndexed { index, system ->
                            DropdownMenuItem(text = { Text(system.name) }, onClick = { selectedSystemIndex = index; systemMenu = false; coreStatus = "Pendiente de verificación." })
                        }
                    }
                    if (profile != null) {
                        Spacer(Modifier.height(10.dp))
                        Text("Recomendado por Oz: ${profile.retroArchCore}", style = OzTypography.body, color = OzColors.Cyan)
                        Text(profile.rationale, style = OzTypography.meta, color = OzColors.TextSecondary)
                        Text("BIOS: ${profile.bios}", style = OzTypography.meta, color = OzColors.TextSecondary, modifier = Modifier.padding(top = 5.dp))
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(coreStatus, style = OzTypography.meta, color = OzColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsButton("Verificar core") {
                            val pkg = manager.preferredInstalledPackage(preferences.retroArchPackage)
                            val core = profile?.retroArchCore
                            if (pkg == null || core == null) {
                                coreStatus = "Instala RetroArch y selecciona un sistema."
                            } else scope.launch {
                                operationRunning = true
                                val query = manager.queryInstalledCores(pkg)
                                coreStatus = when {
                                    !query.supported -> "RetroArch no respondió al inventario; Oz usará el fallback verificado al jugar."
                                    manager.installedCompatibleCore(query, core) != null -> "Core compatible instalado · ${manager.installedCompatibleCore(query, core)}"
                                    else -> "Core recomendado no reportado como instalado."
                                }
                                operationRunning = false
                            }
                        }
                        SettingsButton("Preparar core recomendado", enabled = !operationRunning && profile != null) {
                            val pkg = manager.preferredInstalledPackage(preferences.retroArchPackage)
                            val core = profile?.retroArchCore
                            if (pkg == null || core == null) {
                                toast("Instala RetroArch y selecciona un sistema primero")
                            } else if (!manager.canAutomaticallyInstall(core)) {
                                toast("Este core no está aprobado para descarga automática en esta APK. Instálalo desde RetroArch.")
                                manager.openRetroArch(pkg)
                            } else scope.launch {
                                operationRunning = true
                                coreStatus = "Preparando $core…"
                                runCatching { manager.downloadAndStageCore(core, preferences.retroArchCoreTreeUri) { p -> scope.launch { coreStatus = p.stage } } }
                                    .onSuccess { artifact ->
                                        preferences.setRetroArchCoreConfirmed(pkg, artifact.coreName, false)
                                        if (artifact.directExternalCore) {
                                            coreStatus = "${artifact.coreName} copiado en la carpeta de cores compartida."
                                        } else {
                                            coreStatus = "Entregando ${artifact.coreName} a RetroArch…"
                                            openActivity(manager.buildCoreSideloadIntent(pkg, artifact))
                                        }
                                    }
                                    .onFailure { coreStatus = "No se pudo preparar el core: ${it.message}" }
                                operationRunning = false
                            }
                        }
                    }
                }
            }

            item {
                EmuCard("BIOS y firmware", "Una sola carpeta System/BIOS para verificar firmware antes de abrir contenido.") {
                    Text(friendlyUri(preferences.retroArchSystemTreeUri) ?: "Sin carpeta System/BIOS", style = OzTypography.body, color = OzColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsButton("Elegir System/BIOS") { biosFolderPicker.launch(preferences.retroArchSystemTreeUri) }
                        SettingsButton("Olvidar") { preferences.retroArchSystemTreeUri = null; revision++ }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("FBNeo BIOS/romset de respaldo", style = OzTypography.body, color = OzColors.TextPrimary)
                    Text(friendlyUri(preferences.fbneoBiosTreeUri) ?: "Sin carpeta adicional", style = OzTypography.meta, color = OzColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    SettingsButton("Elegir carpeta FBNeo") { fbneoBiosPicker.launch(preferences.fbneoBiosTreeUri) }
                }
            }

            item {
                EmuCard("Overlays oficiales", "Paquete oficial de Libretro; RetroArch debe apuntar Input Overlays a la misma carpeta una sola vez.") {
                    Text(friendlyUri(preferences.retroArchOverlayTreeUri) ?: "Sin carpeta de overlays", style = OzTypography.body, color = OzColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsButton("Elegir carpeta") { overlayFolderPicker.launch(preferences.retroArchOverlayTreeUri) }
                        SettingsButton("Descargar / actualizar", enabled = !operationRunning) {
                            val target = preferences.retroArchOverlayTreeUri
                            if (target == null) {
                                toast("Selecciona primero una carpeta para overlays")
                                overlayFolderPicker.launch(null)
                            } else scope.launch {
                                operationRunning = true
                                overlayStatus = "Preparando paquete oficial…"
                                runCatching { overlayManager.installOfficialBundle(target) { p -> scope.launch { overlayStatus = p.stage } } }
                                    .onSuccess { overlayStatus = "Actualizado · ${it.entriesInstalled} archivos" }
                                    .onFailure { overlayStatus = "Error: ${it.message}" }
                                operationRunning = false
                            }
                        }
                    }
                    Text(overlayStatus, style = OzTypography.meta, color = OzColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
                }
            }

            item {
                EmuCard("Motores por sistema", "Automático, RetroArch, emulador externo o preguntar al jugar.") {
                    SettingsButton("Configurar motores y emuladores") { openActivity(Intent(appContext, EmulatorSettingsActivity::class.java)) }
                }
            }

            item {
                EmuCard(
                    "iiSU · frontend opcional",
                    "Oz descarga y administra las ROMs; iiSU puede mostrar accesos virtuales y devolver el lanzamiento a Oz.",
                ) {
                    val enabled = preferences.iisuIntegrationEnabled
                    Text(
                        when {
                            !enabled -> "Estado: DESACTIVADA · la biblioteca de Oz funciona de forma independiente"
                            !hasIisuBridgeTarget -> "Estado: CONFIGURACIÓN INCOMPLETA · falta una carpeta de puente"
                            else -> "Estado: ACTIVA · puente virtual listo"
                        },
                        style = OzTypography.body,
                        color = if (enabled && hasIisuBridgeTarget) OzColors.Cyan else OzColors.TextSecondary,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "Configuración inicial: 1) elige una carpeta global de puente; 2) publica una colección; 3) en iiSU usa la subcarpeta de esa plataforma como ÚNICA ROM folder de la consola; 4) Emulator → Custom Launch Command: pega el comando del pack; 5) Summary → Update. Una ROM faltante se descarga al abrirla.",
                        style = OzTypography.meta,
                        color = OzColors.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsButton(if (enabled) "Desactivar iiSU" else "Activar iiSU") {
                            if (enabled) {
                                preferences.iisuIntegrationEnabled = false
                                iisuSyncStatus = "Integración desactivada. No se borró ninguna ROM ni configuración de Oz."
                                revision++
                            } else if (hasIisuBridgeTarget) {
                                preferences.iisuIntegrationEnabled = true
                                iisuSyncStatus = "Integración activa. Puedes sincronizar la biblioteca local de Oz."
                                revision++
                            } else {
                                showIisuAdvanced = true
                                toast("Selecciona una carpeta de puente. No se copiarán ROMs.")
                                iisuBridgePicker.launch(null)
                            }
                        }
                        SettingsButton("Publicar juegos locales", enabled = !operationRunning && enabled) {
                            scope.launch {
                                operationRunning = true
                                iisuSyncStatus = "Publicando accesos de los juegos locales…"
                                runCatching {
                                    require(preferences.iisuIntegrationEnabled) { "Activa primero la integración iiSU." }
                                    require(hasIisuBridgeTarget) { "Configura una carpeta de puente en Opciones avanzadas." }
                                    val localGames = withContext(Dispatchers.IO) { OzDatabase.get(appContext).gameDao().downloadedOnce() }
                                    require(localGames.isNotEmpty()) { "Tu biblioteca de Oz todavía no tiene ROMs locales para sincronizar." }
                                    withContext(Dispatchers.IO) { IisuExporter(appContext).exportConfigured(preferences, localGames) }
                                }.onSuccess { result ->
                                    preferences.recordIisuSync(result.descriptors, result.systems)
                                    iisuSyncStatus = buildString {
                                        append("Publicación lista · ${result.descriptors} accesos · ${result.systems} sistemas")
                                        if (result.systemsWithoutFolder.isNotEmpty()) append(" · ${result.systemsWithoutFolder.size} sistemas sin destino")
                                    }
                                }.onFailure { error ->
                                    iisuSyncStatus = error.message ?: "No se pudo sincronizar iiSU"
                                }
                                operationRunning = false
                            }
                        }
                        SettingsButton(if (showIisuAdvanced) "Ocultar opciones avanzadas" else "Opciones avanzadas") {
                            showIisuAdvanced = !showIisuAdvanced
                        }
                    }
                    Text(iisuSyncStatus, style = OzTypography.meta, color = OzColors.TextSecondary, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        "Para publicar una colección completa —incluidos juegos aún no descargados— usa Colecciones → Publicar en iiSU. Al abrir un acceso faltante, Oz toma el control y ejecuta su flujo normal de descarga.",
                        style = OzTypography.meta,
                        color = OzColors.Cyan,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    if (showIisuAdvanced) {
                        Spacer(Modifier.height(14.dp))
                        Text("Opciones avanzadas del puente", style = OzTypography.body, color = OzColors.TextPrimary)
                        Text(
                            "Oz usa una sola raíz global y crea una subcarpeta por plataforma. No configures también tu carpeta real de ROMs en la misma consola de iiSU: iiSU admite dos carpetas y eso produciría duplicados.",
                            style = OzTypography.meta,
                            color = OzColors.TextSecondary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 9.dp),
                        )
                        Text(
                            "Puente global: ${friendlyUri(preferences.globalVirtualTreeUri) ?: "sin configurar"}",
                            style = OzTypography.meta,
                            color = OzColors.TextSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsButton("Elegir carpeta de puente") { iisuBridgePicker.launch(preferences.globalVirtualTreeUri) }
                            SettingsButton("Limpiar accesos generados", enabled = !operationRunning && preferences.globalVirtualTreeUri != null) {
                                scope.launch {
                                    operationRunning = true
                                    runCatching { withContext(Dispatchers.IO) { IisuExporter(appContext).clearGeneratedBridge(preferences) } }
                                        .onSuccess { removed ->
                                            iisuSyncStatus = "Bridge limpio · $removed accesos de Oz eliminados. Vuelve a publicar las colecciones que quieras ver en iiSU."
                                        }
                                        .onFailure { error -> iisuSyncStatus = error.message ?: "No se pudo limpiar el Bridge" }
                                    operationRunning = false
                                }
                            }
                            SettingsButton("Olvidar puente global") {
                                preferences.globalVirtualTreeUri = null
                                revision++
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Usa solo el Bridge en iiSU para esta consola. Si iiSU mantiene además la carpeta real de ROMs, verás cada juego local duplicado.",
                            style = OzTypography.meta,
                            color = OzColors.Cyan,
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("Custom Launch Command", style = OzTypography.body, color = OzColors.TextPrimary)
                        Text(
                            IisuExporter.IISU_LAUNCH_COMMAND,
                            style = OzTypography.meta,
                            color = OzColors.Cyan,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "Las comillas alrededor de %ROM% son necesarias para rutas con espacios.",
                            style = OzTypography.meta,
                            color = OzColors.TextSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Assets iiSU: ${friendlyUri(preferences.iisuAssetsTreeUri) ?: "opcionales"}",
                            style = OzTypography.meta,
                            color = OzColors.TextSecondary,
                        )
                        Spacer(Modifier.height(7.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsButton("Assets iiSU") { iisuAssetsPicker.launch(preferences.iisuAssetsTreeUri) }
                            SettingsButton("Exportar pack de integración") { integrationPackPicker.launch(null) }
                        }
                    }
                }
            }

            item {
                EmuCard("Compatibilidad y diagnóstico", "Opciones de puente entre Oz y emuladores; no reemplazan la configuración interna de RetroArch.") {
                    SettingsToggle(
                        "Borrar copias temporales de compatibilidad al cerrar el emulador",
                        preferences.deleteAfterPlay,
                    ) { preferences.deleteAfterPlay = it; revision++ }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsButton("Carpeta de cores compartida") { coreFolderPicker.launch(preferences.retroArchCoreTreeUri) }
                        SettingsButton("Permiso de archivos RetroArch") {
                            val pkg = manager.preferredInstalledPackage(preferences.retroArchPackage)
                            if (pkg == null || !manager.openStorageAccessSettings(pkg)) toast("No se pudo abrir el permiso de RetroArch")
                        }
                    }
                }
            }

            item {
                EmuCard("Ajustes que conserva RetroArch", "Video/Vulkan, audio, latencia, shaders, controles/remaps, saves/states, core options y overrides siguen perteneciendo a retroarch.cfg y al Quick Menu.") {
                    Text(
                        "Oz pasa el CONFIGFILE real al lanzar. Así no crea una segunda configuración ni pisa tus ajustes por core/juego.",
                        style = OzTypography.meta,
                        color = OzColors.TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingsButton("Abrir RetroArch para esos ajustes") {
                        val pkg = manager.preferredInstalledPackage(preferences.retroArchPackage)
                        if (pkg == null || !manager.openRetroArch(pkg)) toast("RetroArch no está disponible")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmuCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(OzColors.PanelRaised, OzShapes.large)
            .border(1.dp, OzColors.Cyan.copy(alpha = .18f), OzShapes.large)
            .padding(16.dp),
    ) {
        Text(title, style = OzTypography.section, color = OzColors.TextPrimary)
        Text(subtitle, style = OzTypography.meta, color = OzColors.TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        content()
    }
}

@Composable
private fun SettingsButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = OzColors.Cyan.copy(alpha = .16f),
            contentColor = OzColors.Cyan,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, OzColors.Cyan.copy(alpha = .42f)),
        shape = OzShapes.pill,
    ) { Text(text, style = OzTypography.meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = OzColors.TextPrimary) }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = OzTypography.body, color = OzColors.TextPrimary, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun friendlyUri(uri: Uri?): String? = uri?.lastPathSegment
    ?.substringAfterLast(':')
    ?.replace("%20", " ")
    ?.takeIf { it.isNotBlank() }
    ?: uri?.toString()?.takeIf { it.isNotBlank() }

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cl.gakuto.kizuna.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import cl.gakuto.kizuna.catalog.CatalogSystem
import cl.gakuto.kizuna.catalog.LibretroCatalogRepository
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.ArtworkMode
import cl.gakuto.kizuna.data.GameTileStyle
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTheme
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.util.ArtworkResolver

/**
 * Focused ROM-library settings. This is intentionally separate from SettingsActivity so
 * storage/artwork choices are not duplicated inside the emulator/integration screen.
 */
class LibrarySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = AppPreferences(this)
        val systems = runCatching { LibretroCatalogRepository(this, OzDatabase.get(this)).systems() }
            .getOrDefault(emptyList())
        setContent {
            OzTheme {
                LibrarySettingsContent(
                    preferences = preferences,
                    systems = systems,
                    onBack = ::finish,
                    persist = ::persist,
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
private fun LibrarySettingsContent(
    preferences: AppPreferences,
    systems: List<CatalogSystem>,
    onBack: () -> Unit,
    persist: (Uri) -> Unit,
) {
    var revision by remember { mutableStateOf(0) }
    var selectedSystemIndex by remember { mutableStateOf(0) }
    var systemMenuExpanded by remember { mutableStateOf(false) }
    var steamGridDbKeyDraft by remember { mutableStateOf(preferences.steamGridDbApiKey) }
    val selectedSystem = systems.getOrNull(selectedSystemIndex)

    val globalPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persist(uri)
        preferences.globalRomTreeUri = uri
        revision++
    }
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val system = selectedSystem ?: return@rememberLauncherForActivityResult
        persist(uri)
        preferences.setSystemRomTreeUri(system.id, uri)
        revision++
    }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persist(uri)
        preferences.localArtworkTreeUri = uri
        ArtworkResolver.clearLocalCache()
        revision++
    }


    // Reading revision keeps the screen refreshed after a SAF selection without duplicating state.
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
                .background(Brush.horizontalGradient(listOf(OzColors.PurpleDeep, OzColors.DeepSpace, OzColors.ElectricBlue.copy(alpha = .18f))))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text("‹  Volver", style = OzTypography.meta, color = OzColors.Cyan, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 4.dp))
            Text("Biblioteca y almacenamiento", style = OzTypography.hero, color = OzColors.TextPrimary)
            Text("ROMs persistentes · artwork · carpetas por sistema", style = OzTypography.meta, color = OzColors.TextSecondary)
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LibrarySettingCard("Biblioteca ROMs principal", "La ROM terminada vive aquí; el staging temporal se elimina tras guardar correctamente.") {
                    Text(preferences.globalRomTreeUri?.let(::friendlyUri) ?: "Sin carpeta seleccionada", style = OzTypography.body, color = OzColors.TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    LibraryButton("Elegir carpeta principal") { globalPicker.launch(preferences.globalRomTreeUri) }
                }
            }

            item {
                LibrarySettingCard("Carpeta por sistema", "Opcional. Un sistema puede guardar sus ROMs en una carpeta distinta de la biblioteca principal.") {
                    if (systems.isEmpty()) {
                        Text("No hay sistemas disponibles.", style = OzTypography.body, color = OzColors.Warning)
                    } else {
                        LibraryButton("Sistema: ${selectedSystem?.name.orEmpty()}") { systemMenuExpanded = true }
                        DropdownMenu(expanded = systemMenuExpanded, onDismissRequest = { systemMenuExpanded = false }) {
                            systems.forEachIndexed { index, system ->
                                DropdownMenuItem(
                                    text = { Text(system.name) },
                                    onClick = { selectedSystemIndex = index; systemMenuExpanded = false },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            selectedSystem?.let { system ->
                                preferences.systemRomTreeUri(system.id)?.let(::friendlyUri)
                                    ?: "Usa la biblioteca principal"
                            }.orEmpty(),
                            style = OzTypography.body,
                            color = OzColors.TextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LibraryButton("Elegir carpeta", Modifier.weight(1f)) {
                                selectedSystem?.let { systemPicker.launch(preferences.systemRomTreeUri(it.id)) }
                            }
                            LibraryButton("Usar principal", Modifier.weight(1f)) {
                                selectedSystem?.let { preferences.setSystemRomTreeUri(it.id, null); revision++ }
                            }
                        }
                    }
                }
            }



            item {
                LibrarySettingCard("Vista de juegos", "Prioriza el juego: iconos cuadrados compactos para navegar o carátulas verticales clásicas.") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        GameTileStyle.entries.forEach { style ->
                            FilterChip(
                                selected = preferences.gameTileStyle == style,
                                onClick = { preferences.gameTileStyle = style; revision++ },
                                label = { Text(style.label) },
                            )
                        }
                    }
                    Text(
                        if (preferences.gameTileStyle == GameTileStyle.COMPACT_ICON)
                            "Modo recomendado: mosaicos cuadrados pequeños; SteamGridDB si está sincronizado y carátula Oz como respaldo."
                        else "Modo clásico: conserva la proporción vertical de las carátulas.",
                        style = OzTypography.meta,
                        color = OzColors.TextSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                LibrarySettingCard("SteamGridDB · iconos", "La clave sólo se usa cuando pulsas sincronizar iconos en una colección. Los resultados quedan en caché local para navegar sin conexión.") {
                    OutlinedTextField(
                        value = steamGridDbKeyDraft,
                        onValueChange = { steamGridDbKeyDraft = it },
                        label = { Text("API key de SteamGridDB") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryButton("Guardar clave", Modifier.weight(1f)) {
                            preferences.steamGridDbApiKey = steamGridDbKeyDraft
                            steamGridDbKeyDraft = preferences.steamGridDbApiKey
                            revision++
                        }
                        LibraryButton("Borrar", Modifier.weight(1f)) {
                            preferences.steamGridDbApiKey = ""
                            steamGridDbKeyDraft = ""
                            revision++
                        }
                    }
                    Text(
                        if (preferences.steamGridDbApiKey.isBlank()) "Sin API key configurada." else "API key guardada en almacenamiento privado de Oz.",
                        style = OzTypography.meta,
                        color = if (preferences.steamGridDbApiKey.isBlank()) OzColors.Warning else OzColors.Positive,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                LibrarySettingCard("Carátulas", "Elige la prioridad visual y, si quieres, una carpeta propia de imágenes.") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        ArtworkMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.artworkMode == mode,
                                onClick = { preferences.artworkMode = mode; revision++ },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(preferences.localArtworkTreeUri?.let(::friendlyUri) ?: "Sin carpeta local de artwork", style = OzTypography.body, color = OzColors.TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryButton("Elegir artwork", Modifier.weight(1f)) { artworkPicker.launch(preferences.localArtworkTreeUri) }
                        LibraryButton("Olvidar", Modifier.weight(1f)) {
                            preferences.localArtworkTreeUri = null
                            ArtworkResolver.clearLocalCache()
                            revision++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySettingCard(title: String, subtitle: String, content: @Composable () -> Unit) {
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
private fun LibraryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = OzShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = OzColors.Cyan.copy(alpha = .16f),
            contentColor = OzColors.Cyan,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, OzColors.Cyan.copy(alpha = .42f)),
    ) { Text(text, style = OzTypography.meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = OzColors.TextPrimary) }
}

private fun friendlyUri(uri: Uri): String = uri.lastPathSegment
    ?.substringAfterLast(':')
    ?.replace("%20", " ")
    ?.takeIf { it.isNotBlank() }
    ?: "Carpeta autorizada"

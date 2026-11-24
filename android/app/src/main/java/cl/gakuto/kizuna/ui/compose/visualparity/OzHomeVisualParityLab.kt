package cl.gakuto.kizuna.ui.compose.visualparity

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.gakuto.kizuna.ui.compose.theme.*
import coil3.compose.AsyncImage

/**
 * Oz Home Visual Parity Lab.
 * Reconstructing the UI to match the "Next Gen Console" mockups perfectly.
 */

@Composable
fun OzGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(OzColors.PanelRaised.copy(alpha = 0.72f))
            .border(1.dp, OzColors.Outline.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun OzHeroSection(
    title: String,
    metadata: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = OzTypography.marquee.copy(
                fontSize = 38.sp, 
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = OzColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(metadata, style = OzTypography.meta, color = OzColors.Cyan, fontWeight = FontWeight.Bold)
            
            DotSeparator()
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // User icon placeholder
                Box(Modifier.size(16.dp).border(1.2.dp, OzColors.TextSecondary, RoundedCornerShape(3.dp)))
                Text("1 JUGADOR", style = OzTypography.meta, color = OzColors.TextSecondary)
            }
            
            DotSeparator()
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Save icon placeholder
                Box(Modifier.size(16.dp).border(1.2.dp, OzColors.TextSecondary, RoundedCornerShape(3.dp)))
                Text("GUARDADO", style = OzTypography.meta, color = OzColors.TextSecondary)
            }
        }
        
        Spacer(Modifier.height(18.dp))
        
        Text(
            text = description,
            style = OzTypography.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = OzColors.TextSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.55f)
        )
    }
}

@Composable
private fun DotSeparator() {
    Box(Modifier.size(4.dp).clip(CircleShape).background(OzColors.TextSecondary.copy(alpha = 0.4f)))
}

@Composable
fun OzNavDock(
    modifier: Modifier = Modifier,
    activeRoute: String = "home"
) {
    Column(
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight()
            .background(Color.White.copy(alpha = 0.05f))
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Logo
        Text(
            "OZ", 
            style = OzTypography.marquee.copy(fontSize = 28.sp, color = OzColors.Cyan),
            modifier = Modifier.padding(bottom = 20.dp)
        )
        
        NavItem("home", activeRoute == "home", label = "Inicio")
        NavItem("catalog", activeRoute == "catalog", label = "Catálogo")
        NavItem("collections", activeRoute == "collections", label = "Colecciones")
        NavItem("library", activeRoute == "library", label = "Biblioteca")
        
        Spacer(Modifier.weight(1f))
        
        NavItem("settings", activeRoute == "settings", label = "Ajustes")
    }
}

@Composable
private fun NavItem(id: String, active: Boolean, label: String) {
    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        if (active) {
            // Selected capsule indicator
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(OzColors.Cyan.copy(alpha = 0.12f))
                    .border(1.5.dp, OzColors.Cyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            )
            
            // Interaction line (from mockup)
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(3.dp)
                    .height(24.dp)
                    .clip(CircleShape)
                    .background(OzColors.Cyan)
            )
        }
        
        // Icon (In a real app we'd use painters, using placeholders for now)
        Box(
            Modifier
                .size(24.dp)
                .background(
                    if (active) OzColors.Cyan else OzColors.TextSecondary.copy(alpha = 0.7f), 
                    RoundedCornerShape(4.dp)
                )
        )
        
        if (active) {
            // Floating label (optional, mockup shows it)
            // Text(label, style = OzTypography.meta, modifier = Modifier.offset(x = 60.dp))
        }
    }
}

@Composable
fun OzControllerHints(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HintItem("A", "Abrir", OzColors.Cyan)
        HintItem("B", "Volver", OzColors.Magenta)
        HintItem("Y", "Buscar", OzColors.Warning)
        HintItem("X", "Filtros", OzColors.ElectricBlue)
        
        Spacer(Modifier.weight(1f))
        
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, OzColors.Outline.copy(alpha = 0.3f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("START  Acciones", style = OzTypography.meta, color = OzColors.TextSecondary)
        }
        
        Text("L / R  Página", style = OzTypography.meta, color = OzColors.TextSecondary)
    }
}

@Composable
private fun HintItem(key: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.dp, color.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(key, style = OzTypography.meta.copy(fontSize = 10.sp), color = color)
        }
        Text(label, style = OzTypography.meta, color = OzColors.TextSecondary)
    }
}

@Composable
fun OzHomeMosaicParity(modifier: Modifier = Modifier) {
    // Recreating the layout seen in Mockup 5
    // Top Row: 2x2, 2x1, 2x1
    // Bottom Row: 2x1, 2x1, 1x1, 1x1 (approx)
    
    Column(modifier.padding(horizontal = 24.dp)) {
        Row(Modifier.height(200.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ParityTile("Echoes of Astra", isLarge = true, modifier = Modifier.weight(2f))
            Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ParityTile("Neon Outrider", isWide = true, modifier = Modifier.weight(1f))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                   ParityTile("Velvet Circuit", modifier = Modifier.weight(1f))
                   ParityTile("Star Harbor", modifier = Modifier.weight(1f))
                }
            }
            ParityTile("Velvet Circuit", modifier = Modifier.weight(1f))
        }
        
        Spacer(Modifier.height(16.dp))
        
        Row(Modifier.height(140.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ParityTile("Crimson Run", isWide = true, modifier = Modifier.weight(1.5f))
            ParityTile("Lunar Forge", isWide = true, modifier = Modifier.weight(1.5f))
            ParityTile("Whisper Grove", modifier = Modifier.weight(1f))
            ParityTile("Byte Drifter", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ParityTile(
    title: String,
    isLarge: Boolean = false,
    isWide: Boolean = false,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "parity-scale")
    
    Box(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(OzShapes.medium)
            .background(OzColors.PanelRaised)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) OzColors.Cyan else OzColors.Outline.copy(alpha = 0.1f),
                shape = OzShapes.medium
            )
            .clickable { }
            .focusable()
    ) {
        // Mock image
        Box(Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(OzColors.PurpleDeep, OzColors.DeepSpace))
        ))
        
        // Label (Mockup shows labels on some tiles)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
        )
        
        Text(
            title.uppercase(),
            style = OzTypography.tileTitle,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        if (focused) {
            // Glow effect
            Box(
                Modifier
                    .fillMaxSize()
                    .border(2.dp, OzColors.Cyan.copy(alpha = 0.3f), OzShapes.medium)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
fun OzHomeVisualParityFullPreview() {
    OzTheme {
        Box(Modifier.fillMaxSize().background(OzColors.DeepSpace)) {
            // Adaptive Background
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.4f)
                    .blur(100.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(OzColors.ArcadeViolet.copy(alpha = 0.3f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(200f, 200f)
                        )
                    )
            )
            
            Row(Modifier.fillMaxSize()) {
                OzNavDock()
                
                Column(Modifier.weight(1f)) {
                    OzHeroSection(
                        title = "Echoes of Astra",
                        metadata = "16-BIT  ·  1994  ·  ADVENTURE",
                        description = "Asume el mando del Halcyon y desmantela el Círculo Umbra en una guerra por las fronteras estelares."
                    )
                    
                    OzHomeMosaicParity(Modifier.weight(1f))
                    
                    OzControllerHints()
                }
                
                // Right panel mockup (Organize mode)
                Box(Modifier.padding(24.dp).width(240.dp).fillMaxHeight()) {
                    OzGlassPanel(Modifier.fillMaxWidth()) {
                        Text("MODO ORGANIZAR", style = OzTypography.meta, color = OzColors.Magenta)
                        Spacer(Modifier.height(8.dp))
                        CommandItem("D-pad", "Mover")
                        CommandItem("A", "Colocar")
                        CommandItem("B", "Cancelar")
                        CommandItem("X", "Tamaño")
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandItem(key: String, label: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(24.dp)
                .border(1.dp, OzColors.TextSecondary.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(key, style = OzTypography.meta.copy(fontSize = 9.sp), color = OzColors.TextPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = OzTypography.body, color = OzColors.TextSecondary)
    }
}

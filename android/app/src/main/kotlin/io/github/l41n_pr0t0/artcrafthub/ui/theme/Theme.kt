package io.github.l41n_pr0t0.artcrafthub.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Near-black ground; the hub is a monochrome, glass-on-ink design and always dark. */
val Ink = Color(0xFF0A0A0B)
val Paper = Color(0xFFEDEDEF)
val Muted = Color(0xFF9C9CA2)
val Faint = Color(0xFF5F5F66)
val Hairline = Color.White.copy(alpha = 0.12f)

private val Colors = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    primaryContainer = Color(0xFF26262A),
    onPrimaryContainer = Paper,
    secondary = Color(0xFFBDBDC2),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF232326),
    onSecondaryContainer = Paper,
    tertiary = Color(0xFFD6D6DA),
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Color(0xFF1C1C1F),
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Color(0xFF080809),
    surfaceContainerLow = Color(0xFF111113),
    surfaceContainer = Color(0xFF151517),
    surfaceContainerHigh = Color(0xFF1B1B1E),
    surfaceContainerHighest = Color(0xFF222225),
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    outline = Color(0xFF45454B),
    outlineVariant = Color(0xFF2A2A2E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val base = Typography()
private val Type = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Light),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Light),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Normal),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Normal),
)

/** Wide-tracked capitals for names and section titles ("A R T C R A F T"). */
val Spaced = TextStyle(fontWeight = FontWeight.Light, letterSpacing = 0.38.em)

/** Small monospace captions: versions, counts, subtitles. */
val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 0.02.em)

@Composable
fun HubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}

/** A frosted panel: a faint white wash with a rim of light that fades downwards. */
fun Modifier.glass(shape: Shape, strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.075f * strength), Color.White.copy(alpha = 0.018f * strength)),
        ),
    )
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f))),
        shape,
    )

/** The ink ground with a soft glow behind the top of the screen. */
fun Modifier.hubBackground(): Modifier = this
    .background(Ink)
    .drawBehind {
        drawRect(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(size.width / 2, size.height * 0.2f),
                radius = size.width * 0.95f,
            ),
        )
    }

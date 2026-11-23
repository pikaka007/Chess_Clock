package com.bara.chessclock.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Board palette: warm cream on a deep green-black, like a wooden clock in a quiet room. */
object ClockColors {
    val Background = Color(0xFF0E1412)
    val Surface = Color(0xFF17201E)
    val SurfaceRaised = Color(0xFF1F2A26)
    val Outline = Color(0xFF33423C)
    val Cream = Color(0xFFE8DCC8)
    val CreamDim = Color(0xFF9FB0A8)
    val Amber = Color(0xFFD9A24A)
    val OnAmber = Color(0xFF1A1408)
    val Danger = Color(0xFFF08C82)
    val OnCream = Color(0xFF101512)

    /** "Lit" face while that side is on the move: light background, so the state is readable by
     *  luminance alone and not only by hue. */
    val ActiveFace = Color(0xFFEDE3CF)
    val ActiveFaceBorder = Color(0xFFD9A24A)
    val InactiveFace = Surface
    val FrozenFace = Color(0xFF16211D)
}

private val scheme = darkColorScheme(
    primary = ClockColors.Amber,
    onPrimary = ClockColors.OnAmber,
    primaryContainer = ClockColors.SurfaceRaised,
    onPrimaryContainer = ClockColors.Cream,
    background = ClockColors.Background,
    onBackground = ClockColors.Cream,
    surface = ClockColors.Surface,
    onSurface = ClockColors.Cream,
    surfaceVariant = ClockColors.SurfaceRaised,
    onSurfaceVariant = ClockColors.CreamDim,
    outline = ClockColors.Outline,
    error = ClockColors.Danger,
    onError = Color(0xFF2A0F0C),
)

private val typography = Typography().let { base ->
    base.copy(
        // Digits are monospaced everywhere: a ticking number must not shift the layout.
        displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        displayMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun ChessClockTheme(content: @Composable () -> Unit) {
    // The clock board is intentionally dark even in light mode: it sits next to a board and must
    // not glow. Nothing else in the app needs a light variant, so there is no light scheme.
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

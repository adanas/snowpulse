package com.snowpulse.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val NeonCyan = Color(0xFF38BDF8)
val DarkBackground = Color(0xFF030712)
val CardBackground = Color(0xFF0F172A)
val AccentAmber = Color(0xFFF59E0B)
val SuccessEmerald = Color(0xFF10B981)
val TrickPurple = Color(0xFFC084FC)

// 2026 Modern Trend Design System (Glassmorphism 2.0 & Ambient Aurora)
val GlassSurfaceDark = Color(0xBF0F172A)
val GlassBorderSubtle = Color(0x1FFFFFFF)
val AuroraGlowCyan = Color(0x3322D3EE)
val AuroraGlowEmerald = Color(0x2610B981)
val AuroraGlowPurple = Color(0x24C084FC)

/**
 * 2026 Trend: Ambient Aurora Glow 背景グラデーション (平常時・待機時)
 */
fun ambientAuroraBrush(): Brush {
    return Brush.radialGradient(
        colors = listOf(AuroraGlowCyan, AuroraGlowEmerald, DarkBackground),
        radius = 1200f
    )
}

/**
 * 2026 Trend: Ambient Aurora Glow ライド滑走中アクティブグラデーション
 */
fun activeRideAuroraBrush(): Brush {
    return Brush.radialGradient(
        colors = listOf(AuroraGlowCyan, AuroraGlowPurple, DarkBackground),
        radius = 1400f
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    secondary = AccentAmber,
    onSecondary = Color.Black,
    tertiary = TrickPurple,
    background = DarkBackground,
    surface = CardBackground,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun SnowPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

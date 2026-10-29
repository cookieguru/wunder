package app.wunder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The web page's color tokens. Series colors keep ≥3:1 contrast on the background and the night band. */
data class WuColors(
    val bg: Color, val panel: Color, val day: Color, val dayAlt: Color, val nightAlt: Color, val fg: Color, val muted: Color, val grid: Color,
    val night: Color, val line: Color, val outline: Color,
    val temp: Color, val feels: Color, val pop: Color, val snow: Color, val rh: Color, val cloud: Color, val pres: Color, val qpf: Color, val wind: Color,
    val hi: Color, val lo: Color, val bannerBg: Color, val bannerFg: Color, val link: Color, val isDark: Boolean,
) {
    fun series(token: String): Color = when (token) {
        "temp" -> temp
        "feels" -> feels
        "pop" -> pop
        "snow" -> snow
        "rh" -> rh
        "cloud" -> cloud
        "pres" -> pres
        "qpf" -> qpf
        "wind" -> wind
        else -> fg
    }
}

private fun c(hex: Long) = Color(0xff000000 or hex)

val LightColors = WuColors(
    bg = c(0xffffff), panel = c(0xf3f4f6), day = c(0xffffff), dayAlt = c(0xf5f6f8), nightAlt = c(0xe2e5ea), fg = c(0x1b1d21), muted = c(0x5b616c),
    grid = c(0xdde0e5), night = c(0xeceef2), line = c(0x1b1d21), outline = c(0xffffff),
    temp = c(0xc2410c), feels = c(0x7c3aed), pop = c(0x0369a1), snow = c(0x6d28d9), rh = c(0x6b6400), cloud = c(0x5f6b7a), pres = c(0x1b1d21),
    qpf = c(0x15803d), wind = c(0x1d4ed8), hi = c(0xc2410c), lo = c(0x0369a1), bannerBg = c(0xffd23f), bannerFg = c(0x111111), link = c(0x0369a1),
    isDark = false,
)

val DarkColors = WuColors(
    bg = c(0x17181a), panel = c(0x1e1f22), day = c(0x2c2f34), dayAlt = c(0x26282c), nightAlt = c(0x17181b), fg = c(0xe6e7ea), muted = c(0xa3a8b0),
    grid = c(0x2b2d31), night = c(0x101113), line = c(0xf2f3f5), outline = c(0x0b0c0d),
    temp = c(0xff6b4a), feels = c(0xb8a4ff), pop = c(0x56b4e9), snow = c(0xd7c7ff), rh = c(0xc9c35a), cloud = c(0xa7adb6), pres = c(0xf2f3f5),
    qpf = c(0x3fbf7f), wind = c(0x5b9bff), hi = c(0xff6b4a), lo = c(0x56b4e9), bannerBg = c(0xffd23f), bannerFg = c(0x111111), link = c(0x56b4e9),
    isDark = true,
)

/** Quickie keyword colors: cool blues through warm reds. */
fun quickieColor(kind: app.wunder.data.QuickieKind, w: WuColors): Color = when (kind) {
    app.wunder.data.QuickieKind.MUCH_COOLER, app.wunder.data.QuickieKind.COOLER -> w.lo
    app.wunder.data.QuickieKind.NEARLY_THE_SAME -> w.fg
    else -> w.hi
}

val LocalWu = staticCompositionLocalOf { LightColors }

@Composable
fun WuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val w = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(primary = w.link, onPrimary = w.bg, background = w.bg, onBackground = w.fg, surface = w.bg, onSurface = w.fg,
            surfaceVariant = w.panel, onSurfaceVariant = w.muted, surfaceContainer = w.panel, surfaceContainerLow = w.panel, outline = w.grid)
    } else {
        lightColorScheme(primary = w.link, onPrimary = w.bg, background = w.bg, onBackground = w.fg, surface = w.bg, onSurface = w.fg,
            surfaceVariant = w.panel, onSurfaceVariant = w.muted, surfaceContainer = w.panel, surfaceContainerLow = w.panel, outline = w.grid)
    }
    CompositionLocalProvider(LocalWu provides w) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

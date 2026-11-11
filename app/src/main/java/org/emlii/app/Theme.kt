package org.emlii.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Same colour keys as the desktop GUI's THEMES / THEME_PRESETS in emlii_gui.py. */
data class EmliiColors(
    val bg: Color, val surface: Color, val border: Color,
    val purple: Color, val purpleHi: Color,
    val userBg: Color, val userBorder: Color,
    val predBg: Color, val predBorder: Color,
    val warnBg: Color, val warnBorder: Color,
    val text: Color, val muted: Color,
    val green: Color, val cyan: Color, val yellow: Color, val red: Color, val pink: Color,
    val btnFg: Color
)

private fun c(hex: Long): Color = Color(0xFF000000L or hex)

private val Dark = EmliiColors(
    bg = c(0x0b0a10), surface = c(0x151220), border = c(0x2a2240),
    purple = c(0xb84bff), purpleHi = c(0xd9a3ff),
    userBg = c(0x1c1430), userBorder = c(0x3a2a5c),
    predBg = c(0x2a0f24), predBorder = c(0xff4fd8),
    warnBg = c(0x2a2410), warnBorder = c(0xf0c040),
    text = c(0xd4cdea), muted = c(0x8a82b0),
    green = c(0x00e676), cyan = c(0x00c8b4), yellow = c(0xf0c040), red = c(0xe05555), pink = c(0xff4fd8),
    btnFg = c(0x0b0a10)
)

private val Light = EmliiColors(
    bg = c(0xfaf8ff), surface = c(0xefeaf9), border = c(0xd8cdee),
    purple = c(0x8a2be2), purpleHi = c(0x6b21a8),
    userBg = c(0xe9defa), userBorder = c(0xcbb6ee),
    predBg = c(0xfde7f7), predBorder = c(0xd6339f),
    warnBg = c(0xfff6dc), warnBorder = c(0xc58a00),
    text = c(0x2b2540), muted = c(0x7a7098),
    green = c(0x0a8f4e), cyan = c(0x00796b), yellow = c(0x9a6700), red = c(0xc0392b), pink = c(0xd6339f),
    btnFg = c(0xffffff)
)

private val Presets: Map<String, EmliiColors> = linkedMapOf(
    "Purple neon" to Dark,
    "Light" to Light,
    "Hacker green" to Dark.copy(
        bg = c(0x020a04), surface = c(0x07140b), border = c(0x12401f), text = c(0xc9ffd6),
        muted = c(0x4f9a64), purple = c(0x39ff14), purpleHi = c(0x7dff9a), userBg = c(0x0b2414),
        userBorder = c(0x17572a), predBg = c(0x14250a), predBorder = c(0xb6ff00),
        green = c(0x39ff14), cyan = c(0x00ffa2), pink = c(0xb6ff00)
    ),
    "Cyberpunk" to Dark.copy(
        bg = c(0x0a0612), surface = c(0x140b24), border = c(0x3a1a66), text = c(0xf0e6ff),
        muted = c(0x9a7fd0), purple = c(0xff2e97), purpleHi = c(0x00f0ff), userBg = c(0x1c0f35),
        userBorder = c(0x5a2aa0), predBg = c(0x2a0a24), predBorder = c(0xff2e97),
        green = c(0x00ffa2), cyan = c(0x00f0ff), yellow = c(0xfcee0a), pink = c(0xff2e97)
    ),
    "Ocean" to Dark.copy(
        bg = c(0x06111c), surface = c(0x0c1c2d), border = c(0x17405f), text = c(0xd6ecff),
        muted = c(0x7fa6c4), purple = c(0x2f9bff), purpleHi = c(0x8ccaff), userBg = c(0x0e2a45),
        userBorder = c(0x1f5a8a), predBg = c(0x0b2f3a), predBorder = c(0x2fe0c8),
        green = c(0x2fe08a), cyan = c(0x2fe0c8), pink = c(0x2fe0c8)
    ),
    "Rose" to Dark.copy(
        bg = c(0x140a10), surface = c(0x201019), border = c(0x4a1f36), text = c(0xffe4ef),
        muted = c(0xc08aa4), purple = c(0xff6fa5), purpleHi = c(0xffb3d1), userBg = c(0x2e1422),
        userBorder = c(0x6b2a4d), predBg = c(0x3a1428), predBorder = c(0xff6fa5), pink = c(0xff6fa5)
    ),
    "Mono" to Dark.copy(
        bg = c(0x0e0e0e), surface = c(0x181818), border = c(0x333333), text = c(0xe6e6e6),
        muted = c(0x8a8a8a), purple = c(0xd0d0d0), purpleHi = c(0xffffff), userBg = c(0x222222),
        userBorder = c(0x444444), predBg = c(0x2a2a2a), predBorder = c(0xffffff), pink = c(0xffffff)
    )
)

object Themes {
    val names: List<String> = Presets.keys.toList()

    private fun luminance(col: Color): Float = 0.299f * col.red + 0.587f * col.green + 0.114f * col.blue

    /** Readable text on the accent colour, like build_theme() in the desktop GUI. */
    fun get(name: String): EmliiColors {
        val base = Presets[name] ?: Dark
        val fg = if (luminance(base.purple) > 0.45f) c(0x08060e) else c(0xffffff)
        return base.copy(btnFg = fg)
    }
}

val LocalEmlii = staticCompositionLocalOf { Themes.get("Purple neon") }
val LocalFs = staticCompositionLocalOf { 11 }

/** "#rrggbb" -> Color, or null if it isn't one. */
fun hexColor(h: String): Color? =
    if (Regex("#[0-9a-fA-F]{6}").matches(h)) Color(android.graphics.Color.parseColor(h)) else null

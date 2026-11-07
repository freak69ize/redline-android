package com.redline.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import android.content.res.Configuration

val Red = Color(0xFFFF1F1F)
val Black = Color(0xFF000000)
val DefaultBg = Color(0xFF0F0F0F)
val Surface = Color(0xFF141414)
val Surface2 = Color(0xFF1E1E1E)
val Ink = Color(0xFFF4F4F5)
val Muted = Color(0xFFA3A3A3)
val Line = Color(0xFF2C2C2C)

private val Colors = darkColorScheme(
    primary = Red,
    onPrimary = Black,
    background = DefaultBg,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    outline = Line,
)

@Composable
fun isTelevision(): Boolean {
    val mode = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK
    return mode == Configuration.UI_MODE_TYPE_TELEVISION
}

fun colorFromHex(raw: String): Color {
    var hex = raw.trim().removePrefix("#")
    if (hex.length == 3) hex = buildString { hex.forEach { append(it).append(it) } }
    if (hex.length != 6 || hex.any { it !in "0123456789abcdefABCDEF" }) return DefaultBg
    return Color(0xFF000000 or hex.toLong(16))
}

fun hexFromColor(color: Color): String {
    val r = (color.red * 255f).toInt().coerceIn(0, 255)
    val g = (color.green * 255f).toInt().coerceIn(0, 255)
    val b = (color.blue * 255f).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

fun hexToHsv(raw: String): FloatArray {
    val color = colorFromHex(raw)
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val sat = if (max == 0f) 0f else delta / max
    return floatArrayOf(hue, sat, max)
}

@Composable
fun RedlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

@Composable
fun Modifier.tvFocus(shape: Shape = RoundedCornerShape(10.dp)): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(3.dp, if (focused) Color.White else Color.Transparent, shape)
}

@Composable
fun Modifier.redFocus(): Modifier = tvFocus()

package com.redline.player.data

enum class Screen { Shelf, Settings, Player }

data class VideoItem(
    val uri: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val addedAt: Long,
    val favorite: Boolean,
    val favoredAt: Long,
    val positionMs: Long,
    val thumbPath: String,
)

data class UserGif(
    val uri: String,
    val name: String,
)

data class Settings(
    val backdrop: String = "solid",
    val gifId: String? = null,
    val scrim: Float = 0.5f,
    val favoritesOnTop: Boolean = true,
    val appTitle: String = "Redline",
    val logoUri: String? = null,
    val backgroundColor: String = "#0F0F0F",
)

fun Settings.shelfTitle(): String = appTitle.trim().ifBlank { "Redline" }

fun Settings.usesGif(): Boolean = backdrop == "gif" && !gifId.isNullOrBlank()

data class PresetGif(
    val id: String,
    val name: String,
    val assetPath: String,
)

val PRESET_GIFS = listOf(
    PresetGif("asset:embers", "Embers", "file:///android_asset/backdrops/embers.gif"),
    PresetGif("asset:leak", "Light leak", "file:///android_asset/backdrops/leak.gif"),
    PresetGif("asset:pulse", "Pulse", "file:///android_asset/backdrops/pulse.gif"),
)

data class UiState(
    val videos: List<VideoItem> = emptyList(),
    val gifs: List<UserGif> = emptyList(),
    val settings: Settings = Settings(),
    val screen: Screen = Screen.Shelf,
    val playingUri: String? = null,
    val focusedUri: String? = null,
    val ready: Boolean = false,
    val busy: Boolean = false,
)

sealed interface PlayerCommand {
    data class SeekBy(val ms: Long) : PlayerCommand
    data object TogglePlay : PlayerCommand
    data class VolumeBy(val delta: Float) : PlayerCommand
}

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    val digits = if (value >= 10.0) 0 else 1
    return "%.${digits}f %s".format(value, units[index])
}

fun backdropModel(settings: Settings, gifs: List<UserGif>): Any? {
    if (settings.backdrop != "gif" || settings.gifId == null) return null
    PRESET_GIFS.firstOrNull { it.id == settings.gifId }?.let { return it.assetPath }
    return gifs.firstOrNull { it.uri == settings.gifId }?.uri
}

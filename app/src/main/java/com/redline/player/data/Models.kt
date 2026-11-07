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
    val sourceTree: String? = null,
    val rowIds: List<String> = emptyList(),
)

data class UserGif(
    val uri: String,
    val name: String,
)

data class FolderEntry(
    val uri: String,
    val name: String,
    val excluded: Boolean,
)

data class ShelfRow(
    val id: String,
    val name: String,
    val order: Int,
    val kind: String,
)

fun defaultRows(): List<ShelfRow> = listOf(
    ShelfRow("favorites", "Favorites", 0, "favorites"),
    ShelfRow("continue", "Continue watching", 1, "continue"),
    ShelfRow("library", "Library", 2, "library"),
)

data class Settings(
    val backdrop: String = "solid",
    val gifId: String? = null,
    val scrim: Float = 0.55f,
    val appTitle: String = "Redline",
    val logoUri: String? = null,
    val backgroundColor: String = "#0F0F0F",
    val seekStepMs: Long = 10_000,
    val resumePlayback: Boolean = true,
    val playbackSpeed: Float = 1f,
    val aspect: String = "fit",
    val repeatMode: String = "off",
    val autoplayNext: Boolean = true,
    val showRemaining: Boolean = false,
    val subtitleScale: Float = 1f,
    val confirmRemove: Boolean = true,
    val sleepMinutes: Int = 0,
    val rows: List<ShelfRow> = defaultRows(),
)

fun Settings.shelfTitle(): String = appTitle.trim().ifBlank { "Redline" }

fun Settings.usesGif(): Boolean = backdrop == "gif" && !gifId.isNullOrBlank() && !gifId.startsWith("asset:")

data class UiState(
    val videos: List<VideoItem> = emptyList(),
    val gifs: List<UserGif> = emptyList(),
    val folders: List<FolderEntry> = emptyList(),
    val settings: Settings = Settings(),
    val screen: Screen = Screen.Shelf,
    val playingUri: String? = null,
    val playingRowId: String? = null,
    val focusedUri: String? = null,
    val menuUri: String? = null,
    val menuArmed: Boolean = true,
    val confirmUri: String? = null,
    val ready: Boolean = false,
    val busy: Boolean = false,
)

sealed interface PlayerCommand {
    data class SeekBy(val ms: Long) : PlayerCommand
    data object TogglePlay : PlayerCommand
    data class VolumeBy(val delta: Float) : PlayerCommand
    data object Reveal : PlayerCommand
}

fun videosFor(row: ShelfRow, videos: List<VideoItem>): List<VideoItem> {
    return when (row.kind) {
        "favorites" -> videos.filter { it.favorite }.sortedByDescending { it.favoredAt }
        "continue" -> videos.filter { item ->
            item.positionMs > 3_000 && (item.durationMs == 0L || item.positionMs < item.durationMs - 4_000)
        }
        "library" -> videos
        else -> videos.filter { row.id in it.rowIds }
    }
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
    if (!settings.usesGif()) return null
    return gifs.firstOrNull { it.uri == settings.gifId }?.uri
}

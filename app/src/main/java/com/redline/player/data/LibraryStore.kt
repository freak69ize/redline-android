package com.redline.player.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class FoundVideo(
    val uri: Uri,
    val name: String,
    val size: Long,
)

class LibraryStore(private val context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val thumbs = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun load(): UiState {
        if (!file.exists()) return UiState(ready = true)
        return try {
            val root = JSONObject(file.readText())
            val settingsJson = root.optJSONObject("settings")
            val settings = if (settingsJson == null) {
                Settings()
            } else {
                val storedBackdrop = settingsJson.optString("backdrop", "solid")
                val gifId = settingsJson.optString("gifId").ifBlank { null }
                val cleanGif = if (gifId != null && gifId.startsWith("asset:")) null else gifId
                Settings(
                    backdrop = if (cleanGif == null || storedBackdrop == "black") "solid" else storedBackdrop,
                    gifId = cleanGif,
                    scrim = settingsJson.optDouble("scrim", 0.55).toFloat().coerceIn(0f, 0.8f),
                    appTitle = settingsJson.optString("appTitle", "Redline").ifBlank { "Redline" },
                    logoUri = settingsJson.optString("logoUri").ifBlank { null },
                    backgroundColor = settingsJson.optString("backgroundColor", "#0F0F0F").ifBlank { "#0F0F0F" },
                    selectorColor = settingsJson.optString("selectorColor", "#FFFFFF").ifBlank { "#FFFFFF" },
                    panelOpacity = settingsJson.optDouble("panelOpacity", 0.7).toFloat().coerceIn(0.15f, 1f),
                    seekStepMs = settingsJson.optLong("seekStepMs", 10_000).coerceIn(5_000, 60_000),
                    resumePlayback = settingsJson.optBoolean("resumePlayback", true),
                    playbackSpeed = settingsJson.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(0.5f, 2f),
                    aspect = settingsJson.optString("aspect", "fit").ifBlank { "fit" },
                    repeatMode = settingsJson.optString("repeatMode", "off").ifBlank { "off" },
                    autoplayNext = settingsJson.optBoolean("autoplayNext", true),
                    showRemaining = settingsJson.optBoolean("showRemaining", false),
                    subtitleScale = settingsJson.optDouble("subtitleScale", 1.0).toFloat().coerceIn(0.6f, 2f),
                    confirmRemove = settingsJson.optBoolean("confirmRemove", true),
                    sleepMinutes = settingsJson.optInt("sleepMinutes", 0).coerceIn(0, 180),
                    rows = settingsJson.optJSONArray("rows").toRows(),
                )
            }
            UiState(
                videos = root.optJSONArray("videos").toVideos(),
                gifs = root.optJSONArray("gifs").toGifs().filterNot { it.uri.startsWith("asset:") },
                folders = root.optJSONArray("folders").toFolders(),
                settings = settings,
                ready = true,
            )
        } catch (_: Exception) {
            UiState(ready = true)
        }
    }

    fun save(state: UiState) {
        val rows = JSONArray()
        state.settings.rows.sortedBy { it.order }.forEach { row ->
            rows.put(
                JSONObject()
                    .put("id", row.id)
                    .put("name", row.name)
                    .put("order", row.order)
                    .put("kind", row.kind),
            )
        }
        val root = JSONObject()
        root.put(
            "settings",
            JSONObject()
                .put("backdrop", state.settings.backdrop)
                .put("gifId", state.settings.gifId ?: "")
                .put("scrim", state.settings.scrim.toDouble())
                .put("appTitle", state.settings.appTitle)
                .put("logoUri", state.settings.logoUri ?: "")
                .put("backgroundColor", state.settings.backgroundColor)
                .put("selectorColor", state.settings.selectorColor)
                .put("panelOpacity", state.settings.panelOpacity.toDouble())
                .put("seekStepMs", state.settings.seekStepMs)
                .put("resumePlayback", state.settings.resumePlayback)
                .put("playbackSpeed", state.settings.playbackSpeed.toDouble())
                .put("aspect", state.settings.aspect)
                .put("repeatMode", state.settings.repeatMode)
                .put("autoplayNext", state.settings.autoplayNext)
                .put("showRemaining", state.settings.showRemaining)
                .put("subtitleScale", state.settings.subtitleScale.toDouble())
                .put("confirmRemove", state.settings.confirmRemove)
                .put("sleepMinutes", state.settings.sleepMinutes)
                .put("rows", rows),
        )
        val videos = JSONArray()
        state.videos.forEach { item ->
            val membership = JSONArray()
            item.rowIds.forEach { membership.put(it) }
            videos.put(
                JSONObject()
                    .put("uri", item.uri)
                    .put("title", item.title)
                    .put("durationMs", item.durationMs)
                    .put("sizeBytes", item.sizeBytes)
                    .put("addedAt", item.addedAt)
                    .put("favorite", item.favorite)
                    .put("favoredAt", item.favoredAt)
                    .put("positionMs", item.positionMs)
                    .put("thumbPath", item.thumbPath)
                    .put("sourceTree", item.sourceTree ?: "")
                    .put("rowIds", membership),
            )
        }
        val gifs = JSONArray()
        state.gifs.forEach { gif ->
            gifs.put(JSONObject().put("uri", gif.uri).put("name", gif.name))
        }
        val folders = JSONArray()
        state.folders.forEach { folder ->
            folders.put(
                JSONObject()
                    .put("uri", folder.uri)
                    .put("name", folder.name)
                    .put("excluded", folder.excluded),
            )
        }
        root.put("videos", videos)
        root.put("gifs", gifs)
        root.put("folders", folders)
        file.writeText(root.toString())
    }

    fun treeName(uri: Uri): String {
        val id = decodeId(uri)
        return id.substringAfterLast(':').substringAfterLast('/').ifBlank { "Folder" }
    }

    fun decodeId(uri: Uri): String {
        return try {
            val raw = when {
                DocumentsContract.isTreeUri(uri) -> DocumentsContract.getTreeDocumentId(uri)
                else -> DocumentsContract.getDocumentId(uri)
            }
            Uri.decode(raw)
        } catch (_: Exception) {
            ""
        }
    }

    fun findVideos(treeUri: Uri, excludedUris: List<String>): List<FoundVideo> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val skip = excludedUris.map { decodeId(Uri.parse(it)) }.filter { it.isNotEmpty() }
        val out = mutableListOf<FoundVideo>()
        walk(root, 0, skip, out)
        return out
    }

    fun makeItem(found: FoundVideo, sourceTree: String): VideoItem {
        val id = found.uri.toString().hashCode().toUInt().toString(16)
        val title = found.name.substringBeforeLast('.').replace('_', ' ').ifBlank { found.name }
        return VideoItem(
            uri = found.uri.toString(),
            title = title,
            durationMs = readDuration(found.uri),
            sizeBytes = found.size,
            addedAt = System.currentTimeMillis(),
            favorite = false,
            favoredAt = 0L,
            positionMs = 0L,
            thumbPath = writeThumb(found.uri, id),
            sourceTree = sourceTree,
        )
    }

    fun readDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        return uri.lastPathSegment ?: "Video"
    }

    fun readSize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
        }
        return 0L
    }

    fun readDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    fun writeThumb(uri: Uri, id: String, durationMs: Long = 0L): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = if (durationMs > 0) durationMs else {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
            val frame = frameAt(retriever, duration) ?: return ""
            val software = if (frame.config == Bitmap.Config.ARGB_8888) frame else frame.copy(Bitmap.Config.ARGB_8888, false) ?: frame
            val scaled = Bitmap.createScaledBitmap(software, 480, 270, true)
            thumbs.listFiles()?.forEach { old ->
                if (old.name == "$id.jpg" || old.name.startsWith("$id-")) old.delete()
            }
            val out = File(thumbs, "$id-${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { stream -> scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream) }
            if (scaled != software) scaled.recycle()
            if (software != frame) software.recycle()
            frame.recycle()
            out.absolutePath
        } catch (_: Exception) {
            ""
        } finally {
            retriever.release()
        }
    }

    private fun frameAt(retriever: MediaMetadataRetriever, durationMs: Long): Bitmap? {
        val tries = ArrayList<Long>()
        if (durationMs >= 180_000L) {
            tries += 210_000L
            tries += 180_000L
            tries += minOf(300_000L, durationMs - 2_000L)
        }
        if (durationMs > 8_000L) tries += (durationMs * 3L) / 10L
        tries += 10_000L
        tries += 1_000L
        tries += 0L
        var fallback: Bitmap? = null
        for (ms in tries.distinct()) {
            if (durationMs > 0 && ms >= durationMs) continue
            val frame = retriever.getFrameAtTime(ms * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: continue
            if (fallback == null) fallback = frame
            if (!isDim(frame)) {
                if (fallback != frame) fallback?.recycle()
                return frame
            }
            if (frame != fallback) frame.recycle()
        }
        return fallback
    }

    private fun isDim(frame: Bitmap): Boolean {
        val sample = if (frame.config == Bitmap.Config.ARGB_8888) frame else frame.copy(Bitmap.Config.ARGB_8888, false) ?: frame
        var sum = 0L
        var count = 0
        val stepX = (sample.width / 10).coerceAtLeast(1)
        val stepY = (sample.height / 10).coerceAtLeast(1)
        var y = 0
        while (y < sample.height) {
            var x = 0
            while (x < sample.width) {
                val pixel = sample.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                sum += r + g + b
                count++
                x += stepX
            }
            y += stepY
        }
        if (sample != frame) sample.recycle()
        return count == 0 || sum / count < 36
    }

    private fun walk(node: DocumentFile, depth: Int, skip: List<String>, out: MutableList<FoundVideo>) {
        if (depth > 6) return
        val children = node.listFiles() ?: return
        for (child in children) {
            val id = decodeId(child.uri)
            if (skip.any { prefix -> id.startsWith(prefix) }) continue
            if (child.isDirectory) {
                walk(child, depth + 1, skip, out)
            } else if (isVideoDoc(child.name, child.type)) {
                out += FoundVideo(child.uri, child.name ?: "Video", child.length().coerceAtLeast(0L))
            }
        }
    }

    private fun isVideoDoc(name: String?, mime: String?): Boolean {
        if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) return true
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return ext in VIDEO_EXT
    }

    private fun JSONArray?.toVideos(): List<VideoItem> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                val membership = item.optJSONArray("rowIds")
                val rowIds = buildList {
                    if (membership != null) {
                        for (r in 0 until membership.length()) {
                            val id = membership.optString(r)
                            if (id.isNotBlank()) add(id)
                        }
                    }
                }
                add(
                    VideoItem(
                        uri = item.optString("uri"),
                        title = item.optString("title"),
                        durationMs = item.optLong("durationMs"),
                        sizeBytes = item.optLong("sizeBytes"),
                        addedAt = item.optLong("addedAt"),
                        favorite = item.optBoolean("favorite"),
                        favoredAt = item.optLong("favoredAt"),
                        positionMs = item.optLong("positionMs"),
                        thumbPath = item.optString("thumbPath"),
                        sourceTree = item.optString("sourceTree").ifBlank { null },
                        rowIds = rowIds,
                    ),
                )
            }
        }.filter { it.uri.isNotBlank() }
    }

    private fun JSONArray?.toGifs(): List<UserGif> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                val uri = item.optString("uri")
                if (uri.isNotBlank()) add(UserGif(uri, item.optString("name")))
            }
        }
    }

    private fun JSONArray?.toFolders(): List<FolderEntry> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                val uri = item.optString("uri")
                if (uri.isNotBlank()) {
                    add(FolderEntry(uri, item.optString("name").ifBlank { "Folder" }, item.optBoolean("excluded")))
                }
            }
        }
    }

    private fun JSONArray?.toRows(): List<ShelfRow> {
        val parsed = if (this == null) {
            emptyList()
        } else {
            buildList {
                for (i in 0 until length()) {
                    val item = optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    add(
                        ShelfRow(
                            id = id,
                            name = item.optString("name").ifBlank { "Row" },
                            order = item.optInt("order", i),
                            kind = item.optString("kind").ifBlank { "custom" },
                        ),
                    )
                }
            }
        }
        if (parsed.isEmpty()) return defaultRows()
        val kinds = parsed.map { it.kind }.toSet()
        val missing = defaultRows().filter { it.kind !in kinds }
        return (parsed + missing.mapIndexed { index, row -> row.copy(order = parsed.size + index) })
            .distinctBy { it.id }
    }

    companion object {
        private val VIDEO_EXT = setOf(
            "mp4", "m4v", "mov", "mkv", "avi", "3gp", "3g2", "flv", "wmv", "asf", "ts", "m2ts", "mts",
            "webm", "mpg", "mpeg", "vob", "ogv", "divx",
            "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "ac3", "eac3",
        )
    }
}

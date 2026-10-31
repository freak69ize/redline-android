package com.redline.player.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

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
                Settings(
                    backdrop = if (storedBackdrop == "black") "solid" else storedBackdrop,
                    gifId = settingsJson.optString("gifId").ifBlank { null },
                    scrim = settingsJson.optDouble("scrim", 0.5).toFloat().coerceIn(0f, 0.8f),
                    favoritesOnTop = settingsJson.optBoolean("favoritesOnTop", true),
                    appTitle = settingsJson.optString("appTitle", "Redline").ifBlank { "Redline" },
                    logoUri = settingsJson.optString("logoUri").ifBlank { null },
                    backgroundColor = settingsJson.optString("backgroundColor", "#0F0F0F").ifBlank { "#0F0F0F" },
                )
            }
            val videos = root.optJSONArray("videos").toVideos()
            val gifs = root.optJSONArray("gifs").toGifs()
            UiState(videos = videos, gifs = gifs, settings = settings, ready = true)
        } catch (_: Exception) {
            UiState(ready = true)
        }
    }

    fun save(state: UiState) {
        val root = JSONObject()
        root.put(
            "settings",
            JSONObject()
                .put("backdrop", state.settings.backdrop)
                .put("gifId", state.settings.gifId ?: "")
                .put("scrim", state.settings.scrim.toDouble())
                .put("favoritesOnTop", state.settings.favoritesOnTop)
                .put("appTitle", state.settings.appTitle)
                .put("logoUri", state.settings.logoUri ?: "")
                .put("backgroundColor", state.settings.backgroundColor),
        )
        val videos = JSONArray()
        state.videos.forEach { item ->
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
                    .put("thumbPath", item.thumbPath),
            )
        }
        val gifs = JSONArray()
        state.gifs.forEach { gif ->
            gifs.put(JSONObject().put("uri", gif.uri).put("name", gif.name))
        }
        root.put("videos", videos)
        root.put("gifs", gifs)
        file.writeText(root.toString())
    }

    fun readDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
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

    fun writeThumb(uri: Uri, id: String): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime()
            if (frame == null) return ""
            val scaled = Bitmap.createScaledBitmap(frame, 480, 270, true)
            val out = File(thumbs, "$id.jpg")
            FileOutputStream(out).use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 72, stream)
            }
            if (scaled != frame) scaled.recycle()
            frame.recycle()
            out.absolutePath
        } catch (_: Exception) {
            ""
        } finally {
            retriever.release()
        }
    }

    private fun JSONArray?.toVideos(): List<VideoItem> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
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
}

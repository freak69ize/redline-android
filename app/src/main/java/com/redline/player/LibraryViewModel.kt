package com.redline.player

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redline.player.data.FolderEntry
import com.redline.player.data.LibraryStore
import com.redline.player.data.PlayerCommand
import com.redline.player.data.Screen
import com.redline.player.data.Settings
import com.redline.player.data.ShelfRow
import com.redline.player.data.UiState
import com.redline.player.data.UserGif
import com.redline.player.data.VideoItem
import com.redline.player.data.videosFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LibraryStore(app)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _commands = MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 8)
    val commands = _commands.asSharedFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { store.load() }
            _state.value = loaded.copy(ready = true, screen = Screen.Shelf)
        }
    }

    fun onFocus(uri: String) {
        _state.value = _state.value.copy(focusedUri = uri)
    }

    fun openSettings() {
        if (_state.value.screen == Screen.Player) return
        _state.value = _state.value.copy(screen = Screen.Settings, menuUri = null, confirmUri = null)
    }

    fun play(uri: String, rowId: String) {
        val current = _state.value
        if (current.menuUri != null && !current.menuArmed) return
        _state.value = current.copy(
            screen = Screen.Player,
            playingUri = uri,
            playingRowId = rowId,
            focusedUri = uri,
            menuUri = null,
            confirmUri = null,
            menuArmed = true,
        )
    }

    fun closePlayer() {
        _state.value = _state.value.copy(screen = Screen.Shelf, playingUri = null)
    }

    fun handleBack(): Boolean {
        val current = _state.value
        if (current.confirmUri != null) {
            _state.value = current.copy(confirmUri = null)
            return true
        }
        if (current.menuUri != null) {
            _state.value = current.copy(menuUri = null)
            return true
        }
        if (current.screen == Screen.Player) {
            closePlayer()
            return true
        }
        if (current.screen == Screen.Settings) {
            _state.value = current.copy(screen = Screen.Shelf)
            return true
        }
        return false
    }

    fun seek(forward: Boolean) {
        if (_state.value.screen != Screen.Player) return
        val step = _state.value.settings.seekStepMs
        send(PlayerCommand.SeekBy(if (forward) step else -step))
    }

    fun volume(delta: Float) {
        if (_state.value.screen != Screen.Player) return
        send(PlayerCommand.VolumeBy(delta))
    }

    @Volatile
    var playerChrome: Boolean = false

    fun noteChrome(visible: Boolean) {
        playerChrome = visible
    }

    fun revealPlayer() {
        if (_state.value.screen == Screen.Player) send(PlayerCommand.Reveal)
    }

    fun confirm() {
        if (_state.value.screen == Screen.Player) send(PlayerCommand.TogglePlay)
    }

    fun renameVideo(uri: String, name: String) {
        val clean = name.trim().take(80)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            mutate { current ->
                current.copy(
                    videos = current.videos.map { video ->
                        if (video.uri == uri) video.copy(title = clean) else video
                    },
                    menuUri = null,
                )
            }
        }
    }

    fun moveVideoToRow(uri: String, rowId: String) {
        viewModelScope.launch {
            mutate { current ->
                val row = current.settings.rows.firstOrNull { it.id == rowId } ?: return@mutate current
                current.copy(
                    videos = current.videos.map { video ->
                        if (video.uri != uri) video
                        else when (row.kind) {
                            "favorites" -> video.copy(favorite = true, favoredAt = System.currentTimeMillis())
                            "library" -> video.copy(rowIds = emptyList())
                            else -> video.copy(rowIds = listOf(row.id))
                        }
                    },
                    menuUri = null,
                )
            }
        }
    }

    fun openMenu() {
        if (_state.value.screen != Screen.Shelf) return
        val uri = _state.value.focusedUri ?: return
        _state.value = _state.value.copy(menuUri = uri, confirmUri = null, menuArmed = false)
    }

    fun armMenu() {
        val current = _state.value
        if (current.menuUri == null || current.menuArmed) return
        _state.value = current.copy(menuArmed = true)
    }

    fun closeMenu() {
        val current = _state.value
        if (!current.menuArmed) return
        _state.value = current.copy(menuUri = null)
    }

    fun onVideoEnded() {
        val current = _state.value
        if (current.settings.repeatMode == "one") return
        val row = current.settings.rows.firstOrNull { it.id == current.playingRowId }
            ?: current.settings.rows.firstOrNull { it.kind == "library" }
            ?: return
        val list = videosFor(row, current.videos)
        val index = list.indexOfFirst { it.uri == current.playingUri }
        if (index < 0) return
        val next = when {
            index < list.lastIndex -> list[index + 1]
            current.settings.repeatMode == "all" && list.isNotEmpty() -> list.first()
            else -> null
        } ?: return
        if (current.settings.autoplayNext || current.settings.repeatMode == "all") {
            play(next.uri, row.id)
        }
    }

    fun toggleFavoriteFocused() {
        val uri = when (_state.value.screen) {
            Screen.Player -> _state.value.playingUri
            else -> _state.value.menuUri ?: _state.value.focusedUri
        } ?: return
        toggleFavorite(uri)
    }

    fun toggleFavorite(uri: String) {
        viewModelScope.launch {
            mutate { current ->
                current.copy(
                    videos = current.videos.map { item ->
                        if (item.uri != uri) item
                        else item.copy(
                            favorite = !item.favorite,
                            favoredAt = if (!item.favorite) System.currentTimeMillis() else 0L,
                        )
                    },
                )
            }
        }
    }

    fun askRemoveFocused() {
        val uri = _state.value.menuUri ?: _state.value.focusedUri ?: return
        askRemove(uri)
    }

    fun askRemove(uri: String) {
        if (_state.value.settings.confirmRemove) {
            _state.value = _state.value.copy(confirmUri = uri, menuUri = null)
        } else {
            remove(uri)
        }
    }

    fun remove(uri: String) {
        viewModelScope.launch {
            val thumb = _state.value.videos.firstOrNull { it.uri == uri }?.thumbPath
            releaseGrant(uri)
            if (!thumb.isNullOrBlank()) {
                withContext(Dispatchers.IO) { File(thumb).delete() }
            }
            mutate { current ->
                val nextScreen = if (current.playingUri == uri) Screen.Shelf else current.screen
                current.copy(
                    videos = current.videos.filterNot { it.uri == uri },
                    playingUri = if (current.playingUri == uri) null else current.playingUri,
                    screen = nextScreen,
                    menuUri = null,
                    confirmUri = null,
                    focusedUri = if (current.focusedUri == uri) null else current.focusedUri,
                )
            }
        }
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            mutate { it.copy(settings = settings) }
        }
    }

    fun savePosition(uri: String, positionMs: Long) {
        viewModelScope.launch {
            mutate { current ->
                current.copy(
                    videos = current.videos.map { item ->
                        if (item.uri == uri) item.copy(positionMs = positionMs) else item
                    },
                )
            }
        }
    }

    fun addVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val added = mutableListOf<VideoItem>()
            try {
                val existing = _state.value.videos.map { it.uri }.toMutableSet()
                for (uri in uris) {
                    val key = uri.toString()
                    if (!existing.add(key)) continue
                    val mime = getApplication<Application>().contentResolver.getType(uri).orEmpty()
                    val name = withContext(Dispatchers.IO) { store.readDisplayName(uri) }
                    if (!mime.startsWith("video/") && !isVideoName(name)) continue
                    takeGrant(uri)
                    val item = withContext(Dispatchers.IO) {
                        val id = key.hashCode().toUInt().toString(16)
                        VideoItem(
                            uri = key,
                            title = name.substringBeforeLast('.').replace('_', ' ').ifBlank { name },
                            durationMs = store.readDuration(uri),
                            sizeBytes = store.readSize(uri),
                            addedAt = System.currentTimeMillis(),
                            favorite = false,
                            favoredAt = 0L,
                            positionMs = 0L,
                            thumbPath = store.writeThumb(uri, id),
                        )
                    }
                    added += item
                }
                if (added.isNotEmpty()) {
                    mutate { current ->
                        current.copy(videos = (added + current.videos).distinctBy { it.uri })
                    }
                }
            } finally {
                _state.value = _state.value.copy(
                    busy = false,
                    focusedUri = added.firstOrNull()?.uri ?: _state.value.focusedUri,
                )
            }
        }
    }

    fun includeFolder(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                val name = withContext(Dispatchers.IO) { store.treeName(uri) }
                val excluded = _state.value.folders.filter { it.excluded }.map { it.uri }
                val found = withContext(Dispatchers.IO) { store.findVideos(uri, excluded) }
                val existing = _state.value.videos.map { it.uri }.toMutableSet()
                val added = mutableListOf<VideoItem>()
                for (hit in found) {
                    if (!existing.add(hit.uri.toString())) continue
                    takeGrant(hit.uri)
                    added += withContext(Dispatchers.IO) { store.makeItem(hit, uri.toString()) }
                }
                mutate { current ->
                    val folders = listOf(FolderEntry(uri.toString(), name, false)) +
                        current.folders.filterNot { it.uri == uri.toString() }
                    current.copy(
                        videos = (added + current.videos).distinctBy { it.uri },
                        folders = folders,
                        focusedUri = added.firstOrNull()?.uri ?: current.focusedUri,
                    )
                }
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    fun excludeFolder(uri: Uri) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { store.treeName(uri) }
            val prefix = withContext(Dispatchers.IO) { store.decodeId(uri) }
            mutate { current ->
                val videos = current.videos.filterNot { video ->
                    val id = store.decodeId(Uri.parse(video.uri))
                    video.sourceTree == uri.toString() || (prefix.isNotEmpty() && id.startsWith(prefix))
                }
                val folders = listOf(FolderEntry(uri.toString(), name, true)) +
                    current.folders.filterNot { it.uri == uri.toString() }
                current.copy(videos = videos, folders = folders)
            }
        }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch {
            releaseGrant(uri)
            mutate { current -> current.copy(folders = current.folders.filterNot { it.uri == uri }) }
        }
    }

    fun addRow(name: String) {
        val clean = name.trim().take(32)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            mutate { current ->
                val order = (current.settings.rows.maxOfOrNull { it.order } ?: 0) + 1
                val row = ShelfRow("row-$order-${clean.hashCode().toUInt().toString(16)}", clean, order, "custom")
                current.copy(settings = current.settings.copy(rows = current.settings.rows + row))
            }
        }
    }

    fun renameRow(id: String, name: String) {
        val clean = name.take(32)
        viewModelScope.launch {
            mutate { current ->
                current.copy(
                    settings = current.settings.copy(
                        rows = current.settings.rows.map { row ->
                            if (row.id == id) row.copy(name = clean.ifBlank { row.name }) else row
                        },
                    ),
                )
            }
        }
    }

    fun moveRow(id: String, direction: Int) {
        viewModelScope.launch {
            mutate { current ->
                val sorted = current.settings.rows.sortedBy { it.order }.toMutableList()
                val index = sorted.indexOfFirst { it.id == id }
                val target = index + direction
                if (index < 0 || target !in sorted.indices) return@mutate current
                val moving = sorted[index]
                sorted[index] = sorted[target]
                sorted[target] = moving
                current.copy(
                    settings = current.settings.copy(
                        rows = sorted.mapIndexed { order, row -> row.copy(order = order) },
                    ),
                )
            }
        }
    }

    fun deleteRow(id: String) {
        viewModelScope.launch {
            mutate { current ->
                val row = current.settings.rows.firstOrNull { it.id == id } ?: return@mutate current
                if (row.kind != "custom") return@mutate current
                current.copy(
                    settings = current.settings.copy(rows = current.settings.rows.filterNot { it.id == id }),
                    videos = current.videos.map { video ->
                        if (id !in video.rowIds) video else video.copy(rowIds = video.rowIds.filterNot { it == id })
                    },
                )
            }
        }
    }

    fun toggleVideoRow(uri: String, rowId: String) {
        viewModelScope.launch {
            mutate { current ->
                current.copy(
                    videos = current.videos.map { video ->
                        if (video.uri != uri) video
                        else if (rowId in video.rowIds) video.copy(rowIds = video.rowIds.filterNot { it == rowId })
                        else video.copy(rowIds = video.rowIds + rowId)
                    },
                )
            }
        }
    }

    fun setLogo(uri: Uri) {
        viewModelScope.launch {
            val mime = getApplication<Application>().contentResolver.getType(uri).orEmpty()
            val name = withContext(Dispatchers.IO) { store.readDisplayName(uri) }
            if (!isLogo(mime, name)) return@launch
            val previous = _state.value.settings.logoUri
            takeGrant(uri)
            if (previous != null && previous != uri.toString()) releaseGrant(previous)
            mutate { current ->
                current.copy(settings = current.settings.copy(logoUri = uri.toString()))
            }
        }
    }

    fun clearLogo() {
        viewModelScope.launch {
            val previous = _state.value.settings.logoUri ?: return@launch
            releaseGrant(previous)
            mutate { current -> current.copy(settings = current.settings.copy(logoUri = null)) }
        }
    }

    fun addGif(uri: Uri) {
        viewModelScope.launch {
            val mime = getApplication<Application>().contentResolver.getType(uri).orEmpty()
            val name = withContext(Dispatchers.IO) { store.readDisplayName(uri) }
            if (mime != "image/gif" && !name.endsWith(".gif", ignoreCase = true)) return@launch
            val previous = _state.value.gifs.firstOrNull()?.uri
            takeGrant(uri)
            if (previous != null && previous != uri.toString()) releaseGrant(previous)
            val gif = UserGif(uri.toString(), name.substringBeforeLast('.'))
            mutate { current ->
                current.copy(
                    gifs = listOf(gif),
                    settings = current.settings.copy(backdrop = "gif", gifId = gif.uri),
                )
            }
        }
    }

    fun removeGif(uri: String) {
        viewModelScope.launch {
            releaseGrant(uri)
            mutate { current ->
                current.copy(
                    gifs = current.gifs.filterNot { it.uri == uri },
                    settings = if (current.settings.gifId == uri) {
                        current.settings.copy(gifId = null, backdrop = "solid")
                    } else {
                        current.settings
                    },
                )
            }
        }
    }

    private fun send(command: PlayerCommand) {
        _commands.tryEmit(command)
    }

    private suspend fun mutate(block: (UiState) -> UiState) {
        mutex.withLock {
            val next = block(_state.value)
            withContext(Dispatchers.IO) { store.save(next) }
            _state.value = next
        }
    }

    private fun takeGrant(uri: Uri) {
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // The picker grant still lasts for this session.
        }
    }

    private fun releaseGrant(uriString: String) {
        if (!uriString.startsWith("content:")) return
        try {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Already released.
        }
    }

    private fun isVideoName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXT
    }

    private fun isLogo(mime: String, name: String): Boolean {
        if (mime == "image/jpeg" || mime == "image/png" || mime == "image/jpg") return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext == "jpg" || ext == "jpeg" || ext == "png"
    }

    companion object {
        private val VIDEO_EXT = setOf("mp4", "m4v", "webm", "mov", "mkv", "avi", "3gp", "ts", "m2ts", "ogv")
    }
}

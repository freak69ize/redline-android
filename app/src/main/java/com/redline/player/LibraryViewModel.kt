package com.redline.player

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redline.player.data.LibraryStore
import com.redline.player.data.PlayerCommand
import com.redline.player.data.Screen
import com.redline.player.data.Settings
import com.redline.player.data.UiState
import com.redline.player.data.UserGif
import com.redline.player.data.VideoItem
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
    private val handler = Handler(Looper.getMainLooper())
    private var pendingLeft: Runnable? = null
    private var armedAt = 0L

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

    fun play(uri: String) {
        _state.value = _state.value.copy(screen = Screen.Player, playingUri = uri, focusedUri = uri)
    }

    fun closePlayer() {
        val playing = _state.value.playingUri
        _state.value = _state.value.copy(screen = Screen.Shelf, playingUri = null)
        if (playing != null) {
            // Position is flushed by the player before this returns.
        }
    }

    fun toggleSettings() {
        _state.updateScreen { current ->
            when (current) {
                Screen.Player -> current
                Screen.Settings -> Screen.Shelf
                Screen.Shelf -> Screen.Settings
            }
        }
    }

    fun handleBack(): Boolean {
        val screen = _state.value.screen
        if (screen == Screen.Player) {
            closePlayer()
            return true
        }
        if (screen == Screen.Settings) {
            _state.value = _state.value.copy(screen = Screen.Shelf)
            return true
        }
        return false
    }

    /**
     * D-pad Left.
     * Shelf: the first tap waits 280ms so a second tap opens settings without moving focus.
     * Settings: the first tap is not consumed (sliders and focus still see it). A second tap closes.
     * Player: seek backward.
     *
     * @return true when this press was fully consumed.
     */
    fun onLeft(onSingle: () -> Unit): Boolean {
        val screen = _state.value.screen
        if (screen == Screen.Player) {
            send(PlayerCommand.SeekBy(-10_000))
            return true
        }
        val now = SystemClock.uptimeMillis()
        if (armedAt != 0L && now - armedAt <= DOUBLE_MS) {
            pendingLeft?.let { handler.removeCallbacks(it) }
            pendingLeft = null
            armedAt = 0L
            toggleSettings()
            return true
        }
        armedAt = now
        if (screen == Screen.Shelf) {
            val job = Runnable {
                pendingLeft = null
                onSingle()
            }
            pendingLeft = job
            handler.postDelayed(job, DOUBLE_MS)
            return true
        }
        return false
    }

    fun onRight(onSingle: () -> Unit) {
        if (_state.value.screen == Screen.Player) send(PlayerCommand.SeekBy(10_000)) else onSingle()
    }

    fun onUp(onSingle: () -> Unit) {
        if (_state.value.screen == Screen.Player) send(PlayerCommand.VolumeBy(0.1f)) else onSingle()
    }

    fun onDown(onSingle: () -> Unit) {
        if (_state.value.screen == Screen.Player) send(PlayerCommand.VolumeBy(-0.1f)) else onSingle()
    }

    fun confirm() {
        if (_state.value.screen == Screen.Player) send(PlayerCommand.TogglePlay)
    }

    fun toggleFavoriteFocused() {
        val uri = when (_state.value.screen) {
            Screen.Player -> _state.value.playingUri
            Screen.Shelf -> _state.value.focusedUri
            Screen.Settings -> null
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

    fun removeFocused() {
        if (_state.value.screen == Screen.Settings) return
        val uri = if (_state.value.screen == Screen.Player) {
            _state.value.playingUri
        } else {
            _state.value.focusedUri
        } ?: return
        remove(uri)
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

    fun addGif(uri: Uri) {
        viewModelScope.launch {
            val mime = getApplication<Application>().contentResolver.getType(uri).orEmpty()
            val name = withContext(Dispatchers.IO) { store.readDisplayName(uri) }
            if (mime != "image/gif" && !name.endsWith(".gif", ignoreCase = true)) return@launch
            takeGrant(uri)
            val gif = UserGif(uri.toString(), name.substringBeforeLast('.'))
            mutate { current ->
                current.copy(
                    gifs = listOf(gif) + current.gifs.filterNot { it.uri == gif.uri },
                    settings = current.settings.copy(backdrop = "gif", gifId = gif.uri),
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

    fun removeGif(uri: String) {
        viewModelScope.launch {
            releaseGrant(uri)
            mutate { current ->
                val gifs = current.gifs.filterNot { it.uri == uri }
                val settings = if (current.settings.gifId == uri) {
                    current.settings.copy(gifId = "asset:embers", backdrop = "gif")
                } else {
                    current.settings
                }
                current.copy(gifs = gifs, settings = settings)
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

    override fun onCleared() {
        pendingLeft?.let { handler.removeCallbacks(it) }
    }

    private inline fun MutableStateFlow<UiState>.updateScreen(crossinline map: (Screen) -> Screen) {
        value = value.copy(screen = map(value.screen))
    }

    companion object {
        private const val DOUBLE_MS = 280L
        private val VIDEO_EXT = setOf("mp4", "m4v", "webm", "mov", "mkv", "avi", "3gp", "ts", "m2ts", "ogv")
    }
}

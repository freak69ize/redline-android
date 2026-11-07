package com.redline.player

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import com.redline.player.data.Screen
import com.redline.player.ui.RedlineApp

class MainActivity : ComponentActivity() {
    private lateinit var vm: LibraryViewModel
    private var folderExcluded = false
    private var swallowSelectUp = false

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addVideos(uris)
    }

    private val gifPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addGif(uri)
    }

    private val logoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setLogo(uri)
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // The grant still lasts for this session.
        }
        if (folderExcluded) vm.excludeFolder(uri) else vm.includeFolder(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[LibraryViewModel::class.java]
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!vm.handleBack()) finish()
                }
            },
        )
        setContent {
            RedlineApp(
                vm = vm,
                onAddVideos = { videoPicker.launch(arrayOf("video/*")) },
                onAddGif = { gifPicker.launch(arrayOf("image/gif")) },
                onPickLogo = { logoPicker.launch(arrayOf("image/jpeg", "image/png")) },
                onAddFolder = {
                    folderExcluded = false
                    folderPicker.launch(null)
                },
                onExcludeFolder = {
                    folderExcluded = true
                    folderPicker.launch(null)
                },
            )
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::vm.isInitialized) return super.dispatchKeyEvent(event)
        val screen = vm.state.value.screen
        if (screen == Screen.Shelf && isSelectKey(event.keyCode)) {
            val menuOpen = vm.state.value.menuUri != null
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && menuOpen && swallowSelectUp) {
                swallowSelectUp = false
            } else if (swallowSelectUp) {
                return true
            } else if (!menuOpen && event.action == KeyEvent.ACTION_DOWN) {
                val held = event.eventTime - event.downTime
                val longPress = event.isLongPress ||
                    (event.repeatCount > 0 && held >= ViewConfiguration.getLongPressTimeout())
                if (longPress) {
                    swallowSelectUp = true
                    vm.openMenu()
                    return true
                }
            }
        }
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        if (screen == Screen.Player) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    vm.seek(false)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    vm.seek(true)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    vm.volume(0.1f)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    vm.volume(-0.1f)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                -> {
                    if (!vm.playerChrome) {
                        vm.revealPlayer()
                        return true
                    }
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                -> {
                    vm.confirm()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                -> {
                    if (!vm.playerChrome) {
                        vm.confirm()
                        vm.revealPlayer()
                        return true
                    }
                }
            }
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_X -> {
                vm.toggleFavoriteFocused()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_Y -> {
                vm.askRemoveFocused()
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (screen == Screen.Shelf) {
                    vm.openMenu()
                    return true
                }
            }
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (vm.handleBack()) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isSelectKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_A
    }
}

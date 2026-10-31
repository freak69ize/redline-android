package com.redline.player

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import com.redline.player.data.Screen
import com.redline.player.ui.RedlineApp

class MainActivity : ComponentActivity() {
    private lateinit var vm: LibraryViewModel

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addVideos(uris)
    }

    private val gifPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addGif(uri)
    }

    private val logoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setLogo(uri)
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
            )
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        if (!::vm.isInitialized) return super.dispatchKeyEvent(event)
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.repeatCount > 0 && vm.state.value.screen == Screen.Player) {
                    vm.onLeft { }
                    return true
                }
                if (event.repeatCount > 0) return true
                val consumed = vm.onLeft { moveFocus(View.FOCUS_LEFT) }
                return if (consumed) true else super.dispatchKeyEvent(event)
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                vm.onRight { moveFocus(View.FOCUS_RIGHT) }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                vm.onUp { moveFocus(View.FOCUS_UP) }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                vm.onDown { moveFocus(View.FOCUS_DOWN) }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            -> {
                if (vm.state.value.screen == Screen.Player) {
                    vm.confirm()
                    return true
                }
            }
            KeyEvent.KEYCODE_BUTTON_X -> {
                vm.toggleFavoriteFocused()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_Y -> {
                vm.removeFocused()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (vm.handleBack()) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun moveFocus(direction: Int) {
        val focused = window.decorView.findFocus() ?: return
        focused.focusSearch(direction)?.requestFocus()
    }
}

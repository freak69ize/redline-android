package com.redline.player.ui

import android.net.Uri
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.redline.player.R

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.splash}")
            setMediaItem(MediaItem.fromUri(uri))
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onFinished()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                onFinished()
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val view = LayoutInflater.from(ctx).inflate(R.layout.player_view, FrameLayout(ctx), false) as PlayerView
                view.player = exo
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                view
            },
        )
    }
}

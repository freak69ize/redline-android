package com.redline.player.ui

import android.net.Uri
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.redline.player.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(customUri: String?, onFinished: () -> Unit) {
    val context = LocalContext.current
    val video = customUri == null || isVideoSplash(context, customUri)
    if (video) {
        VideoSplash(customUri, onFinished)
    } else {
        ImageSplash(customUri.orEmpty(), onFinished)
    }
}

@Composable
private fun VideoSplash(customUri: String?, onFinished: () -> Unit) {
    val context = LocalContext.current
    val exo = remember(customUri) {
        ExoPlayer.Builder(context).build().apply {
            val uri = if (customUri.isNullOrBlank()) {
                Uri.parse("android.resource://${context.packageName}/${R.raw.splash}")
            } else {
                Uri.parse(customUri)
            }
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
        SkipSplash(onFinished)
    }
}

@Composable
private fun ImageSplash(uri: String, onFinished: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(uri) {
        delay(4_000)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(uri)
                .decoderFactory(GifDecoder.Factory())
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        SkipSplash(onFinished)
    }
}

@Composable
private fun SkipSplash(onFinished: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) onFinished()
                true
            },
    )
}

private fun isVideoSplash(context: android.content.Context, uriString: String): Boolean {
    val mime = runCatching { context.contentResolver.getType(Uri.parse(uriString)) }.getOrNull()
    if (mime != null) return mime.startsWith("video/")
    val ext = Uri.decode(uriString).substringAfterLast('.', "").lowercase()
    return ext !in setOf("png", "jpg", "jpeg", "gif", "webp")
}

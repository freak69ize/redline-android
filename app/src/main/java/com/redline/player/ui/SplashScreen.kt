package com.redline.player.ui

import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.redline.player.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen(customUri: String?, onFinished: () -> Unit) {
    val context = LocalContext.current
    val video = customUri == null || !isImageSplash(context, customUri)
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
    var model by remember(uri) { mutableStateOf<Any?>(null) }
    LaunchedEffect(uri) {
        model = withContext(Dispatchers.IO) { splashImageModel(context, uri) }
        delay(4_000)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .decoderFactory(
                        if (Build.VERSION.SDK_INT >= 28) {
                            ImageDecoderDecoder.Factory()
                        } else {
                            GifDecoder.Factory()
                        },
                    )
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
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

private fun isImageSplash(context: android.content.Context, uriString: String): Boolean {
    val uri = Uri.parse(uriString)
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty().lowercase()
    if (mime.startsWith("image/")) return true
    if (mime.startsWith("video/")) return false
    val decoded = Uri.decode(uriString).lowercase()
    if (listOf(".gif", ".png", ".jpg", ".jpeg", ".webp").any { decoded.contains(it) }) return true
    if (listOf(".mp4", ".mkv", ".webm", ".mov", ".m4v", ".avi", ".3gp").any { decoded.contains(it) }) return false
    val header = ByteArray(6)
    val read = runCatching { context.contentResolver.openInputStream(uri)?.use { it.read(header) } }.getOrNull() ?: return false
    if (read >= 6 && header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte()) return true
    if (read >= 2 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) return true
    if (read >= 4 && header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte()) return true
    return false
}

private fun splashImageModel(context: android.content.Context, uriString: String): Any {
    val uri = Uri.parse(uriString)
    val header = ByteArray(6)
    val read = runCatching { context.contentResolver.openInputStream(uri)?.use { it.read(header) } }.getOrNull() ?: 0
    val gif = read >= 3 && header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte()
    if (!gif) return uri
    val file = File(context.cacheDir, "splash.gif")
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
    }
    return if (file.length() > 0L) file else uri
}

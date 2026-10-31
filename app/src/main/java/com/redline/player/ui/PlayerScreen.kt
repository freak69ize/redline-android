package com.redline.player.ui

import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.redline.player.R
import com.redline.player.data.PlayerCommand
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

@Composable
fun PlayerScreen(
    uri: String,
    title: String,
    favorite: Boolean,
    startMs: Long,
    commands: Flow<PlayerCommand>,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onRemove: () -> Unit,
    onProgress: (Long) -> Unit,
    background: Color = DefaultBg,
) {
    val context = LocalContext.current
    val exo = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            if (startMs > 1_000L) seekTo(startMs)
            playWhenReady = true
        }
    }
    LaunchedEffect(uri) {
        commands.collect { command ->
            when (command) {
                is PlayerCommand.SeekBy -> {
                    val limit = exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    exo.seekTo((exo.currentPosition + command.ms).coerceIn(0L, limit))
                }
                PlayerCommand.TogglePlay -> if (exo.isPlaying) exo.pause() else exo.play()
                is PlayerCommand.VolumeBy -> exo.volume = (exo.volume + command.delta).coerceIn(0f, 1f)
            }
        }
    }
    LaunchedEffect(uri) {
        while (true) {
            delay(2_000)
            onProgress(exo.currentPosition)
        }
    }
    DisposableEffect(uri) {
        onDispose {
            onProgress(exo.currentPosition)
            exo.release()
        }
    }
    Column(Modifier.fillMaxSize().background(background)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.redFocus()) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text(
                title,
                color = Ink,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onFavorite, modifier = Modifier.redFocus()) {
                Icon(
                    if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (favorite) Red else Ink,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.redFocus()) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Ink)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx).inflate(R.layout.player_view, FrameLayout(ctx), false) as PlayerView
                    view.player = exo
                    view
                },
                update = { it.player = exo },
            )
        }
        Text(
            "A play/pause · Left/Right seek · Up/Down volume · B back · X favorite · Y remove",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

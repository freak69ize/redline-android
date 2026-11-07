package com.redline.player.ui

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.redline.player.R
import com.redline.player.data.PlayerCommand
import com.redline.player.data.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

@Composable
fun PlayerScreen(
    uri: String,
    title: String,
    startMs: Long,
    resume: Boolean,
    speed: Float,
    aspect: String,
    repeatMode: String,
    showRemaining: Boolean,
    subtitleScale: Float,
    sleepMinutes: Int,
    favorite: Boolean,
    commands: Flow<PlayerCommand>,
    onEnded: () -> Unit,
    onSleep: () -> Unit,
    onProgress: (Long) -> Unit,
    onFavorite: () -> Unit,
    onChrome: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val exo = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            if (resume && startMs > 1_000L) seekTo(startMs)
            playWhenReady = true
        }
    }
    var position by remember(uri) { mutableLongStateOf(if (resume) startMs else 0L) }
    var showControls by remember(uri) { mutableStateOf(true) }
    var playing by remember(uri) { mutableStateOf(true) }
    var controlsTick by remember(uri) { mutableLongStateOf(0L) }
    val repeatNow by rememberUpdatedState(repeatMode)
    val endedNow by rememberUpdatedState(onEnded)

    LaunchedEffect(uri) {
        commands.collect { command ->
            showControls = true
            controlsTick = System.currentTimeMillis()
            when (command) {
                is PlayerCommand.SeekBy -> {
                    val limit = exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    exo.seekTo((exo.currentPosition + command.ms).coerceIn(0L, limit))
                    position = exo.currentPosition
                }
                PlayerCommand.TogglePlay -> {
                    if (exo.isPlaying) exo.pause() else exo.play()
                    playing = exo.isPlaying
                }
                is PlayerCommand.VolumeBy -> exo.volume = (exo.volume + command.delta).coerceIn(0f, 1f)
                PlayerCommand.Reveal -> Unit
            }
        }
    }
    LaunchedEffect(showControls) {
        onChrome(showControls)
    }
    DisposableEffect(Unit) {
        onDispose { onChrome(false) }
    }
    LaunchedEffect(speed) {
        exo.playbackParameters = PlaybackParameters(speed)
    }
    LaunchedEffect(repeatMode) {
        exo.repeatMode = if (repeatMode == "one") Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    LaunchedEffect(uri, sleepMinutes) {
        if (sleepMinutes > 0) {
            delay(sleepMinutes * 60_000L)
            onSleep()
        }
    }
    LaunchedEffect(uri) {
        var ticks = 0
        while (true) {
            delay(500)
            position = exo.currentPosition
            playing = exo.isPlaying
            ticks++
            if (ticks % 4 == 0) onProgress(position)
        }
    }
    LaunchedEffect(controlsTick) {
        if (!showControls) return@LaunchedEffect
        delay(4_000)
        showControls = false
    }
    DisposableEffect(uri) {
        var alive = true
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (alive && state == Player.STATE_ENDED && repeatNow != "one") endedNow()
            }
        }
        exo.addListener(listener)
        onDispose {
            alive = false
            onProgress(exo.currentPosition)
            exo.removeListener(listener)
            exo.release()
        }
    }

    val duration = exo.duration.takeIf { it > 0 } ?: 0L
    val shown = if (showRemaining && duration > 0) duration - position else position
    val timeLabel = if (showRemaining && duration > 0) "-${formatDuration(shown)}" else formatDuration(shown)

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(60)
            runCatching { playFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val frame = when (aspect) {
            "16:9" -> Modifier.fillMaxHeight().aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
            "4:3" -> Modifier.fillMaxHeight().aspectRatio(4f / 3f, matchHeightConstraintsFirst = true)
            else -> Modifier.fillMaxSize()
        }
        AndroidView(
            modifier = frame.align(Alignment.Center),
            factory = { ctx ->
                val view = LayoutInflater.from(ctx).inflate(R.layout.player_view, FrameLayout(ctx), false) as PlayerView
                view.player = exo
                view.resizeMode = resizeMode(aspect)
                styleSubtitles(view, subtitleScale)
                view
            },
            update = { view ->
                view.player = exo
                view.resizeMode = resizeMode(aspect)
                styleSubtitles(view, subtitleScale)
            },
        )
        if (showControls) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(title, color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onFavorite, modifier = Modifier.tvFocus()) {
                    Icon(
                        if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (favorite) Red else Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (exo.isPlaying) exo.pause() else exo.play() },
                        modifier = Modifier.focusRequester(playFocus).tvFocus(),
                    ) {
                        if (playing) {
                            Row(
                                Modifier.size(22.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(5.dp, 16.dp).background(Color.White))
                                Box(Modifier.size(5.dp, 16.dp).background(Color.White))
                            }
                        } else {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play or pause",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Text(timeLabel, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(end = 12.dp))
                    Slider(
                        value = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { fraction ->
                            if (duration > 0) {
                                val target = (duration * fraction).toLong()
                                exo.seekTo(target)
                                position = target
                            }
                        },
                        modifier = Modifier.weight(1f).focusProperties { canFocus = false },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                        ),
                    )
                    Text(
                        formatDuration(duration),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

private fun resizeMode(aspect: String): Int {
    return when (aspect) {
        "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
}

private fun styleSubtitles(view: PlayerView, scale: Float) {
    view.subtitleView?.apply {
        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
        setStyle(
            CaptionStyleCompat(
                AndroidColor.WHITE,
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                AndroidColor.BLACK,
                Typeface.DEFAULT,
            ),
        )
    }
}

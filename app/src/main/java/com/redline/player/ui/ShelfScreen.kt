package com.redline.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.redline.player.data.VideoItem
import com.redline.player.data.formatBytes
import com.redline.player.data.formatDuration
import java.io.File

@Composable
fun ShelfScreen(
    videos: List<VideoItem>,
    favoritesOnTop: Boolean,
    busy: Boolean,
    isTv: Boolean,
    firstFocus: FocusRequester,
    onAdd: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onFocus: (String) -> Unit,
) {
    val favorites = videos.filter { it.favorite }.sortedByDescending { it.favoredAt }
    val resume = videos.filter { it.positionMs > 3_000 && (it.durationMs == 0L || it.positionMs < it.durationMs - 4_000) }
        .sortedByDescending { it.positionMs }
    val focusKey = when {
        favoritesOnTop && favorites.isNotEmpty() -> "fav-${favorites.first().uri}"
        resume.isNotEmpty() -> "resume-${resume.first().uri}"
        videos.isNotEmpty() -> "lib-${videos.first().uri}"
        else -> null
    }
    val side = if (isTv) 48.dp else 20.dp
    val favWidth = if (isTv) 280.dp else 248.dp
    val cardWidth = if (isTv) 232.dp else 196.dp
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = side, end = side, bottom = if (isTv) 48.dp else 150.dp),
    ) {
        if (!busy && videos.isEmpty()) {
            EmptyShelf(onAdd)
        } else {
            if (favoritesOnTop && favorites.isNotEmpty()) {
                SectionTitle("Favorites", "Larger thumbnails")
                CardRows(favorites, cardWidth = favWidth, onOpen = onOpen, onFocus = onFocus, keyPrefix = "fav", focusKey = focusKey, firstFocus = firstFocus)
                Spacer(Modifier.height(22.dp))
            }
            if (resume.isNotEmpty()) {
                SectionTitle("Continue watching", null)
                CardRows(resume, cardWidth = cardWidth, onOpen = onOpen, onFocus = onFocus, keyPrefix = "resume", focusKey = focusKey, firstFocus = firstFocus)
                Spacer(Modifier.height(22.dp))
            }
            SectionTitle("Library", videos.size.toString())
            CardRows(videos, cardWidth = cardWidth, onOpen = onOpen, onFocus = onFocus, keyPrefix = "lib", focusKey = focusKey, firstFocus = firstFocus)
        }
    }
}

@Composable
private fun SectionTitle(title: String, meta: String?) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (meta != null) Text(meta, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun CardRows(
    videos: List<VideoItem>,
    cardWidth: Dp,
    onOpen: (VideoItem) -> Unit,
    onFocus: (String) -> Unit,
    keyPrefix: String,
    focusKey: String?,
    firstFocus: FocusRequester,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 12.dp
        val cols = ((maxWidth + gap) / (cardWidth + gap)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            videos.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { video ->
                        val cardKey = "$keyPrefix-${video.uri}"
                        VideoCard(
                            video = video,
                            width = cardWidth,
                            onOpen = { onOpen(video) },
                            onFocus = { onFocus(video.uri) },
                            modifier = Modifier,
                            key = cardKey,
                            focusRequester = if (cardKey == focusKey) firstFocus else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(
    video: VideoItem,
    width: Dp,
    onOpen: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier,
    key: String,
    focusRequester: FocusRequester?,
) {
    var focused by remember(key) { mutableStateOf(false) }
    val progress = if (video.durationMs > 0 && video.positionMs > 0) {
        (video.positionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(width)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .border(3.dp, if (focused) Red else Color.Transparent, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface),
        ) {
            if (video.thumbPath.isNotBlank()) {
                AsyncImage(
                    model = File(video.thumbPath),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                )
            }
            if (video.favorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Red,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(16.dp),
                )
            }
            Text(
                formatDuration(video.durationMs),
                color = Ink,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(Red),
                )
            }
        }
        Text(
            video.title,
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(formatBytes(video.sizeBytes), color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyShelf(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Red, modifier = Modifier.size(56.dp))
        }
        Text(
            "YOUR SHELF IS EMPTY",
            color = Ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            "Only plays videos already on this device. Nothing is uploaded.",
            color = Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
        )
        Button(
            onClick = onAdd,
            modifier = Modifier.padding(top = 16.dp).redFocus(),
            colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Black),
        ) {
            Text("Add videos", fontWeight = FontWeight.SemiBold)
        }
    }
}

package com.redline.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.redline.player.data.ShelfRow
import com.redline.player.data.VideoItem
import com.redline.player.data.formatBytes
import com.redline.player.data.formatDuration
import com.redline.player.data.videosFor
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun ShelfScreen(
    videos: List<VideoItem>,
    rows: List<ShelfRow>,
    busy: Boolean,
    isTv: Boolean,
    firstFocus: FocusRequester,
    onAdd: () -> Unit,
    onOpen: (VideoItem, String) -> Unit,
    onFocus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = rows.sortedBy { it.order }.map { row -> row to videosFor(row, videos) }.filter { (row, items) ->
        items.isNotEmpty() || row.kind == "custom" || row.kind == "library"
    }
    val firstRowId = visible.firstOrNull { it.second.isNotEmpty() }?.first?.id
    val side = if (isTv) 48.dp else 16.dp
    if (!busy && videos.isEmpty() && visible.none { it.second.isNotEmpty() }) {
        EmptyShelf(onAdd, modifier.padding(horizontal = side))
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = if (isTv) 48.dp else 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(visible, key = { it.first.id }) { (row, items) ->
            VideoRow(
                title = row.name,
                videos = items,
                cardWidth = if (row.kind == "favorites") if (isTv) 280.dp else 220.dp else if (isTv) 220.dp else 180.dp,
                side = side,
                firstFocus = if (row.id == firstRowId) firstFocus else null,
                onOpen = { onOpen(it, row.id) },
                onFocus = onFocus,
            )
        }
    }
}

@Composable
private fun VideoRow(
    title: String,
    videos: List<VideoItem>,
    cardWidth: Dp,
    side: Dp,
    firstFocus: FocusRequester?,
    onOpen: (VideoItem) -> Unit,
    onFocus: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = side, end = side, bottom = 8.dp),
        )
        if (videos.isEmpty()) {
            Text(
                "Empty. Hold Select on a video and add it here.",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = side, bottom = 4.dp),
            )
        } else {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = side),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(videos, key = { it.uri }) { video ->
                    val index = videos.indexOf(video)
                    VideoCard(
                        video = video,
                        width = cardWidth,
                        focusRequester = if (index == 0) firstFocus else null,
                        onOpen = { onOpen(video) },
                        onFocus = {
                            onFocus(video.uri)
                            scope.launch { listState.animateScrollToItem(index.coerceAtLeast(0)) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCard(
    video: VideoItem,
    width: Dp,
    focusRequester: FocusRequester?,
    onOpen: () -> Unit,
    onFocus: () -> Unit,
) {
    var focused by remember(video.uri) { mutableStateOf(false) }
    val progress = if (video.durationMs > 0 && video.positionMs > 0) {
        (video.positionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(width)
            .onFocusChanged {
                val now = it.isFocused
                if (now && !focused) onFocus()
                focused = now
            }
            .border(3.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface),
        ) {
            if (video.thumbPath.isNotBlank()) {
                AsyncImage(
                    model = File(video.thumbPath),
                    contentDescription = video.title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                )
            }
            if (video.favorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(14.dp),
                )
            }
            Text(
                formatDuration(video.durationMs),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(Color.White),
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
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            formatBytes(video.sizeBytes),
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun EmptyShelf(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "YOUR SHELF IS EMPTY",
            color = Ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Add videos or a folder. Nothing is uploaded.",
            color = Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
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

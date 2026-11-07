package com.redline.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.redline.player.LibraryViewModel
import com.redline.player.data.Screen
import com.redline.player.data.backdropModel
import com.redline.player.data.shelfTitle
import kotlinx.coroutines.delay

@Composable
fun RedlineApp(
    vm: LibraryViewModel,
    onAddVideos: () -> Unit,
    onAddGif: () -> Unit,
    onPickLogo: () -> Unit,
    onAddFolder: () -> Unit,
    onExcludeFolder: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val settingsFocus = remember { FocusRequester() }
    val logoFocus = remember { FocusRequester() }
    val shelfFocus = remember { FocusRequester() }
    val scrimClicks = remember { MutableInteractionSource() }
    val tv = isTelevision()
    val bg = colorFromHex(state.settings.backgroundColor)
    val title = state.settings.shelfTitle()
    LaunchedEffect(state.screen, state.ready) {
        if (state.screen == Screen.Settings) {
            delay(60)
            runCatching { settingsFocus.requestFocus() }
        } else if (state.screen == Screen.Shelf && state.ready) {
            delay(120)
            val target = if (state.videos.isEmpty()) logoFocus else shelfFocus
            runCatching { target.requestFocus() }
        }
    }
    RedlineTheme {
        Box(Modifier.fillMaxSize().background(bg)) {
            val model = backdropModel(state.settings, state.gifs)
            if (model != null && state.screen != Screen.Player) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .decoderFactory(GifDecoder.Factory())
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(bg.copy(alpha = state.settings.scrim)))
            }
            if (state.screen == Screen.Shelf) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(
                            start = if (tv) 48.dp else 16.dp,
                            end = if (tv) 48.dp else 16.dp,
                            top = if (tv) 24.dp else 12.dp,
                            bottom = 8.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShelfMark(
                            title = title,
                            logoUri = state.settings.logoUri,
                            size = if (tv) 56.dp else 44.dp,
                            focusRequester = logoFocus,
                            onClick = vm::openSettings,
                        )
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                title,
                                color = Ink,
                                fontSize = if (tv) 32.sp else 26.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Hold a video to favorite, move, rename, or delete.",
                                color = Muted,
                                fontSize = if (tv) 14.sp else 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            if (state.busy) "Adding…" else "Add videos",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .tvFocus(RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(DefaultBg)
                                .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                                .clickable(enabled = !state.busy, onClick = onAddVideos)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (!state.ready) {
                        Text("Opening your shelf…", color = Muted, modifier = Modifier.padding(24.dp))
                    } else {
                        ShelfScreen(
                            videos = state.videos,
                            rows = state.settings.rows,
                            busy = state.busy,
                            isTv = tv,
                            firstFocus = shelfFocus,
                            onAdd = onAddVideos,
                            onOpen = { video, rowId -> vm.play(video.uri, rowId) },
                            onFocus = vm::onFocus,
                            onMenu = { video ->
                                vm.onFocus(video.uri)
                                vm.openMenu()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (state.screen == Screen.Settings) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Black.copy(alpha = 0.55f))
                        .clickable(interactionSource = scrimClicks, indication = null, onClick = { vm.handleBack() })
                        .focusProperties { canFocus = false },
                )
                SettingsScreen(
                    settings = state.settings,
                    gifs = state.gifs,
                    folders = state.folders,
                    onChange = vm::updateSettings,
                    onAddGif = onAddGif,
                    onRemoveGif = vm::removeGif,
                    onPickLogo = onPickLogo,
                    onClearLogo = vm::clearLogo,
                    onAddFolder = onAddFolder,
                    onExcludeFolder = onExcludeFolder,
                    onRemoveFolder = vm::removeFolder,
                    onAddRow = vm::addRow,
                    onRenameRow = vm::renameRow,
                    onMoveRow = vm::moveRow,
                    onDeleteRow = vm::deleteRow,
                    onClose = { vm.handleBack() },
                    closeRequester = settingsFocus,
                    panelMax = if (tv) 640.dp else 480.dp,
                )
            }
            if (state.screen != Screen.Player && !tv) {
                OnScreenDpad(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    onLeft = { focus.moveFocus(FocusDirection.Left) },
                    onRight = { focus.moveFocus(FocusDirection.Right) },
                    onUp = { focus.moveFocus(FocusDirection.Up) },
                    onDown = { focus.moveFocus(FocusDirection.Down) },
                )
            }
            if (state.screen == Screen.Player) {
                val playing = state.videos.firstOrNull { it.uri == state.playingUri }
                if (playing != null) {
                    PlayerScreen(
                        uri = playing.uri,
                        title = playing.title,
                        startMs = playing.positionMs,
                        resume = state.settings.resumePlayback,
                        speed = state.settings.playbackSpeed,
                        aspect = state.settings.aspect,
                        repeatMode = state.settings.repeatMode,
                        showRemaining = state.settings.showRemaining,
                        subtitleScale = state.settings.subtitleScale,
                        sleepMinutes = state.settings.sleepMinutes,
                        favorite = playing.favorite,
                        commands = vm.commands,
                        onEnded = vm::onVideoEnded,
                        onSleep = vm::closePlayer,
                        onProgress = { vm.savePosition(playing.uri, it) },
                        onFavorite = { vm.toggleFavorite(playing.uri) },
                        onChrome = vm::noteChrome,
                    )
                }
            }
            val menuVideo = state.videos.firstOrNull { it.uri == state.menuUri }
            if (menuVideo != null) {
                var renaming by remember(menuVideo.uri) { mutableStateOf(false) }
                var draft by remember(menuVideo.uri) { mutableStateOf(menuVideo.title) }
                val moveRows = state.settings.rows.filter { it.kind == "custom" || it.kind == "library" }.sortedBy { it.order }
                Dialog(onDismissRequest = vm::closeMenu) {
                    Column(
                        Modifier
                            .width(340.dp)
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState())
                            .background(Surface, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(menuVideo.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        DialogButton("Play") { vm.play(menuVideo.uri, state.playingRowId ?: "library") }
                        DialogButton(if (menuVideo.favorite) "Remove from favorites" else "Add to favorites") {
                            vm.toggleFavorite(menuVideo.uri)
                        }
                        Text("Move to a row", color = SoftRed, fontSize = 12.sp)
                        moveRows.forEach { row ->
                            DialogButton("Move to ${row.name}") { vm.moveVideoToRow(menuVideo.uri, row.id) }
                        }
                        if (renaming) {
                            RedlineTextField(value = draft, onValueChange = { draft = it.take(80) }, label = "Name")
                            DialogButton("Save name") { vm.renameVideo(menuVideo.uri, draft) }
                        } else {
                            DialogButton("Rename video") { renaming = true }
                        }
                        DialogButton("Delete from player") { vm.askRemove(menuVideo.uri) }
                        DialogButton("Close") { vm.closeMenu() }
                    }
                }
            }
            val confirmVideo = state.videos.firstOrNull { it.uri == state.confirmUri }
            if (confirmVideo != null) {
                Dialog(onDismissRequest = { vm.handleBack() }) {
                    Column(
                        Modifier
                            .width(320.dp)
                            .background(Surface, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Delete ${confirmVideo.title} from the player?", color = Color.White, fontSize = 16.sp)
                        Text("The file stays on the device.", color = SoftRed, fontSize = 13.sp)
                        DialogButton("Delete") { vm.remove(confirmVideo.uri) }
                        DialogButton("Cancel") { vm.handleBack() }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().tvFocus(),
        colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = androidx.compose.ui.graphics.Color.White),
    ) {
        Text(label)
    }
}

@Composable
private fun ShelfMark(
    title: String,
    logoUri: String?,
    size: androidx.compose.ui.unit.Dp,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val mark = Modifier
        .size(size)
        .focusRequester(focusRequester)
        .tvFocus(CircleShape)
        .clip(CircleShape)
        .clickable(onClick = onClick)
    if (logoUri != null) {
        AsyncImage(
            model = logoUri,
            contentDescription = "Settings. $title",
            modifier = mark,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(mark.background(Red), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Settings", tint = Black)
        }
    }
}

@Composable
private fun OnScreenDpad(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PadButton(onLeft) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Left", tint = Ink) }
            Column {
                PadButton(onUp) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Up", tint = Ink) }
                Spacer(Modifier.size(8.dp))
                PadButton(onDown) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Down", tint = Ink) }
            }
            PadButton(onRight) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Right", tint = Ink) }
        }
    }
}

@Composable
private fun PadButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp).background(Surface, RoundedCornerShape(12.dp)).tvFocus(),
    ) {
        icon()
    }
}

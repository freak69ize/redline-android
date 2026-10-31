package com.redline.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val settingsFocus = remember { FocusRequester() }
    val addFocus = remember { FocusRequester() }
    val shelfFocus = remember { FocusRequester() }
    val scrimClicks = remember { MutableInteractionSource() }
    val tv = isTelevision()
    val bg = colorFromHex(state.settings.backgroundColor)
    val title = state.settings.shelfTitle()
    LaunchedEffect(state.screen, state.ready) {
        if (state.screen == Screen.Settings) {
            runCatching { settingsFocus.requestFocus() }
        } else if (state.screen == Screen.Shelf && state.ready) {
            delay(80)
            val target = if (state.videos.isEmpty()) addFocus else shelfFocus
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
                            top = if (tv) 27.dp else 12.dp,
                            bottom = if (tv) 16.dp else 12.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShelfMark(title = title, logoUri = state.settings.logoUri, size = if (tv) 56.dp else 40.dp)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(
                                title,
                                color = Ink,
                                fontSize = if (tv) 34.sp else 26.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (tv) "Local files  ·  Left ×2 settings" else "Local files only",
                                color = Muted,
                                fontSize = if (tv) 16.sp else 12.sp,
                            )
                        }
                        Button(
                            onClick = onAddVideos,
                            enabled = !state.busy,
                            modifier = Modifier.focusRequester(addFocus).redFocus(),
                            colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Black),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.busy) "Adding…" else "Add videos", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (!state.ready) {
                        Text("Opening your shelf…", color = Muted, modifier = Modifier.padding(24.dp))
                    } else {
                        ShelfScreen(
                            videos = state.videos,
                            favoritesOnTop = state.settings.favoritesOnTop,
                            busy = state.busy,
                            isTv = tv,
                            firstFocus = shelfFocus,
                            onAdd = onAddVideos,
                            onOpen = { vm.play(it.uri) },
                            onFocus = vm::onFocus,
                        )
                    }
                }
            }
            if (state.screen == Screen.Settings) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Black.copy(alpha = 0.7f))
                        .clickable(
                            interactionSource = scrimClicks,
                            indication = null,
                            onClick = { vm.handleBack() },
                        )
                        .focusProperties { canFocus = false },
                )
                SettingsScreen(
                    settings = state.settings,
                    gifs = state.gifs,
                    onChange = vm::updateSettings,
                    onAddGif = onAddGif,
                    onRemoveGif = vm::removeGif,
                    onPickLogo = onPickLogo,
                    onClearLogo = vm::clearLogo,
                    onClose = { vm.handleBack() },
                    closeRequester = settingsFocus,
                    panelMax = if (tv) 560.dp else 420.dp,
                    bottomInset = if (tv) 48.dp else 160.dp,
                )
            }
            if (state.screen != Screen.Player && !tv) {
                OnScreenDpad(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    onLeft = {
                        val consumed = vm.onLeft { focus.moveFocus(FocusDirection.Left) }
                        if (!consumed) focus.moveFocus(FocusDirection.Left)
                    },
                    onRight = { vm.onRight { focus.moveFocus(FocusDirection.Right) } },
                    onUp = { vm.onUp { focus.moveFocus(FocusDirection.Up) } },
                    onDown = { vm.onDown { focus.moveFocus(FocusDirection.Down) } },
                )
            } else {
                val playing = state.videos.firstOrNull { it.uri == state.playingUri }
                if (playing != null) {
                    PlayerScreen(
                        uri = playing.uri,
                        title = playing.title,
                        favorite = playing.favorite,
                        startMs = playing.positionMs,
                        commands = vm.commands,
                        onBack = { vm.handleBack() },
                        onFavorite = { vm.toggleFavorite(playing.uri) },
                        onRemove = { vm.remove(playing.uri) },
                        onProgress = { vm.savePosition(playing.uri, it) },
                        background = bg,
                    )
                }
            }
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
            PadButton(onLeft) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Left. Double-tap for settings", tint = Ink) }
            Column {
                PadButton(onUp) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Up", tint = Ink) }
                BoxSpacer()
                PadButton(onDown) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Down", tint = Ink) }
            }
            PadButton(onRight) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Right", tint = Ink) }
        }
        Text("Left ×2 settings", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PadButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .background(Surface, RoundedCornerShape(12.dp))
            .redFocus(),
    ) {
        icon()
    }
}

@Composable
private fun BoxSpacer() {
    Spacer(Modifier.size(8.dp))
}

@Composable
private fun ShelfMark(title: String, logoUri: String?, size: Dp) {
    if (logoUri != null) {
        AsyncImage(
            model = logoUri,
            contentDescription = title,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(size).background(Red, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Black)
        }
    }
}

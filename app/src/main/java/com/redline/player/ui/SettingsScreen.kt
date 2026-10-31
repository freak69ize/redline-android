package com.redline.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.redline.player.data.PRESET_GIFS
import com.redline.player.data.Settings
import com.redline.player.data.UserGif

@Composable
fun SettingsScreen(
    settings: Settings,
    gifs: List<UserGif>,
    onChange: (Settings) -> Unit,
    onAddGif: () -> Unit,
    onRemoveGif: (String) -> Unit,
    onPickLogo: () -> Unit,
    onClearLogo: () -> Unit,
    onClose: () -> Unit,
    closeRequester: FocusRequester,
    panelMax: Dp = 420.dp,
    bottomInset: Dp = 160.dp,
) {
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .widthIn(max = panelMax)
            .background(Surface)
            .pointerInput(Unit) { detectTapGestures { } }
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = bottomInset),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SETTINGS", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("Double-tap Left again to close. B or Back works too.", color = Muted, fontSize = 13.sp)
            }
            IconButton(onClick = onClose, modifier = Modifier.focusRequester(closeRequester).redFocus()) {
                Icon(Icons.Filled.Close, contentDescription = "Close settings", tint = Ink)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Shelf", color = Ink, fontWeight = FontWeight.SemiBold)
        Text("The name and picture next to it on the shelf. The home-screen label stays Redline.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = settings.appTitle,
            onValueChange = { onChange(settings.copy(appTitle = it.take(40))) },
            singleLine = true,
            label = { Text("Shelf title") },
            placeholder = { Text("Redline") },
            colors = shelfFieldColors(),
            modifier = Modifier.fillMaxWidth().redFocus(),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (settings.logoUri != null) {
                AsyncImage(
                    model = settings.logoUri,
                    contentDescription = "Shelf logo",
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier.size(64.dp).background(Red, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Black)
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Button(
                    onClick = onPickLogo,
                    modifier = Modifier.redFocus(),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Ink),
                ) {
                    Text("Use a JPG or PNG")
                }
                if (settings.logoUri != null) {
                    Button(
                        onClick = onClearLogo,
                        modifier = Modifier.padding(top = 8.dp).redFocus(),
                        colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Ink),
                    ) {
                        Text("Remove picture")
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Background", color = Ink, fontWeight = FontWeight.SemiBold)
        Text("Default color is 0F0F0F. A GIF can sit on top of it.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceButton(
                label = "Color",
                selected = settings.backdrop != "gif",
                onClick = { onChange(settings.copy(backdrop = "solid")) },
                modifier = Modifier.weight(1f),
            )
            ChoiceButton(
                label = "GIF",
                selected = settings.backdrop == "gif",
                onClick = {
                    onChange(settings.copy(backdrop = "gif", gifId = settings.gifId ?: PRESET_GIFS.first().id))
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (settings.backdrop != "gif") {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.backgroundColor,
                onValueChange = { raw ->
                    val cleaned = raw.filter { it == '#' || it.isDigit() || it.lowercaseChar() in 'a'..'f' }.take(7)
                    onChange(settings.copy(backgroundColor = cleaned, backdrop = "solid"))
                },
                singleLine = true,
                label = { Text("Background color") },
                placeholder = { Text("#0F0F0F") },
                colors = shelfFieldColors(),
                modifier = Modifier.fillMaxWidth().redFocus(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                listOf("#0F0F0F", "#000000", "#1A1A1A", "#2A0A0A").forEach { hex ->
                    ColorSwatch(
                        hex = hex,
                        selected = colorFromHex(settings.backgroundColor) == colorFromHex(hex) &&
                            settings.backgroundColor.length >= 6,
                        onClick = { onChange(settings.copy(backgroundColor = hex, backdrop = "solid")) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PRESET_GIFS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                row.forEach { preset ->
                    GifTile(
                        model = preset.assetPath,
                        label = preset.name,
                        selected = settings.backdrop == "gif" && settings.gifId == preset.id,
                        onClick = { onChange(settings.copy(backdrop = "gif", gifId = preset.id)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        gifs.forEach { gif ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GifTile(
                    model = gif.uri,
                    label = gif.name,
                    selected = settings.backdrop == "gif" && settings.gifId == gif.uri,
                    onClick = { onChange(settings.copy(backdrop = "gif", gifId = gif.uri)) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Remove",
                    color = Muted,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .redFocus()
                        .clickable { onRemoveGif(gif.uri) }
                        .padding(8.dp),
                )
            }
        }
        Button(
            onClick = onAddGif,
            modifier = Modifier.redFocus(),
            colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Ink),
        ) {
            Text("Add a GIF")
        }
        if (settings.backdrop == "gif") {
            Text("Dim the GIF", color = Ink, modifier = Modifier.padding(top = 16.dp))
            Slider(
                value = settings.scrim,
                onValueChange = { onChange(settings.copy(scrim = it)) },
                valueRange = 0f..0.8f,
                modifier = Modifier.redFocus(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Favorites", color = Ink, fontWeight = FontWeight.SemiBold)
        Text(
            "Pinned videos sit on the top row with a slightly larger thumbnail.",
            color = Muted,
            fontSize = 13.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Show favorites on top", color = Ink, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.favoritesOnTop,
                onCheckedChange = { onChange(settings.copy(favoritesOnTop = it)) },
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("Controller", color = Ink, fontWeight = FontWeight.SemiBold)
        Text("Move with the D-pad. The highlight is red, not white. Works on a phone and on Android TV.", color = Muted, fontSize = 13.sp)
        Text("A or Enter plays. X or F isn't needed: favorite from the shelf with the heart, or press X.", color = Muted, fontSize = 13.sp)
        Text("Y removes the focused video. Double-tap Left opens this panel.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun shelfFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    focusedContainerColor = Surface2,
    unfocusedContainerColor = Surface2,
    cursorColor = Red,
    focusedBorderColor = Red,
    unfocusedBorderColor = Line,
    focusedLabelColor = Red,
    unfocusedLabelColor = Muted,
    focusedPlaceholderColor = Muted,
    unfocusedPlaceholderColor = Muted,
)

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .redFocus()
            .clip(RoundedCornerShape(10.dp))
            .background(colorFromHex(hex))
            .border(2.dp, if (selected) Red else Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.redFocus().height(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Red else Surface2,
            contentColor = if (selected) Black else Ink,
        ),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GifTile(
    model: Any,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier
            .redFocus()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Red else Surface2)
            .clickable(onClick = onClick)
            .padding(if (selected) 3.dp else 0.dp),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .decoderFactory(GifDecoder.Factory())
                .build(),
            contentDescription = label,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )
        Text(label, color = if (selected) Black else Ink, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
    }
}

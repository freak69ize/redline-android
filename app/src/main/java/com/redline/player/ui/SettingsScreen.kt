package com.redline.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.redline.player.data.FolderEntry
import com.redline.player.data.Settings
import com.redline.player.data.UserGif
import com.redline.player.data.usesGif

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    gifs: List<UserGif>,
    folders: List<FolderEntry>,
    onChange: (Settings) -> Unit,
    onAddGif: () -> Unit,
    onRemoveGif: (String) -> Unit,
    onPickLogo: () -> Unit,
    onClearLogo: () -> Unit,
    onAddFolder: () -> Unit,
    onExcludeFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onAddRow: (String) -> Unit,
    onRenameRow: (String, String) -> Unit,
    onMoveRow: (String, Int) -> Unit,
    onDeleteRow: (String) -> Unit,
    onClose: () -> Unit,
    closeRequester: FocusRequester,
    panelMax: Dp = 560.dp,
) {
    var rowDraft by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .widthIn(max = panelMax)
            .background(Surface)
            .pointerInput(Unit) { detectTapGestures { } }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.focusRequester(closeRequester).tvFocus(CircleShape)) {
                Icon(Icons.Filled.Close, contentDescription = "Close settings", tint = Color.White)
            }
        }
        Text("Press Back to close. Select a text box to type.", color = SoftRed, fontSize = 12.sp)

        Section("Shelf")
        RedlineTextField(
            value = settings.appTitle,
            onValueChange = { onChange(settings.copy(appTitle = it.take(40))) },
            label = "Name",
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (settings.logoUri != null) {
                AsyncImage(
                    model = settings.logoUri,
                    contentDescription = "Logo",
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Red), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Black)
                }
            }
            Button(onClick = onPickLogo, modifier = Modifier.tvFocus(), colors = darkButton()) { Text("Logo JPG / PNG") }
            if (settings.logoUri != null) {
                Button(onClick = onClearLogo, modifier = Modifier.tvFocus(), colors = darkButton()) { Text("Clear") }
            }
        }

        Section("Background")
        ColorPicker(settings.backgroundColor) { hex ->
            onChange(settings.copy(backgroundColor = hex, backdrop = "solid"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onAddGif, modifier = Modifier.tvFocus(), colors = darkButton()) { Text("Add GIF", color = Color.White) }
            val gif = gifs.firstOrNull { it.uri == settings.gifId } ?: gifs.firstOrNull()
            if (gif != null && settings.usesGif()) {
                Text(gif.name, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Button(onClick = { onRemoveGif(gif.uri) }, modifier = Modifier.tvFocus(), colors = darkButton()) { Text("Remove", color = Color.White) }
            } else if (gif != null) {
                Button(
                    onClick = { onChange(settings.copy(backdrop = "gif", gifId = gif.uri)) },
                    modifier = Modifier.tvFocus(),
                    colors = darkButton(),
                ) { Text("Use GIF", color = Color.White) }
            }
        }
        if (settings.usesGif()) {
            Text("Dim GIF  ${(settings.scrim * 100).toInt()}%", color = SoftRed, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepButton("Dim −") {
                    onChange(settings.copy(scrim = (settings.scrim - 0.1f).coerceIn(0f, 0.8f)))
                }
                StepButton("Dim +") {
                    onChange(settings.copy(scrim = (settings.scrim + 0.1f).coerceIn(0f, 0.8f)))
                }
            }
        }

        Section("Folders")
        Text("Add a folder to scan videos. Exclude a folder to leave it out.", color = SoftRed, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddFolder, modifier = Modifier.tvFocus(), colors = darkButton()) { Text("Add folder") }
            Button(onClick = onExcludeFolder, modifier = Modifier.tvFocus(), colors = darkButton()) { Text("Exclude folder") }
        }
        folders.forEach { folder ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    (if (folder.excluded) "Exclude  " else "Include  ") + folder.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Remove",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.tvFocus().clickable { onRemoveFolder(folder.uri) }.padding(8.dp),
                )
            }
        }

        Section("Rows")
        Text("Create a row, rename it, then move it. Hold a thumbnail for favorite, move, rename, or delete.", color = SoftRed, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RedlineTextField(
                value = rowDraft,
                onValueChange = { rowDraft = it.take(32) },
                label = "New row",
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onAddRow(rowDraft)
                    rowDraft = ""
                },
                modifier = Modifier.tvFocus(),
                colors = darkButton(),
            ) { Text("Add") }
        }
        settings.rows.sortedBy { it.order }.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RedlineTextField(
                    value = row.name,
                    onValueChange = { onRenameRow(row.id, it) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMoveRow(row.id, -1) }, modifier = Modifier.tvFocus()) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${row.name} up", tint = Color.White)
                }
                IconButton(onClick = { onMoveRow(row.id, 1) }, modifier = Modifier.tvFocus()) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${row.name} down", tint = Color.White)
                }
                if (row.kind == "custom") {
                    Text(
                        "Delete",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.tvFocus().clickable { onDeleteRow(row.id) }.padding(6.dp),
                    )
                }
            }
        }

        Section("Playback")
        ChoiceLine("Seek", listOf(5_000L to "5s", 10_000L to "10s", 15_000L to "15s", 30_000L to "30s"), settings.seekStepMs) {
            onChange(settings.copy(seekStepMs = it))
        }
        ChoiceLine(
            "Speed",
            listOf(0.5f to "0.5", 0.75f to "0.75", 1f to "1x", 1.25f to "1.25", 1.5f to "1.5", 2f to "2x"),
            settings.playbackSpeed,
        ) { onChange(settings.copy(playbackSpeed = it)) }
        ChoiceLine(
            "Picture",
            listOf("fit" to "Fit", "fill" to "Fill", "zoom" to "Zoom", "16:9" to "16:9", "4:3" to "4:3"),
            settings.aspect,
        ) { onChange(settings.copy(aspect = it)) }
        ChoiceLine(
            "Repeat",
            listOf("off" to "Off", "one" to "One", "all" to "All"),
            settings.repeatMode,
        ) { onChange(settings.copy(repeatMode = it)) }
        ChoiceLine(
            "Subtitles",
            listOf(0.7f to "Small", 1f to "Normal", 1.4f to "Large", 1.8f to "XL"),
            settings.subtitleScale,
        ) { onChange(settings.copy(subtitleScale = it)) }
        ChoiceLine(
            "Sleep",
            listOf(0 to "Off", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m", 90 to "90m"),
            settings.sleepMinutes,
        ) { onChange(settings.copy(sleepMinutes = it)) }
        Toggle("Resume where you left off", settings.resumePlayback) { onChange(settings.copy(resumePlayback = it)) }
        Toggle("Play the next video", settings.autoplayNext) { onChange(settings.copy(autoplayNext = it)) }
        Toggle("Show time remaining", settings.showRemaining) { onChange(settings.copy(showRemaining = it)) }
        Toggle("Ask before removing a video", settings.confirmRemove) { onChange(settings.copy(confirmRemove = it)) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Red, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.tvFocus())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceLine(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(label, color = SoftRed, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, name) ->
                val on = value == selected
                Button(
                    onClick = { onSelect(value) },
                    modifier = Modifier.tvFocus().height(36.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (on) Color.White else Surface2,
                        contentColor = if (on) Color.Black else Color.White,
                    ),
                ) {
                    Text(name, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(hex: String, onChange: (String) -> Unit) {
    val swatches = listOf(
        "#0F0F0F", "#000000", "#1C1C1C", "#FFFFFF",
        "#FF1F1F", "#B71C1C", "#E53935", "#FF8A80",
        "#1565C0", "#2E7D32", "#F9A825", "#6A1B9A",
        "#00838F", "#4E342E", "#455A64", "#F5F5F5",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colorFromHex(hex))
                    .border(2.dp, Color.White, CircleShape),
            )
            Text(hex.uppercase(), color = Color.White, fontSize = 13.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.forEach { swatch ->
                val selected = swatch.equals(hex, ignoreCase = true)
                Box(
                    Modifier
                        .size(32.dp)
                        .tvFocus(CircleShape)
                        .clip(CircleShape)
                        .background(colorFromHex(swatch))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Red else Color.White, CircleShape)
                        .clickable { onChange(swatch) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper("Hue") { onChange(shiftColor(hex, hueDelta = -20f)) }
            Stepper("Hue", plus = true) { onChange(shiftColor(hex, hueDelta = 20f)) }
            Stepper("Color") { onChange(shiftColor(hex, satDelta = -0.15f)) }
            Stepper("Color", plus = true) { onChange(shiftColor(hex, satDelta = 0.15f)) }
            Stepper("Light") { onChange(shiftColor(hex, valueDelta = -0.1f)) }
            Stepper("Light", plus = true) { onChange(shiftColor(hex, valueDelta = 0.1f)) }
        }
    }
}

@Composable
private fun Stepper(label: String, plus: Boolean = false, onClick: () -> Unit) {
    StepButton((if (plus) "$label +" else "$label −"), onClick)
}

private fun shiftColor(hex: String, hueDelta: Float = 0f, satDelta: Float = 0f, valueDelta: Float = 0f): String {
    val hsv = hexToHsv(hex)
    var saturation = (hsv[1] + satDelta).coerceIn(0f, 1f)
    var brightness = (hsv[2] + valueDelta).coerceIn(0f, 1f)
    val hue = (hsv[0] + hueDelta + 360f) % 360f
    if (hueDelta != 0f && saturation < 0.35f) saturation = 0.85f
    if (hueDelta != 0f && brightness < 0.2f) brightness = 0.55f
    return hexFromColor(Color.hsv(hue, saturation, brightness))
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .tvFocus()
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
fun RedlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var editing by remember { mutableStateOf(false) }
    if (!editing) {
        Column(
            modifier
                .tvFocus()
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2)
                .clickable { editing = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (label != null) Text(label, color = SoftRed, fontSize = 11.sp)
            Text(value.ifBlank { " " }, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        val keyboard = LocalSoftwareKeyboardController.current
        val requester = remember { FocusRequester() }
        var sawFocus by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = label?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                editing = false
                keyboard?.hide()
            }),
            colors = fieldColors(),
            modifier = modifier
                .focusRequester(requester)
                .onFocusChanged {
                    if (it.isFocused) sawFocus = true
                    else if (sawFocus) editing = false
                },
        )
        LaunchedEffect(Unit) {
            runCatching { requester.requestFocus() }
            keyboard?.show()
        }
        DisposableEffect(Unit) {
            onDispose { keyboard?.hide() }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Surface2,
    unfocusedContainerColor = Surface2,
    cursorColor = Color.White,
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Line,
    focusedLabelColor = SoftRed,
    unfocusedLabelColor = SoftRed,
)

@Composable
private fun darkButton() = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Color.White)

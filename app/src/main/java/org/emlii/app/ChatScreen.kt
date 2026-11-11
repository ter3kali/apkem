package org.emlii.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private val Mono = FontFamily.Monospace

// The GUI's sidebar: built-in commands, then plugins (built-in and custom, with their one-line help).
private val BUILTINS = listOf("help", "version", "date", "memory", "plugins", "reload")

private fun EmliiColors.byKey(k: String): Color = when (k) {
    "purpleHi" -> purpleHi
    "pink" -> pink
    "cyan" -> cyan
    "yellow" -> yellow
    "green" -> green
    "text" -> text
    else -> muted
}

private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = this.drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
    )
}

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onMic: () -> Unit,
    onPickWallpaper: () -> Unit,
    onImportPlugin: () -> Unit
) {
    val t = Themes.get(vm.theme.value)
    val fs = vm.fontSize.value

    CompositionLocalProvider(LocalEmlii provides t, LocalFs provides fs) {
        var input by remember { mutableStateOf("") }
        var showSettings by remember { mutableStateOf(false) }
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()
        val count = vm.messages.size
        val busy = vm.busy.value

        LaunchedEffect(count, busy) {
            val last = count - 1 + if (busy) 1 else 0
            if (last >= 0) listState.animateScrollToItem(last)
        }

        val doSend = {
            if (!vm.busy.value && input.isNotBlank()) {
                vm.send(input)
                input = ""
            }
        }

        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = t.surface, modifier = Modifier.width(240.dp)) {
                    Sidebar(plugins = vm.pluginList.value, onPick = { cmd ->
                        input = "!$cmd "
                        scope.launch { drawer.close() }
                    })
                }
            }
        ) {
            Column(Modifier.fillMaxSize().background(t.bg)) {
                // ── top bar ──
                Row(
                    Modifier.fillMaxWidth().background(t.surface).padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BarBtn("☰") { scope.launch { drawer.open() } }
                    Spacer(Modifier.width(4.dp))
                    Text("EMLII", color = t.purpleHi, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("v${ChatViewModel.VERSION}", color = t.muted, fontFamily = Mono, fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    BarBtn(if (vm.speakOn.value) "🔊 on" else "🔊 off") { vm.toggleSpeak() }
                    BarBtn("Clear") { vm.clear() }
                    BarBtn("◐") { vm.quickToggleTheme() }
                    BarBtn("⚙") { showSettings = true }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(t.border))

                // ── chat feed (with the wallpaper behind it) ──
                Box(Modifier.weight(1f).fillMaxWidth().background(t.bg)) {
                    when (vm.bgMode.value) {
                        "gradient" -> {
                            val bottom = hexColor(vm.bgColor2.value) ?: t.surface
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t.bg, bottom))))
                        }
                        "image" -> {
                            val img = File(vm.bgImage.value)
                            if (vm.bgImage.value.isNotBlank() && img.exists()) {
                                AsyncImage(
                                    model = img,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // dim toward the theme background so message text stays readable
                                Box(Modifier.fillMaxSize().background(t.bg.copy(alpha = vm.bgDim.value / 100f)))
                            }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(vm.messages) { m ->
                            MsgView(
                                m = m,
                                userName = vm.username.value,
                                onAccept = { vm.accept(m) },
                                onEdit = { new -> vm.edit(m, new) }
                            )
                        }
                        if (busy) {
                            item { TypingBubble() }
                        }
                    }
                }

                // ── input row:  name ▶  [ message ]  🎤  Send ──
                val name = vm.username.value
                val prefix = if (name.length in 1..8) "$name ▶" else "▶"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(t.surface)
                        .border(1.dp, t.border)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(prefix, color = t.purpleHi, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = (fs + 1).sp)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        enabled = !busy,
                        singleLine = true,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        textStyle = TextStyle(color = t.text, fontFamily = Mono, fontSize = fs.sp),
                        cursorBrush = SolidColor(t.purpleHi),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { doSend() }),
                        decorationBox = { inner ->
                            Box {
                                if (input.isEmpty()) {
                                    Text("Message EMLII…", color = t.muted, fontFamily = Mono, fontSize = fs.sp)
                                }
                                inner()
                            }
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    BarBtn("🎤") { onMic() }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .background(t.purple, RoundedCornerShape(4.dp))
                            .clickable { doSend() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Send", color = t.btnFg, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // ── status line ──
                Text(
                    "session: ${vm.username.value}   ·   model: ${vm.aiModel.value}   ·   voice: android",
                    color = t.muted, fontFamily = Mono, fontSize = 9.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().background(t.bg).padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        if (vm.username.value.isBlank()) UsernameDialog(onDone = { vm.setUsername(it) })
        if (showSettings) SettingsDialog(
            vm = vm,
            onPickWallpaper = onPickWallpaper,
            onImportPlugin = onImportPlugin,
            onClose = { showSettings = false }
        )
    }
}

@Composable
private fun BarBtn(label: String, onClick: () -> Unit) {
    val t = LocalEmlii.current
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .border(1.dp, t.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(label, color = t.muted, fontFamily = Mono, fontSize = 11.sp)
    }
}

// ── sidebar (☰) ──────────────────────────────────────────────────────────────
@Composable
private fun Sidebar(plugins: List<Pair<String, String>>, onPick: (String) -> Unit) {
    val t = LocalEmlii.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        SideHeading("COMMANDS")
        BUILTINS.forEach { SideItem(it, "", onPick) }
        SideHeading("PLUGINS")
        plugins.forEach { (c, d) -> SideItem(c, d, onPick) }
        Spacer(Modifier.height(10.dp))
        Text(
            "Tap a command to type it.",
            color = t.muted, fontFamily = Mono, fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
    }
}

@Composable
private fun SideHeading(text: String) {
    val t = LocalEmlii.current
    Text(
        text, color = t.muted, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 4.dp)
    )
}

@Composable
private fun SideItem(cmd: String, desc: String, onPick: (String) -> Unit) {
    val t = LocalEmlii.current
    Column(
        Modifier.fillMaxWidth().clickable { onPick(cmd) }.padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text("!$cmd", color = t.purpleHi, fontFamily = Mono, fontSize = 14.sp)
        if (desc.isNotBlank()) {
            Text(desc, color = t.muted, fontFamily = Mono, fontSize = 10.sp)
        }
    }
}

// ── one chat message ─────────────────────────────────────────────────────────
@Composable
private fun MsgView(m: Msg, userName: String, onAccept: () -> Unit, onEdit: (String) -> Unit) {
    val t = LocalEmlii.current
    val fs = LocalFs.current
    val small = maxOf(fs - 2, 8)
    val tiny = maxOf(fs - 3, 7)
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.88f).dp
    val shape = RoundedCornerShape(10.dp)
    val (fill, line) = when {
        m.isSystem -> t.bg to t.border
        m.fromUser -> t.userBg to t.userBorder
        m.warn -> t.warnBg to t.warnBorder
        m.pending -> t.predBg to t.predBorder
        else -> t.surface to t.border
    }
    val horiz = when {
        m.isSystem -> Alignment.CenterHorizontally
        m.fromUser -> Alignment.End
        else -> Alignment.Start
    }
    var editing by remember { mutableStateOf(false) }
    var editText by remember(m.text) { mutableStateOf(m.text) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = horiz) {
        if (!m.isSystem) {
            Row(Modifier.padding(start = 2.dp, end = 2.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (m.fromUser) userName else "EMLII").uppercase(),
                    color = if (m.fromUser) t.muted else t.purpleHi,
                    fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = small.sp
                )
                m.badge?.let { b ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        b, color = t.purpleHi, fontFamily = Mono, fontSize = tiny.sp,
                        modifier = Modifier.border(1.dp, t.purpleHi).padding(horizontal = 4.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(m.ts, color = t.muted, fontFamily = Mono, fontSize = tiny.sp)
            }
        }

        val base = Modifier.widthIn(max = maxW).background(fill, shape)
        val framed = if (m.isSystem) base.dashedBorder(line, 10.dp) else base.border(1.dp, line, shape)

        Column(framed.padding(horizontal = 12.dp, vertical = 9.dp)) {
            val rows = m.rows
            if (rows != null) {
                SelectionContainer {
                    Column {
                        Text((m.title ?: "").uppercase(), color = t.muted, fontFamily = Mono, fontSize = tiny.sp)
                        Spacer(Modifier.height(6.dp))
                        val stacked = rows.any { it.first.length > 12 }
                        val lw = (rows.maxOf { it.first.length }.coerceAtMost(12) * fs * 0.62f + 14f).dp
                        rows.forEach { (a, c) ->
                            if (stacked) {
                                Column(Modifier.padding(bottom = 6.dp)) {
                                    Text(a, color = t.byKey(m.lc), fontFamily = Mono, fontSize = fs.sp)
                                    Text(c, color = t.byKey(m.rc), fontFamily = Mono, fontSize = small.sp)
                                }
                            } else {
                                Row(Modifier.padding(bottom = 4.dp)) {
                                    Text(a, color = t.byKey(m.lc), fontFamily = Mono, fontSize = fs.sp, modifier = Modifier.width(lw))
                                    Text(c, color = t.byKey(m.rc), fontFamily = Mono, fontSize = fs.sp, modifier = Modifier.weight(1f, fill = false))
                                }
                            }
                        }
                    }
                }
            } else {
                SelectionContainer {
                    Text(
                        m.text,
                        color = when {
                            m.isSystem -> t.muted
                            m.fromUser -> t.text
                            else -> t.purpleHi
                        },
                        fontFamily = Mono,
                        fontSize = (if (m.isSystem) small else fs).sp,
                        textAlign = if (m.isSystem) TextAlign.Center else TextAlign.Start
                    )
                }
            }
            m.imageUrl?.let { url ->
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                )
            }
            if (m.pending) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(line))
                Spacer(Modifier.height(8.dp))
                if (editing) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontFamily = Mono, fontSize = fs.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = t.text, unfocusedTextColor = t.text,
                            focusedBorderColor = t.purple, unfocusedBorderColor = t.border, cursorColor = t.purple
                        )
                    )
                    Row(Modifier.padding(top = 6.dp)) {
                        ActionBtn("Save", t.green) { onEdit(editText); editing = false }
                        Spacer(Modifier.width(8.dp))
                        ActionBtn("Cancel", t.muted) { editing = false }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Correct?", color = t.muted, fontFamily = Mono, fontSize = tiny.sp)
                        Spacer(Modifier.weight(1f))
                        ActionBtn("✓ ok", t.green, onAccept)
                        Spacer(Modifier.width(8.dp))
                        ActionBtn("✎ edit", t.muted) { editing = true }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionBtn(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, color)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, color = color, fontFamily = Mono, fontSize = 12.sp)
    }
}

@Composable
private fun TypingBubble() {
    val t = LocalEmlii.current
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            "EMLII is thinking…",
            color = t.muted, fontFamily = Mono, fontSize = 11.sp,
            modifier = Modifier
                .background(t.surface, shape)
                .border(1.dp, t.border, shape)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ── dialogs ──────────────────────────────────────────────────────────────────
@Composable
private fun UsernameDialog(onDone: (String) -> Unit) {
    val t = LocalEmlii.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { },
        containerColor = t.surface,
        titleContentColor = t.purpleHi,
        textContentColor = t.text,
        title = { Text("EMLII", fontFamily = Mono, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter your username:", fontFamily = Mono, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = t.text, unfocusedTextColor = t.text,
                        focusedBorderColor = t.purple, unfocusedBorderColor = t.border, cursorColor = t.purple
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(name) }) { Text("OK", color = t.purple, fontFamily = Mono) }
        }
    )
}

@Composable
private fun SettingLabel(text: String) {
    val t = LocalEmlii.current
    Text(
        text, color = t.muted, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsDialog(
    vm: ChatViewModel,
    onPickWallpaper: () -> Unit,
    onImportPlugin: () -> Unit,
    onClose: () -> Unit
) {
    val t = LocalEmlii.current
    var uname by remember { mutableStateOf(vm.username.value) }
    var gradText by remember { mutableStateOf(vm.bgColor2.value) }
    val finish = {
        vm.setUsername(uname)
        onClose()
    }
    AlertDialog(
        onDismissRequest = { finish() },
        containerColor = t.surface,
        titleContentColor = t.purpleHi,
        textContentColor = t.text,
        title = { Text("Settings", fontFamily = Mono, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingLabel("THEME")
                Themes.names.forEach { name ->
                    val c = Themes.get(name)
                    val selected = vm.theme.value == name
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.setTheme(name) }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(16.dp).background(c.bg, CircleShape).border(1.dp, c.border, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.size(16.dp).background(c.purple, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            name,
                            color = if (selected) t.purpleHi else t.text,
                            fontFamily = Mono, fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) Text("✓", color = t.green, fontFamily = Mono)
                    }
                }

                SettingLabel("FONT SIZE  ${vm.fontSize.value}")
                Slider(
                    value = vm.fontSize.value.toFloat(),
                    onValueChange = { vm.setFontSize(it.roundToInt()) },
                    valueRange = 8f..20f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = t.purple,
                        activeTrackColor = t.purple,
                        inactiveTrackColor = t.border
                    )
                )

                SettingLabel("USERNAME")
                OutlinedTextField(
                    value = uname,
                    onValueChange = { uname = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = t.text, unfocusedTextColor = t.text,
                        focusedBorderColor = t.purple, unfocusedBorderColor = t.border, cursorColor = t.purple
                    )
                )

                SettingLabel("BACKGROUND")
                listOf("solid" to "Solid colour", "gradient" to "Gradient", "image" to "Wallpaper image").forEach { (mode, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.setBgMode(mode) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = vm.bgMode.value == mode,
                            onClick = { vm.setBgMode(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = t.purple, unselectedColor = t.muted)
                        )
                        Text(label, fontFamily = Mono, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Gradient bottom colour", color = t.muted, fontFamily = Mono, fontSize = 11.sp)
                Row(Modifier.padding(vertical = 6.dp)) {
                    listOf("#2a0f4a", "#0a2a4a", "#0a3a1a", "#4a0f2a", "#3a2a0a", "#1a1a1a").forEach { hex ->
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(28.dp)
                                .background(hexColor(hex) ?: t.surface, CircleShape)
                                .border(1.dp, t.border, CircleShape)
                                .clickable {
                                    gradText = hex
                                    vm.setBgColor2(hex)
                                    vm.setBgMode("gradient")
                                }
                        )
                    }
                }
                OutlinedTextField(
                    value = gradText,
                    onValueChange = {
                        gradText = it
                        if (hexColor(it) != null) vm.setBgColor2(it)
                    },
                    singleLine = true,
                    placeholder = { Text("#rrggbb", fontFamily = Mono, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = t.text, unfocusedTextColor = t.text,
                        focusedBorderColor = t.purple, unfocusedBorderColor = t.border, cursorColor = t.purple
                    )
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionBtn("Choose image…", t.purple) { onPickWallpaper() }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (vm.bgImage.value.isBlank()) "none chosen" else File(vm.bgImage.value).name,
                        color = t.muted, fontFamily = Mono, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                SettingLabel("DIM IMAGE  ${vm.bgDim.value}%")
                Slider(
                    value = vm.bgDim.value.toFloat(),
                    onValueChange = { vm.setBgDim(it.roundToInt()) },
                    valueRange = 0f..90f,
                    steps = 17,
                    colors = SliderDefaults.colors(
                        thumbColor = t.purple,
                        activeTrackColor = t.purple,
                        inactiveTrackColor = t.border
                    )
                )
                Text("Tip: dim the image so message text stays readable.", color = t.muted, fontFamily = Mono, fontSize = 10.sp)

                SettingLabel("PLUGINS")
                Text("Tools folder:", color = t.muted, fontFamily = Mono, fontSize = 10.sp)
                Text(vm.folders.toolsDir.absolutePath, fontFamily = Mono, fontSize = 10.sp)
                Row(Modifier.padding(top = 8.dp)) {
                    ActionBtn("Import plugin…", t.purple) { onImportPlugin() }
                    Spacer(Modifier.width(8.dp))
                    ActionBtn("Reload", t.muted) { vm.reloadPlugins() }
                }

                SettingLabel("LEARNED REPLIES")
                Text("Stored in:", color = t.muted, fontFamily = Mono, fontSize = 10.sp)
                Text(vm.folders.learnedDir.absolutePath, fontFamily = Mono, fontSize = 10.sp)

                SettingLabel("AI MODEL")
                Text(
                    "Set with  !ai url <address>  and  !ai model <name>  in the chat.",
                    color = t.muted, fontFamily = Mono, fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { finish() }) { Text("Done", color = t.purple, fontFamily = Mono) }
        }
    )
}

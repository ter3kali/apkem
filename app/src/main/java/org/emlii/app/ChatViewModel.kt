package org.emlii.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Msg(
    val fromUser: Boolean,
    val text: String,
    val ts: String,
    val badge: String? = null,
    val pending: Boolean = false,
    val hash: String? = null,
    val originalPrompt: String? = null,
    val imageUrl: String? = null,
    val isSystem: Boolean = false,
    val warn: Boolean = false,
    // "card" messages (like the GUI's !help / !hash tables): a title and label/value rows
    val title: String? = null,
    val rows: List<Pair<String, String>>? = null,
    val lc: String = "purpleHi",
    val rc: String = "muted"
)

class ChatViewModel(ctx: Context) : ViewModel() {
    companion object {
        const val VERSION = "3.5"
    }

    private val appCtx = ctx.applicationContext
    val folders = AppFolders(appCtx)
    private val engine = Engine(folders.learnedDir)          // learned replies live in their own folder
    private val ai = AiTool(appCtx)
    private val prefs = Prefs(appCtx)
    private val plugins = PluginRegistry(folders.toolsDir)
    private var pendingTeach: Pair<String, String>? = null   // hash, original prompt

    var voice: VoiceOutput? = null

    /** Set while the voice orb is open: it gets (reply text, what to speak) instead of the plain TTS path. */
    var botListener: ((String, String) -> Unit)? = null

    val messages = mutableStateListOf<Msg>()
    val busy = mutableStateOf(false)

    // UI settings (the GUI's emlii_gui_settings.json)
    val username = mutableStateOf(prefs.username)
    val theme = mutableStateOf(prefs.theme)
    val fontSize = mutableStateOf(prefs.fontSize)
    val speakOn = mutableStateOf(prefs.speakOn)
    val aiModel = mutableStateOf(ai.modelName())

    // background / wallpaper
    val bgMode = mutableStateOf(prefs.bgMode)            // solid | gradient | image
    val bgColor2 = mutableStateOf(prefs.bgColor2)        // gradient bottom colour, "#rrggbb" or ""
    val bgImage = mutableStateOf(prefs.bgImage)
    val bgDim = mutableStateOf(prefs.bgDim)

    /** (name, help) for the sidebar — built-in and custom plugins alike. */
    val pluginList = mutableStateOf<List<Pair<String, String>>>(emptyList())

    init {
        plugins.register(HashPlugin())
        plugins.register(TradePlugin(TradingTool(File(folders.dataDir, "trading.json"))))
        plugins.register(WaifuPlugin(appCtx))
        plugins.register(AiPlugin(ai))
        plugins.reload()
        refreshPlugins()
        if (username.value.isNotBlank()) greet()
    }

    private fun refreshPlugins() {
        pluginList.value = plugins.all().map { it.name to it.help }
    }

    private fun greet() {
        bot(
            "Hello, ${username.value}. I am EMLII v$VERSION.\n" +
                "I learn and predict — the more we chat, the smarter I get.\n" +
                "Type !help, or tap ☰ and pick a command."
        )
    }

    // ── settings ──
    fun setUsername(raw: String) {
        val name = raw.trim().ifBlank { "User" }
        val first = username.value.isBlank()
        username.value = name
        prefs.username = name
        if (first && messages.isEmpty()) greet()
    }

    fun setTheme(name: String) {
        if (name !in Themes.names) return
        theme.value = name
        prefs.theme = name
    }

    /** ◐ — flip between Light and your last dark preset. */
    fun quickToggleTheme() {
        if (theme.value == "Light") {
            setTheme(prefs.lastDark)
        } else {
            prefs.lastDark = theme.value
            setTheme("Light")
        }
    }

    fun setFontSize(n: Int) {
        val v = n.coerceIn(8, 20)
        fontSize.value = v
        prefs.fontSize = v
    }

    fun toggleSpeak() {
        speakOn.value = !speakOn.value
        prefs.speakOn = speakOn.value
        if (!speakOn.value) voice?.stop()
    }

    fun setBgMode(mode: String) {
        if (mode != "solid" && mode != "gradient" && mode != "image") return
        bgMode.value = mode
        prefs.bgMode = mode
    }

    fun setBgColor2(hex: String) {
        bgColor2.value = hex
        prefs.bgColor2 = hex
    }

    fun setBgDim(n: Int) {
        val v = n.coerceIn(0, 90)
        bgDim.value = v
        prefs.bgDim = v
    }

    /** Copies the picked picture into the wallpaper folder and switches the background to it. */
    fun setWallpaperFromUri(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val out = File(folders.wallpaperDir, "wp_${System.currentTimeMillis()}.img")
                    val input = appCtx.contentResolver.openInputStream(uri) ?: return@withContext null
                    input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                    folders.wallpaperDir.listFiles()?.filter { it != out }?.forEach { it.delete() }
                    out.absolutePath
                } catch (e: Exception) {
                    null
                }
            }
            if (path == null) {
                sys("Couldn't read that image.")
            } else {
                bgImage.value = path
                prefs.bgImage = path
                setBgMode("image")
            }
        }
    }

    // ── plugins ──
    fun reloadPlugins() {
        plugins.reload()
        refreshPlugins()
        val custom = plugins.all().count { it.custom }
        sys("Plugins reloaded — $custom custom plugin(s) found.")
        plugins.errors.forEach { sys("⚠ $it") }
    }

    /** Settings → Import plugin: validate the picked .json, copy it into the tools folder, reload. */
    fun importPlugin(uri: Uri) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val text = appCtx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: return@withContext "Couldn't read that file."
                    if (text.length > 100_000) return@withContext "That file is too big to be a plugin."
                    val p = JsonPlugin.parse(text)
                    plugins.install(p, text) ?: "Plugin !${p.name} installed."
                } catch (e: Exception) {
                    "Couldn't import that plugin: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            refreshPlugins()
            sys(msg)
        }
    }

    // ── messages ──
    private fun ts() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    private fun user(text: String) {
        messages.add(Msg(fromUser = true, text = text, ts = ts()))
    }

    /** Speech: the voice orb if it's open, else the plain 🔊 toggle. */
    private fun announce(text: String, spoken: String) {
        val l = botListener
        if (l != null) l(text, spoken) else if (speakOn.value) voice?.speak(spoken)
    }

    private fun bot(
        text: String,
        badge: String? = null,
        pending: Boolean = false,
        hash: String? = null,
        originalPrompt: String? = null,
        imageUrl: String? = null,
        warn: Boolean = false
    ) {
        messages.add(
            Msg(
                fromUser = false, text = text, ts = ts(), badge = badge, pending = pending,
                hash = hash, originalPrompt = originalPrompt, imageUrl = imageUrl, warn = warn
            )
        )
        val tooLongForSpeech = text.count { it == '\n' } >= 4 || text.contains("http")
        announce(text, if (tooLongForSpeech) "Done. The result is in the chat." else text)
    }

    private fun kv(title: String, rows: List<Pair<String, String>>, lc: String = "purpleHi", rc: String = "muted") {
        messages.add(
            Msg(
                fromUser = false,
                text = rows.joinToString("\n") { "${it.first}  ${it.second}" },
                ts = ts(), title = title, rows = rows, lc = lc, rc = rc
            )
        )
        announce(title, "Done. The result is in the chat.")
    }

    private fun sys(text: String) {
        messages.add(Msg(false, text, ts(), isSystem = true))
    }

    private fun replace(old: Msg, f: (Msg) -> Msg) {
        val i = messages.indexOfFirst { it === old }
        if (i >= 0) messages[i] = f(old)
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy.value) return
        user(text)

        pendingTeach?.let { (hash, prompt) ->
            pendingTeach = null
            engine.learn(hash, prompt, text)
            bot(text)
            return
        }

        if (text.startsWith("!")) {
            runCommand(text)
            return
        }

        when (val r = engine.process(text)) {
            is EngineResult.Reply -> bot(r.text)
            is EngineResult.Predicted -> bot(
                r.text,
                badge = "~similar input · ${(r.confidence * 100).toInt()}%",
                pending = true,
                hash = r.hash,
                originalPrompt = text
            )
            is EngineResult.NeedsTeaching -> {
                pendingTeach = r.hash to r.prompt
                bot("I don't know how to respond to \"${r.prompt}\". Type a reply and I'll remember it.", warn = true)
            }
        }
    }

    private fun runCommand(text: String) {
        val parts = text.removePrefix("!").trim().split(Regex("\\s+"), 2)
        val cmd = parts.getOrElse(0) { "" }.lowercase()
        val arg = parts.getOrElse(1) { "" }
        when (cmd) {
            "help", "" -> {
                val rows = mutableListOf(
                    "!help" to "Show all commands",
                    "!version" to "EMLII version info",
                    "!date" to "Current date & time",
                    "!memory" to "How much EMLII has learned",
                    "!plugins" to "List plugins and the tools folder",
                    "!reload" to "Reload plugins from the tools folder"
                )
                plugins.all().forEach { rows.add("!${it.name}" to it.help) }
                kv("Commands", rows)
            }
            "version" -> bot("EMLII v$VERSION — Self-Learning Edition")
            "date" -> bot(SimpleDateFormat("EEEE, d MMMM yyyy · HH:mm:ss", Locale.getDefault()).format(Date()))
            "memory" -> bot(
                "I have learned ${engine.entryCount()} repl${if (engine.entryCount() == 1) "y" else "ies"}.\n" +
                    "Stored in: ${folders.learnedDir.absolutePath}"
            )
            "plugins" -> {
                val rows = plugins.all().map { "!${it.name}" to (it.help + if (it.custom) "  [custom]" else "") }.toMutableList()
                rows.add("tools folder" to folders.toolsDir.absolutePath)
                plugins.errors.forEach { rows.add("error" to it) }
                kv("Plugins", rows)
            }
            "reload" -> reloadPlugins()
            else -> {
                val p = plugins.find(cmd)
                if (p != null) runPlugin(p, arg)
                else bot("Unknown command '!$cmd'. Type !help to see what's available.")
            }
        }
    }

    /** Every plugin runs off the main thread; the typing bubble shows while it works. */
    private fun runPlugin(p: Plugin, arg: String) {
        busy.value = true
        viewModelScope.launch {
            val r = try {
                withContext(Dispatchers.IO) { p.run(arg) }
            } catch (e: Exception) {
                PluginResult("!${p.name} failed: ${e.message ?: e.javaClass.simpleName}")
            }
            aiModel.value = ai.modelName()
            busy.value = false
            val rows = r.rows
            if (rows != null) kv(r.title ?: p.name, rows, r.lc, r.rc)
            else bot(r.text, imageUrl = r.imageUrl)
        }
    }

    /** "✓ ok" on a predicted reply: remember this exact input -> this exact reply from now on. */
    fun accept(msg: Msg) {
        val h = msg.hash ?: return
        engine.learn(h, msg.originalPrompt ?: "", msg.text)
        replace(msg) { it.copy(pending = false, badge = null) }
    }

    /** "✎ edit": save the corrected reply under the same hash. */
    fun edit(msg: Msg, newText: String) {
        val h = msg.hash ?: return
        if (newText.isBlank()) return
        engine.correct(h, newText)
        replace(msg) { it.copy(text = newText, pending = false, badge = null) }
        sys("Correction saved.")
    }

    fun clear() {
        messages.clear()
    }
}

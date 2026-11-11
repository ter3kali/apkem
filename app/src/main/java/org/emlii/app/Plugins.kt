package org.emlii.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** What a plugin hands back. [rows] != null shows as a label/value card like !hash. */
class PluginResult(
    val text: String,
    val imageUrl: String? = null,
    val title: String? = null,
    val rows: List<Pair<String, String>>? = null,
    val lc: String = "purpleHi",
    val rc: String = "muted"
)

/** A !command. run() is always called on a background thread. */
interface Plugin {
    val name: String
    val help: String
    val custom: Boolean get() = false
    fun run(arg: String): PluginResult
}

// ── built-in plugins (the four tools from before, now behind the same interface) ──────────
class HashPlugin : Plugin {
    override val name = "hash"
    override val help = "MD5 / SHA1 / SHA256 / SHA512"
    override fun run(arg: String): PluginResult {
        if (arg.isBlank()) return PluginResult("Usage: !hash <text>")
        val lines = HashTool.run(arg).lines()
        val rows = lines.drop(1).mapNotNull { l ->
            val i = l.indexOf(": ")
            if (i > 0) l.substring(0, i) to l.substring(i + 2) else null
        }
        return if (lines.isEmpty() || rows.isEmpty()) PluginResult(lines.joinToString("\n"))
        else PluginResult(lines.joinToString("\n"), title = lines[0], rows = rows, lc = "pink", rc = "cyan")
    }
}

class TradePlugin(private val trading: TradingTool) : Plugin {
    override val name = "trade"
    override val help = "prices | buy | sell | port | next | news | chart | reset"
    override fun run(arg: String): PluginResult {
        val p = arg.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val sub = p.getOrElse(0) { "" }.lowercase()
        val text = when (sub) {
            "", "help" -> "TERMSIM\n!trade prices | buy | sell | port | next | news | chart | reset\n\n${trading.portfolioText()}"
            "prices", "price", "market" -> trading.pricesText()
            "port", "portfolio", "pf" -> trading.portfolioText()
            "news" -> trading.newsText()
            "chart", "c" -> if (p.size > 1) trading.chartText(p[1]) else "Usage: !trade chart <TICKER>"
            "buy" -> if (p.size > 2) trading.buy(p[1], p[2]) else "Usage: !trade buy <TICKER> <QTY|max>"
            "sell" -> if (p.size > 2) trading.sell(p[1], p[2]) else "Usage: !trade sell <TICKER> <QTY|all>"
            "next", "n" -> trading.advanceDay((p.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, 30))
            "reset" -> if (p.getOrNull(1)?.equals("confirm", true) == true) trading.reset()
                       else "This erases your portfolio. Type  !trade reset confirm  to continue."
            else -> "Unknown option '$sub'.\n\n!trade prices | buy | sell | port | next | news | chart | reset"
        }
        return PluginResult(text)
    }
}

class WaifuPlugin(private val ctx: Context) : Plugin {
    override val name = "waifu"
    override val help = "anime picture / GIF  [tag]  — saved to Pictures/EMLII  (!waifu tags, add nosave to skip)"
    override fun run(arg: String): PluginResult {
        val r = WaifuTool.run(arg)
        val url = r.imageUrl ?: return PluginResult(r.text)
        val skip = arg.lowercase().split(Regex("\\s+")).contains("nosave")
        if (skip) return PluginResult(r.text, imageUrl = url)
        val saved = ImageSaver.save(ctx, url)
        val note = if (saved != null) "\nSaved to $saved" else "\n(couldn't save it to the phone)"
        return PluginResult(r.text + note, imageUrl = url)
    }
}

class AiPlugin(private val ai: AiTool) : Plugin {
    override val name = "ai"
    override val help = "status | url <addr> | model <name> | test"
    override fun run(arg: String): PluginResult {
        val p = arg.trim().split(Regex("\\s+"), 2)
        val sub = p.getOrElse(0) { "status" }.lowercase()
        val a = p.getOrElse(1) { "" }
        val text = when (sub) {
            "", "status", "s" -> ai.status()
            "url" -> ai.setUrl(a)
            "model" -> ai.setModel(a)
            "test" -> ai.ask("Say hello in five words.") ?: "No answer from the AI.\n\n${ai.status()}"
            else -> "Options: !ai status | !ai url <address> | !ai model <n> | !ai test"
        }
        return PluginResult(text)
    }
}

// ── plugins you add yourself: a .json file in the tools folder ─────────────────────────────
/**
 * {"name":"joke", "help":"random joke", "type":"http",
 *  "url":"https://official-joke-api.appspot.com/random_joke",
 *  "reply":"{setup}\n{punchline}"}
 *
 * type "text"   -> "reply" (use {arg} for what was typed after the command)
 * type "random" -> "replies": ["a", "b", ...]  (one picked at random; {arg} works too)
 * type "http"   -> GET "url" ({arg} is URL-encoded); "reply" fills {a.b.0}-style JSON paths;
 *                  optional "image": "json.path.to.image.url" and "headers": {...}
 */
class JsonPlugin private constructor(
    override val name: String,
    override val help: String,
    private val type: String,
    private val reply: String?,
    private val replies: List<String>,
    private val url: String?,
    private val image: String?,
    private val headers: Map<String, String>
) : Plugin {
    override val custom: Boolean get() = true

    companion object {
        private val NAME_RE = Regex("[a-z0-9_]{1,24}")

        fun parse(text: String): JsonPlugin {
            val o = try { JSONObject(text) } catch (e: Exception) { throw IllegalArgumentException("not valid JSON (${e.message})") }
            val name = o.optString("name").trim().lowercase()
            if (!NAME_RE.matches(name)) throw IllegalArgumentException("\"name\" must be 1-24 letters, digits or _")
            val type = o.optString("type", "text").lowercase()
            val reply = if (o.has("reply")) o.optString("reply") else null
            val replies = mutableListOf<String>()
            val arr = o.optJSONArray("replies")
            if (arr != null) for (i in 0 until arr.length()) replies.add(arr.optString(i))
            val url = if (o.has("url")) o.optString("url") else null
            when (type) {
                "text" -> {
                    if (reply == null) throw IllegalArgumentException("type \"text\" needs a \"reply\"")
                }
                "random" -> {
                    if (replies.isEmpty()) throw IllegalArgumentException("type \"random\" needs a \"replies\" list")
                }
                "http" -> {
                    if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
                        throw IllegalArgumentException("type \"http\" needs a \"url\" starting with http:// or https://")
                    }
                }
                else -> throw IllegalArgumentException("unknown type \"$type\" (use text, random or http)")
            }
            val headers = mutableMapOf<String, String>()
            o.optJSONObject("headers")?.let { h ->
                val keys = h.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    headers[k] = h.optString(k)
                }
            }
            return JsonPlugin(
                name, o.optString("help", "custom plugin"), type, reply, replies, url,
                if (o.has("image")) o.optString("image") else null, headers
            )
        }

        private fun fill(tpl: String, lookup: (String) -> String?): String =
            Regex("\\{([^{}]+)\\}").replace(tpl) { m -> lookup(m.groupValues[1].trim()) ?: "" }

        private fun jsonGet(root: Any?, path: String): String? {
            var cur: Any? = root
            for (part in path.split(".")) {
                val c = cur
                cur = when (c) {
                    is JSONObject -> if (c.has(part)) c.get(part) else return null
                    is JSONArray -> {
                        val i = part.toIntOrNull() ?: return null
                        if (i in 0 until c.length()) c.get(i) else return null
                    }
                    else -> return null
                }
            }
            return if (cur == null || cur == JSONObject.NULL) null else cur.toString()
        }
    }

    override fun run(arg: String): PluginResult {
        when (type) {
            "text" -> return PluginResult(fill(reply ?: "") { k -> if (k == "arg") arg else null })
            "random" -> return PluginResult(fill(replies.random()) { k -> if (k == "arg") arg else null })
        }
        val target = fill(url ?: "") { k -> if (k == "arg") URLEncoder.encode(arg, "UTF-8") else null }
        return try {
            val conn = URL(target).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "EMLII-Android/1.0")
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) return PluginResult("!$name: HTTP $code\n${body.take(300)}")
            val trimmed = body.trim()
            val json: Any? = try {
                when {
                    trimmed.startsWith("{") -> JSONObject(trimmed)
                    trimmed.startsWith("[") -> JSONArray(trimmed)
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
            val text = if (reply != null) {
                fill(reply) { k -> if (k == "arg") arg else if (json != null) jsonGet(json, k) else null }
            } else trimmed.take(1500)
            val img = if (image != null && json != null) jsonGet(json, image) else null
            PluginResult(text.ifBlank { "(empty reply)" }, imageUrl = img)
        } catch (e: Exception) {
            PluginResult("!$name couldn't reach $target\n${e.message ?: e.javaClass.simpleName}")
        }
    }
}

/** Built-in plugins + the .json plugins found in the tools folder (the GUI's core.TOOLS). */
class PluginRegistry(private val toolsDir: File) {
    private val builtIn = mutableListOf<Plugin>()
    private val customList = mutableListOf<Plugin>()
    val errors = mutableListOf<String>()

    /** Command names that are handled by the app itself. */
    private val reserved = setOf("help", "version", "date", "memory", "plugins", "reload")

    fun register(p: Plugin) {
        builtIn.add(p)
    }

    fun all(): List<Plugin> = builtIn + customList

    fun find(name: String): Plugin? = all().firstOrNull { it.name == name }

    fun reload() {
        customList.clear()
        errors.clear()
        writeHelpFiles()
        val files = toolsDir.listFiles { f -> f.isFile && f.name.endsWith(".json", true) }?.sortedBy { it.name } ?: emptyList()
        for (f in files) {
            try {
                val p = JsonPlugin.parse(f.readText())
                val clash = clashWith(p.name)
                if (clash != null) errors.add("${f.name}: $clash") else customList.add(p)
            } catch (e: Exception) {
                errors.add("${f.name}: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun clashWith(name: String): String? = when {
        name in reserved -> "!$name is a built-in command"
        builtIn.any { it.name == name } -> "!$name is a built-in plugin"
        customList.any { it.name == name } -> "!$name is already defined by another file"
        else -> null
    }

    /** Saves a plugin file into the tools folder. Returns an error message, or null when installed. */
    fun install(p: JsonPlugin, json: String): String? {
        if (p.name in reserved || builtIn.any { it.name == p.name }) return "!${p.name} is a built-in command"
        return try {
            File(toolsDir, "${p.name}.json").writeText(json)
            reload()
            null
        } catch (e: Exception) {
            "couldn't save the plugin (${e.message})"
        }
    }

    private fun writeHelpFiles() {
        try {
            toolsDir.mkdirs()
            val readme = File(toolsDir, "README.txt")
            if (!readme.exists()) {
                readme.writeText(
                    "EMLII plugins\n" +
                        "=============\n" +
                        "Every *.json file in this folder becomes a !command. Type !reload in the chat\n" +
                        "(or use Settings -> Import plugin) after adding or changing one.\n\n" +
                        "Types:\n" +
                        "  text    {\"name\":\"hi\",\"type\":\"text\",\"reply\":\"Hello {arg}!\"}\n" +
                        "  random  {\"name\":\"coin\",\"type\":\"random\",\"replies\":[\"Heads\",\"Tails\"]}\n" +
                        "  http    {\"name\":\"joke\",\"type\":\"http\",\"url\":\"https://official-joke-api.appspot.com/random_joke\",\n" +
                        "           \"reply\":\"{setup}\\n{punchline}\"}\n\n" +
                        "http plugins: {arg} in the url is URL-encoded; {a.b.0} in \"reply\" reads that path from the\n" +
                        "JSON answer; \"image\":\"path\" shows a picture; \"headers\":{...} adds request headers.\n" +
                        "Add \"help\":\"one line\" to describe it in !help and the sidebar.\n"
                )
            }
            val example = File(toolsDir, "joke.json.example")
            if (!example.exists()) {
                example.writeText(
                    "{\n  \"name\": \"joke\",\n  \"help\": \"a random joke\",\n  \"type\": \"http\",\n" +
                        "  \"url\": \"https://official-joke-api.appspot.com/random_joke\",\n" +
                        "  \"reply\": \"{setup}\\n{punchline}\"\n}\n"
                )
            }
        } catch (_: Exception) {
        }
    }
}

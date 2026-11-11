package org.emlii.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * !ai — the AI fallback, talking to an Ollama server over HTTP. The phone can't run Ollama
 * itself, so this points at one running elsewhere: a PC on the same Wi-Fi, or Termux on this
 * same phone (http://127.0.0.1:11434, the default). Settings are saved in SharedPreferences.
 */
class AiTool(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("emlii_ai", Context.MODE_PRIVATE)

    private var url: String
        get() = prefs.getString("url", DEFAULT_URL) ?: DEFAULT_URL
        set(v) = prefs.edit().putString("url", v).apply()

    private var model: String
        get() = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = prefs.edit().putString("model", v).apply()

    fun modelName(): String = model

    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:11434"
        const val DEFAULT_MODEL = "hf.co/bartowski/SmolLM2-360M-Instruct-GGUF:Q5_K_M"
    }

    /** Runs on a background thread — the caller is expected to dispatch off the main thread. */
    fun status(): String {
        val sb = StringBuilder("AI server : $url\nAI model  : $model\n")
        return try {
            val conn = URL("$url/api/tags").openConnection() as HttpURLConnection
            conn.connectTimeout = 4_000
            conn.readTimeout = 4_000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(body).getJSONArray("models")
            val names = (0 until arr.length()).map { arr.getJSONObject(it).optString("name", "?") }
            sb.append("Reachable : yes\n")
            sb.append("Installed : ").append(if (names.isEmpty()) "(none — run: ollama pull <model>)" else names.take(6).joinToString(", "))
            if (names.isNotEmpty() && names.none { it == model || model.substringBefore(":") in it }) {
                sb.append("\nHeads-up  : '$model' isn't installed there — use  !ai model <name>")
            }
            sb.toString()
        } catch (e: Exception) {
            sb.append("Reachable : no (${e.message ?: e.javaClass.simpleName})\n")
            sb.append("Start Ollama there, or point to another one with  !ai url <address>")
            sb.toString()
        }
    }

    fun setUrl(raw: String): String {
        val v = raw.trim()
        url = when {
            v.isEmpty() -> return "Usage: !ai url <address>   e.g. !ai url http://192.168.1.20:11434"
            v.equals("default", true) || v.equals("reset", true) || v.equals("local", true) -> DEFAULT_URL
            v.startsWith("http://") || v.startsWith("https://") -> v.trimEnd('/')
            else -> "http://$v".trimEnd('/')
        }
        return "Saved.\n\n${status()}"
    }

    fun setModel(raw: String): String {
        val v = raw.trim()
        if (v.isEmpty()) return "Usage: !ai model <name>   e.g. !ai model llama3.2:1b"
        model = if (v.equals("default", true)) DEFAULT_MODEL else v
        return "Saved.\n\n${status()}"
    }

    /** Runs on a background thread — the caller is expected to dispatch off the main thread. */
    fun ask(prompt: String): String? {
        return try {
            val conn = URL("$url/api/generate").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 90_000
            conn.readTimeout = 90_000
            val payload = JSONObject().put("model", model).put("prompt", prompt).put("stream", false)
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("response").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}

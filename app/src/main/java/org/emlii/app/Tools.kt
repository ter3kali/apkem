package org.emlii.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** !hash <text> — MD5, SHA-1, SHA-256, SHA-512. */
object HashTool {
    fun run(text: String): String {
        val algos = listOf("MD5", "SHA-1", "SHA-256", "SHA-512")
        val sb = StringBuilder("Hashes of \"$text\"\n")
        for (a in algos) {
            val d = MessageDigest.getInstance(a).digest(text.toByteArray(Charsets.UTF_8))
            val hex = d.joinToString("") { "%02x".format(it) }
            sb.append(a.replace("-", "")).append(": ").append(hex).append("\n")
        }
        return sb.toString().trim()
    }
}

/** !waifu [tag] [save] — anime pictures/GIFs from the nekos.best API (same service as the desktop tool). */
object WaifuTool {
    private val IMAGE_TAGS = listOf("waifu", "neko", "husbando", "kitsune")
    private val GIF_TAGS = listOf(
        "hug", "kiss", "pat", "cry", "cuddle", "dance", "blush", "happy", "smile",
        "smug", "wave", "wink", "poke", "nom", "bite", "slap", "kick", "punch",
        "laugh", "bored", "sleep", "yawn", "think", "nod", "run", "pout", "feed",
        "tickle", "yeet", "shrug", "highfive", "handshake", "stare", "baka", "shoot"
    )
    private val ALL_TAGS = IMAGE_TAGS + GIF_TAGS

    data class Result(val text: String, val imageUrl: String?)

    fun tagList(): String =
        "Image tags: ${IMAGE_TAGS.joinToString(", ")}\n\nGIF reaction tags: ${GIF_TAGS.joinToString(", ")}"

    /** Runs on a background thread — the caller is expected to dispatch off the main thread. */
    fun run(arg: String): Result {
        val words = arg.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.firstOrNull() in listOf("tags", "list", "help", "?")) {
            return Result(tagList(), null)
        }
        val tag = words.firstOrNull { it in ALL_TAGS } ?: "waifu"
        return try {
            val conn = URL("https://nekos.best/api/v2/$tag").openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "EMLII-Android/1.0")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(body).getJSONArray("results").getJSONObject(0)
            val url = obj.getString("url")
            val anime = obj.optString("anime_name", "")
            val artist = obj.optString("artist_name", "")
            val meta = listOfNotNull(
                anime.takeIf { it.isNotBlank() }?.let { "anime: $it" },
                artist.takeIf { it.isNotBlank() }?.let { "artist: $it" }
            ).joinToString(" · ")
            val kind = if (tag in GIF_TAGS) " · GIF" else ""
            Result("$tag$kind\n$url" + if (meta.isNotBlank()) "\n$meta" else "", url)
        } catch (e: Exception) {
            Result("Couldn't reach nekos.best (${e.message ?: e.javaClass.simpleName}). Check your internet connection.", null)
        }
    }
}

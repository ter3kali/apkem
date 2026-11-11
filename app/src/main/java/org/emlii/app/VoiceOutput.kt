package org.emlii.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Spoken replies via Android's built-in TextToSpeech. There is no reliable, universal
 * "gender" tag across TTS engines/voices, so picking a female voice is best-effort: it looks
 * for a name or feature hint and falls back to the engine's default for the device's language.
 */
class VoiceOutput(ctx: Context) {
    private var tts: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())
    private var onDone: (() -> Unit)? = null
    var ready: Boolean = false
        private set

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                pickFemaleVoice()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { finish() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { finish() }
                    override fun onError(utteranceId: String?, errorCode: Int) { finish() }
                })
            }
        }
    }

    private fun finish() {
        main.post {
            val cb = onDone
            onDone = null
            cb?.invoke()
        }
    }

    private fun pickFemaleVoice() {
        val engine = tts ?: return
        val voices = engine.voices ?: return
        val lang = Locale.getDefault().language
        val sameLang = voices.filter { it.locale.language == lang }
        val female = sameLang.firstOrNull { v ->
            v.name.contains("female", true) ||
                (v.features?.any { f -> f.contains("female", true) } == true)
        } ?: sameLang.firstOrNull()
        female?.let { engine.voice = it }
    }

    /** Strips links and table-ish punctuation so spoken replies don't read out raw data. */
    private fun speakable(text: String): String {
        var t = Regex("https?://\\S+").replace(text, "link")
        t = Regex("[`*_#>|│●▲▼·•=─\\[\\]]+").replace(t, " ")
        return Regex("\\s+").replace(t, " ").trim()
    }

    /** [done] is called on the main thread when the speech has finished (or couldn't start). */
    fun speak(text: String, done: (() -> Unit)? = null) {
        val clean = speakable(text).take(600)
        if (!ready || clean.isBlank()) {
            main.post { done?.invoke() }
            return
        }
        onDone = done
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "emlii")
    }

    fun stop() {
        onDone = null
        tts?.stop()
    }

    fun shutdown() {
        onDone = null
        tts?.stop()
        tts?.shutdown()
    }
}

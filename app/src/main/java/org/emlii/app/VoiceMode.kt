package org.emlii.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// The voice orb's palette, from emlii_gui.py (C_DEEP / C_CORE / C_HI / C_HOT, VBG)
private val VBG = Color(0xFF07040D)
private val C_DEEP = Color(58, 10, 107)
private val C_CORE = Color(184, 75, 255)
private val C_HI = Color(217, 163, 255)
private val C_HOT = Color(255, 110, 235)
private val V_TEXT = Color(0xFFD4CDEA)
private val V_MUTED = Color(0xFF8A82B0)
private val V_BORDER = Color(0xFF2A2240)
private val Mono = FontFamily.Monospace

/**
 * Hands-free voice conversation: listens (Android SpeechRecognizer), sends what it heard to the
 * chat engine, speaks the reply, then listens again.
 */
class VoiceController(
    private val ctx: Context,
    private val vm: ChatViewModel,
    private val tts: VoiceOutput?
) {
    var state by mutableStateOf("idle")          // idle | listening | thinking | speaking
    var status by mutableStateOf("idle")
    var caption by mutableStateOf("")
    var level by mutableStateOf(0f)
    var muted by mutableStateOf(false)
    var speakReplies by mutableStateOf(true)

    private var recognizer: SpeechRecognizer? = null
    private var isOpen = false
    private val handler = Handler(Looper.getMainLooper())

    private fun setState(s: String, text: String) {
        state = s
        status = text
    }

    fun open() {
        isOpen = true
        vm.botListener = { text, spoken -> onBotReply(text, spoken) }
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            caption = "Speech recognition isn't available on this phone."
            setState("idle", "speech input unavailable")
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(listener) }
        say("Hi ${vm.username.value}, I'm listening.")
    }

    fun close() {
        isOpen = false
        vm.botListener = null
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        try { recognizer?.cancel() } catch (e: Exception) { }
        try { recognizer?.destroy() } catch (e: Exception) { }
        recognizer = null
    }

    /** Speak a line of our own (greeting), then go back to listening. */
    private fun say(text: String) {
        caption = text
        speakThenListen(text)
    }

    private fun speakThenListen(spoken: String) {
        if (speakReplies && tts != null) {
            setState("speaking", "speaking")
            tts.speak(spoken) { if (isOpen) listen() }
        } else {
            setState("speaking", "reply is in the chat")
            handler.postDelayed({ if (isOpen) listen() }, 700)
        }
    }

    private fun onBotReply(text: String, spoken: String) {
        if (!isOpen) return
        caption = text.take(180)
        speakThenListen(spoken)
    }

    private fun listen() {
        if (!isOpen) return
        if (muted) {
            setState("idle", "mic muted")
            return
        }
        val r = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }
        setState("listening", "listening")
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            setState("idle", "couldn't start the mic — tap the orb to retry")
        }
    }

    private fun heard(text: String) {
        caption = text
        if (vm.busy.value) {
            listen()
            return
        }
        setState("thinking", "thinking")
        vm.send(text)
        // vm.send() answers synchronously for most things (-> onBotReply). If it produced no
        // reply at all, don't get stuck on "thinking".
        handler.postDelayed({
            if (isOpen && state == "thinking" && !vm.busy.value) listen()
        }, 1500)
    }

    fun tap() {
        when (state) {
            "speaking" -> {
                tts?.stop()
                handler.removeCallbacksAndMessages(null)
                listen()
            }
            "listening" -> {
                try { recognizer?.cancel() } catch (e: Exception) { }
                level = 0f
                setState("idle", "tap the orb to talk")
            }
            "idle" -> listen()
            else -> {}
        }
    }

    fun toggleMute() {
        muted = !muted
        if (muted) {
            try { recognizer?.cancel() } catch (e: Exception) { }
            level = 0f
            if (state == "listening" || state == "idle") setState("idle", "mic muted")
        } else if (state == "idle") {
            listen()
        }
    }

    fun toggleSpeech() {
        speakReplies = !speakReplies
        if (!speakReplies) tts?.stop()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) caption = t
        }

        override fun onResults(results: Bundle?) {
            level = 0f
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim().orEmpty()
            if (text.isBlank()) listen() else heard(text)
        }

        override fun onError(error: Int) {
            if (!isOpen) return
            level = 0f
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    handler.postDelayed({ if (isOpen && state == "listening") listen() }, 300)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    try { recognizer?.cancel() } catch (e: Exception) { }
                    handler.postDelayed({ if (isOpen && state == "listening") listen() }, 700)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    caption = "Microphone permission is needed."
                    setState("idle", "tap the orb to retry")
                }
                else -> setState("idle", "speech error ($error) — tap the orb to retry")
            }
        }
    }
}

// ── The orb: an audio-reactive "ferrofluid" — a sphere of ~12k dots laid out as contour rings,
//    pushed around by layered waves; the colour drifts slowly through the whole spectrum. ──
private const val RINGS = 70
private const val PER = 170

private val BASE: Array<FloatArray> = run {
    val xs = FloatArray(RINGS * PER)
    val ys = FloatArray(RINGS * PER)
    val zs = FloatArray(RINGS * PER)
    val pi = PI.toFloat()
    for (i in 0 until RINGS) {
        val th = pi * (i + 0.5f) / RINGS
        val sTh = sin(th)
        val cTh = cos(th)
        for (j in 0 until PER) {
            val ph = 2f * pi * j / PER
            val idx = i * PER + j
            xs[idx] = sTh * cos(ph)
            ys[idx] = cTh
            zs[idx] = sTh * sin(ph)
        }
    }
    arrayOf(xs, ys, zs)
}

private class OrbAnim {
    var tick by mutableStateOf(0L)
    var level = 0f
    var spin = 1f
    var hue = 270f          // current colour (0..360), drifts continuously
    var morph = 0f          // time for the wave field; runs faster with more energy
    var ang = 0f
    val front = FloatArray(PER * 2)
    val back = FloatArray(PER * 2)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    fun update(dt: Float, state: String, mic: Float) {
        val sm = { k: Float -> 1f - (1f - k).pow(dt * 30f) }
        val tt = morph
        val pulse = (0.5f + 0.5f * sin(tt * 5f)).coerceIn(0f, 1f) * 0.3f
        val target = when (state) {
            "listening" -> max(0.06f, mic)
            "speaking" -> 0.30f + 0.22f * sin(tt * 4.5f) * sin(tt * 1.7f) + pulse
            "thinking" -> 0.20f + 0.10f * sin(tt * 3f)
            else -> 0.03f
        }
        level += (target - level) * sm(0.2f)
        spin += ((if (state == "thinking") 2.6f else 1f) - spin) * sm(0.10f)
        morph += dt * (0.5f + 1.6f * level) * (0.7f + 0.4f * spin)
        ang += dt * 0.22f * spin
        hue = (hue + dt * (16f + 34f * level) * (0.6f + 0.4f * spin)) % 360f
    }
}

private fun DrawScope.drawOrb(a: OrbAnim) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val c = Offset(cx, cy)
    val r = min(size.width, size.height) * 0.30f
    val lv = a.level
    val dens = density
    val amp = 0.16f + 0.60f * lv
    val m = a.morph
    val ca = cos(a.ang)
    val sa = sin(a.ang)
    val tilt = 0.95f
    val ct = cos(tilt)
    val st = sin(tilt)

    // faint glow in the current colour
    val glow = Color.hsv(a.hue, 0.7f, 1f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glow.copy(alpha = 0.10f + 0.18f * lv), Color.Transparent),
            center = c,
            radius = r * 2.2f
        ),
        radius = r * 2.2f,
        center = c
    )

    val canvas = drawContext.canvas.nativeCanvas
    val paint = a.paint
    paint.strokeWidth = 1.7f * dens
    val front = a.front
    val back = a.back
    val bx = BASE[0]
    val by = BASE[1]
    val bz = BASE[2]

    for (i in 0 until RINGS) {
        var nf = 0
        var nb = 0
        val base = i * PER
        for (j in 0 until PER) {
            val x = bx[base + j]
            val y = by[base + j]
            val z = bz[base + j]
            // smooth lobes + a few sharper ferrofluid peaks
            val d = 0.55f * sin(3f * x + 1.3f * m) * sin(2.2f * y - 0.9f * m) +
                0.40f * sin(4f * z + 0.8f * m + 2f * x) +
                0.30f * sin(5f * (x + y) - 1.1f * m)
            val sp = max(0f, sin(6f * x - 4f * z + 1.7f * m) * sin(5f * y + 1.2f * m))
            val rad = max(0.15f, 1f + amp * (d + 1.4f * sp * sp))
            val px0 = x * rad
            val py0 = y * rad
            val pz0 = z * rad
            // spin around the vertical axis, then tilt so the contour rings show
            val xr = px0 * ca + pz0 * sa
            val zr = -px0 * sa + pz0 * ca
            val y2 = py0 * ct - zr * st
            val z2 = py0 * st + zr * ct
            val persp = 1f / (1f - z2 * 0.12f)
            val sx = cx + xr * r * persp
            val sy = cy + y2 * r * persp
            if (z2 >= 0f) {
                front[nf++] = sx
                front[nf++] = sy
            } else {
                back[nb++] = sx
                back[nb++] = sy
            }
        }
        // each ring is a slightly different hue, so colour flows across the surface as it drifts
        val col = Color.hsv((a.hue + i * 1.3f) % 360f, 0.55f, 1f)
        paint.color = col.copy(alpha = 0.30f).toArgb()
        canvas.drawPoints(back, 0, nb, paint)
        paint.color = col.copy(alpha = 0.60f).toArgb()
        canvas.drawPoints(front, 0, nf, paint)
    }
}

@Composable
private fun Orb(ctl: VoiceController, modifier: Modifier) {
    val anim = remember { OrbAnim() }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                last = now
                anim.update(dt, ctl.state, ctl.level)
                anim.tick = now
            }
        }
    }
    Canvas(modifier) {
        val subscribe = anim.tick     // reading it makes the canvas redraw every frame
        if (subscribe >= 0L) drawOrb(anim)
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .border(1.dp, V_BORDER, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = V_MUTED, fontFamily = Mono, fontSize = 12.sp)
    }
}

/** Full-screen voice mode — the GUI's 🎤 orb. */
@Composable
fun VoiceScreen(vm: ChatViewModel, voice: VoiceOutput?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ctl = remember { VoiceController(ctx.applicationContext, vm, voice) }
    DisposableEffect(Unit) {
        ctl.open()
        onDispose { ctl.close() }
    }
    BackHandler { onClose() }

    Column(Modifier.fillMaxSize().background(VBG)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("[EMLII] VOICE", color = C_HI, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Text("android speech", color = V_MUTED, fontFamily = Mono, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            PillButton("✕ close", onClose)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { ctl.tap() }
        ) {
            Orb(ctl, Modifier.fillMaxSize())
        }
        Text(
            ctl.status,
            color = C_HI, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )
        Box(Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                ctl.caption,
                color = V_TEXT, fontFamily = Mono, fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 4
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            PillButton(if (ctl.muted) "unmute mic" else "mute mic") { ctl.toggleMute() }
            PillButton("voice replies: " + if (ctl.speakReplies) "on" else "off") { ctl.toggleSpeech() }
        }
    }
}

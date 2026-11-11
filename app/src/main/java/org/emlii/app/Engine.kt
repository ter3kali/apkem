package org.emlii.app

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * The learning/prediction engine, ported from EMLII.py's memory system:
 * an exact match replies directly; a close-enough match is offered as a
 * guess the person can accept or correct; anything new asks to be taught.
 * This is a fresh, Kotlin-only memory — it does not read or share the
 * Python version's data/mem files.
 */
data class MemEntry(val prompt: String, val response: String)

sealed class EngineResult {
    data class Reply(val text: String) : EngineResult()
    data class Predicted(val text: String, val confidence: Double, val hash: String) : EngineResult()
    data class NeedsTeaching(val hash: String, val prompt: String) : EngineResult()
}

class Engine(dir: File) {
    private val memFile = File(dir, "memory.json")
    private val memory: MutableMap<String, MemEntry> = mutableMapOf()

    init {
        dir.mkdirs()
        load()
    }

    private fun load() {
        if (!memFile.exists()) return
        try {
            val obj = JSONObject(memFile.readText())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val e = obj.getJSONObject(k)
                memory[k] = MemEntry(e.optString("prompt"), e.optString("response"))
            }
        } catch (_: Exception) {
            // a corrupt or missing file just means we start fresh
        }
    }

    private fun save() {
        val obj = JSONObject()
        for ((k, v) in memory) {
            val e = JSONObject()
            e.put("prompt", v.prompt)
            e.put("response", v.response)
            obj.put(k, e)
        }
        try {
            memFile.parentFile?.mkdirs()
            memFile.writeText(obj.toString())
        } catch (_: Exception) {
        }
    }

    private fun hashOf(s: String): String {
        val norm = s.trim().lowercase()
        val digest = MessageDigest.getInstance("MD5").digest(norm.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Word-overlap similarity, 0..1 — simpler than Python's SequenceMatcher but a fair stand-in. */
    private fun similarity(a: String, b: String): Double {
        val wa = a.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        val wb = b.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        if (wa.isEmpty() || wb.isEmpty()) return 0.0
        val inter = wa.intersect(wb).size.toDouble()
        val union = wa.union(wb).size.toDouble()
        return if (union == 0.0) 0.0 else inter / union
    }

    fun process(input: String): EngineResult {
        val h = hashOf(input)
        memory[h]?.let { return EngineResult.Reply(it.response) }

        var bestKey: String? = null
        var bestScore = 0.0
        for ((k, v) in memory) {
            val s = similarity(input, v.prompt)
            if (s > bestScore) {
                bestScore = s
                bestKey = k
            }
        }
        if (bestKey != null && bestScore >= 0.55) {
            val entry = memory[bestKey]!!
            return EngineResult.Predicted(entry.response, bestScore, h)
        }
        return EngineResult.NeedsTeaching(h, input)
    }

    fun learn(hash: String, prompt: String, response: String) {
        memory[hash] = MemEntry(prompt, response)
        save()
    }

    fun correct(hash: String, newResponse: String) {
        val old = memory[hash]
        memory[hash] = MemEntry(old?.prompt ?: "", newResponse)
        save()
    }

    fun entryCount(): Int = memory.size
}

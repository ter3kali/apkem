package org.emlii.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a picture and stores it on the phone (Gallery → Pictures/EMLII). */
object ImageSaver {
    /** Returns where it was saved, or null if the download or save failed. Call off the main thread. */
    fun save(ctx: Context, url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "EMLII-Android/1.0")
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return null

            val last = url.substringBefore('?').substringAfterLast('/')
            val ext = last.substringAfterLast('.', "").lowercase().let {
                if (it in listOf("png", "jpg", "jpeg", "gif", "webp")) it else "jpg"
            }
            val mime = when (ext) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            val base = last.substringBeforeLast('.').ifBlank { "waifu" }.take(40)
            val name = "${base}_${System.currentTimeMillis() % 100000}.$ext"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EMLII")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                val out = resolver.openOutputStream(uri)
                if (out == null) {
                    resolver.delete(uri, null, null)
                    return null
                }
                out.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Pictures/EMLII/$name"
            } else {
                // Android 9 and older: no permission-free gallery write, so use the app's own Pictures folder
                val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: ctx.filesDir, "EMLII")
                dir.mkdirs()
                val f = File(dir, name)
                f.writeBytes(bytes)
                f.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }
}

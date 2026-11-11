package org.emlii.app

import android.content.Context
import java.io.File

/**
 * Where EMLII keeps its files, split into separate folders like the desktop app:
 *   learned/    memory.json — the replies EMLII has learned
 *   tools/      plugin .json files
 *   data/       trading.json etc.
 *   wallpaper/  the chosen wallpaper image
 * They live in the app's external files folder (so a file manager / USB can reach them where the
 * phone allows it), falling back to private storage.
 */
class AppFolders(ctx: Context) {
    val root: File = (ctx.getExternalFilesDir(null) ?: ctx.filesDir).also { it.mkdirs() }
    val learnedDir = File(root, "learned")
    val toolsDir = File(root, "tools")
    val dataDir = File(root, "data")
    val wallpaperDir = File(root, "wallpaper")

    init {
        listOf(learnedDir, toolsDir, dataDir, wallpaperDir).forEach { it.mkdirs() }
        // earlier builds kept everything in <private files>/data — carry it over once
        val old = File(ctx.filesDir, "data")
        migrate(File(old, "memory.json"), File(learnedDir, "memory.json"))
        migrate(File(old, "trading.json"), File(dataDir, "trading.json"))
    }

    private fun migrate(from: File, to: File) {
        if (from.exists() && !to.exists()) {
            try { from.copyTo(to) } catch (_: Exception) { }
        }
    }
}

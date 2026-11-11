package org.emlii.app

import android.content.Context

/** The GUI's emlii_gui_settings.json, as SharedPreferences. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("emlii_ui", Context.MODE_PRIVATE)

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(v) = sp.edit().putString("username", v).apply()

    var theme: String
        get() = sp.getString("theme", "Purple neon") ?: "Purple neon"
        set(v) = sp.edit().putString("theme", v).apply()

    var lastDark: String
        get() = sp.getString("last_dark", "Purple neon") ?: "Purple neon"
        set(v) = sp.edit().putString("last_dark", v).apply()

    var fontSize: Int
        get() = sp.getInt("font_size", 11).coerceIn(8, 20)
        set(v) = sp.edit().putInt("font_size", v).apply()

    var speakOn: Boolean
        get() = sp.getBoolean("speak_on", false)
        set(v) = sp.edit().putBoolean("speak_on", v).apply()

    var bgMode: String
        get() = sp.getString("bg_mode", "solid") ?: "solid"
        set(v) = sp.edit().putString("bg_mode", v).apply()

    var bgColor2: String
        get() = sp.getString("bg_color2", "") ?: ""
        set(v) = sp.edit().putString("bg_color2", v).apply()

    var bgImage: String
        get() = sp.getString("bg_image", "") ?: ""
        set(v) = sp.edit().putString("bg_image", v).apply()

    var bgDim: Int
        get() = sp.getInt("bg_dim", 55).coerceIn(0, 90)
        set(v) = sp.edit().putInt("bg_dim", v).apply()
}

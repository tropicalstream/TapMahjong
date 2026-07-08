package com.tapmahjong

import android.content.Context
import android.os.Build

/** Persistent settings + stats. RayNeo hardware detected by identity, not model (guide gotcha #24). */
class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("tapmahjong", Context.MODE_PRIVATE)

    private val deviceText = listOf(
        Build.MODEL, Build.MANUFACTURER, Build.BRAND, Build.DEVICE, Build.PRODUCT
    ).joinToString(" ").lowercase()

    private val isRayNeoX3 =
        "rayneo" in deviceText || "leiniao" in deviceText || "ffalcon" in deviceText ||
            ("x3" in deviceText && ("tcl" in deviceText || "falcon" in deviceText))

    init {
        if (isRayNeoX3 && !p.getBoolean("rayneoSbsV1", false)) {
            p.edit().putBoolean("sbs", true).putBoolean("rayneoSbsV1", true).apply()
        }
    }

    /** 0..2 -> Easy/Medium/Hard layout (see Layout enum). */
    var difficulty: Int
        get() = p.getInt("difficulty", 1)
        set(v) { p.edit().putInt("difficulty", v.coerceIn(0, 2)).apply() }

    var timerOn: Boolean
        get() = p.getBoolean("timerOn", true)
        set(v) { p.edit().putBoolean("timerOn", v).apply() }

    fun bestTime(layout: Int): Int = p.getInt("best$layout", 0)
    fun setBestTime(layout: Int, sec: Int) { p.edit().putInt("best$layout", sec).apply() }

    var showLegal: Boolean
        get() = p.getBoolean("showLegal", true)
        set(v) { p.edit().putBoolean("showLegal", v).apply() }

    var showCoords: Boolean
        get() = p.getBoolean("showCoords", true)
        set(v) { p.edit().putBoolean("showCoords", v).apply() }

    var soundVolume: Int
        get() = p.getInt("sndVol", 8)
        set(v) { p.edit().putInt("sndVol", v.coerceIn(0, 10)).apply() }

    var swipeSens: Float
        get() = p.getFloat("swipeSens", 1.0f)
        set(v) { p.edit().putFloat("swipeSens", v.coerceIn(0.4f, 2.5f)).apply() }

    var flipVertical: Boolean
        get() = p.getBoolean("flipV", false)
        set(v) { p.edit().putBoolean("flipV", v).apply() }

    var flipHorizontal: Boolean
        get() = p.getBoolean("flipH", false)
        set(v) { p.edit().putBoolean("flipH", v).apply() }

    var safeTap: Boolean
        get() = p.getBoolean("safeTap", true)
        set(v) { p.edit().putBoolean("safeTap", v).apply() }

    var particlesLevel: Int
        get() = p.getInt("particles", 1)
        set(v) { p.edit().putInt("particles", v.coerceIn(0, 2)).apply() }

    var frameCap30: Boolean
        get() = p.getBoolean("cap30", false)
        set(v) { p.edit().putBoolean("cap30", v).apply() }

    var sbs: Boolean
        get() = p.getBoolean("sbs", isRayNeoX3)
        set(v) { p.edit().putBoolean("sbs", v).apply() }

    var wins: Int
        get() = p.getInt("wins", 0)
        set(v) { p.edit().putInt("wins", v).apply() }

    fun resetStats() {
        p.edit().putInt("wins", 0).remove("best0").remove("best1").remove("best2").apply()
    }

    /** Restore every preference to its default (keeps the win record and any saved game). */
    fun resetSettings() {
        p.edit()
            .remove("difficulty").remove("timerOn")
            .remove("showLegal").remove("showCoords").remove("sndVol")
            .remove("swipeSens").remove("flipV").remove("flipH").remove("safeTap")
            .remove("particles").remove("cap30").remove("sbs")
            .apply()
    }

    /** In-progress game, serialized by the engine so a game survives an exit. */
    var savedGame: String?
        get() = p.getString("savedGame", null)
        set(v) { p.edit().apply { if (v == null) remove("savedGame") else putString("savedGame", v) }.apply() }

    fun clearSavedGame() { p.edit().remove("savedGame").apply() }
}


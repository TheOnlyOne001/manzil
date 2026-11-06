package app.manzil.system

import android.content.SharedPreferences
import androidx.core.content.edit

/** Small user preferences that are not journey data. */
class AppSettings(private val prefs: SharedPreferences) {
    /** Off by default: steps are only spoken when the user turns this on or taps "Read aloud". */
    var speakSteps: Boolean
        get() = prefs.getBoolean(KEY_SPEAK_STEPS, false)
        set(value) = prefs.edit { putBoolean(KEY_SPEAK_STEPS, value) }

    var voiceName: String?
        get() = prefs.getString(KEY_VOICE, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_VOICE) else putString(KEY_VOICE, value) }

    fun bubblePosition(): Pair<Int, Int>? =
        if (prefs.contains(KEY_BUBBLE_X)) prefs.getInt(KEY_BUBBLE_X, 0) to prefs.getInt(KEY_BUBBLE_Y, 0) else null

    fun saveBubblePosition(x: Int, y: Int) = prefs.edit {
        putInt(KEY_BUBBLE_X, x)
        putInt(KEY_BUBBLE_Y, y)
    }

    private companion object {
        const val KEY_SPEAK_STEPS = "speak_steps"
        const val KEY_VOICE = "tts_voice_name"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"
    }
}

package app.manzil.data

import android.content.SharedPreferences

/**
 * Minimal durable string store. All [write] changes are applied atomically and must be on disk
 * before it returns true, so progress survives a process kill immediately after a command.
 */
interface KeyValueStore {
    fun read(key: String): String?

    /** Apply all changes (null value = remove) atomically. Returns false if not persisted. */
    fun write(changes: Map<String, String?>): Boolean
}

/**
 * SharedPreferences-backed store for this slice. Uses commit() (synchronous); callers run it off
 * the main thread. Planned migration to Room is described in docs/ANDROID_IMPLEMENTATION.md.
 */
class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun read(key: String): String? = prefs.getString(key, null)

    override fun write(changes: Map<String, String?>): Boolean {
        val editor = prefs.edit()
        changes.forEach { (key, value) ->
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        return editor.commit()
    }
}

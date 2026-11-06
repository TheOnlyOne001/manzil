package app.manzil.offline.location

import android.content.Context
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local GPS shared by preview and active guidance. Nothing automatically resumes on disk. */
class OfflineLocationSession private constructor(context: Context) {
    val gps = OfflineGpsSource(context.applicationContext)
    private val owners = GpsOwnership()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Pair once per visible Activity lifecycle, including configuration changes. */
    fun acquirePreview() { mainThread(); owners.acquirePreview(); applyOwnership() }
    fun releasePreview() { mainThread(); owners.releasePreview(); applyOwnership() }
    /** After a runtime permission result; does not add another owner. */
    fun refresh() { mainThread(); applyOwnership() }

    internal fun serviceStarted() { mainThread(); owners.setService(true); _error.value = null; applyOwnership() }
    internal fun serviceStopped() { mainThread(); owners.setService(false); applyOwnership() }
    internal fun reportError(message: String) { _error.value = message }

    private fun applyOwnership() { if (owners.shouldRun) gps.start() else gps.stop() }
    private fun mainThread() { check(Looper.myLooper() == Looper.getMainLooper()) }

    companion object {
        @Volatile private var instance: OfflineLocationSession? = null
        fun get(context: Context): OfflineLocationSession = instance ?: synchronized(this) {
            instance ?: OfflineLocationSession(context).also { instance = it }
        }
    }
}

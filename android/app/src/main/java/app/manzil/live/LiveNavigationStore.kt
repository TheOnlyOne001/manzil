package app.manzil.live

import android.content.Context
import app.manzil.data.KeyValueStore
import app.manzil.data.SharedPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Process-wide observable holder for the last Google Maps notification observation, shared by
 * [MapsNotificationListener] (writer) and the overlay / app (readers). Only [MapsObservation]s —
 * which can only come from the Maps package — are ever stored or persisted.
 *
 * Disk writes go through [persistExecutor] (single thread in production, so writes stay ordered
 * and never block the listener's main-thread callbacks).
 */
class LiveNavigationStore internal constructor(
    private val kv: KeyValueStore,
    private val clock: () -> Long,
    private val persistExecutor: Executor,
) {
    private val lock = Any()
    private val _state = MutableStateFlow(LiveNavigationState(saved = SavedObservationCodec.decode(kv.read(KEY_SAVED))))
    val state: StateFlow<LiveNavigationState> = _state.asStateFlow()
    private var lastPersistedReceivedAtMs: Long = _state.value.saved?.receivedAtMs ?: 0L

    fun onPosted(observation: MapsObservation) = synchronized(lock) {
        val current = _state.value
        val saved = current.saved
        // An ongoing-only Maps notification never displaces a live navigation-category one.
        if (saved != null && current.observedThisSession && saved.status == ObservationStatus.ACTIVE &&
            saved.observation.key != observation.key && saved.observation.isNavigationCategory &&
            !observation.isNavigationCategory
        ) {
            return@synchronized
        }
        val now = clock()
        val sameActive = saved != null && saved.status == ObservationStatus.ACTIVE &&
            saved.observation.key == observation.key
        val contentChanged = saved == null || !sameActive || !saved.observation.sameContentAs(observation)
        val next = SavedObservation(
            observation = observation,
            status = ObservationStatus.ACTIVE,
            receivedAtMs = now,
            statusChangedAtMs = if (sameActive) saved!!.statusChangedAtMs else now,
        )
        _state.value = current.copy(saved = next, observedThisSession = true)
        // Persist on change; otherwise refresh the on-disk timestamp at most once a minute.
        if (contentChanged || now - lastPersistedReceivedAtMs >= TIMESTAMP_PERSIST_INTERVAL_MS) persist(next)
    }

    fun onRemoved(key: String?) = synchronized(lock) {
        val current = _state.value
        val saved = current.saved ?: return@synchronized
        if (key == null || saved.observation.key != key || saved.status != ObservationStatus.ACTIVE) return@synchronized
        markRemoved(current, saved)
    }

    /** Reconcile with what the system is actually showing right now (Maps candidates only). */
    fun onListenerConnected(activeMapsCandidates: List<MapsObservation>) = synchronized(lock) {
        _state.value = _state.value.copy(listenerConnected = true)
        val current = _state.value
        val saved = current.saved
        // The saved notification is no longer on screen: it ended while we were not listening.
        if (saved != null && saved.status == ObservationStatus.ACTIVE &&
            activeMapsCandidates.none { it.key == saved.observation.key }
        ) {
            markRemoved(current, saved)
        }
        MapsNotificationFilter.best(activeMapsCandidates)?.let { onPosted(it) }
    }

    fun onListenerDisconnected() = synchronized(lock) {
        _state.value = _state.value.copy(listenerConnected = false)
    }

    /** User-initiated: forget the saved observation (memory and disk). */
    fun clear() = synchronized(lock) {
        _state.value = _state.value.copy(saved = null, observedThisSession = false)
        lastPersistedReceivedAtMs = 0L
        persistExecutor.execute { kv.write(mapOf(KEY_SAVED to null)) }
    }

    private fun markRemoved(current: LiveNavigationState, saved: SavedObservation) {
        val next = saved.copy(status = ObservationStatus.REMOVED, statusChangedAtMs = clock())
        _state.value = current.copy(saved = next)
        persist(next)
    }

    private fun persist(saved: SavedObservation) {
        lastPersistedReceivedAtMs = saved.receivedAtMs
        val json = SavedObservationCodec.encode(saved)
        persistExecutor.execute { kv.write(mapOf(KEY_SAVED to json)) }
    }

    companion object {
        internal const val PREFS_NAME = "manzil_live_observation"
        internal const val KEY_SAVED = "saved_maps_observation"
        internal const val TIMESTAMP_PERSIST_INTERVAL_MS = 60_000L

        @Volatile
        private var instance: LiveNavigationStore? = null

        fun get(context: Context): LiveNavigationStore =
            instance ?: synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext
                    val writer = Executors.newSingleThreadExecutor { r ->
                        Thread(r, "manzil-live-store").apply { isDaemon = true }
                    }
                    LiveNavigationStore(
                        kv = SharedPreferencesStore(app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)),
                        clock = System::currentTimeMillis,
                        persistExecutor = writer,
                    ).also { instance = it }
                }
            }
    }
}

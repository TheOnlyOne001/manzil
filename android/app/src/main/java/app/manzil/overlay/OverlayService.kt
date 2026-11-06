package app.manzil.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.manzil.MainActivity
import app.manzil.R
import app.manzil.agent.CommandEffect
import app.manzil.agent.LocalCommand
import app.manzil.appGraph
import app.manzil.live.LiveFeedback
import app.manzil.live.LiveFreshness
import app.manzil.live.LiveNavigationRepository
import app.manzil.live.LiveNavigationSnapshot
import app.manzil.model.ProgressStatus
import app.manzil.model.StoredState
import app.manzil.model.TimeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Foreground service (type specialUse) that owns the optional panda bubble. It is started only
 * from the visible app after the user grants "Display over other apps", never restarts itself
 * (START_NOT_STICKY), and does no capture, location, network or microphone work.
 */
class OverlayService : Service() {

    companion object {
        private const val ACTION_START = "app.manzil.overlay.START"
        private const val ACTION_STOP = "app.manzil.overlay.STOP"
        private const val CHANNEL_ID = "companion_bubble"
        private const val NOTIFICATION_ID = 41

        /** Optional String extra on the start intent selecting [MODE_SAMPLE] (default) or [MODE_LIVE]. */
        const val EXTRA_MODE = "app.manzil.overlay.EXTRA_MODE"

        /** Saved/sample journey with Next/Repeat (original behaviour). */
        const val MODE_SAMPLE = "sample"

        /** Real Google Maps notification observation only; no sample journey, Next disabled. */
        const val MODE_LIVE = "live"

        private const val LIVE_REFRESH_MS = 30_000L

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _mode = MutableStateFlow<String?>(null)

        /** Mode of the running bubble, or null when not running. */
        val mode: StateFlow<String?> = _mode.asStateFlow()

        /**
         * Call only from a resumed, visible activity. Starting again while running switches mode
         * in place. Existing callers keep sample mode.
         */
        fun start(context: Context, mode: String = MODE_SAMPLE) {
            val intent = Intent(context, OverlayService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, if (mode == MODE_LIVE) MODE_LIVE else MODE_SAMPLE)
            ContextCompat.startForegroundService(context, intent)
        }

        fun startLive(context: Context) = start(context, MODE_LIVE)

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: CompanionOverlay? = null
    private var currentMode: String = MODE_SAMPLE
    private var renderJob: Job? = null
    private var lastLiveFeedback: String? = null

    private val liveRepository by lazy { LiveNavigationRepository.get(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val requestedMode = when (intent?.getStringExtra(EXTRA_MODE)) {
            MODE_LIVE -> MODE_LIVE
            MODE_SAMPLE -> MODE_SAMPLE
            else -> if (overlay != null) currentMode else MODE_SAMPLE
        }
        try {
            promoteToForeground(requestedMode)
        } catch (e: RuntimeException) {
            // e.g. ForegroundServiceStartNotAllowedException if somehow started from background.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlay == null) attachOverlay()
        if (overlay == null) return START_NOT_STICKY // attach failed and stopSelf() was called
        applyMode(requestedMode)
        _running.value = true
        return START_NOT_STICKY
    }

    private fun attachOverlay() {
        val graph = appGraph
        val created = CompanionOverlay(this, graph.settings, object : CompanionOverlay.Actions {
            override fun onNext() {
                if (currentMode == MODE_LIVE) overlay?.showMessage(getString(R.string.live_next_unavailable))
                else perform(LocalCommand.NextStep)
            }
            override fun onRepeat() {
                if (currentMode == MODE_LIVE) repeatLive() else perform(LocalCommand.RepeatInstruction)
            }
            override fun onOpenApp() {
                overlay?.collapseIfExpanded()
                val open = Intent(this@OverlayService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // Live mode just returns to the app; it must not open the sample journey.
                if (currentMode != MODE_LIVE) open.putExtra(MainActivity.EXTRA_OPEN_JOURNEY, true)
                startActivity(open)
            }
            override fun onClose() = stopSelf()
        })
        try {
            created.show()
        } catch (e: RuntimeException) {
            // WindowManager.BadTokenException / permission revoked between check and add.
            stopSelf()
            return
        }
        overlay = created
    }

    /** (Re)starts the render pipeline for [mode]; no-op if already rendering that mode. */
    private fun applyMode(mode: String) {
        if (mode == currentMode && renderJob?.isActive == true) return
        renderJob?.cancel()
        currentMode = mode
        _mode.value = mode
        lastLiveFeedback = null
        overlay?.showMessage(null)
        renderJob = if (mode == MODE_LIVE) {
            liveRepository.requestRebind()
            scope.launch {
                val ticker = flow {
                    while (true) {
                        emit(Unit)
                        delay(LIVE_REFRESH_MS) // re-evaluates staleness and access status
                    }
                }
                combine(liveRepository.state, ticker) { _, _ -> liveRepository.snapshot() }
                    .collect { renderLive(it) }
            }
        } else {
            val controller = appGraph.controller
            scope.launch {
                controller.load()
                controller.state.collect { state -> overlay?.render(modelFor(state)) }
            }
        }
    }

    private fun clock(ms: Long?): String =
        if (ms == null || ms <= 0L) "--:--" else TimeText.clock(ms, ZoneId.systemDefault())

    private fun renderLive(snapshot: LiveNavigationSnapshot) {
        val target = overlay ?: return
        val observed = snapshot.displayText
        val observedAt = clock(snapshot.observation?.sourcePostedAtMs?.takeIf { it > 0L } ?: snapshot.receivedAtMs)
        val model = when {
            observed == null -> OverlayModel(
                hasJourney = false,
                stepLabel = getString(R.string.live_label_waiting),
                title = getString(R.string.live_title_waiting),
                status = getString(R.string.live_status_none),
                canGoNext = false,
            )
            else -> OverlayModel(
                hasJourney = true,
                stepLabel = when (snapshot.freshness) {
                    LiveFreshness.CURRENT -> getString(R.string.live_label_current, observedAt)
                    LiveFreshness.REMOVED -> getString(R.string.live_label_removed, clock(snapshot.statusChangedAtMs))
                    else -> getString(R.string.live_label_stale, observedAt)
                },
                title = observed,
                isCurrent = snapshot.freshness == LiveFreshness.CURRENT,
                status = getString(
                    if (snapshot.freshness == LiveFreshness.CURRENT) R.string.live_status_current else R.string.live_status_saved,
                    observedAt,
                ),
                canGoNext = false,
            )
        }
        target.render(model)
        val feedback = when (snapshot.feedback) {
            LiveFeedback.NONE -> null
            LiveFeedback.ACCESS_OFF -> getString(R.string.live_feedback_access_off)
            LiveFeedback.NOT_CONNECTED -> getString(R.string.live_feedback_not_connected)
            LiveFeedback.WAITING -> getString(R.string.live_feedback_waiting)
            LiveFeedback.STALE -> getString(R.string.live_feedback_stale)
            LiveFeedback.REMOVED -> getString(R.string.live_feedback_removed, clock(snapshot.statusChangedAtMs))
        }
        // Only push feedback when it changes, so a Repeat message isn't wiped by the 30 s tick.
        if (feedback != lastLiveFeedback) {
            lastLiveFeedback = feedback
            target.showMessage(feedback)
        }
    }

    /** Repeat in live mode: only the text Google Maps actually provided, labelled if not current. */
    private fun repeatLive() {
        val snapshot = liveRepository.snapshot()
        val observed = snapshot.spokenText
        if (observed == null) {
            overlay?.showMessage(getString(R.string.live_repeat_nothing))
            return
        }
        val text = if (snapshot.freshness == LiveFreshness.CURRENT) observed else getString(R.string.live_repeat_saved, observed)
        overlay?.showMessage(text)
        appGraph.speech.start()
        if (!appGraph.speech.speak(text)) overlay?.showMessage("Voice is not ready. $text")
    }

    private fun perform(command: LocalCommand) {
        scope.launch {
            val outcome = appGraph.controller.execute(command)
            overlay?.showMessage(if (outcome.success && command == LocalCommand.NextStep) null else outcome.message)
            val effect = outcome.effect
            if (effect is CommandEffect.Speak && appGraph.settings.speakSteps) {
                appGraph.speech.speak(effect.text)
            }
        }
    }

    private fun modelFor(state: StoredState): OverlayModel {
        val saved = state.activeJourney
        val progress = state.progress
        if (saved == null || progress == null) {
            return OverlayModel(
                hasJourney = false,
                stepLabel = getString(R.string.overlay_no_journey_label),
                title = getString(R.string.overlay_no_journey_title),
                status = getString(R.string.overlay_status_none),
                canGoNext = false,
            )
        }
        val steps = saved.journey.steps
        val index = progress.stepIndex.coerceIn(0, steps.lastIndex)
        val finished = progress.status == ProgressStatus.FINISHED
        val savedClock = TimeText.clock(saved.savedAtEpochMs, ZoneId.systemDefault())
        val status = getString(
            if (saved.journey.isSample) R.string.overlay_status_sample else R.string.overlay_status_saved,
            savedClock,
        )
        return OverlayModel(
            hasJourney = true,
            stepLabel = if (finished) {
                getString(R.string.overlay_finished_label)
            } else {
                getString(R.string.step_of, index + 1, steps.size).uppercase()
            },
            title = steps[index].title,
            status = status,
            canGoNext = !finished && index < steps.lastIndex,
        )
    }

    private fun promoteToForeground(mode: String) {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                    description = getString(R.string.notif_channel_description)
                    setShowBadge(false)
                },
            )
        }
        val notification = buildNotification(openJourney = mode != MODE_LIVE)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(openJourney: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_JOURNEY, openJourney),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_manzil)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.notif_stop), stop)
            .build()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigurationChanged()
    }

    override fun onDestroy() {
        renderJob?.cancel()
        overlay?.remove()
        overlay = null
        scope.cancel()
        _running.value = false
        _mode.value = null
        super.onDestroy()
    }
}

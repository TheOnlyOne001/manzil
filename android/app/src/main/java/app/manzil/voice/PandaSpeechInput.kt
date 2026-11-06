package app.manzil.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Push-to-talk speech input for the panda voice sheet.
 *
 * Uses the phone's currently installed/default [SpeechRecognizer] service. It makes no offline
 * promise: whether recognition runs on-device or needs a network is decided by that service, not
 * by Manzil. No API keys, no cloud calls of our own.
 *
 * Contract for the caller (the Compose voice sheet owned by the root UI):
 * - Call [start] only after RECORD_AUDIO has been granted. This class never requests permission.
 * - One [start] = one utterance. There is no auto-listen, restart or loop.
 * - Each session ends with exactly one of: [onResult] with the single best non-blank transcript,
 *   or a terminal [onState] message (see [TERMINAL_STATES]). [STATE_STARTING], [STATE_LISTENING]
 *   and [STATE_UNDERSTANDING] are progress states.
 * - [stop] cancels the session and emits nothing afterwards; late results are discarded.
 * - [destroy] releases the recognizer; the instance cannot be restarted.
 * - Callbacks are delivered on the main thread. Calls from other threads are posted to main.
 */
class PandaSpeechInput(
    context: Context,
    private val onState: (String) -> Unit,
    private val onResult: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    // All fields below are touched on the main thread only.
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var destroyed = false
    private var generation = 0

    /** Begins one listening session in [languageTag] (one of [SUPPORTED_LANGUAGE_TAGS]). */
    fun start(languageTag: String) = onMain { startOnMain(languageTag) }

    /** Cancels any session. No result or state is delivered for it afterwards. */
    fun stop() = onMain { stopOnMain() }

    /** Stops and permanently releases the recognizer. Call from the owner's onDispose/onDestroy. */
    fun destroy() = onMain {
        stopOnMain()
        destroyed = true
    }

    private fun startOnMain(languageTag: String) {
        if (destroyed) return
        stopOnMain() // a new press replaces any previous session

        if (languageTag !in SUPPORTED_LANGUAGE_TAGS) {
            onState(STATE_LANGUAGE_UNAVAILABLE)
            return
        }
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onState(STATE_PERMISSION_NEEDED)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onState(STATE_UNAVAILABLE)
            return
        }

        val session = ++generation
        val created = try {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } catch (e: RuntimeException) {
            null
        }
        if (created == null) {
            onState(STATE_UNAVAILABLE)
            return
        }
        recognizer = created
        active = true
        created.setRecognitionListener(Listener(session))

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
        onState(STATE_STARTING)
        try {
            created.startListening(intent)
        } catch (e: RuntimeException) {
            finish(session, STATE_UNAVAILABLE)
        }
    }

    private fun stopOnMain() {
        active = false
        generation++ // invalidates any listener still holding the old session id
        release()
    }

    private fun release() {
        val r = recognizer ?: return
        recognizer = null
        destroyQuietly(r)
    }

    private fun destroyQuietly(r: SpeechRecognizer) {
        try {
            r.cancel()
        } catch (ignored: RuntimeException) {
        }
        try {
            r.destroy()
        } catch (ignored: RuntimeException) {
        }
    }

    private fun isCurrent(session: Int) = active && !destroyed && session == generation

    /** Ends [session] with a terminal state message (or nothing if [state] is null). */
    private fun finish(session: Int, state: String?) {
        if (!isCurrent(session)) return
        active = false
        // Detach now, destroy after the callback returns (not from inside the listener). Only this
        // session's recognizer is released, never one created by a later start().
        val r = recognizer
        recognizer = null
        if (r != null) mainHandler.post { destroyQuietly(r) }
        if (state != null) onState(state)
    }

    private inner class Listener(private val session: Int) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (isCurrent(session)) onState(STATE_LISTENING)
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (isCurrent(session)) onState(STATE_UNDERSTANDING)
        }

        override fun onError(error: Int) = finish(session, messageFor(error))

        override fun onResults(results: Bundle?) {
            if (!isCurrent(session)) return
            val best = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
            if (best == null) {
                finish(session, STATE_NO_SPEECH)
            } else {
                finish(session, null)
                onResult(best)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    companion object {
        /** Language tags the voice sheet may pass. The installed service may still reject one. */
        val SUPPORTED_LANGUAGE_TAGS: Set<String> = setOf("en-IN", "hi-IN", "ta-IN", "te-IN")

        const val STATE_STARTING = "Starting microphone…"
        const val STATE_LISTENING = "Listening…"
        const val STATE_UNDERSTANDING = "Understanding…"
        const val STATE_NO_SPEECH = "No speech heard. Try again."
        const val STATE_UNAVAILABLE = "Speech recognition unavailable."
        const val STATE_PERMISSION_NEEDED = "Microphone permission needed."
        const val STATE_MIC_PROBLEM = "Microphone problem. Try again."
        const val STATE_NETWORK = "Speech service couldn't be reached. Try again."
        const val STATE_BUSY = "Speech recognition is busy. Try again."
        const val STATE_TOO_MANY = "Speech service is busy. Try again later."
        const val STATE_LANGUAGE_UNAVAILABLE = "This language isn't available for speech input."
        const val STATE_INTERRUPTED = "Speech input stopped. Try again."

        val TERMINAL_STATES: Set<String> = setOf(
            STATE_NO_SPEECH, STATE_UNAVAILABLE, STATE_PERMISSION_NEEDED, STATE_MIC_PROBLEM,
            STATE_NETWORK, STATE_BUSY, STATE_TOO_MANY, STATE_LANGUAGE_UNAVAILABLE, STATE_INTERRUPTED,
        )

        // API 31+ error codes, as literals so no InlinedApi lint on minSdk 26.
        private const val ERROR_TOO_MANY_REQUESTS = 10
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13

        private fun messageFor(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> STATE_NO_SPEECH
            SpeechRecognizer.ERROR_AUDIO -> STATE_MIC_PROBLEM
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> STATE_PERMISSION_NEEDED
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            ERROR_SERVER_DISCONNECTED -> STATE_NETWORK
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> STATE_BUSY
            ERROR_TOO_MANY_REQUESTS -> STATE_TOO_MANY
            ERROR_LANGUAGE_NOT_SUPPORTED,
            ERROR_LANGUAGE_UNAVAILABLE -> STATE_LANGUAGE_UNAVAILABLE
            SpeechRecognizer.ERROR_CLIENT -> STATE_INTERRUPTED
            else -> STATE_UNAVAILABLE
        }
    }
}

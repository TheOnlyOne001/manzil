package app.manzil.system

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class VoiceOption(
    val name: String,
    val label: String,
    val languageTag: String,
)

sealed interface SpeechState {
    data object NotStarted : SpeechState
    data object Initializing : SpeechState

    /** No engine, engine error, or no installed voice that works without a network. */
    data class Unavailable(val reason: Reason) : SpeechState {
        enum class Reason { NO_ENGINE, NO_OFFLINE_VOICE }
    }

    data class Ready(val voices: List<VoiceOption>, val selected: VoiceOption) : SpeechState
}

/**
 * Optional read-aloud using the phone's installed TextToSpeech engine. Only voices that report
 * networkConnectionRequired=false and are not flagged "notInstalled" are offered. That is the
 * engine's own claim; it is re-tested on the device in airplane mode, not assumed.
 * This class does NOT do speech recognition.
 */
class SpeechOutput(context: Context, private val settings: AppSettings) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var voicesByName: Map<String, Voice> = emptyMap()
    private var utteranceCounter = 0L

    private val _state = MutableStateFlow<SpeechState>(SpeechState.NotStarted)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    /** Binds to the default engine once. Safe to call repeatedly. Main thread. */
    fun start() {
        if (tts != null) return
        _state.value = SpeechState.Initializing
        tts = TextToSpeech(appContext) { status -> onInit(status) }
    }

    private fun onInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            _state.value = SpeechState.Unavailable(SpeechState.Unavailable.Reason.NO_ENGINE)
            return
        }
        val offline = try {
            engine.voices.orEmpty().filter { voice ->
                !voice.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty()
            }
        } catch (e: RuntimeException) {
            emptyList()
        }
        if (offline.isEmpty()) {
            _state.value = SpeechState.Unavailable(SpeechState.Unavailable.Reason.NO_OFFLINE_VOICE)
            return
        }
        voicesByName = offline.associateBy { it.name }
        val options = offline
            .sortedWith(compareBy<Voice>({ rank(it.locale) }, { it.locale.getDisplayName(Locale.ENGLISH) }, { it.name }))
            .map { VoiceOption(it.name, labelFor(it), it.locale.toLanguageTag()) }
        val selected = options.firstOrNull { it.name == settings.voiceName } ?: options.first()
        applyVoice(selected)
        _state.value = SpeechState.Ready(options, selected)
    }

    private fun rank(locale: Locale): Int = when {
        locale.language == "en" && locale.country == "IN" -> 0
        locale.language == "ta" -> 1
        locale.language == "en" -> 2
        else -> 3
    }

    private fun labelFor(voice: Voice): String = voice.locale.getDisplayName(Locale.ENGLISH)

    private fun applyVoice(option: VoiceOption) {
        val voice = voicesByName[option.name] ?: return
        tts?.setVoice(voice)
    }

    fun selectVoice(name: String) {
        val ready = _state.value as? SpeechState.Ready ?: return
        val option = ready.voices.firstOrNull { it.name == name } ?: return
        settings.voiceName = name
        applyVoice(option)
        _state.value = ready.copy(selected = option)
    }

    /** Returns true only if the engine accepted the utterance. */
    fun speak(text: String): Boolean {
        val engine = tts ?: return false
        if (_state.value !is SpeechState.Ready) return false
        utteranceCounter += 1
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "manzil-$utteranceCounter") == TextToSpeech.SUCCESS
    }

    fun stop() {
        tts?.stop()
    }
}

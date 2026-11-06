package app.manzil.voice
import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Foreground reply playback using installed phone voices; no claim of cloud-native audio. */
class AgentSpeaker(context:Context) {
 private var ready=false
 private var closed=false
 private val engine=TextToSpeech(context.applicationContext){ready=it==TextToSpeech.SUCCESS && !closed}
 fun speak(text:String,language:String):Boolean {
  if(!ready || closed)return false
  if(engine.setLanguage(Locale.forLanguageTag(language))<TextToSpeech.LANG_AVAILABLE)return false
  return engine.speak(text,TextToSpeech.QUEUE_FLUSH,null,"panda-reply")==TextToSpeech.SUCCESS
 }
 fun stop(){engine.stop()}
 fun destroy(){closed=true;ready=false;engine.stop();engine.shutdown()}
}

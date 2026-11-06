package app.manzil.voice

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.manzil.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AgentMessage(val role:String,val text:String)
data class AgentContext(val destination:String?,val layer:String,val routeSummary:String?)
data class AgentReply(val reply:String,val action:String,val destinationQuery:String?,val layer:String?)
data class AgentState(val messages:List<AgentMessage> = emptyList(),val busy:Boolean=false,val error:String?=null,val pending:AgentReply?=null,val language:String="en-IN",val answer:String?=null)

class LivingAgentClient(private val appContext:Context) {
    suspend fun turn(text:String,language:String,history:List<AgentMessage>,context:AgentContext):AgentReply=withContext(Dispatchers.IO) {
        if(!BuildConfig.AGENT_API_URL.startsWith("https://"))throw IllegalStateException("Panda’s conversation service is not configured.")
        val token=java.io.File(appContext.filesDir,"agent-access-token").takeIf{it.isFile}?.readText()?.trim()
        if(token.isNullOrBlank())throw IllegalStateException("Panda needs its device connection set up. Your maps still work.")
        val body=JSONObject().put("text",text).put("language",language)
            .put("history",JSONArray().apply{history.takeLast(8).forEach{put(JSONObject().put("role",it.role).put("text",it.text))}})
            .put("context",JSONObject().put("destination",context.destination?:JSONObject.NULL).put("layer",context.layer).put("route_summary",context.routeSummary?:JSONObject.NULL))
        val connection=URL(BuildConfig.AGENT_API_URL.trimEnd('/')+"/api/v1/agent/turn").openConnection() as HttpURLConnection
        try {
            connection.requestMethod="POST";connection.connectTimeout=6000;connection.readTimeout=25000;connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json")
            connection.instanceFollowRedirects=false
            connection.setRequestProperty("X-Manzil-Device-Token",token)
            connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            if(connection.responseCode!=200)throw IllegalStateException("Panda couldn’t reach its conversation service. Your map still works. Try again.")
            val raw=connection.inputStream.use{stream-> val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(1024);while(out.size()<=32768){val n=stream.read(buffer);if(n<0)break;out.write(buffer,0,n)};out.toByteArray()}
            require(raw.size<=32768)
            val json=JSONObject(raw.toString(Charsets.UTF_8))
            val action=json.getString("action")
            require(action in setOf("plan_route","show_layer","recenter","route_summary","none","find_nearby","journey_brief","emergency_help","stop_guidance"))
            val layer=json.optString("layer").takeIf{it in setOf("night","flood","map")}
            val query=if(json.isNull("destination_query"))null else json.getString("destination_query").take(200).takeIf{it.isNotBlank()}
            require(json.optString("provider")=="gemini")
            require(action!="plan_route" || query!=null)
            require(action!="show_layer" || layer!=null)
            require(action!="find_nearby" || query!=null)
            ensureActive()
            AgentReply(json.getString("reply").take(1200),action,query,layer)
        } finally { connection.disconnect() }
    }
}

class LivingAgentViewModel(application:Application):AndroidViewModel(application) {
    private val _state=MutableStateFlow(AgentState());val state=_state.asStateFlow()
    private val client=LivingAgentClient(application)
    private var job:Job?=null
    fun language(tag:String){if(tag in PandaSpeechInput.SUPPORTED_LANGUAGE_TAGS)_state.value=_state.value.copy(language=tag)}
    fun ask(text:String,context:AgentContext) {
        val query=text.trim().take(1000);if(query.isBlank() || _state.value.busy)return
        val before=_state.value
        _state.value=before.copy(messages=(before.messages+AgentMessage("user",query)).takeLast(8),busy=true,error=null,pending=null,answer=null)
        job=viewModelScope.launch {
            try {
                val reply=client.turn(query,before.language,before.messages,context)
                ensureActive()
                _state.value=_state.value.copy(busy=false,messages=(_state.value.messages+AgentMessage("assistant",reply.reply)).takeLast(8),pending=reply,answer=reply.reply)
            } catch(e:CancellationException){throw e}
            catch(e:Exception){_state.value=_state.value.copy(busy=false,error=if(e is IllegalStateException)e.message else "Panda is offline right now. You can still search and use saved maps.")}
        }
    }
    fun applied(message:String?=null){_state.value=_state.value.copy(pending=null,answer=message?:_state.value.answer,messages=if(message==null)_state.value.messages else (_state.value.messages+AgentMessage("assistant",message)).takeLast(8))}
    fun cancel(){job?.cancel();_state.value=_state.value.copy(busy=false,pending=null)}
}

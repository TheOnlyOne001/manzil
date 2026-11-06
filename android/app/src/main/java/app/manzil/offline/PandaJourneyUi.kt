package app.manzil.offline

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.manzil.R
import app.manzil.ui.components.*
import app.manzil.voice.PandaSpeechInput
import kotlinx.coroutines.delay

private val Ink = Color(0xFF142D4E)
private val Bamboo = Color(0xFF087E73)

/** A segmented bamboo shoot: indeterminate activity, never a fake percentage. */
@Composable
internal fun BambooLoading(label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduced = remember { isReducedMotion(context) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(reduced) { if (!reduced) while (true) { delay(260); frame = (frame + 1) % 5 } }
    Row(modifier.semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(28.dp, 32.dp)) {
            repeat(4) { i ->
                val y = size.height - (i + 1) * size.height / 5
                val alpha = if (reduced || i <= frame) 1f else .22f
                drawLine(Bamboo.copy(alpha = alpha), Offset(size.width * .46f,y), Offset(size.width * .46f,y-size.height*.14f), 3.dp.toPx(), StrokeCap.Round)
                if (i == 1 || i == 2) drawLine(Bamboo.copy(alpha = alpha),Offset(size.width*.46f,y),Offset(size.width*(if(i==1).72f else .18f),y-size.height*.12f),2.dp.toPx(),StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color=Bamboo, fontSize=13.sp, lineHeight=18.sp)
    }
}

@Composable
internal fun JourneyWelcome(loading: Boolean, savedDestination: String?, onSearch:()->Unit, onVoice:()->Unit, onResume:()->Unit, onNight:()->Unit, onFlood:()->Unit) {
    Surface(shape=RoundedCornerShape(28.dp), color=Color.White, shadowElevation=4.dp) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                PandaMark(52.dp,ackKey=1)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Where shall we go?",fontSize=23.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold,color=Ink)
                    Text("A little help along the way.",fontSize=13.sp,color=Color(0xFF62737B),modifier=Modifier.padding(top=3.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().background(Color(0xFFF0F5F3),RoundedCornerShape(28.dp)),verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(onClick=onSearch).padding(start=18.dp,top=18.dp,bottom=18.dp),verticalAlignment=Alignment.CenterVertically) {
                    MzIcon(R.drawable.ic_search,null,tint=Bamboo,size=21.dp)
                    Spacer(Modifier.width(10.dp));Text("Find a place or street",fontSize=15.sp,color=Ink)
                }
                FilledIconButton(onClick=onVoice,modifier=Modifier.padding(end=5.dp).size(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Bamboo,contentColor=Color.White)) { MzIcon(R.drawable.ic_mic,"Talk to Panda") }
            }
            Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=onNight) { MzIcon(R.drawable.ic_layer_night,null,tint=Bamboo,size=18.dp);Spacer(Modifier.width(7.dp));Text("Night walk",color=Ink,fontSize=13.sp) }
                TextButton(onClick=onFlood) { MzIcon(R.drawable.ic_layer_flood,null,tint=Color(0xFF987249),size=18.dp);Spacer(Modifier.width(7.dp));Text("Flood context",color=Ink,fontSize=13.sp) }
            }
            if(loading) BambooLoading("Preparing your Chennai map")
            else if(savedDestination!=null) {
                HorizontalDivider(color=Color(0xFFE9EFED),modifier=Modifier.padding(vertical=6.dp))
                Row(Modifier.fillMaxWidth().clickable(onClick=onResume).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    MzIcon(R.drawable.ic_route,null,tint=Bamboo,size=19.dp);Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("Continue your journey",fontSize=12.sp,color=Bamboo);Text(savedDestination,fontSize=14.sp,maxLines=1,color=Ink) }
                    MzIcon(R.drawable.ic_chevron_right,null,tint=Ink,size=18.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PandaVoiceSheet(agent: app.manzil.voice.AgentState,onDismiss:()->Unit,onLanguage:(String)->Unit,onBrief:()->Unit,onEmergency:()->Unit,onSend:(String,Boolean)->Unit) {
    val context=LocalContext.current
    val language=agent.language
    val conversationScroll=rememberScrollState()
    LaunchedEffect(conversationScroll.maxValue){conversationScroll.animateScrollTo(conversationScroll.maxValue)}
    var state by remember { mutableStateOf("Tap the mic to talk") }
    var transcript by rememberSaveable { mutableStateOf("") }
    val send by rememberUpdatedState(onSend)
    val speech=remember { PandaSpeechInput(context,{state=it},{transcript="";state="Tap the mic to talk";send(it,true)}) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) speech.start(language) else state="Microphone permission is off. You can type below."
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_STOP) { speech.stop();state="Tap the mic to talk" } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);speech.destroy() }
    }
    val listening=state in setOf(PandaSpeechInput.STATE_STARTING,PandaSpeechInput.STATE_LISTENING,PandaSpeechInput.STATE_UNDERSTANDING)
    ModalBottomSheet(onDismissRequest={speech.stop();onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal=24.dp).padding(bottom=20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                PandaMark(44.dp,ackKey=if(agent.busy)2 else if(agent.answer!=null)3 else 1)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Panda",fontSize=25.sp,fontWeight=FontWeight.SemiBold,color=Ink)
                    Text("Talk, plan, explore",fontSize=12.sp,color=Bamboo)
                }
                IconButton(onClick=onDismiss){MzIcon(R.drawable.ic_x,"Close Panda",tint=Ink)}
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                listOf("en-IN" to "English","hi-IN" to "हिन्दी","ta-IN" to "தமிழ்","te-IN" to "తెలుగు").forEach { (tag,label)->
                    FilterChip(enabled=!agent.busy,colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0xFFDFF1EA),selectedLabelColor=Bamboo),selected=language==tag,onClick={speech.stop();onLanguage(tag);state="Tap the mic to talk"},label={Text(label)})
                }
            }
            if(agent.messages.isEmpty()) {
                Text("Where do you want to go?",fontSize=21.sp,fontWeight=FontWeight.SemiBold,color=Ink,modifier=Modifier.padding(top=10.dp,bottom=14.dp))
                listOf("Find a pharmacy near me","Plan a night walk to Marina Beach","Brief me before I leave").forEachIndexed { index,prompt->
                    Row(Modifier.fillMaxWidth().clickable(enabled=!agent.busy){send(prompt,false)}.padding(vertical=11.dp),verticalAlignment=Alignment.CenterVertically) {
                        MzIcon(listOf(R.drawable.ic_search,R.drawable.ic_layer_night,R.drawable.ic_check)[index],null,tint=Bamboo,size=20.dp);Spacer(Modifier.width(12.dp));Text(prompt,fontSize=14.sp,color=Ink,modifier=Modifier.weight(1f));MzIcon(R.drawable.ic_chevron_right,null,tint=Bamboo,size=18.dp)
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().heightIn(max=260.dp).verticalScroll(conversationScroll)) {
                    agent.messages.takeLast(4).forEach { message->
                        Text(if(message.role=="user")"You" else "Panda",fontSize=10.sp,color=Bamboo,modifier=Modifier.padding(top=12.dp))
                        Text(message.text,fontSize=14.sp,lineHeight=21.sp,color=Ink,modifier=Modifier.padding(top=3.dp,bottom=6.dp))
                    }
                }
            }
            if(agent.busy) BambooLoading("Thinking about your journey",Modifier.padding(vertical=12.dp))
            else if(listening) BambooLoading(state,Modifier.padding(vertical=12.dp))
            else Text(agent.error?:state,fontSize=12.sp,lineHeight=17.sp,color=if(agent.error!=null)Color(0xFFAA4356) else Bamboo,modifier=Modifier.padding(vertical=12.dp))
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value=transcript,onValueChange={transcript=it.take(1000)},enabled=!agent.busy,placeholder={Text("Ask Panda…",fontSize=14.sp)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(24.dp),maxLines=3)
                FilledIconButton(enabled=!agent.busy,onClick={if(listening){speech.stop();state="Tap the mic to talk"}else if(transcript.isNotBlank()){speech.stop();send(transcript.trim(),false);transcript=""}else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)speech.start(language) else permission.launch(Manifest.permission.RECORD_AUDIO)},modifier=Modifier.size(52.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Bamboo,contentColor=Color.White)) {
                    MzIcon(if(listening)R.drawable.ic_x else if(transcript.isNotBlank())R.drawable.ic_arrow_send else R.drawable.ic_mic,if(listening)"Stop listening" else if(transcript.isNotBlank())"Send to Panda" else "Start listening")
                }
            }
            Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=onBrief,enabled=!agent.busy){MzIcon(R.drawable.ic_check,null,tint=Bamboo,size=17.dp);Spacer(Modifier.width(6.dp));Text("Journey check",color=Bamboo,fontSize=12.sp)}
                TextButton(onClick=onEmergency){MzIcon(R.drawable.ic_help,null,tint=Color(0xFFB34862),size=17.dp);Spacer(Modifier.width(6.dp));Text("Emergency help",color=Color(0xFFB34862),fontSize=12.sp)}
            }
            Text("Online conversation · microphone only when you tap",fontSize=10.sp,color=Color(0xFF62737B),modifier=Modifier.padding(top=12.dp))
        }
    }
}

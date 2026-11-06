package app.manzil.frontend

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun LiveCompanionScreen(instruction: String?, status: String, accessGranted: Boolean, onAccess: () -> Unit, onStart: () -> Unit, onMaps: () -> Unit, onStop: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ink = Color(0xFF142D4E)
    val muted = Color(0xFF53657A)
    Scaffold(containerColor = Color.White, bottomBar = {
        Column(Modifier.navigationBarsPadding().padding(horizontal=28.dp, vertical=12.dp)) {
            Button(onClick={context.startActivity(android.content.Intent(context, app.manzil.offline.OfflineActivity::class.java))},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("Open Manzil map") }
            Button(onClick=onStart, modifier=Modifier.fillMaxWidth().heightIn(min=56.dp), colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF2457D6))) { Text("Keep Panda with me") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=onMaps) { Text("Return to Maps") }
                TextButton(onClick=onStop) { Text("Dismiss Panda", color=muted) }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=28.dp)) {
            Row(Modifier.padding(top=12.dp), verticalAlignment=Alignment.CenterVertically) {
                AndroidView(factory={PandaFaceView(it)},modifier=Modifier.size(44.dp))
                Text("manzil",color=ink,fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(start=8.dp))
            }
            Spacer(Modifier.height(48.dp))
            Text("Maps companion", color=muted,fontSize=14.sp)
            Text(instruction ?: "Your route.\nPanda beside you.",color=ink,fontSize=34.sp,lineHeight=43.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
            Text(status,color=muted,fontSize=14.sp,lineHeight=22.sp,modifier=Modifier.padding(top=20.dp))
            HorizontalDivider(Modifier.padding(vertical=32.dp),color=Color(0xFFD8E0E9))
            Text("Your navigation stays in Maps",color=ink,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            Text("Panda reads directions shared by Google Maps notifications. It does not choose a different route or guess the next turn.",color=muted,fontSize=15.sp,lineHeight=24.sp,modifier=Modifier.padding(top=12.dp))
            if(!accessGranted) {
                TextButton(onClick=onAccess,modifier=Modifier.padding(top=16.dp)) { Text("Allow Google Maps directions") }
                Text("Android grants notification access broadly. Manzil only processes Google Maps notifications.",color=muted,fontSize=12.sp,lineHeight=19.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text("When offline, a saved observation is labelled with its age. It is not a fresh turn instruction.",color=muted,fontSize=12.sp,lineHeight=19.sp)
            Spacer(Modifier.height(28.dp))
        }
    }
}

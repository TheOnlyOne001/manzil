package app.manzil.offline

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
import app.manzil.R
import app.manzil.ui.components.MzIcon
import app.manzil.ui.components.PandaMark
import app.manzil.voice.BriefSignal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JourneyBriefSheet(destination:String?,summary:String?,signals:List<BriefSignal>,onDismiss:()->Unit,onReview:()->Unit,onNight:()->Unit) {
 val ink=Color(0xFF142D4E);val green=Color(0xFF087E73)
 ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Color.White) {
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal=24.dp).padding(bottom=20.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically){PandaMark(40.dp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("Before you go",fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=ink);Text(destination?:"Your next walk",fontSize=13.sp,color=green)};IconButton(onClick=onDismiss){MzIcon(R.drawable.ic_x,"Close journey check",tint=ink)}}
   summary?.let{Text(it,fontSize=28.sp,fontWeight=FontWeight.Medium,color=ink,modifier=Modifier.padding(top=18.dp,bottom=8.dp))}
   signals.forEachIndexed { index,signal->
    Row(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
     MzIcon(listOf(R.drawable.ic_download,R.drawable.ic_navigation,R.drawable.ic_route,R.drawable.ic_mic)[index],null,tint=green,size=23.dp)
     Spacer(Modifier.width(16.dp))
     Column(Modifier.weight(1f)){Text(signal.title,fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=ink);Text(signal.detail,fontSize=12.sp,lineHeight=17.sp,color=Color(0xFF62737B),modifier=Modifier.padding(top=3.dp))}
     Spacer(Modifier.width(8.dp));MzIcon(if(signal.ready)R.drawable.ic_check else R.drawable.ic_info,if(signal.ready)"Available" else "Needs attention",tint=if(signal.ready)green else Color(0xFFAF7A23),size=20.dp)
    }
    if(index<signals.lastIndex)HorizontalDivider(color=Color(0xFFEEF2F0))
   }
   Text("Lighting and flood layers provide context, not a safety guarantee.",fontSize=11.sp,lineHeight=16.sp,color=Color(0xFF62737B),modifier=Modifier.padding(vertical=10.dp))
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick=onNight){MzIcon(R.drawable.ic_layer_night,null,tint=green,size=19.dp);Spacer(Modifier.width(6.dp));Text("Inspect night layer",color=green)};TextButton(onClick=onReview){Text("Review route",color=ink);MzIcon(R.drawable.ic_chevron_right,null,tint=ink,size=18.dp)}}
  }
 }
}

package app.manzil.frontend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

data class JourneyDisplayStep(val title: String, val detail: String)

private val Ink = Color(0xFF142D4E)
private val Muted = Color(0xFF53657A)
private val Blue = Color(0xFF2457D6)
private val Teal = Color(0xFF087E83)
private val Rule = Color(0xFFD8E0E9)

/** Product surface owned independently of storage, service and command plumbing. */
@Composable
fun JourneyScreen(
    saved: Boolean,
    active: Boolean,
    stepIndex: Int,
    steps: List<JourneyDisplayStep>,
    savedAt: String,
    connection: String,
    speechReady: Boolean,
    onSave: () -> Unit,
    onStart: () -> Unit,
    onSelectStep: (Int) -> Unit,
    onNext: () -> Unit,
    onRead: () -> Unit,
    onAsk: () -> Unit,
    onMaps: () -> Unit,
    onOverlay: () -> Unit,
    onEnd: () -> Unit,
) {
    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            Column(Modifier.background(Color.White).navigationBarsPadding().padding(horizontal = 24.dp)) {
                if (active) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onRead, modifier = Modifier.heightIn(min = 52.dp)) { Text("Read step", color = Ink) }
                        Spacer(Modifier.weight(1f))
                        MainAction(if (stepIndex == steps.lastIndex) "Finish journey" else "Next step", if (stepIndex == steps.lastIndex) onEnd else onNext, Modifier.weight(1.3f))
                    }
                } else {
                    MainAction(if (saved) "Start saved journey" else "Save sample journey", if (saved) onStart else onSave, Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onAsk) { Text("Ask Panda", color = Ink) }
                    TextButton(onClick = onMaps) { Text("Open Google Maps", color = Muted) }
                }
            }
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)
                .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AndroidView(factory = { PandaFaceView(it) }, modifier = Modifier.size(44.dp).semantics { contentDescription = "Manzil panda" })
                Text("manzil", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(6.dp).background(Teal, CircleShape))
                Text(connection, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 7.dp))
            }
            Spacer(Modifier.height(32.dp))
            Text(if (active) "Your next saved step" else "A journey, kept close.", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                if (active) steps.getOrNull(stepIndex)?.title ?: "Saved journey" else "Chennai\nCentral",
                color = Ink, fontSize = if (active) 30.sp else 42.sp,
                lineHeight = if (active) 38.sp else 48.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            if (active) {
                Text(steps.getOrNull(stepIndex)?.detail.orEmpty(), color = Muted, fontSize = 16.sp, lineHeight = 25.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.width(36.dp).height(16.dp)) {
                        drawLine(Teal, Offset(3.dp.toPx(), size.height / 2), Offset(size.width - 3.dp.toPx(), size.height / 2), 1.5.dp.toPx())
                        drawCircle(Teal, 3.dp.toPx(), Offset(3.dp.toPx(), size.height / 2))
                        drawCircle(Blue, 3.dp.toPx(), Offset(size.width - 3.dp.toPx(), size.height / 2))
                    }
                    Text("From Guindy", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sample journey", color = Color(0xFF975700), fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFF4E9)).padding(horizontal = 9.dp, vertical = 5.dp))
                Text(if (active) "  ${stepIndex + 1} of ${steps.size}" else "  Explore offline mode", color = Muted, fontSize = 12.sp)
            }
            Text("Illustrative steps. Check routes and services before travel.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = Rule)
            if (!active) {
                Text("Before you go", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
                ReadinessRow("Journey notes", if (saved) "Saved on this phone" else "Ready to save", if (saved) "Saved" else "Sample")
                ReadinessRow("Read aloud", if (speechReady) "Installed offline voice" else "Text is always available", if (speechReady) "Available" else "Unavailable")
                ReadinessRow("Google Maps area", "Check downloads inside Google Maps", "User managed")
                Spacer(Modifier.height(18.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("The saved plan", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${steps.size} steps", color = Muted, fontSize = 12.sp)
            }
            steps.forEachIndexed { index, step ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .then(if (saved) Modifier.clickable { onSelectStep(index) } else Modifier)
                        .padding(vertical = 12.dp),
                ) {
                    Box(Modifier.padding(top = 1.dp).size(28.dp).background(if (active && index == stepIndex) Blue else Color(0xFFF0F3F7), CircleShape), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = if (active && index == stepIndex) Color.White else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(step.title, color = Ink, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = if (active && index == stepIndex) FontWeight.SemiBold else FontWeight.Medium)
                        if (active && index == stepIndex) Text("Current saved step", color = Teal, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (saved) {
                Text(savedAt, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp))
                Text("No live traffic, location or service updates.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            }
            if (active) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onOverlay) { Text("Keep Panda above Maps", color = Blue) }
                TextButton(onClick = onEnd) { Text("End journey", color = Muted) }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ReadinessRow(title: String, detail: String, state: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(state, color = Muted, fontSize = 12.sp)
        }
        Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MainAction(label: String, action: () -> Unit, modifier: Modifier) {
    Button(onClick = action, modifier = modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

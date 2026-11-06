package app.manzil.frontend

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PandaCommandSheet(reply: String?, onCommand: (String) -> Unit, onDismiss: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val submit = { if (input.isNotBlank()) { onCommand(input); input = "" } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AndroidView(factory = { PandaFaceView(it) }, modifier = Modifier.size(48.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("A little help along the way", color = Color(0xFF142D4E), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Saved journey commands", color = Color(0xFF53657A), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Text(reply ?: "I can show your saved steps, repeat an instruction or explain the plan. These commands work offline.", color = Color(0xFF53657A), fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onCommand("next step") }) { Text("Next step") }
                TextButton(onClick = { onCommand("explain") }) { Text("Explain") }
                TextButton(onClick = { onCommand("data age") }) { Text("Saved when?") }
            }
            TextField(
                value = input, onValueChange = { input = it.take(300) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("Ask about your saved journey") },
                singleLine = false, maxLines = 3,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF5F7FA), unfocusedContainerColor = Color(0xFFF5F7FA), focusedIndicatorColor = Color(0xFF2457D6), unfocusedIndicatorColor = Color.Transparent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                trailingIcon = { TextButton(onClick = { submit() }, enabled = input.isNotBlank(), modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Send") } },
            )
            Text("Local commands only. No live traffic or route changes.", color = Color(0xFF53657A), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

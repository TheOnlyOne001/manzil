package app.manzil.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.manzil.R
import app.manzil.agent.CommandParser
import app.manzil.agent.LocalCommand
import app.manzil.ui.components.MzIcon
import app.manzil.ui.components.PandaMark
import app.manzil.ui.components.PandaReplyCard
import app.manzil.ui.theme.MzColors

private data class Suggestion(val label: Int, val command: LocalCommand)

/**
 * "Ask Panda": suggestion chips issue typed tool calls directly; the text field goes through the
 * deterministic [CommandParser]. No model, no speech input, no network.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommandSheet(
    reply: PandaReply?,
    finished: Boolean,
    onDismiss: () -> Unit,
    onCommand: (LocalCommand) -> Unit,
    onSubmit: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        val value = text.trim()
        if (value.isNotEmpty()) {
            onSubmit(value)
            text = ""
        }
    }
    val suggestions = buildList {
        if (!finished) add(Suggestion(R.string.chip_next, LocalCommand.NextStep))
        add(Suggestion(R.string.chip_repeat, LocalCommand.RepeatInstruction))
        add(Suggestion(R.string.chip_explain, LocalCommand.ExplainSavedStep))
        if (!finished) add(Suggestion(R.string.chip_previous, LocalCommand.PreviousStep))
        add(Suggestion(R.string.chip_age, LocalCommand.ShowDataAge))
        add(Suggestion(R.string.chip_maps, LocalCommand.OpenGoogleMapsDestination))
        if (!finished) add(Suggestion(R.string.chip_finish, LocalCommand.EndJourney))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MzColors.Surface,
        contentColor = MzColors.Ink,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PandaMark(size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MzColors.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.sheet_subtitle), style = MaterialTheme.typography.bodyMedium, color = MzColors.Muted)
                }
            }

            if (reply != null) {
                Spacer(Modifier.height(16.dp))
                PandaReplyCard(message = reply.message, success = reply.success, ackKey = reply.id)
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.sheet_try), style = MaterialTheme.typography.labelSmall, color = MzColors.Muted)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { s ->
                    Surface(
                        onClick = { onCommand(s.command) },
                        shape = CircleShape,
                        color = MzColors.PrimarySoft,
                        contentColor = MzColors.Primary,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(s.label), style = MaterialTheme.typography.labelMedium, color = MzColors.Primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= CommandParser.MAX_INPUT_CHARS) text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sheet_input_label)) },
                placeholder = { Text(stringResource(R.string.sheet_input_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MzColors.Primary,
                    unfocusedBorderColor = MzColors.Border,
                    focusedLabelColor = MzColors.Primary,
                    unfocusedLabelColor = MzColors.Muted,
                    cursorColor = MzColors.Primary,
                ),
                trailingIcon = {
                    IconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                        MzIcon(
                            R.drawable.ic_chevron_right,
                            contentDescription = stringResource(R.string.cd_send_command),
                            tint = if (text.isNotBlank()) MzColors.Primary else MzColors.Muted,
                        )
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.sheet_footnote), style = MaterialTheme.typography.bodySmall, color = MzColors.Muted)
        }
    }
}

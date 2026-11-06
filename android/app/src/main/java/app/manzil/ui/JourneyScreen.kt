package app.manzil.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manzil.R
import app.manzil.agent.LocalCommand
import app.manzil.model.JourneyProgress
import app.manzil.model.JourneyStep
import app.manzil.model.ProgressStatus
import app.manzil.model.SavedJourney
import app.manzil.model.TimeText
import app.manzil.system.NetworkStatus
import app.manzil.system.SpeechState
import app.manzil.ui.components.MzIcon
import app.manzil.ui.components.NetworkPill
import app.manzil.ui.components.PandaMark
import app.manzil.ui.components.PandaReplyCard
import app.manzil.ui.components.PrimaryButton
import app.manzil.ui.components.SoftButton
import app.manzil.ui.components.icon
import app.manzil.ui.theme.MzColors
import java.time.ZoneId

/** Everything the journey screen can ask for. All journey changes go through [onCommand]. */
class JourneyActions(
    val onBack: () -> Unit,
    val onCommand: (LocalCommand) -> Unit,
    val onSubmitText: (String) -> Unit,
    val onReadAloud: () -> Unit,
    val onSpeakStepsChange: (Boolean) -> Unit,
    val onSelectVoice: (String) -> Unit,
    val onBubbleChange: (Boolean) -> Unit,
    val onRequestOverlayPermission: () -> Unit,
    val onStartAgain: () -> Unit,
    val onDismissReply: () -> Unit,
)

@Composable
fun JourneyScreen(
    saved: SavedJourney,
    progress: JourneyProgress,
    network: NetworkStatus,
    speech: SpeechState,
    speakSteps: Boolean,
    reply: PandaReply?,
    overlayPermission: Boolean,
    overlayRunning: Boolean,
    actions: JourneyActions,
) {
    BackHandler(onBack = actions.onBack)
    val journey = saved.journey
    val steps = journey.steps
    val index = progress.stepIndex.coerceIn(0, steps.lastIndex)
    val finished = progress.status == ProgressStatus.FINISHED
    val isLast = index == steps.lastIndex
    val zone = remember { ZoneId.systemDefault() }

    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showVoicePicker by rememberSaveable { mutableStateOf(false) }
    var showOverlayExplainer by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MzColors.Paper,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MzColors.Paper)
                    .windowInsetsPadding(
                        WindowInsets.statusBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.onBack) {
                    MzIcon(R.drawable.ic_arrow_left, contentDescription = stringResource(R.string.cd_back), tint = MzColors.Ink)
                }
                Text(
                    stringResource(R.string.journey_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MzColors.Ink,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                NetworkPill(network)
            }
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MzColors.Paper)
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { showSheet = true },
                    modifier = Modifier.heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    MzIcon(R.drawable.ic_help, contentDescription = null, tint = MzColors.Primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_ask), style = MaterialTheme.typography.labelLarge, color = MzColors.Primary)
                }
                Spacer(Modifier.width(12.dp))
                when {
                    finished -> PrimaryButton(
                        text = stringResource(R.string.cta_back_home),
                        onClick = actions.onBack,
                        modifier = Modifier.weight(1f),
                    )
                    isLast -> PrimaryButton(
                        text = stringResource(R.string.cta_finish),
                        icon = R.drawable.ic_check,
                        onClick = { actions.onCommand(LocalCommand.EndJourney) },
                        modifier = Modifier.weight(1f),
                    )
                    else -> PrimaryButton(
                        text = stringResource(R.string.cta_next_step),
                        icon = R.drawable.ic_chevron_right,
                        onClick = { actions.onCommand(LocalCommand.NextStep) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                if (journey.isSample) {
                    Notice(
                        icon = R.drawable.ic_info,
                        text = stringResource(R.string.journey_sample_notice),
                        fg = MzColors.Warning,
                        bg = MzColors.WarningSoft,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    stringResource(R.string.journey_to_destination, journey.destination.name),
                    style = MaterialTheme.typography.displaySmall,
                    color = MzColors.Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        R.string.journey_saved_meta,
                        TimeText.savedAt(saved.savedAtEpochMs, System.currentTimeMillis(), zone),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MzColors.Muted,
                )

                Spacer(Modifier.height(20.dp))
                if (finished) {
                    FinishedHero(stepCount = steps.size, onStartAgain = actions.onStartAgain)
                } else {
                    CurrentStepHero(
                        index = index,
                        total = steps.size,
                        step = steps[index],
                        canReadAloud = speech is SpeechState.Ready,
                        onReadAloud = actions.onReadAloud,
                        onPrevious = { actions.onCommand(LocalCommand.PreviousStep) },
                        onRepeat = { actions.onCommand(LocalCommand.RepeatInstruction) },
                        onExplain = { actions.onCommand(LocalCommand.ExplainSavedStep) },
                    )
                }

                if (reply != null && !showSheet) {
                    Spacer(Modifier.height(12.dp))
                    PandaReplyCard(message = reply.message, success = reply.success, ackKey = reply.id)
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(R.string.journey_steps_heading, steps.size),
                    style = MaterialTheme.typography.titleLarge,
                    color = MzColors.Ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.journey_steps_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MzColors.Muted,
                )
                Spacer(Modifier.height(16.dp))
                steps.forEachIndexed { i, step ->
                    val state = when {
                        finished || i < index -> StepState.DONE
                        i == index -> StepState.CURRENT
                        else -> StepState.UPCOMING
                    }
                    StepRow(
                        number = i + 1,
                        step = step,
                        state = state,
                        isLast = i == steps.lastIndex,
                        onSelect = { actions.onCommand(LocalCommand.GoToStep(i + 1)) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.journey_more_heading),
                    style = MaterialTheme.typography.titleLarge,
                    color = MzColors.Ink,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                ActionRow(
                    icon = R.drawable.ic_navigation,
                    title = stringResource(R.string.more_maps_title, journey.destination.name),
                    subtitle = stringResource(R.string.more_maps_subtitle),
                    onClick = { actions.onCommand(LocalCommand.OpenGoogleMapsDestination) },
                )
                HorizontalDivider(color = MzColors.Border)
                SwitchRow(
                    icon = R.drawable.ic_route,
                    title = stringResource(R.string.more_bubble_title),
                    subtitle = when {
                        overlayRunning -> stringResource(R.string.more_bubble_on)
                        !overlayPermission -> stringResource(R.string.more_bubble_needs_permission)
                        else -> stringResource(R.string.more_bubble_off)
                    },
                    checked = overlayRunning,
                    enabled = true,
                    onCheckedChange = { on ->
                        if (on && !overlayPermission) showOverlayExplainer = true else actions.onBubbleChange(on)
                    },
                )
                HorizontalDivider(color = MzColors.Border)
                val speechReady = speech as? SpeechState.Ready
                SwitchRow(
                    icon = R.drawable.ic_volume,
                    title = stringResource(R.string.more_speak_title),
                    subtitle = when (speech) {
                        is SpeechState.Ready -> stringResource(R.string.more_speak_ready, speech.selected.label)
                        is SpeechState.Unavailable -> stringResource(R.string.more_speak_unavailable)
                        else -> stringResource(R.string.ready_voice_checking)
                    },
                    checked = speakSteps && speechReady != null,
                    enabled = speechReady != null,
                    onCheckedChange = actions.onSpeakStepsChange,
                )
                if (speechReady != null && speechReady.voices.size > 1) {
                    HorizontalDivider(color = MzColors.Border)
                    ActionRow(
                        icon = R.drawable.ic_volume,
                        title = stringResource(R.string.more_voice_title),
                        subtitle = speechReady.selected.label,
                        onClick = { showVoicePicker = true },
                    )
                }
                HorizontalDivider(color = MzColors.Border)
                ActionRow(
                    icon = R.drawable.ic_clock,
                    title = stringResource(R.string.more_age_title),
                    subtitle = stringResource(R.string.more_age_subtitle),
                    onClick = { actions.onCommand(LocalCommand.ShowDataAge) },
                )
                if (!finished) {
                    HorizontalDivider(color = MzColors.Border)
                    ActionRow(
                        icon = R.drawable.ic_check,
                        title = stringResource(R.string.more_finish_title),
                        subtitle = stringResource(R.string.more_finish_subtitle),
                        onClick = { actions.onCommand(LocalCommand.EndJourney) },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSheet) {
        CommandSheet(
            reply = reply,
            finished = finished,
            onDismiss = { showSheet = false },
            onCommand = actions.onCommand,
            onSubmit = actions.onSubmitText,
        )
    }

    val readyForPicker = speech as? SpeechState.Ready
    if (showVoicePicker && readyForPicker != null) {
        VoicePicker(readyForPicker, onSelect = actions.onSelectVoice, onDismiss = { showVoicePicker = false })
    }

    if (showOverlayExplainer) {
        AlertDialog(
            onDismissRequest = { showOverlayExplainer = false },
            icon = { PandaMark(size = 48.dp) },
            title = { Text(stringResource(R.string.overlay_explainer_title), textAlign = TextAlign.Center) },
            text = { Text(stringResource(R.string.overlay_explainer_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayExplainer = false
                    actions.onRequestOverlayPermission()
                }) { Text(stringResource(R.string.overlay_explainer_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayExplainer = false }) { Text(stringResource(R.string.action_not_now)) }
            },
            containerColor = MzColors.Surface,
        )
    }
}

@Composable
private fun Notice(@DrawableRes icon: Int, text: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(14.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        MzIcon(icon, contentDescription = null, tint = fg, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CurrentStepHero(
    index: Int,
    total: Int,
    step: JourneyStep,
    canReadAloud: Boolean,
    onReadAloud: () -> Unit,
    onPrevious: () -> Unit,
    onRepeat: () -> Unit,
    onExplain: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MzColors.Surface)
            .border(1.dp, MzColors.Border, RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MzIcon(step.mode.icon(), contentDescription = null, tint = MzColors.Primary, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.step_of, index + 1, total).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MzColors.Primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(step.title, style = MaterialTheme.typography.headlineSmall, color = MzColors.Ink)
                Spacer(Modifier.height(8.dp))
                Text(step.detail, style = MaterialTheme.typography.bodyLarge, color = MzColors.Ink)
                if (step.caution != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        MzIcon(R.drawable.ic_triangle_alert, contentDescription = null, tint = MzColors.Warning, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(step.caution, style = MaterialTheme.typography.labelMedium, color = MzColors.Warning)
                    }
                }
            }
            if (canReadAloud) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onReadAloud,
                    modifier = Modifier.align(Alignment.Top).clip(CircleShape).background(MzColors.PrimarySoft),
                ) {
                    MzIcon(R.drawable.ic_volume, contentDescription = stringResource(R.string.cd_read_aloud), tint = MzColors.Primary)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            ControlButton(R.drawable.ic_chevron_left, stringResource(R.string.action_previous), enabled = index > 0, onClick = onPrevious, modifier = Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(8.dp))
            ControlButton(R.drawable.ic_rotate_ccw, stringResource(R.string.action_repeat), enabled = true, onClick = onRepeat, modifier = Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(8.dp))
            ControlButton(R.drawable.ic_info, stringResource(R.string.action_explain), enabled = true, onClick = onExplain, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun ControlButton(@DrawableRes icon: Int, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MzColors.PrimarySoft,
        contentColor = MzColors.Primary,
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val tint = if (enabled) MzColors.Primary else MzColors.Muted
            MzIcon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FinishedHero(stepCount: Int, onStartAgain: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MzColors.TealSoft)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            PandaMark(size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.finished_title), style = MaterialTheme.typography.headlineSmall, color = MzColors.Ink)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.finished_body, stepCount), style = MaterialTheme.typography.bodyMedium, color = MzColors.TealDeep)
            }
        }
        Spacer(Modifier.height(16.dp))
        SoftButton(text = stringResource(R.string.cta_start_again), icon = R.drawable.ic_rotate_ccw, onClick = onStartAgain, modifier = Modifier.fillMaxWidth())
    }
}

private enum class StepState { DONE, CURRENT, UPCOMING }

@Composable
private fun StepRow(number: Int, step: JourneyStep, state: StepState, isLast: Boolean, onSelect: () -> Unit) {
    val stateText = stringResource(
        when (state) {
            StepState.DONE -> R.string.step_state_done
            StepState.CURRENT -> R.string.step_state_current
            StepState.UPCOMING -> R.string.step_state_upcoming
        },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = state != StepState.CURRENT,
                onClickLabel = stringResource(R.string.step_select_label),
                role = Role.Button,
                onClick = onSelect,
            )
            .semantics(mergeDescendants = true) { stateDescription = stateText }
            .heightIn(min = 56.dp),
    ) {
        Column(Modifier.width(40.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            StepBadge(number, state)
            if (!isLast) {
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(if (state == StepState.DONE) MzColors.Teal else MzColors.Border),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 20.dp, end = 4.dp)) {
            Text(
                step.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (state == StepState.CURRENT) MzColors.Primary else MzColors.Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                step.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MzColors.Muted,
                maxLines = if (state == StepState.CURRENT) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state == StepState.UPCOMING && step.caution != null) {
                Spacer(Modifier.height(4.dp))
                Text(step.caution, style = MaterialTheme.typography.bodySmall, color = MzColors.Warning)
            }
        }
    }
}

@Composable
private fun StepBadge(number: Int, state: StepState) {
    val modifier = Modifier.size(32.dp).clip(CircleShape)
    when (state) {
        StepState.DONE -> Box(modifier.background(MzColors.Teal), contentAlignment = Alignment.Center) {
            MzIcon(R.drawable.ic_check, contentDescription = null, tint = MzColors.Surface, size = 18.dp)
        }
        StepState.CURRENT -> Box(modifier.background(MzColors.Primary), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = MzColors.Surface)
        }
        StepState.UPCOMING -> Box(
            modifier.background(MzColors.Surface).border(1.5.dp, MzColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = MzColors.Ink)
        }
    }
}

@Composable
private fun ActionRow(@DrawableRes icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MzIcon(icon, contentDescription = null, tint = MzColors.Muted)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MzColors.Ink)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MzColors.Muted)
        }
        Spacer(Modifier.width(8.dp))
        MzIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = MzColors.Muted, size = 20.dp)
    }
}

@Composable
private fun SwitchRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MzIcon(icon, contentDescription = null, tint = MzColors.Muted)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MzColors.Ink)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MzColors.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MzColors.Primary,
                uncheckedBorderColor = MzColors.Muted,
                uncheckedThumbColor = MzColors.Muted,
            ),
        )
    }
}

@Composable
private fun VoicePicker(ready: SpeechState.Ready, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_picker_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                Text(stringResource(R.string.voice_picker_body), style = MaterialTheme.typography.bodyMedium, color = MzColors.Muted)
                Spacer(Modifier.height(8.dp))
                ready.voices.forEach { voice ->
                    val selected = voice.name == ready.selected.name
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(voice.name) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(voice.label, style = MaterialTheme.typography.bodyLarge, color = MzColors.Ink)
                            Text(voice.name, style = MaterialTheme.typography.bodySmall, color = MzColors.Muted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
        containerColor = MzColors.Surface,
    )
}

package app.manzil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.manzil.R
import app.manzil.model.Journey
import app.manzil.model.JourneyProgress
import app.manzil.model.LoadIssue
import app.manzil.model.ProgressStatus
import app.manzil.model.SampleJourneys
import app.manzil.model.SavedJourney
import app.manzil.model.StoredState
import app.manzil.model.TimeText
import app.manzil.system.NetworkStatus
import app.manzil.system.SpeechState
import app.manzil.ui.components.MzIcon
import app.manzil.ui.components.NetworkPill
import app.manzil.ui.components.PandaMark
import app.manzil.ui.components.PrimaryButton
import app.manzil.ui.components.ReadinessRow
import app.manzil.ui.components.RouteLine
import app.manzil.ui.components.SampleTag
import app.manzil.ui.components.Tone
import app.manzil.ui.theme.MzColors
import java.time.ZoneId

@Composable
fun HomeScreen(
    stored: StoredState,
    network: NetworkStatus,
    speech: SpeechState,
    notice: String?,
    busy: Boolean,
    onSaveSample: () -> Unit,
    onStart: (String) -> Unit,
    onRemove: (String) -> Unit,
    onOpenGoogleMaps: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val now = System.currentTimeMillis()
    val savedSample = stored.find(SampleJourneys.CHENNAI_CENTRAL_ID)
    val featured: Journey = savedSample?.journey ?: SampleJourneys.chennaiCentral
    val progressFor: (String) -> JourneyProgress? = { id -> stored.progress?.takeIf { it.journeyId == id } }
    var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }

    val featuredProgress = progressFor(featured.id)
    val featuredStep = (featuredProgress?.stepIndex ?: 0) + 1
    val ctaText = when {
        savedSample == null -> stringResource(R.string.cta_save_journey)
        featuredProgress?.status == ProgressStatus.ACTIVE ->
            stringResource(R.string.cta_continue_step, featuredStep, featured.steps.size)
        featuredProgress?.status == ProgressStatus.FINISHED -> stringResource(R.string.cta_start_again)
        else -> stringResource(R.string.cta_start_journey)
    }
    val ctaIcon = if (savedSample == null) R.drawable.ic_download else R.drawable.ic_play

    Scaffold(
        containerColor = MzColors.Paper,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MzColors.Paper)
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (notice != null) {
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MzColors.Danger,
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                PrimaryButton(
                    text = ctaText,
                    icon = ctaIcon,
                    enabled = !busy,
                    onClick = { if (savedSample == null) onSaveSample() else onStart(featured.id) },
                    modifier = Modifier.widthIn(max = 600.dp),
                )
            }
        },
    ) { inner ->
        Column(
            modifier = Modifier
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
                // Header
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PandaMark(size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MzColors.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    NetworkPill(network)
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(R.string.home_headline),
                    style = MaterialTheme.typography.displaySmall,
                    color = MzColors.Ink,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.home_subhead), style = MaterialTheme.typography.bodyLarge, color = MzColors.Muted)

                if (stored.loadIssue != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(
                            if (stored.loadIssue == LoadIssue.NEWER_SCHEMA) R.string.notice_newer_schema else R.string.notice_corrupt_reset,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MzColors.Warning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MzColors.WarningSoft)
                            .padding(12.dp),
                    )
                }

                Spacer(Modifier.height(24.dp))
                DestinationCard(featured)

                Spacer(Modifier.height(28.dp))
                SectionHeading(stringResource(R.string.home_before_you_leave))
                Readiness(
                    saved = savedSample,
                    speech = speech,
                    zone = zone,
                    now = now,
                    onOpenGoogleMaps = onOpenGoogleMaps,
                )

                Spacer(Modifier.height(20.dp))
                SectionHeading(stringResource(R.string.home_on_this_phone))
                Spacer(Modifier.height(8.dp))
                if (stored.journeys.isEmpty()) {
                    EmptySavedState()
                } else {
                    stored.journeys.forEach { saved ->
                        SavedJourneyRow(
                            saved = saved,
                            progress = progressFor(saved.journey.id),
                            zone = zone,
                            now = now,
                            onOpen = { onStart(saved.journey.id) },
                            onRemove = { confirmRemove = saved.journey.id },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.home_footer), style = MaterialTheme.typography.bodyMedium, color = MzColors.Muted)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.home_credits), style = MaterialTheme.typography.bodySmall, color = MzColors.Muted)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    confirmRemove?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.remove_title)) },
            text = { Text(stringResource(R.string.remove_body)) },
            confirmButton = {
                TextButton(onClick = { onRemove(id); confirmRemove = null }) {
                    Text(stringResource(R.string.remove_confirm), color = MzColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.action_cancel)) }
            },
            containerColor = MzColors.Surface,
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MzColors.Ink,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun DestinationCard(journey: Journey) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MzColors.PrimarySoft)
            .padding(20.dp),
    ) {
        if (journey.isSample) {
            SampleTag()
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MzIcon(R.drawable.ic_map_pin, contentDescription = null, tint = MzColors.Primary, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.journey_to), style = MaterialTheme.typography.labelMedium, color = MzColors.Muted)
        }
        Spacer(Modifier.height(4.dp))
        Text(journey.destination.name, style = MaterialTheme.typography.displaySmall, color = MzColors.Ink)
        Spacer(Modifier.height(20.dp))
        RouteLine(origin = journey.originName, destination = journey.destination.name, modes = journey.modeSequence)
        if (journey.isSample) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.sample_disclaimer), style = MaterialTheme.typography.bodyMedium, color = MzColors.Ink)
        }
    }
}

@Composable
private fun Readiness(
    saved: SavedJourney?,
    speech: SpeechState,
    zone: ZoneId,
    now: Long,
    onOpenGoogleMaps: () -> Unit,
) {
    val steps = (saved?.journey ?: SampleJourneys.chennaiCentral).steps.size
    ReadinessRow(
        icon = R.drawable.ic_route,
        title = stringResource(R.string.ready_steps_title),
        detail = stringResource(R.string.ready_steps_detail, steps),
        status = if (saved == null) {
            stringResource(R.string.ready_steps_not_saved)
        } else {
            stringResource(R.string.ready_steps_saved, TimeText.savedAt(saved.savedAtEpochMs, now, zone))
        },
        tone = if (saved == null) Tone.NEUTRAL else Tone.READY,
    )
    HorizontalDivider(color = MzColors.Border)
    ReadinessRow(
        icon = R.drawable.ic_map_pin,
        title = stringResource(R.string.ready_maps_title),
        detail = stringResource(R.string.ready_maps_detail),
        status = stringResource(R.string.ready_maps_status),
        tone = Tone.NEUTRAL,
        action = {
            TextButton(
                onClick = onOpenGoogleMaps,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.ready_maps_action), style = MaterialTheme.typography.labelLarge, color = MzColors.Primary)
            }
        },
    )
    HorizontalDivider(color = MzColors.Border)
    val (voiceStatus, voiceTone) = when (speech) {
        is SpeechState.Ready -> stringResource(R.string.ready_voice_ready, speech.selected.label) to Tone.READY
        is SpeechState.Unavailable -> stringResource(R.string.ready_voice_unavailable) to Tone.NEUTRAL
        SpeechState.Initializing, SpeechState.NotStarted -> stringResource(R.string.ready_voice_checking) to Tone.NEUTRAL
    }
    ReadinessRow(
        icon = R.drawable.ic_volume,
        title = stringResource(R.string.ready_voice_title),
        detail = stringResource(R.string.ready_voice_detail),
        status = voiceStatus,
        tone = voiceTone,
    )
    HorizontalDivider(color = MzColors.Border)
    ReadinessRow(
        icon = R.drawable.ic_help,
        title = stringResource(R.string.ready_commands_title),
        detail = stringResource(R.string.ready_commands_detail),
        status = stringResource(R.string.ready_commands_status),
        tone = Tone.READY,
    )
}

@Composable
private fun EmptySavedState() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MzColors.Border, RoundedCornerShape(16.dp))
            .background(MzColors.Surface)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MzColors.Paper),
            contentAlignment = Alignment.Center,
        ) {
            MzIcon(R.drawable.ic_bookmark, contentDescription = null, tint = MzColors.Muted)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium, color = MzColors.Ink)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyMedium, color = MzColors.Muted)
        }
    }
}

@Composable
private fun SavedJourneyRow(
    saved: SavedJourney,
    progress: JourneyProgress?,
    zone: ZoneId,
    now: Long,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val journey = saved.journey
    val stepNumber = (progress?.stepIndex ?: 0) + 1
    val status = when (progress?.status) {
        ProgressStatus.ACTIVE -> stringResource(R.string.saved_row_in_progress, stepNumber, journey.steps.size)
        ProgressStatus.FINISHED -> stringResource(R.string.saved_row_finished)
        null -> stringResource(R.string.saved_row_not_started, journey.steps.size)
    }
    val openLabel = stringResource(R.string.saved_row_open)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MzColors.Surface)
            .border(1.dp, MzColors.Border, RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(onClickLabel = openLabel, onClick = onOpen)
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MzIcon(R.drawable.ic_bookmark, contentDescription = null, tint = MzColors.Teal)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.saved_row_title, journey.originName, journey.destination.name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MzColors.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.saved_row_meta, TimeText.savedAt(saved.savedAtEpochMs, now, zone), status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MzColors.Muted,
                )
                if (journey.isSample) {
                    Spacer(Modifier.height(6.dp))
                    SampleTag()
                }
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.padding(end = 4.dp)) {
            MzIcon(R.drawable.ic_x, contentDescription = stringResource(R.string.cd_remove_saved, journey.destination.name), tint = MzColors.Muted)
        }
    }
    Spacer(Modifier.height(8.dp))
}

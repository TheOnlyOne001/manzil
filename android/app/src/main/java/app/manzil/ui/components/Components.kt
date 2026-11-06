package app.manzil.ui.components

import android.content.Context
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manzil.R
import app.manzil.model.StepMode
import app.manzil.system.NetworkStatus
import app.manzil.ui.theme.MzColors

@Composable
fun MzIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Icon(painter = painterResource(id), contentDescription = contentDescription, modifier = modifier.size(size), tint = tint)
}

fun isReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * The original panda. Decorative (no announcement). [ackKey] > 0 plays one short nod after a
 * successful command; never loops, and is skipped when system animations are off.
 */
@Composable
fun PandaMark(size: Dp, modifier: Modifier = Modifier, ackKey: Int = 0) {
    val context = LocalContext.current
    val reduced = remember { isReducedMotion(context) }
    val scale = remember { Animatable(1f) }
    val blink = remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(ackKey) {
        blink.value=false
        if (ackKey > 0 && !reduced) {
            blink.value=true
            scale.animateTo(1.08f, tween(durationMillis = 110))
            blink.value=false
            scale.animateTo(1f, tween(durationMillis = 130))
        }
    }
    Image(
        painter = painterResource(if(blink.value)R.drawable.panda_blink else R.drawable.panda),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MzColors.Primary,
            contentColor = MzColors.Surface,
            disabledContainerColor = MzColors.Border,
            disabledContentColor = MzColors.Ink,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            MzIcon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MzColors.PrimarySoft, contentColor = MzColors.Primary),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            MzIcon(icon, contentDescription = null)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

private data class PillStyle(@DrawableRes val icon: Int, val label: Int, val fg: Color, val bg: Color)

/** Default-network status from Android. Says nothing about traffic or data freshness. */
@Composable
fun NetworkPill(status: NetworkStatus, modifier: Modifier = Modifier) {
    val style = when (status) {
        NetworkStatus.OFFLINE -> PillStyle(R.drawable.ic_wifi_off, R.string.net_offline, MzColors.TealDeep, MzColors.TealSoft)
        NetworkStatus.NO_INTERNET -> PillStyle(R.drawable.ic_wifi_off, R.string.net_no_internet, MzColors.Warning, MzColors.WarningSoft)
        NetworkStatus.ONLINE -> PillStyle(R.drawable.ic_wifi, R.string.net_online, MzColors.Ink, MzColors.Surface)
    }
    val label = stringResource(style.label)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(style.bg)
            .border(1.dp, if (status == NetworkStatus.ONLINE) MzColors.Border else Color.Transparent, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MzIcon(style.icon, contentDescription = null, tint = style.fg, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = style.fg)
    }
}

@Composable
fun SampleTag(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.sample_tag),
        style = MaterialTheme.typography.labelSmall,
        color = MzColors.Warning,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MzColors.WarningSoft)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@DrawableRes
fun StepMode.icon(): Int = when (this) {
    StepMode.WALK -> R.drawable.ic_footprints
    StepMode.TRAIN -> R.drawable.ic_train_front
    StepMode.OTHER -> R.drawable.ic_route
}

fun StepMode.label(context: Context): String = context.getString(
    when (this) {
        StepMode.WALK -> R.string.mode_walk
        StepMode.TRAIN -> R.string.mode_train
        StepMode.OTHER -> R.string.mode_other
    },
)

/**
 * Schematic route line: origin dot, one chip per travel mode, destination ring. Not a map and
 * not geographically scaled; announced as a single sentence.
 */
@Composable
fun RouteLine(origin: String, destination: String, modes: List<StepMode>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val description = stringResource(
        R.string.route_line_cd,
        origin,
        modes.joinToString(", ") { it.label(context) },
        destination,
    )
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Box(Modifier.fillMaxWidth().height(32.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val y = size.height / 2
                drawLine(
                    color = MzColors.Primary.copy(alpha = 0.4f),
                    start = Offset(6.dp.toPx(), y),
                    end = Offset(size.width - 8.dp.toPx(), y),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).background(MzColors.Primary, CircleShape))
                modes.forEach { mode ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MzColors.Surface, CircleShape)
                            .border(1.dp, MzColors.Border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        MzIcon(mode.icon(), contentDescription = null, tint = MzColors.Primary, size = 18.dp)
                    }
                }
                Box(
                    Modifier
                        .size(16.dp)
                        .background(MzColors.Surface, CircleShape)
                        .border(3.dp, MzColors.Primary, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(origin, style = MaterialTheme.typography.bodyMedium, color = MzColors.Muted, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(
                destination,
                style = MaterialTheme.typography.bodyMedium,
                color = MzColors.Muted,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

enum class Tone { READY, NEUTRAL, CAUTION }

/** One row of the readiness list. Status always pairs an icon and words, never colour alone. */
@Composable
fun ReadinessRow(
    @DrawableRes icon: Int,
    title: String,
    detail: String,
    status: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val toneColor = when (tone) {
        Tone.READY -> MzColors.Teal
        Tone.NEUTRAL -> MzColors.Muted
        Tone.CAUTION -> MzColors.Warning
    }
    val toneIcon = when (tone) {
        Tone.READY -> R.drawable.ic_check
        Tone.NEUTRAL -> R.drawable.ic_info
        Tone.CAUTION -> R.drawable.ic_triangle_alert
    }
    Row(modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        MzIcon(icon, contentDescription = null, tint = MzColors.Muted, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MzColors.Ink)
                Spacer(Modifier.height(2.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MzColors.Muted)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MzIcon(toneIcon, contentDescription = null, tint = toneColor, size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(status, style = MaterialTheme.typography.labelMedium, color = toneColor)
                }
            }
            if (action != null) action()
        }
    }
}

/** Panda's reply to the last command; announced politely by TalkBack. */
@Composable
fun PandaReplyCard(message: String, success: Boolean, ackKey: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (success) MzColors.TealSoft else MzColors.Paper)
            .border(1.dp, if (success) Color.Transparent else MzColors.Border, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
    ) {
        PandaMark(size = 32.dp, ackKey = if (success) ackKey else 0)
        Spacer(Modifier.width(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MzColors.Ink, modifier = Modifier.weight(1f))
    }
}

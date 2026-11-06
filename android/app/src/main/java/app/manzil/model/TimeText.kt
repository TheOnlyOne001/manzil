package app.manzil.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Deterministic, locale-pinned time phrases used by tool replies and labels. */
object TimeText {
    private val clockFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** "6:10 pm" */
    fun clock(epochMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(clockFormat).lowercase(Locale.ENGLISH)

    /** "today at 6:10 pm", "yesterday at 6:10 pm" or "3 Nov at 6:10 pm". */
    fun savedAt(epochMs: Long, nowEpochMs: Long, zone: ZoneId): String {
        val day = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val time = clock(epochMs, zone)
        return when (day) {
            today -> "today at $time"
            today.minusDays(1) -> "yesterday at $time"
            else -> "${Instant.ofEpochMilli(epochMs).atZone(zone).format(dayFormat)} at $time"
        }
    }

    /** "5 Nov 2026" */
    fun date(epochMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(dateFormat)

    /** "just now", "12 min ago", "3 h ago", "2 days ago". Negative ages read as "just now". */
    fun relative(ageMs: Long): String {
        val minutes = ageMs / 60_000L
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} h ago"
            minutes < 2 * 24 * 60 -> "1 day ago"
            else -> "${minutes / (24 * 60)} days ago"
        }
    }
}

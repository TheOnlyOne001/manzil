package app.manzil.live

/**
 * Plain-JVM view of one posted notification. The listener builds this ONLY after
 * [MapsNotificationFilter.isMapsPackage] returned true, so extras of other apps are never read.
 */
data class RawNotification(
    val packageName: String?,
    val key: String?,
    val category: String?,
    val channelId: String?,
    val ongoing: Boolean,
    val groupSummary: Boolean,
    val title: CharSequence?,
    val text: CharSequence?,
    val bigText: CharSequence?,
    val postTimeMs: Long,
)

/**
 * What Manzil keeps from a Google Maps notification: normalized title / text / big text exactly as
 * Maps provided them, plus non-content metadata useful for the real-phone test. Nothing here is
 * parsed into route, destination, ETA or turn fields — Manzil does not interpret it.
 */
data class MapsObservation(
    val key: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val category: String?,
    val channelId: String?,
    val ongoing: Boolean,
    /** StatusBarNotification.postTime from the system (source timestamp). */
    val sourcePostedAtMs: Long,
) {
    val isNavigationCategory: Boolean get() = category == MapsNotificationFilter.CATEGORY_NAVIGATION

    /** Same visible content and identity, ignoring the source timestamp. */
    fun sameContentAs(other: MapsObservation): Boolean =
        key == other.key && title == other.title && text == other.text && bigText == other.bigText &&
            category == other.category && channelId == other.channelId && ongoing == other.ongoing

    /** Observed text for display, built only from what Maps provided. Null if nothing usable. */
    fun displayText(separator: String = " · "): String? {
        val body = when {
            bigText != null && (text == null || bigText.contains(text)) -> bigText
            else -> listOfNotNull(text, bigText).joinToString(separator).ifEmpty { null }
        }
        return listOfNotNull(title, body).distinct().joinToString(separator).ifEmpty { null }
    }
}

object MapsNotificationFilter {
    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    /** Same value as android.app.Notification.CATEGORY_NAVIGATION. */
    const val CATEGORY_NAVIGATION = "navigation"

    /**
     * Maps' turn-by-turn notification is expected to be ongoing and/or category "navigation", but
     * Samsung F56 verified category "navigation" on 5 November 2026. When true, an ongoing Maps notification without the
     * navigation category is also accepted (it never replaces a navigation-category one). Flip to
     * false after the real-phone test if Maps turns out to tag navigation reliably.
     */
    const val ACCEPT_ONGOING_WITHOUT_NAVIGATION_CATEGORY = false

    const val MAX_FIELD_LENGTH = 400

    fun isMapsPackage(packageName: String?): Boolean = packageName == MAPS_PACKAGE

    /** Returns null for anything that is not a usable Google Maps navigation-candidate notification. */
    fun extract(raw: RawNotification): MapsObservation? {
        if (!isMapsPackage(raw.packageName)) return null
        val key = raw.key?.takeIf { it.isNotBlank() } ?: return null
        if (raw.groupSummary) return null
        val navCategory = raw.category == CATEGORY_NAVIGATION
        if (!navCategory && !(ACCEPT_ONGOING_WITHOUT_NAVIGATION_CATEGORY && raw.ongoing)) return null
        val title = normalize(raw.title)
        val text = normalize(raw.text)
        val bigText = normalize(raw.bigText)?.takeIf { it != text }
        if (title == null && text == null && bigText == null) return null
        return MapsObservation(
            key = key,
            title = title,
            text = text,
            bigText = bigText,
            category = normalize(raw.category, 64),
            channelId = normalize(raw.channelId, 128),
            ongoing = raw.ongoing,
            sourcePostedAtMs = raw.postTimeMs,
        )
    }

    /** Choose the best candidate among currently shown notifications (navigation category first, then newest). */
    fun best(candidates: List<MapsObservation>): MapsObservation? =
        candidates.maxWithOrNull(compareBy<MapsObservation>({ it.isNavigationCategory }, { it.sourcePostedAtMs }))

    /**
     * Safe single-line text: drops control characters, unassigned/private-use code points and
     * invisible format characters (bidi marks, zero-width spaces) except ZWJ/ZWNJ, which Indic
     * scripts need; collapses all whitespace (incl. NBSP / narrow NBSP) to one space; trims; caps
     * the length without splitting a surrogate pair. Returns null when nothing remains.
     */
    fun normalize(raw: CharSequence?, maxLength: Int = MAX_FIELD_LENGTH): String? {
        if (raw == null) return null
        val source = raw.toString()
        val out = StringBuilder(minOf(source.length, maxLength + 1))
        var pendingSpace = false
        var i = 0
        while (i < source.length) {
            val cp = source.codePointAt(i)
            i += Character.charCount(cp)
            val type = Character.getType(cp)
            when {
                Character.isWhitespace(cp) || Character.isSpaceChar(cp) || type == Character.CONTROL.toInt() ->
                    pendingSpace = true
                cp == 0x200C || cp == 0x200D -> out.appendCodePoint(cp)
                type == Character.FORMAT.toInt() ||
                    type == Character.PRIVATE_USE.toInt() ||
                    type == Character.UNASSIGNED.toInt() ||
                    type == Character.SURROGATE.toInt() -> Unit
                else -> {
                    if (pendingSpace && out.isNotEmpty()) out.append(' ')
                    pendingSpace = false
                    out.appendCodePoint(cp)
                }
            }
            if (out.length > maxLength) break
        }
        var result = out.toString().trim()
        if (result.length > maxLength) {
            var cut = maxLength - 1
            if (cut > 0 && Character.isHighSurrogate(result[cut - 1])) cut -= 1
            result = result.substring(0, cut).trimEnd() + "…"
        }
        return result.ifEmpty { null }
    }
}

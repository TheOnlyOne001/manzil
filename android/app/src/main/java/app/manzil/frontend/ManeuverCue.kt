package app.manzil.frontend

/** Presentation only: never used for routing or control. Unknown language stays verbatim. */
data class ManeuverCue(val kind: String, val distance: String?, val instruction: String) {
    companion object {
        fun parse(text: String): ManeuverCue {
            val match = Regex("^([0-9]+(?:[.,][0-9]+)?\\s*(?:km|m|ft|mi))\\s*[·•]\\s*(.+)$", RegexOption.IGNORE_CASE).matchEntire(text)
            val body = match?.groupValues?.get(2) ?: text
            val lower = body.lowercase(java.util.Locale.ROOT)
            val kind = when {
                " then " in lower -> "unknown"
                lower.startsWith("make a u-turn") || lower.startsWith("make a u turn") -> "uturn"
                lower.startsWith("turn left") -> "left"
                lower.startsWith("turn right") -> "right"
                lower.startsWith("continue straight") -> "straight"
                "roundabout" in lower -> "roundabout"
                else -> "unknown"
            }
            return ManeuverCue(kind, match?.groupValues?.get(1), body)
        }
    }
}

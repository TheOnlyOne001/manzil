package app.manzil.voice

/** Small explicit commands for the native voice-search slice, not an LLM or safety judgement. */
enum class PandaCommand {
    NIGHT, FLOOD, MAP, LOCATE, SEARCH;
    companion object {
        fun parse(text:String):PandaCommand {
            val q=text.trim().lowercase(java.util.Locale.ROOT).trimEnd('.', '!', '?')
            return when(q) {
                "night walk", "show night walk", "show lighting", "रात का नक्शा", "இரவு நடை", "రాత్రి నడక" -> NIGHT
                "show floods", "flood context", "show flood context", "बाढ़ दिखाओ", "வெள்ளம் காட்டு", "వరదలు చూపించు" -> FLOOD
                "map only", "hide layers", "सिर्फ नक्शा", "வரைபடம் மட்டும்", "మ్యాప్ మాత్రమే" -> MAP
                "my location", "where am i", "locate me", "मेरी लोकेशन", "என் இருப்பிடம்", "నా స్థానం" -> LOCATE
                else -> SEARCH
            }
        }
        fun searchText(text:String):String = text.trim().replace(Regex("^(find|search for|take me to|walk to|go to)\\s+",RegexOption.IGNORE_CASE),"")
    }
}

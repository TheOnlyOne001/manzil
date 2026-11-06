package app.manzil.offline.search

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One usable Text Search result. Coordinates are always finite, in range and not (0, 0). */
data class PlaceCandidate(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

/** Why a place search failed. Never carries credentials, request URLs or response bodies. */
enum class PlaceSearchFailureKind(val userText: String) {
    TIMEOUT("Google place search did not answer in time"),
    NETWORK("Could not reach Google place search"),
    KEY_REJECTED("Google rejected the app's API key or Places API (New) is not enabled"),
    QUOTA("Google place search quota exceeded"),
    BAD_REQUEST("Google rejected the place search"),
    SERVER("Google place search service error"),
    MALFORMED("Google returned unreadable place results"),
}

class PlaceSearchException(val kind: PlaceSearchFailureKind, val httpStatus: Int? = null) :
    Exception(kind.userText + (httpStatus?.let { " (HTTP $it)" } ?: ""))

/**
 * Pure request/response mapping for Places API (New) Text Search,
 * `POST https://places.googleapis.com/v1/places:searchText`. No I/O and no Android APIs beyond
 * org.json, so it is unit-testable on the JVM.
 */
object GooglePlacesProtocol {
    const val ENDPOINT = "https://places.googleapis.com/v1/places:searchText"

    /** Mandatory for searchText (no default field set); only the fields the app uses. */
    const val FIELD_MASK =
        "places.id,places.displayName,places.formattedAddress,places.location,places.businessStatus"

    const val LANGUAGE_CODE = "en"
    const val REGION_CODE = "IN"
    const val MAX_RESULTS = 8

    /** The API maximum for a circle bias. */
    const val BIAS_RADIUS_METERS = 50_000.0

    /** Bias centre when the caller has no usable fix: central Chennai. */
    const val DEFAULT_LATITUDE = 13.0
    const val DEFAULT_LONGITUDE = 80.2

    /** Keeps a pasted paragraph from becoming a huge request; real place queries are short. */
    const val MAX_QUERY_CHARS = 200

    private val WHITESPACE = Regex("\\s+")

    /** Trimmed, whitespace-collapsed, length-capped query; null when nothing searchable remains. */
    fun normalizeQuery(query: String?): String? {
        val text = query.orEmpty().replace(WHITESPACE, " ").trim().take(MAX_QUERY_CHARS).trim()
        return text.ifEmpty { null }
    }

    /** True for a finite, in-range coordinate pair that is not the (0, 0) "missing value" sentinel. */
    fun isUsableCoordinate(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) return false
        return !(latitude == 0.0 && longitude == 0.0)
    }

    /** The supplied position when usable, otherwise the Chennai default. */
    fun biasCenter(latitude: Double?, longitude: Double?): Pair<Double, Double> =
        if (isUsableCoordinate(latitude, longitude)) latitude!! to longitude!!
        else DEFAULT_LATITUDE to DEFAULT_LONGITUDE

    /**
     * JSON body for searchText. [query] must already be normalised (see [normalizeQuery]);
     * an empty textQuery is rejected by the API, so callers should not send one.
     */
    fun requestBody(query: String, latitude: Double?, longitude: Double?): String {
        val (lat, lon) = biasCenter(latitude, longitude)
        val circle = JSONObject()
            .put("center", JSONObject().put("latitude", lat).put("longitude", lon))
            .put("radius", BIAS_RADIUS_METERS)
        return JSONObject()
            .put("textQuery", query)
            .put("languageCode", LANGUAGE_CODE)
            .put("regionCode", REGION_CODE)
            .put("maxResultCount", MAX_RESULTS)
            .put("locationBias", JSONObject().put("circle", circle))
            .toString()
    }

    /**
     * Maps an HTTP status (plus the Google error body, used only for classification and never
     * surfaced) to a failure kind; 2xx returns null.
     */
    fun failureFor(status: Int, errorBody: String? = null): PlaceSearchFailureKind? {
        if (status in 200..299) return null
        val signals = errorSignals(errorBody)
        return when {
            status == 429 || "RESOURCE_EXHAUSTED" in signals || "RATE_LIMIT_EXCEEDED" in signals -> PlaceSearchFailureKind.QUOTA
            status == 401 || status == 403 -> PlaceSearchFailureKind.KEY_REJECTED
            // A bad or expired key comes back as 400 INVALID_ARGUMENT with reason API_KEY_INVALID.
            signals.any { it.startsWith("API_KEY_") } || "SERVICE_DISABLED" in signals ||
                "BILLING_DISABLED" in signals -> PlaceSearchFailureKind.KEY_REJECTED
            status == 408 -> PlaceSearchFailureKind.TIMEOUT
            status in 400..499 -> PlaceSearchFailureKind.BAD_REQUEST
            else -> PlaceSearchFailureKind.SERVER
        }
    }

    /**
     * Parses a 2xx body. An empty body, `{}` or an empty `places` array is a valid "no results".
     * Entries that are permanently closed, lack an id or name, have unusable coordinates, or
     * repeat an earlier id are skipped. Throws [PlaceSearchException] MALFORMED only when the
     * body itself is not the expected JSON shape.
     */
    fun parse(body: String?): List<PlaceCandidate> {
        if (body.isNullOrBlank()) return emptyList()
        try {
            val root = JSONObject(body)
            if (!root.has("places") || root.isNull("places")) return emptyList()
            val places = root.get("places") as? JSONArray
                ?: throw PlaceSearchException(PlaceSearchFailureKind.MALFORMED)
            val seen = HashSet<String>()
            val out = ArrayList<PlaceCandidate>()
            for (i in 0 until places.length()) {
                if (out.size >= MAX_RESULTS) break
                val candidate = places.optJSONObject(i)?.let(::candidate) ?: continue
                if (seen.add(candidate.id)) out += candidate
            }
            return out
        } catch (e: PlaceSearchException) {
            throw e
        } catch (e: JSONException) {
            throw PlaceSearchException(PlaceSearchFailureKind.MALFORMED)
        }
    }

    private fun candidate(place: JSONObject): PlaceCandidate? {
        if (place.optString("businessStatus") == "PERMANENTLY_CLOSED") return null
        val id = place.stringOrNull("id") ?: return null
        val name = place.optJSONObject("displayName")?.stringOrNull("text") ?: return null
        val address = place.stringOrNull("formattedAddress").orEmpty()
        val location = place.optJSONObject("location") ?: return null
        val lat = location.numberOrNull("latitude") ?: return null
        val lon = location.numberOrNull("longitude") ?: return null
        if (!isUsableCoordinate(lat, lon)) return null
        return PlaceCandidate(id, name, address, lat, lon)
    }

    /** Non-blank trimmed string value; JSON null, numbers and objects are rejected. */
    private fun JSONObject.stringOrNull(key: String): String? =
        (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    /** Strict JSON number; quoted numbers and booleans are treated as malformed. */
    private fun JSONObject.numberOrNull(key: String): Double? = (opt(key) as? Number)?.toDouble()

    /** Upper-case status and reason tokens from a Google error body; empty when unreadable. */
    private fun errorSignals(errorBody: String?): Set<String> {
        if (errorBody.isNullOrBlank()) return emptySet()
        return try {
            val error = JSONObject(errorBody).optJSONObject("error") ?: return emptySet()
            val out = HashSet<String>()
            (error.opt("status") as? String)?.let { out += it.uppercase() }
            val details = error.optJSONArray("details")
            if (details != null) {
                for (i in 0 until details.length()) {
                    (details.optJSONObject(i)?.opt("reason") as? String)?.let { out += it.uppercase() }
                }
            }
            out
        } catch (e: JSONException) {
            emptySet()
        }
    }
}

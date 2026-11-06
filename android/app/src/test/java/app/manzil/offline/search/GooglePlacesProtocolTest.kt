package app.manzil.offline.search

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GooglePlacesProtocolTest {
    private fun place(
        id: Any? = "ChIJ1",
        name: Any? = "Chennai Central",
        address: Any? = "Kannappar Thidal, Periyamet, Chennai",
        lat: Any? = 13.0827,
        lon: Any? = 80.2757,
        status: String? = "OPERATIONAL",
    ) = JSONObject().apply {
        id?.let { put("id", it) }
        name?.let { put("displayName", JSONObject().put("text", it).put("languageCode", "en")) }
        address?.let { put("formattedAddress", it) }
        if (lat != null || lon != null) put("location", JSONObject().apply {
            lat?.let { put("latitude", it) }; lon?.let { put("longitude", it) }
        })
        status?.let { put("businessStatus", it) }
    }

    private fun body(vararg places: JSONObject) =
        JSONObject().put("places", JSONArray().apply { places.forEach { put(it) } }).toString()

    private fun assertMalformed(body: String) {
        val e = assertThrows(PlaceSearchException::class.java) { GooglePlacesProtocol.parse(body) }
        assertEquals(PlaceSearchFailureKind.MALFORMED, e.kind)
    }

    // ---- request ----

    @Test
    fun requestBodyCarriesQueryLanguageRegionCountAndSuppliedBias() {
        val json = JSONObject(GooglePlacesProtocol.requestBody("Marina Beach", 13.05, 80.28))
        assertEquals("Marina Beach", json.getString("textQuery"))
        assertEquals("en", json.getString("languageCode"))
        assertEquals("IN", json.getString("regionCode"))
        assertEquals(8, json.getInt("maxResultCount"))
        val circle = json.getJSONObject("locationBias").getJSONObject("circle")
        assertEquals(50_000.0, circle.getDouble("radius"), 0.0)
        assertEquals(13.05, circle.getJSONObject("center").getDouble("latitude"), 0.0)
        assertEquals(80.28, circle.getJSONObject("center").getDouble("longitude"), 0.0)
        assertFalse(json.has("locationRestriction"))
    }

    @Test
    fun requestBodyFallsBackToChennaiForMissingOrInvalidCoordinates() {
        val bad = listOf<Pair<Double?, Double?>>(
            null to null, 13.0 to null, null to 80.0, 0.0 to 0.0, Double.NaN to 80.0,
            13.0 to Double.POSITIVE_INFINITY, 91.0 to 80.0, 13.0 to -181.0,
        )
        for ((lat, lon) in bad) {
            val center = JSONObject(GooglePlacesProtocol.requestBody("tea", lat, lon))
                .getJSONObject("locationBias").getJSONObject("circle").getJSONObject("center")
            assertEquals("lat for $lat,$lon", 13.0, center.getDouble("latitude"), 0.0)
            assertEquals("lon for $lat,$lon", 80.2, center.getDouble("longitude"), 0.0)
        }
    }

    @Test
    fun requestBodyEscapesQuotesAndUnicode() {
        val q = "\"Saravana\" பவன் \\ cafe"
        assertEquals(q, JSONObject(GooglePlacesProtocol.requestBody(q, null, null)).getString("textQuery"))
    }

    @Test
    fun fieldMaskIsExactlyTheRenderedFields() {
        assertEquals(
            "places.id,places.displayName,places.formattedAddress,places.location,places.businessStatus",
            GooglePlacesProtocol.FIELD_MASK,
        )
        assertEquals("https://places.googleapis.com/v1/places:searchText", GooglePlacesProtocol.ENDPOINT)
    }

    @Test
    fun normalizeQueryTrimsCollapsesCapsAndRejectsBlank() {
        assertEquals("anna nagar tower", GooglePlacesProtocol.normalizeQuery("  anna \n nagar\t tower "))
        assertNull(GooglePlacesProtocol.normalizeQuery(""))
        assertNull(GooglePlacesProtocol.normalizeQuery(" \n\t "))
        assertNull(GooglePlacesProtocol.normalizeQuery(null))
        assertEquals(GooglePlacesProtocol.MAX_QUERY_CHARS, GooglePlacesProtocol.normalizeQuery("x".repeat(5000))!!.length)
    }

    // ---- parse ----

    @Test
    fun parsesValidPlacesInOrder() {
        val result = GooglePlacesProtocol.parse(body(
            place(),
            place(id = "ChIJ2", name = " Egmore Station ", address = " Egmore ", lat = 13.0732, lon = 80.2609),
        ))
        assertEquals(
            listOf(
                PlaceCandidate("ChIJ1", "Chennai Central", "Kannappar Thidal, Periyamet, Chennai", 13.0827, 80.2757),
                PlaceCandidate("ChIJ2", "Egmore Station", "Egmore", 13.0732, 80.2609),
            ),
            result,
        )
    }

    @Test
    fun emptyResponsesAreEmptyNotErrors() {
        assertEquals(emptyList<PlaceCandidate>(), GooglePlacesProtocol.parse(""))
        assertEquals(emptyList<PlaceCandidate>(), GooglePlacesProtocol.parse("   "))
        assertEquals(emptyList<PlaceCandidate>(), GooglePlacesProtocol.parse(null))
        assertEquals(emptyList<PlaceCandidate>(), GooglePlacesProtocol.parse("{}"))
        assertEquals(emptyList<PlaceCandidate>(), GooglePlacesProtocol.parse("{\"places\":[]}"))
        assertEquals(emptyList<PlaceCandidate>(), GooglePlacesProtocol.parse("{\"places\":null}"))
    }

    @Test
    fun malformedBodiesThrowTyped() {
        assertMalformed("<html>oops</html>")
        assertMalformed("[]")
        assertMalformed("{\"places\":\"nope\"}")
        assertMalformed("{\"places\":{}}")
        assertMalformed("{\"places\":[")
    }

    @Test
    fun filtersPermanentlyClosedButKeepsTemporarilyClosedAndUnknown() {
        val result = GooglePlacesProtocol.parse(body(
            place(id = "closed", status = "PERMANENTLY_CLOSED"),
            place(id = "temp", status = "CLOSED_TEMPORARILY"),
            place(id = "none", status = null),
        ))
        assertEquals(listOf("temp", "none"), result.map { it.id })
    }

    @Test
    fun deduplicatesPlaceIdsKeepingFirst() {
        val result = GooglePlacesProtocol.parse(body(
            place(id = "A", name = "First"),
            place(id = "B"),
            place(id = "A", name = "Second"),
            place(id = " A ", name = "Padded"),
        ))
        assertEquals(listOf("A" to "First", "B" to "Chennai Central"), result.map { it.id to it.name })
    }

    @Test
    fun skipsWrongOrMissingCoordinates() {
        val result = GooglePlacesProtocol.parse(body(
            place(id = "zero", lat = 0.0, lon = 0.0),
            place(id = "noLoc", lat = null, lon = null),
            place(id = "noLon", lon = null),
            place(id = "badLat", lat = 95.0),
            place(id = "badLon", lon = 181.0),
            place(id = "string", lat = "13.08"),
            place(id = "bool", lon = true),
            place(id = "ok"),
        ))
        assertEquals(listOf("ok"), result.map { it.id })
    }

    @Test
    fun skipsEntriesWithoutIdOrNameAndToleratesMissingAddress() {
        val noName = place(id = "noName").apply { remove("displayName") }
        val blankText = place(id = "blank", name = "   ")
        val result = GooglePlacesProtocol.parse(body(
            place(id = null),
            place(id = ""),
            place(id = 42),
            noName,
            blankText,
            place(id = "noAddr", address = null),
        ))
        assertEquals(listOf(PlaceCandidate("noAddr", "Chennai Central", "", 13.0827, 80.2757)), result)
    }

    @Test
    fun nonObjectEntriesAreSkipped() {
        val raw = "{\"places\":[1,\"x\",null," + place(id = "ok") + "]}"
        assertEquals(listOf("ok"), GooglePlacesProtocol.parse(raw).map { it.id })
    }

    @Test
    fun capsResultsAtEight() {
        val many = (1..12).map { place(id = "P$it") }.toTypedArray()
        assertEquals((1..8).map { "P$it" }, GooglePlacesProtocol.parse(body(*many)).map { it.id })
    }

    // ---- errors ----

    private fun googleError(code: Int, status: String, reason: String? = null) = JSONObject().put(
        "error",
        JSONObject().put("code", code).put("message", "secret detail AIzaSyFAKEKEY").put("status", status).apply {
            reason?.let {
                put("details", JSONArray().put(JSONObject()
                    .put("@type", "type.googleapis.com/google.rpc.ErrorInfo").put("reason", it)))
            }
        },
    ).toString()

    @Test
    fun successStatusesAreNotFailures() {
        assertNull(GooglePlacesProtocol.failureFor(200))
        assertNull(GooglePlacesProtocol.failureFor(204, ""))
    }

    @Test
    fun classifiesHttpFailures() {
        assertEquals(PlaceSearchFailureKind.KEY_REJECTED, GooglePlacesProtocol.failureFor(401))
        assertEquals(PlaceSearchFailureKind.KEY_REJECTED,
            GooglePlacesProtocol.failureFor(403, googleError(403, "PERMISSION_DENIED", "API_KEY_ANDROID_APP_BLOCKED")))
        assertEquals(PlaceSearchFailureKind.KEY_REJECTED,
            GooglePlacesProtocol.failureFor(400, googleError(400, "INVALID_ARGUMENT", "API_KEY_INVALID")))
        assertEquals(PlaceSearchFailureKind.KEY_REJECTED,
            GooglePlacesProtocol.failureFor(400, googleError(400, "FAILED_PRECONDITION", "BILLING_DISABLED")))
        assertEquals(PlaceSearchFailureKind.BAD_REQUEST,
            GooglePlacesProtocol.failureFor(400, googleError(400, "INVALID_ARGUMENT")))
        assertEquals(PlaceSearchFailureKind.BAD_REQUEST, GooglePlacesProtocol.failureFor(404, "not json"))
        assertEquals(PlaceSearchFailureKind.QUOTA, GooglePlacesProtocol.failureFor(429))
        assertEquals(PlaceSearchFailureKind.QUOTA,
            GooglePlacesProtocol.failureFor(403, googleError(403, "RESOURCE_EXHAUSTED", "RATE_LIMIT_EXCEEDED")))
        assertEquals(PlaceSearchFailureKind.TIMEOUT, GooglePlacesProtocol.failureFor(408))
        assertEquals(PlaceSearchFailureKind.SERVER, GooglePlacesProtocol.failureFor(500, "<html/>"))
        assertEquals(PlaceSearchFailureKind.SERVER, GooglePlacesProtocol.failureFor(503))
        assertEquals(PlaceSearchFailureKind.SERVER, GooglePlacesProtocol.failureFor(302))
    }

    @Test
    fun exceptionMessageIsConciseAndNeverEchoesBodyOrKey() {
        val errorBody = googleError(403, "PERMISSION_DENIED", "API_KEY_INVALID")
        val kind = GooglePlacesProtocol.failureFor(403, errorBody)!!
        val e = PlaceSearchException(kind, 403)
        assertEquals(kind.userText + " (HTTP 403)", e.message)
        assertFalse(e.message!!.contains("AIza"))
        assertFalse(e.message!!.contains("secret"))
        assertEquals(PlaceSearchFailureKind.TIMEOUT.userText, PlaceSearchException(PlaceSearchFailureKind.TIMEOUT).message)
        for (kindValue in PlaceSearchFailureKind.values()) {
            assertTrue(kindValue.userText.length in 10..90)
        }
    }
}

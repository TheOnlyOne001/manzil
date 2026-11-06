package app.manzil.system

import app.manzil.model.Destination
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoUriTest {
    @Test
    fun destinationUriUsesDocumentedGeoFormWithEncodedLabel() {
        val uri = GeoUri.forDestination(Destination("Chennai Central", 13.0827, 80.2752))
        assertEquals("geo:13.082700,80.275200?q=13.082700,80.275200(Chennai%20Central)", uri)
    }

    @Test
    fun labelEncodingEscapesReservedAndNonAsciiCharacters() {
        assertEquals("A%26B%20%28x%29", GeoUri.encode("A&B (x)"))
        assertEquals("%E0%AE%9A", GeoUri.encode("ச"))
        assertEquals("a-b_c.d~e", GeoUri.encode("a-b_c.d~e"))
    }
}

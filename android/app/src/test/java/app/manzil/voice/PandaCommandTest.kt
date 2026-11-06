package app.manzil.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class PandaCommandTest {
    @Test fun explicitCommandsOnly() {
        assertEquals(PandaCommand.NIGHT,PandaCommand.parse("Show night walk!"))
        assertEquals(PandaCommand.FLOOD,PandaCommand.parse("வெள்ளம் காட்டு"))
        assertEquals(PandaCommand.LOCATE,PandaCommand.parse("मेरी लोकेशन"))
        assertEquals(PandaCommand.MAP,PandaCommand.parse("మ్యాప్ మాత్రమే"))
    }
    @Test fun placeNamesAndUnknownRequestsStaySearches() {
        assertEquals(PandaCommand.SEARCH,PandaCommand.parse("Night walk cafe"))
        assertEquals(PandaCommand.SEARCH,PandaCommand.parse("call police"))
        assertEquals("Marina Beach",PandaCommand.searchText("Take me to Marina Beach"))
        assertEquals("தமிழ்",PandaCommand.searchText("தமிழ்"))
    }
}

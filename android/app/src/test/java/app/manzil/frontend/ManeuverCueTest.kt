package app.manzil.frontend
import org.junit.Assert.*
import org.junit.Test
class ManeuverCueTest {
 @Test fun splitsObservedDistanceOnly() { val c=ManeuverCue.parse("70 m · Turn left onto Road"); assertEquals("left",c.kind);assertEquals("70 m",c.distance);assertEquals("Turn left onto Road",c.instruction) }
 @Test fun doesNotInferFromRoadName() { assertEquals("unknown",ManeuverCue.parse("Head toward Right Lane").kind); assertEquals("unknown",ManeuverCue.parse("Turn left then turn right").kind) }
 @Test fun unknownLanguageIsPreserved() { val text="அடுத்த வழி";assertEquals(text,ManeuverCue.parse(text).instruction);assertEquals("unknown",ManeuverCue.parse(text).kind) }
}

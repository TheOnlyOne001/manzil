package app.manzil.offline
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class HazardParserTest {
 private fun report(observed:String,expiry:String,evidence:String="reported")="""{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[80.2,13.0]},"properties":{"id":"1","title":"Test fixture","layer":"reports","evidence":"$evidence","observed_at":"$observed","expires_at":"$expiry"}}]}"""
 @Test fun hidesExpiredFutureAndUnqualifiedReports(){val now=Instant.parse("2026-11-06T01:00:00Z")
  assertEquals(1,HazardParser.parse(report("2026-11-06T00:30:00Z","2026-11-06T02:00:00Z"),now).size)
  assertTrue(HazardParser.parse(report("2026-11-06T00:00:00Z","2026-11-06T00:59:00Z"),now).isEmpty())
  assertTrue(HazardParser.parse(report("2026-11-06T01:30:00Z","2026-11-06T02:00:00Z"),now).isEmpty())
  assertTrue(HazardParser.parse(report("2026-11-06T00:30:00Z","2026-11-06T02:00:00Z","historical"),now).isEmpty())
 }

 @Test fun historicalDepthIsNeverUsedAsLiveReportDepth(){
  fun feed(depth:String,kind:String="survey_point")="""{"type":"FeatureCollection","features":[{"geometry":{"type":"Point","coordinates":[80.2,13.0]},"properties":{"id":"s","title":"Survey","layer":"history","evidence":"historical","kind":"$kind","historical_depth_cm":$depth}}]}"""
  assertEquals(17.8,HazardParser.parse(feed("17.8")).single().historicalDepthCm!!,0.001)
  assertNull(HazardParser.parse(feed("-1")).single().historicalDepthCm)
  assertNull(HazardParser.parse(feed("null")).single().historicalDepthCm)
  assertNull(HazardParser.parse(feed("17.8","hotspot")).single().historicalDepthCm)
 }
 @Test fun modelledRiskRequiresFreshQualifiedEvidence(){
  val now=Instant.parse("2026-11-06T01:00:00Z")
  fun risk(expiry:String,band:String="high")=report("2026-11-06T00:30:00Z",expiry,"modelled").replace("\"layer\":\"reports\"","\"layer\":\"risk\",\"risk_band\":\"$band\"")
  assertEquals(1,HazardParser.parse(risk("2026-11-06T02:00:00Z"),now).size)
  assertTrue(HazardParser.parse(risk("2026-11-06T00:59:00Z"),now).isEmpty())
  assertTrue(HazardParser.parse(risk("2026-11-06T02:00:00Z","unknown"),now).isEmpty())
 }

 @Test fun nightEvidencePreservesUnknownAndRejectsUnqualifiedKinds(){
  fun feed(kind:String,evidence:String="mapped")="""{"type":"FeatureCollection","features":[{"geometry":{"type":"Point","coordinates":[80.2,13.0]},"properties":{"id":"n","title":"Street","layer":"night","evidence":"$evidence","kind":"$kind","road_context":{"approximate":true,"type":"LineString","coordinates":[[80.2,13.0],[80.21,13.0]]}}}]}"""
  assertEquals("night_unknown",HazardParser.parse(feed("night_unknown")).single().kind)
  assertEquals(2,HazardParser.parse(feed("night_lit")).single().road.size)
  assertTrue(HazardParser.parse(feed("guaranteed_safe")).isEmpty())
  assertTrue(HazardParser.parse(feed("night_lit","inferred")).isEmpty())
 }
}

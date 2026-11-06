package app.manzil.offline.routing

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class RoutingPackIntegrityTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun rejectsTruncationAndEqualLengthCorruption() {
        val graph=temporary.newFile("E80_N10.rd5")
        val content=ByteArray(2048){(it%251).toByte()}
        val hash=MessageDigest.getInstance("SHA-256").digest(content).joinToString(""){"%02x".format(it.toInt() and 255)}
        graph.writeBytes(content)
        File(graph.path+".json").writeText(JSONObject().put("bytes",content.size).put("sha256",hash).toString())
        assertNull(RoutingPackIntegrity.issue(graph))
        graph.writeBytes(content.copyOf(1200))
        assertTrue(RoutingPackIntegrity.issue(graph)!!.contains("incomplete"))
        graph.writeBytes(content.copyOf().apply{this[0]=99})
        graph.setLastModified(graph.lastModified()+2000)
        assertTrue(RoutingPackIntegrity.issue(graph)!!.contains("checksum"))
    }
    @Test fun rejectsFileWithoutManifest(){val graph=temporary.newFile("missing-manifest.rd5");graph.writeBytes(ByteArray(2048));assertNotNull(RoutingPackIntegrity.issue(graph))}
}

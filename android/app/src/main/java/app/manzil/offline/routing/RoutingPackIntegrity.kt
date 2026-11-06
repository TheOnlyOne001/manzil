package app.manzil.offline.routing

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Size and digest verified once per unchanged local file. No network dependency. */
object RoutingPackIntegrity {
 private val verified = mutableMapOf<String, String>()
 @Synchronized fun issue(file: File): String? {
  if (!file.isFile) return "${file.name} is missing"
  val metadata=File(file.path+".json")
  if (!metadata.isFile) return "${file.name} has no integrity manifest"
  return try {
   val j=JSONObject(metadata.readText());val expected=j.getLong("bytes");val hash=j.getString("sha256")
   if(expected<=1024 || file.length()!=expected) return "${file.name} is incomplete"
   if(!hash.matches(Regex("[a-fA-F0-9]{64}"))) return "${file.name} has an invalid checksum"
   val key="${file.length()}:${file.lastModified()}:${metadata.lastModified()}:$hash"
   if(verified[file.absolutePath]==key) return null
   val digest=MessageDigest.getInstance("SHA-256")
   file.inputStream().buffered().use { input -> val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)} }
   val actual=digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
   if(!actual.equals(hash,true)) "${file.name} failed checksum verification" else {verified[file.absolutePath]=key;null}
  }catch(e:Exception){"${file.name} integrity check failed: ${e.message}"}
 }
}

package app.manzil.offline

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Local-only installation and repair. Run on Dispatchers.IO before opening map/search data. */
object BundledOfflinePack {
    fun install(context: Context, root: File) {
        val index = context.assets.open("offline-pack/index.tsv").bufferedReader().use { it.readLines() }
        val base = root.canonicalPath + File.separator
        for (line in index.filter { it.isNotBlank() }) {
            val parts = line.split('\t'); require(parts.size == 3)
            val target = File(root, parts[0]); require(target.canonicalPath.startsWith(base))
            val size = parts[1].toLong(); val expected = parts[2]
            if (target.isFile && target.length() == size && hash(target) == expected) continue
            target.parentFile!!.mkdirs()
            val temporary = File(target.path + ".installing")
            try {
                context.assets.open("offline-pack/" + parts[0]).use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(temporary.length() == size && hash(temporary) == expected) { "Offline file failed verification: ${parts[0]}" }
                check(temporary.renameTo(target)) { "Cannot install offline file: ${parts[0]}" }
            } finally { temporary.delete() }
        }
    }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

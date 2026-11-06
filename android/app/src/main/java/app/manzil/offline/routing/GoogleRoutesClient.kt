package app.manzil.offline.routing

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/**
 * Credentials read at runtime from the manifest `com.google.android.geo.API_KEY` placeholder that
 * the build already fills from local.properties MAPS_API_KEY. [toString] is redacted; never log
 * [apiKey] or any value derived from it.
 */
class GoogleRoutesCredentials private constructor(
    val apiKey: String,
    val packageName: String,
    /** Upper-case SHA-1 hex of the signing cert, no colons, for Android-restricted keys. */
    val certSha1: String?,
) {
    override fun toString() = "GoogleRoutesCredentials(<redacted>)"

    companion object {
        private const val META_KEY = "com.google.android.geo.API_KEY"
        private const val PLACEHOLDER = "MANZIL_KEY_NOT_CONFIGURED"

        /** Touches PackageManager; call off the main thread. */
        @Suppress("DEPRECATION")
        fun fromManifest(context: Context): GoogleRoutesCredentials? = runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            val key = info.metaData?.getString(META_KEY)?.trim().orEmpty()
            if (key.isEmpty() || key == PLACEHOLDER) null
            else GoogleRoutesCredentials(key, context.packageName, signingSha1(context))
        }.getOrNull()

        @Suppress("DEPRECATION")
        private fun signingSha1(context: Context): String? = runCatching {
            val pm = context.packageManager
            val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                    ?: return@runCatching null
                val signers = if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
                signers?.lastOrNull()?.toByteArray()
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()?.toByteArray()
            } ?: return@runCatching null
            MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02X".format(it.toInt() and 0xff) }
        }.getOrNull()
    }
}

/**
 * Direct HTTPS call to Routes API computeRoutes (no local backend / USB bridge needed).
 * Bounded by [totalTimeoutMs] wall clock (covering DNS, which socket timeouts do not bound) in
 * addition to socket timeouts. The blocking exchange runs on an IO worker; on timeout or coroutine
 * cancellation the caller resumes immediately and the socket is aborted in the background, so a
 * stuck lookup can never hold the route request. Cancellation surfaces only as CancellationException.
 */
class GoogleRoutesClient(
    private val credentials: GoogleRoutesCredentials,
    private val totalTimeoutMs: Long = 10_000,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 8_000,
) : OnlineRouteSource {
    private val maxResponseBytes = 4 * 1024 * 1024
    private val io = Dispatchers.IO.asExecutor()

    override suspend fun route(origin: RouteCoordinate, destination: RouteCoordinate): OfflineRoute {
        val started = SystemClock.elapsedRealtime()
        val payload = GoogleRoutesProtocol.requestBody(origin, destination)
        val response = try {
            withTimeoutOrNull(totalTimeoutMs) { execute(payload) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            currentCoroutineContext().ensureActive()
            throw OnlineRoutingException(OnlineFailureKind.TIMEOUT)
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw OnlineRoutingException(OnlineFailureKind.NETWORK)
        }
        if (response == null) throw OnlineRoutingException(OnlineFailureKind.TIMEOUT)
        currentCoroutineContext().ensureActive()
        val (status, body) = response
        GoogleRoutesProtocol.failureForStatus(status)?.let { throw OnlineRoutingException(it, status) }
        return withContext(Dispatchers.Default) { GoogleRoutesProtocol.parse(body, SystemClock.elapsedRealtime() - started) }
    }

    private suspend fun execute(payload: String): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        val connection = URL(GoogleRoutesProtocol.ENDPOINT).openConnection() as HttpsURLConnection
        // Socket close may write TLS close_notify; keep it off the cancelling (possibly main) thread.
        continuation.invokeOnCancellation { io.execute { runCatching { connection.disconnect() } } }
        io.execute {
            // Resuming an already-cancelled continuation is ignored, so a late answer is dropped.
            continuation.resumeWith(runCatching { exchange(connection, payload) })
        }
    }

    /** Blocking. Returns status and body (error bodies are read but never logged or surfaced). */
    private fun exchange(connection: HttpsURLConnection, payload: String): Pair<Int, String> {
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Goog-Api-Key", credentials.apiKey)
            connection.setRequestProperty("X-Goog-FieldMask", GoogleRoutesProtocol.FIELD_MASK)
            // Lets an Android-app-restricted key authorise this REST call; harmless otherwise.
            connection.setRequestProperty("X-Android-Package", credentials.packageName)
            credentials.certSha1?.let { connection.setRequestProperty("X-Android-Cert", it) }
            val bytes = payload.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readCapped(it) }.orEmpty()
            return status to body
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > maxResponseBytes) throw OnlineRoutingException(OnlineFailureKind.MALFORMED)
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

package app.manzil.offline.search

import app.manzil.offline.routing.GoogleRoutesCredentials
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
import javax.net.ssl.HttpsURLConnection

/**
 * Direct HTTPS call to Places API (New) Text Search, reusing the Routes credentials (same manifest
 * key, package name and signing SHA-1 for Android-restricted keys). Never logs or surfaces the key
 * or any response body.
 *
 * Bounded by [totalTimeoutMs] wall clock (covering DNS, which socket timeouts do not bound) in
 * addition to socket timeouts. The blocking exchange runs on an IO worker; on timeout or coroutine
 * cancellation the caller resumes immediately and the socket is closed in the background, so a
 * stuck lookup can never hold the caller and nothing blocks the main thread. Cancellation surfaces
 * only as CancellationException; every other failure is a [PlaceSearchException].
 */
class GooglePlacesClient(
    private val credentials: GoogleRoutesCredentials,
    private val totalTimeoutMs: Long = 12_000,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 8_000,
) {
    private val maxResponseBytes = 1024 * 1024
    private val maxErrorBytes = 64 * 1024
    private val io = Dispatchers.IO.asExecutor()

    /**
     * Searches for [query], biased to a 50 km circle around the given position (or Chennai when
     * it is missing or invalid). A blank query returns an empty list without a network call.
     */
    suspend fun search(query: String, latitude: Double?, longitude: Double?): List<PlaceCandidate> {
        val text = GooglePlacesProtocol.normalizeQuery(query) ?: return emptyList()
        val payload = GooglePlacesProtocol.requestBody(text, latitude, longitude)
        val response = try {
            withTimeoutOrNull(totalTimeoutMs) { execute(payload) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlaceSearchException) {
            throw e
        } catch (e: SocketTimeoutException) {
            currentCoroutineContext().ensureActive()
            throw PlaceSearchException(PlaceSearchFailureKind.TIMEOUT)
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw PlaceSearchException(PlaceSearchFailureKind.NETWORK)
        } catch (e: Exception) {
            // e.g. SecurityException without INTERNET permission; never pass the raw message on.
            currentCoroutineContext().ensureActive()
            throw PlaceSearchException(PlaceSearchFailureKind.NETWORK)
        }
        if (response == null) throw PlaceSearchException(PlaceSearchFailureKind.TIMEOUT)
        currentCoroutineContext().ensureActive()
        val (status, body) = response
        GooglePlacesProtocol.failureFor(status, body)?.let { throw PlaceSearchException(it, status) }
        return withContext(Dispatchers.Default) { GooglePlacesProtocol.parse(body) }
    }

    private suspend fun execute(payload: String): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        val connection = URL(GooglePlacesProtocol.ENDPOINT).openConnection() as HttpsURLConnection
        // Socket close may write TLS close_notify; keep it off the cancelling (possibly main) thread.
        continuation.invokeOnCancellation { io.execute { runCatching { connection.disconnect() } } }
        io.execute {
            // Resuming an already-cancelled continuation is ignored, so a late answer is dropped.
            continuation.resumeWith(runCatching { exchange(connection, payload) })
        }
    }

    /** Blocking. Returns status and body (error bodies are read only to classify the failure). */
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
            connection.setRequestProperty("X-Goog-FieldMask", GooglePlacesProtocol.FIELD_MASK)
            // Lets an Android-app-restricted key authorise this REST call; harmless otherwise.
            connection.setRequestProperty("X-Android-Package", credentials.packageName)
            credentials.certSha1?.let { connection.setRequestProperty("X-Android-Cert", it) }
            val bytes = payload.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val body = if (status in 200..299) {
                connection.inputStream?.use { readCapped(it, maxResponseBytes, failOnOverflow = true) }.orEmpty()
            } else {
                // Classification only: a truncated or unreadable error body just loses detail.
                runCatching { connection.errorStream?.use { readCapped(it, maxErrorBytes, failOnOverflow = false) } }
                    .getOrNull().orEmpty()
            }
            return status to body
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(input: InputStream, limit: Int, failOnOverflow: Boolean): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > limit) {
                if (failOnOverflow) throw PlaceSearchException(PlaceSearchFailureKind.MALFORMED)
                out.write(buffer, 0, limit - out.size())
                break
            }
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

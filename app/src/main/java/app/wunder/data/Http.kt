package app.wunder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

/** One response, possibly an old cached copy when the service failed. */
data class Fetched(val body: JSONObject?, val fetchedAt: Long?, val stale: Boolean, val error: String?)

data class Request(val url: String, val validate: (JSONObject) -> Boolean)

private const val DEFAULT_TTL = 600L
private const val TIMEOUT_MS = 8000

/**
 * When a response expires: Cache-Control max-age, then Expires, then the body's
 * expirationTimeUtc / metadata.expire_time_gmt, then [DEFAULT_TTL].
 */
fun expiryFrom(headers: Map<String, String>, body: JSONObject?, now: Long): Long {
    Regex("""(?<![-\w])max-age=(\d+)""").find(headers["cache-control"].orEmpty())?.let { return now + it.groupValues[1].toLong() }
    headers["expires"]?.let { h ->
        val t = runCatching { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(h)!!.time / 1000 }.getOrNull()
        if (t != null && t > now) return t
    }
    val exp = body?.opt("expirationTimeUtc")
    val expAt = when (exp) {
        is Number -> exp.toLong()
        is org.json.JSONArray -> List(exp.length()) { exp.opt(it) }.filterIsInstance<Number>().minOfOrNull { it.toLong() }
        else -> null
    }
    if (expAt != null && expAt > now) return expAt
    val meta = body?.optJSONObject("metadata")?.long("expire_time_gmt")
    if (meta != null && meta > now) return meta
    return now + DEFAULT_TTL
}

/** GETs JSON through a small disk cache that honors expiry and falls back to old copies on failure. */
class Http(private val dir: File, private val transport: (String) -> RawResponse = ::httpGet) {

    data class RawResponse(val status: Int, val headers: Map<String, String>, val body: String, val error: String?)

    private class Entry(val fetchedAt: Long, val expiresAt: Long, val body: JSONObject)

    /** The API key is stripped before hashing so it never shapes a filename. */
    private fun fileFor(url: String): File {
        val clean = url.replace(Regex("([?&])apiKey=[^&]*&?"), "$1").trimEnd('?', '&')
        val hash = MessageDigest.getInstance("SHA-1").digest(clean.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    private fun read(url: String): Entry? = runCatching {
        val o = JSONObject(fileFor(url).readText())
        Entry(o.getLong("fetchedAt"), o.getLong("expiresAt"), o.getJSONObject("body"))
    }.getOrNull()

    private fun write(url: String, entry: Entry) {
        dir.mkdirs()
        val file = fileFor(url)
        val tmp = File(dir, file.name + "." + System.nanoTime() + ".tmp")
        runCatching {
            tmp.writeText(JSONObject().put("fetchedAt", entry.fetchedAt).put("expiresAt", entry.expiresAt).put("body", entry.body).toString())
            tmp.renameTo(file)
        }
    }

    /** Fetch one request. [force] skips a still-fresh cached copy. */
    suspend fun get(req: Request, now: Long, force: Boolean = false): Fetched = withContext(Dispatchers.IO) {
        val cached = read(req.url)
        if (!force && cached != null && cached.expiresAt > now) {
            return@withContext Fetched(cached.body, cached.fetchedAt, stale = false, error = null)
        }
        val resp = transport(req.url)
        var body: JSONObject? = null
        val error = when {
            resp.error != null -> resp.error
            resp.status != 200 -> "HTTP ${resp.status}"
            else -> {
                val decoded = runCatching { JSONObject(resp.body) }.getOrNull()
                if (decoded != null && runCatching { req.validate(decoded) }.getOrDefault(false)) {
                    body = decoded
                    null
                } else {
                    "bad data"
                }
            }
        }
        val fresh = body
        when {
            fresh != null -> {
                write(req.url, Entry(now, expiryFrom(resp.headers, fresh, now), fresh))
                Fetched(fresh, now, stale = false, error = null)
            }
            cached != null -> Fetched(cached.body, cached.fetchedAt, stale = true, error = error)
            else -> Fetched(null, null, stale = false, error = error)
        }
    }

    /** Fetch every request in parallel; results come back keyed like [requests]. */
    suspend fun getAll(requests: Map<String, Request>, now: Long, force: Boolean = false): Map<String, Fetched> = coroutineScope {
        requests.mapValues { (_, r) -> async { get(r, now, force) } }.mapValues { it.value.await() }
    }

    /** An uncached GET, for searches. */
    suspend fun getUncached(url: String): JSONObject? = withContext(Dispatchers.IO) {
        val r = transport(url)
        if (r.error == null && r.status == 200) runCatching { JSONObject(r.body) }.getOrNull() else null
    }
}

fun httpGet(url: String): Http.RawResponse {
    val conn = URL(url).openConnection() as HttpURLConnection
    return try {
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept-Encoding", "gzip")
        val status = conn.responseCode
        val headers = conn.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(", ") }
        val raw = if (status in 200..299) conn.inputStream else conn.errorStream
        val stream = if (conn.contentEncoding == "gzip" && raw != null) java.util.zip.GZIPInputStream(raw) else raw
        Http.RawResponse(status, headers, stream?.bufferedReader()?.use { it.readText() }.orEmpty(), null)
    } catch (e: java.io.IOException) {
        Http.RawResponse(0, emptyMap(), "", e.message ?: e.javaClass.simpleName)
    } finally {
        conn.disconnect()
    }
}

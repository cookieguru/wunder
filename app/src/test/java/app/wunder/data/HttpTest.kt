package app.wunder.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class HttpTest {
    @Test fun expiryPrefersMaxAgeThenBody() {
        assertEquals(1060L, expiryFrom(mapOf("cache-control" to "public, max-age=60"), null, 1000))
        assertEquals(1500L, expiryFrom(emptyMap(), JSONObject("""{"expirationTimeUtc":[1700,1500]}"""), 1000))
        assertEquals(1600L, expiryFrom(emptyMap(), JSONObject("""{"metadata":{"expire_time_gmt":1600}}"""), 1000))
        assertEquals(1600L, expiryFrom(emptyMap(), null, 1000))
    }

    @Test fun servesFreshCacheAndFallsBackToStaleCopies() = runBlocking {
        val dir = Files.createTempDirectory("wx").toFile()
        var calls = 0
        var up = true
        val http = Http(dir) {
            calls++
            if (up) Http.RawResponse(200, mapOf("cache-control" to "max-age=60"), """{"validTimeUtc":1}""", null)
            else Http.RawResponse(0, emptyMap(), "", "timeout")
        }
        val req = Request("https://x.test/a?apiKey=secret", ::validObs)

        assertFalse(http.get(req, 1000).stale)
        http.get(req, 1030)
        assertEquals(1, calls)

        up = false
        val stale = http.get(req, 2000)
        assertTrue(stale.stale)
        assertEquals(1000L, stale.fetchedAt)
        assertEquals("timeout", stale.error)
        assertTrue(dir.listFiles()!!.none { "secret" in it.name })
    }

    @Test fun rejectsBadData() = runBlocking {
        val http = Http(Files.createTempDirectory("wx").toFile()) { Http.RawResponse(200, emptyMap(), """{"nope":1}""", null) }
        val r = http.get(Request("https://x.test/b", ::validObs), 1000)
        assertNull(r.body)
        assertEquals("bad data", r.error)
    }
}

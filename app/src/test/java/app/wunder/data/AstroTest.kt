package app.wunder.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AstroTest {
    private val body = JSONObject(javaClass.getResource("/astro.json")!!.readText())

    @Test fun parsesTwilightTimes() {
        assertNotNull(validAstro(body))
        val a = parseAstro(body, 0)
        assertEquals(isoEpoch("2026-10-28T07:16:51.557-07:00"), a.civilDawn)
        assertEquals(isoEpoch("2026-10-28T19:08:18.053-07:00"), a.nauticalDusk)
        assertNotNull(a.astroDawn)
    }
}

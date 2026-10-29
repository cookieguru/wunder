package app.wunder.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceTest {
    private fun point(neighborhood: String?, display: String, city: String) = JSONObject(
        """{"location":{"latitude":47.6,"longitude":-122.3,"city":"$city","displayName":"$display",
        "neighborhood":${neighborhood?.let { "\"$it\"" } ?: "null"},"adminDistrictCode":"WA","ianaTimeZone":"America/Los_Angeles"}}""",
    )

    @Test fun namesThePointAsSpecificallyAsPossible() {
        val p = Place.fromPoint(point("West Queen Anne", "Queen Anne", "Seattle"))!!
        assertEquals("West Queen Anne", p.name)
        assertEquals("Queen Anne, Seattle, WA", p.detail)
    }

    @Test fun skipsMissingAndRepeatedLevels() {
        val p = Place.fromPoint(point(null, "Seattle", "Seattle"))!!
        assertEquals("Seattle", p.name)
        assertEquals("WA", p.detail)
    }
}

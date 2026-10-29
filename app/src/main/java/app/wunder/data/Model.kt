package app.wunder.data

import org.json.JSONObject
import java.time.OffsetDateTime

data class Place(val name: String, val detail: String, val lat: Double, val lon: Double, val tz: String) {
    val geocode: String get() = Api.geocode(lat, lon)

    fun toJson(): JSONObject = JSONObject().put("name", name).put("detail", detail).put("lat", lat).put("lon", lon).put("tz", tz)

    companion object {
        fun fromJson(o: JSONObject?): Place? = o?.let {
            runCatching { Place(it.getString("name"), it.optString("detail"), it.getDouble("lat"), it.getDouble("lon"), it.getString("tz")) }.getOrNull()
        }

        /** A v3/location/point response. */
        /** A v3/location/point response, named as specifically as the API allows, e.g. "West Queen Anne" / "Queen Anne, Seattle, WA". */
        fun fromPoint(body: JSONObject): Place? {
            val l = body.obj("location") ?: return null
            val names = listOfNotNull(l.string("neighborhood"), l.string("displayName"), l.string("city")).distinct()
            val name = names.firstOrNull() ?: return null
            val region = l.string("adminDistrictCode") ?: l.string("adminDistrict") ?: l.string("country")
            val detail = (names.drop(1) + listOfNotNull(region)).joinToString(", ")
            return Place(name, detail, l.double("latitude") ?: return null, l.double("longitude") ?: return null, l.string("ianaTimeZone") ?: return null)
        }

        /** Every result of a v3/location/search response. */
        fun fromSearch(body: JSONObject): List<Place> {
            val l = body.obj("location") ?: return emptyList()
            val address = l.strings("address")
            val names = l.strings("displayName")
            val lats = l.doubles("latitude")
            val lons = l.doubles("longitude")
            val zones = l.strings("ianaTimeZone")
            return address.indices.mapNotNull { i ->
                val full = address[i] ?: return@mapNotNull null
                val name = names.at(i) ?: full.substringBefore(',')
                Place(name, full.removePrefix(name).removePrefix(", "), lats.at(i) ?: return@mapNotNull null, lons.at(i) ?: return@mapNotNull null, zones.at(i) ?: return@mapNotNull null)
            }.distinctBy { it.geocode }
        }
    }
}

private val HOURLY_KEYS = listOf(
    "validTimeUtc", "validTimeLocal", "temperature", "temperatureFeelsLike", "precipChance", "precipType",
    "relativeHumidity", "cloudCover", "pressureMeanSeaLevel", "qpf", "windSpeed", "windDirection",
    "windDirectionCardinal", "iconCode", "wxPhraseLong", "dayOrNight",
)

fun validHourly(b: JSONObject): Boolean {
    val n = b.optJSONArray("validTimeUtc")?.length() ?: 0
    return n > 0 && HOURLY_KEYS.all { b.optJSONArray(it)?.length() == n }
}

fun validDaily(b: JSONObject) = b.optJSONArray("validTimeLocal") != null && b.optJSONArray("daypart")?.optJSONObject(0) != null
fun validObs(b: JSONObject) = b.opt("validTimeUtc") is Number
fun validAstro(b: JSONObject) = b.optJSONArray("astroData")?.optJSONObject(0) != null
fun validAlmanac(b: JSONObject) = (b.optJSONArray("almanac_summaries")?.length() ?: 0) > 0
fun validAirQuality(b: JSONObject) = b.obj("globalairquality")?.opt("airQualityIndex") is Number
fun validHistory(b: JSONObject) = b.optJSONArray("temperatureMax") != null && b.optJSONArray("validTimeLocal") != null
fun validPoint(b: JSONObject) = b.obj("location")?.string("ianaTimeZone") != null

class Hourly(o: JSONObject) {
    val t: List<Long> = o.longs("validTimeUtc").map { it ?: 0L }
    val temp = o.doubles("temperature")
    val feels = o.doubles("temperatureFeelsLike")
    val pop = o.doubles("precipChance")
    val ptype = o.strings("precipType")
    val rh = o.doubles("relativeHumidity")
    val cloud = o.doubles("cloudCover")
    val pres = o.doubles("pressureMeanSeaLevel")
    val qpf = o.doubles("qpf")
    val wspd = o.doubles("windSpeed")
    val wdir = o.doubles("windDirection")
    val wcard = o.strings("windDirectionCardinal")
    val icon = o.ints("iconCode")
    val phrase = o.strings("wxPhraseLong")
    val isDay = o.strings("dayOrNight").map { it == "D" }
}

class Daily(o: JSONObject) {
    val validTimeLocal = o.strings("validTimeLocal")
    val tempMax = o.doubles("temperatureMax")
    val tempMin = o.doubles("temperatureMin")
    val calendarMax = o.doubles("calendarDayTemperatureMax")
    val calendarMin = o.doubles("calendarDayTemperatureMin")
    val qpf = o.doubles("qpf")
    val qpfSnow = o.doubles("qpfSnow")
    val sunrise = o.longs("sunriseTimeUtc")
    val sunset = o.longs("sunsetTimeUtc")

    private val dp = o.getJSONArray("daypart").getJSONObject(0)
    val partName = dp.strings("daypartName")
    val partDayOrNight = dp.strings("dayOrNight")
    val partIcon = dp.ints("iconCode")
    val partTemp = dp.doubles("temperature")
    val partPop = dp.doubles("precipChance")
    val partQpf = dp.doubles("qpf")
    val partNarrative = dp.strings("narrative")
    val partPhrase = dp.strings("wxPhraseLong")

    /** Index of a local yyyy-MM-dd date, or null. */
    fun index(date: String): Int? = validTimeLocal.indexOfFirst { it?.startsWith(date) == true }.takeIf { it >= 0 }

    /** The first daypart of day [i] that still has a forecast (today's day part drops off in the afternoon). */
    fun firstPart(i: Int?): Int? = i?.let { if (partName.at(2 * it) != null) 2 * it else 2 * it + 1 }
}

class Observation(o: JSONObject) {
    val time = o.long("validTimeUtc") ?: 0L
    val temp = o.double("temperature")
    val feels = o.double("temperatureFeelsLike")
    val phrase = o.string("wxPhraseLong").orEmpty()
    val icon = o.int("iconCode")
    val windSpeed = o.double("windSpeed")
    val windGust = o.double("windGust")
    val windDir = o.double("windDirection")
    val windCard = o.string("windDirectionCardinal").orEmpty()
    val pressure = o.double("pressureAltimeter")
    val visibility = o.double("visibility")
    val clouds = o.string("cloudCoverPhrase").orEmpty()
    val dewPoint = o.double("temperatureDewPoint")
    val humidity = o.double("relativeHumidity")
    val precip24 = o.double("precip24Hour")
}

data class Pollutant(val name: String, val phrase: String, val amount: Double?, val unit: String, val category: String, val index: Int?)

class AirQuality(o: JSONObject) {
    private val a = o.getJSONObject("globalairquality")
    val index = a.int("airQualityIndex")
    val category = a.string("airQualityCategory").orEmpty()
    val categoryIndex = a.int("airQualityCategoryIndex")
    val color = a.string("airQualityCategoryIndexColor")
    val primary = a.string("primaryPollutant").orEmpty()
    val message = a.obj("messages")?.obj("General")?.string("text").orEmpty()
    val pollutants: List<Pollutant> = a.obj("pollutants")?.let { p ->
        p.keys().asSequence().mapNotNull { k -> p.obj(k) }.map {
            Pollutant(it.string("name").orEmpty(), it.string("phrase").orEmpty(), it.double("amount"), it.string("unit").orEmpty(), it.string("category").orEmpty(), it.int("index"))
        }.toList()
    }.orEmpty()
}

class History(o: JSONObject) {
    val dates = o.strings("validTimeLocal")
    val tempMax = o.doubles("temperatureMax")
}

/** Epoch seconds from an ISO local time string, or null. */
fun isoEpoch(s: String?): Long? = s?.takeIf { it.isNotEmpty() }?.let { runCatching { OffsetDateTime.parse(it).toEpochSecond() }.getOrNull() }

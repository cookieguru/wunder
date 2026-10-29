package app.wunder.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class Repository(context: Context) {
    private val app = context.applicationContext
    val prefs = Prefs(app)
    private val http = Http(File(app.cacheDir, "wx"))

    /** The device's place (its city center when City forecast is on), else the last one found. */
    suspend fun resolvePlace(now: Long): Place? {
        val loc = Locate.current(app)
        val point = loc?.let { http.get(Request(Api.point(Api.geocode(it.latitude, it.longitude)), ::validPoint), now).body }
        val place = point?.let { if (prefs.settings.cityForecast) cityCenter(it, now) else null } ?: point?.let { Place.fromPoint(it) }
        if (place != null) prefs.place = place
        return place ?: prefs.place
    }

    /** The center of the city containing [point], as the official app forecasts it; named for the city, since the point stands for all of it. */
    private suspend fun cityCenter(point: JSONObject, now: Long): Place? {
        val l = point.obj("location") ?: return null
        val city = l.string("city") ?: return null
        val region = l.string("adminDistrictCode") ?: l.string("adminDistrict") ?: l.string("country")
        val body = http.get(Request(Api.search(listOfNotNull(city, region).joinToString(", "), "city")) { it.obj("location") != null }, now).body
        val match = body?.let { Place.fromSearch(it) }?.firstOrNull { it.name.equals(city, ignoreCase = true) } ?: return null
        return match.copy(name = city, detail = region.orEmpty())
    }

    suspend fun forecast(place: Place, now: Long, force: Boolean = false): Map<String, Fetched> = http.getAll(
        mapOf(
            "hourly" to Request(Api.hourly(place.geocode), ::validHourly),
            "daily" to Request(Api.daily(place.geocode), ::validDaily),
            "obs" to Request(Api.observations(place.geocode), ::validObs),
            "history" to Request(Api.history(place.geocode), ::validHistory),
            "aqi" to Request(Api.airQuality(place.geocode), ::validAirQuality),
            "astro" to Request(Api.astro(place.geocode, Instant.ofEpochSecond(now).atZone(ZoneId.of(place.tz)).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)), ::validAstro),
        ),
        now, force,
    )

    /** The extra requests the single-day view needs: astronomy for the date and station history. */
    suspend fun dayExtras(place: Place, date: LocalDate, now: Long): Map<String, Fetched> {
        val zone = ZoneId.of(place.tz)
        val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        val md = DateTimeFormatter.ofPattern("MMdd", Locale.US)
        val r = mutableMapOf("astro" to Request(Api.astro(place.geocode, date.format(DateTimeFormatter.BASIC_ISO_DATE)), ::validAstro))
        if (date.year == today.year) {
            r["almanac"] = Request(Api.almanac(place.lat, place.lon, today.format(md), date.format(md)), ::validAlmanac)
        } else {
            r["almanac"] = Request(Api.almanac(place.lat, place.lon, today.format(md), today.format(md)), ::validAlmanac)
            r["almanac2"] = Request(Api.almanac(place.lat, place.lon, date.format(md), date.format(md)), ::validAlmanac)
        }
        return http.getAll(r, now)
    }

    suspend fun search(query: String): List<Place>? = http.getUncached(Api.search(query))?.let { Place.fromSearch(it) }

    /** Adds a chosen place to the top of the recents, unless it's pinned. */
    fun remember(place: Place) {
        if (prefs.pinned.any { it.geocode == place.geocode }) return
        prefs.recents = listOf(place) + prefs.recents.filter { it.geocode != place.geocode }
    }

    fun pin(place: Place) {
        prefs.recents = prefs.recents.filter { it.geocode != place.geocode }
        prefs.pinned = prefs.pinned.filter { it.geocode != place.geocode } + place
    }

    /** An unpinned place goes back to the top of the recents. */
    fun unpin(place: Place) {
        prefs.pinned = prefs.pinned.filter { it.geocode != place.geocode }
        prefs.recents = listOf(place) + prefs.recents.filter { it.geocode != place.geocode }
    }

    fun clearRecents() {
        prefs.recents = emptyList()
    }
}

fun widgetSnapshot(place: Place, fetched: Map<String, Fetched>, now: Long): WidgetSnapshot? {
    val obs = fetched["obs"]?.body?.let { Observation(it) } ?: return null
    val daily = fetched["daily"]?.body?.let { Daily(it) } ?: return null
    val c = currentModel(obs, daily, null, now, ZoneId.of(place.tz), Units.METRIC)
    val fetchedAt = listOfNotNull(fetched["obs"]?.fetchedAt, fetched["daily"]?.fetchedAt).minOrNull() ?: now
    return WidgetSnapshot(place.name, c.phrase, c.icon, c.temp, c.hi, c.lo, c.pop, fetchedAt)
}

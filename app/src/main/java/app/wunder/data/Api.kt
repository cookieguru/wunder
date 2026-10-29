package app.wunder.data

import app.wunder.BuildConfig
import java.net.URLEncoder
import java.util.Locale

/** URLs for the weather.com endpoints the official app uses. Values are always requested in metric. */
object Api {
    private const val BASE = "https://api.weather.com/"

    fun geocode(lat: Double, lon: Double): String = String.format(Locale.US, "%.3f,%.3f", lat, lon)

    private fun url(path: String, query: Map<String, String>): String =
        BASE + path + "?" + (query + ("apiKey" to BuildConfig.WU_API_KEY)).entries.joinToString("&") { (k, v) ->
            k + "=" + URLEncoder.encode(v, "UTF-8").replace("%2C", ",").replace("%3B", ";")
        }

    private fun common(geocode: String) = mapOf("geocode" to geocode, "units" to "m", "language" to "en-US", "format" to "json")

    fun hourly(geocode: String) = url("v3/wx/forecast/hourly/15day", common(geocode))
    fun daily(geocode: String) = url("v3/wx/forecast/daily/15day", common(geocode))
    fun observations(geocode: String) = url("v3/wx/observations/current", common(geocode))
    fun history(geocode: String) = url("v3/wx/conditions/historical/dailysummary/30day", common(geocode))

    fun airQuality(geocode: String) =
        url("v3/wx/globalAirQuality", mapOf("geocode" to geocode, "language" to "en-US", "scale" to "EPA", "format" to "json"))

    /** [date] is yyyyMMdd. */
    fun astro(geocode: String, date: String) =
        url("v2/astro", mapOf("geocode" to geocode, "days" to "1", "date" to date, "format" to "json"))

    /** [start] and [end] are MMdd. */
    fun almanac(lat: Double, lon: Double, start: String, end: String) = url(
        String.format(Locale.US, "v1/geocode/%.3f/%.3f/almanac/daily.json", lat, lon),
        mapOf("units" to "m", "start" to start, "end" to end),
    )

    fun point(geocode: String) = url("v3/location/point", mapOf("geocode" to geocode, "language" to "en-US", "format" to "json"))

    fun search(query: String, types: String = "city,pws,postCode") = url(
        "v3/location/search",
        mapOf("query" to query, "locationType" to types, "language" to "en-US", "format" to "json"),
    )
}

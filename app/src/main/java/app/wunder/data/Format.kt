package app.wunder.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Units { IMPERIAL, METRIC }

enum class Kind { TEMP, SPEED, PRECIP, PRES, DIST, PCT }

/** Unit conversion and display text, matching the web page's WU library. Values arrive in metric. */
object Fmt {
    fun conv(kind: Kind, v: Double?, units: Units): Double? {
        if (v == null || !v.isFinite()) return null
        if (units == Units.METRIC) return v
        return when (kind) {
            Kind.TEMP -> v * 9 / 5 + 32
            Kind.SPEED, Kind.DIST -> v / 1.609344
            Kind.PRECIP -> v / 25.4
            Kind.PRES -> v * 0.0295299830714
            Kind.PCT -> v
        }
    }

    fun unit(kind: Kind, units: Units): String {
        val e = units == Units.IMPERIAL
        return when (kind) {
            Kind.TEMP -> if (e) "°F" else "°C"
            Kind.SPEED -> if (e) "mph" else "km/h"
            Kind.PRECIP -> if (e) "in" else "mm"
            Kind.PRES -> if (e) "in" else "mb"
            Kind.DIST -> if (e) "miles" else "km"
            Kind.PCT -> "%"
        }
    }

    private fun trim(s: String) = if ('.' in s) s.trimEnd('0').trimEnd('.') else s

    /** Display text for a value in the chosen units; "--" for missing values. */
    fun fmt(kind: Kind, value: Double?, units: Units): String {
        val v = conv(kind, value, units) ?: return "--"
        return when (kind) {
            Kind.TEMP -> "${v.roundToInt()}°"
            Kind.PRECIP -> if (v == 0.0) "0" else trim(String.format(Locale.US, if (units == Units.IMPERIAL) "%.2f" else "%.1f", v))
            Kind.PRES -> if (units == Units.IMPERIAL) String.format(Locale.US, "%.2f", v) else v.roundToInt().toString()
            else -> v.roundToInt().toString()
        }
    }

    /** Whole degrees in the chosen units, or null. */
    fun deg(c: Double?, units: Units): Int? = conv(Kind.TEMP, c, units)?.roundToInt()

    private val formatters = HashMap<String, DateTimeFormatter>()
    private fun pattern(p: String): DateTimeFormatter = synchronized(formatters) { formatters.getOrPut(p) { DateTimeFormatter.ofPattern(p, Locale.US) } }
    private fun at(epoch: Long, zone: ZoneId) = Instant.ofEpochSecond(epoch).atZone(zone)

    fun time(epoch: Long?, zone: ZoneId, is24: Boolean = true): String =
        if (epoch == null) "--" else at(epoch, zone).format(pattern(if (is24) "HH:mm" else "h:mm a"))

    fun dayShort(epoch: Long, zone: ZoneId): String = at(epoch, zone).format(pattern("EEE M/d"))
    fun monthDay(epoch: Long, zone: ZoneId): String = at(epoch, zone).format(pattern("MMM d"))
    fun duration(h: Int, m: Int) = "${h}h ${m}m"

    /** The API writes forecast text in the requested (metric) units; rewrite its numbers for imperial. */
    fun narrative(text: String, units: Units): String {
        if (units == Units.METRIC) return text
        val f = { c: String -> (c.toDouble() * 9 / 5 + 32).roundToInt() }
        val mph = { k: String -> (k.toDouble() / 1.609344).roundToInt() }
        val inch = { v: String, per: Double, digits: Int -> trim(String.format(Locale.US, "%.${digits}f", v.toDouble() / per)) }
        val rules = listOf<Pair<Regex, (MatchResult) -> String>>(
            Regex("""(-?\d+) to (-?\d+)C\b""") to { m -> "${f(m.groupValues[1])} to ${f(m.groupValues[2])}F" },
            Regex("""(-?\d+)C\b""") to { m -> "${f(m.groupValues[1])}F" },
            Regex("""(\d+) to (\d+) km/h""") to { m -> "${mph(m.groupValues[1])} to ${mph(m.groupValues[2])} mph" },
            Regex("""(\d+) km/h""") to { m -> "${mph(m.groupValues[1])} mph" },
            Regex("""(\d+(?:\.\d+)?) to (\d+(?:\.\d+)?)( ?)mm\b""") to { m -> "${inch(m.groupValues[1], 25.4, 2)} to ${inch(m.groupValues[2], 25.4, 2)}${m.groupValues[3]}in" },
            Regex("""(\d+(?:\.\d+)?) to (\d+(?:\.\d+)?)( ?)cm\b""") to { m -> "${inch(m.groupValues[1], 2.54, 1)} to ${inch(m.groupValues[2], 2.54, 1)}${m.groupValues[3]}in" },
            Regex("""(\d+(?:\.\d+)?)( ?)mm\b""") to { m -> "${inch(m.groupValues[1], 25.4, 2)}${m.groupValues[2]}in" },
            Regex("""(\d+(?:\.\d+)?)( ?)cm\b""") to { m -> "${inch(m.groupValues[1], 2.54, 1)}${m.groupValues[2]}in" },
        )
        return rules.fold(text) { s, (re, fn) -> re.replace(s, fn) }
    }

    private fun title(w: String) = w.take(1) + w.drop(1).lowercase()
    fun moonName(code: String): String = code.split('_').filter { it.isNotEmpty() }.joinToString(" ") { title(it) }
    fun moonIcon(code: String): String = when {
        "FIRST" in code -> "moon-first-quarter"
        "LAST" in code -> "moon-last-quarter"
        code == "NEW_MOON" -> "moon-new"
        code == "FULL_MOON" -> "moon-full"
        else -> "moon-" + code.lowercase().replace('_', '-')
    }

    private val ICONS = mapOf(
        0 to "tornado", 1 to "hurricane", 2 to "hurricane", 3 to "thunderstorms-extreme", 4 to "thunderstorms",
        5 to "sleet", 6 to "sleet", 7 to "sleet", 8 to "drizzle", 9 to "drizzle", 10 to "sleet", 11 to "rain",
        12 to "rain", 13 to "snow", 14 to "snow", 15 to "wind-snow", 16 to "snow", 17 to "hail", 18 to "sleet",
        19 to "dust", 20 to "fog", 21 to "haze", 22 to "smoke", 23 to "wind", 24 to "wind", 25 to "snowflake",
        26 to "cloudy", 27 to "overcast-night", 28 to "overcast-day", 29 to "partly-cloudy-night",
        30 to "partly-cloudy-day", 31 to "clear-night", 32 to "clear-day", 33 to "mostly-clear-night",
        34 to "mostly-clear-day", 35 to "hail", 36 to "sun-hot", 37 to "thunderstorms-day", 38 to "thunderstorms-day",
        39 to "partly-cloudy-day-rain", 40 to "rain", 41 to "partly-cloudy-day-snow", 42 to "snow", 43 to "wind-snow",
        44 to "not-available", 45 to "partly-cloudy-night-rain", 46 to "partly-cloudy-night-snow", 47 to "thunderstorms-night",
    )

    fun iconName(code: Int?): String = ICONS[code] ?: "not-available"

    private fun luminance(rgb: Int): Double {
        val c = listOf(rgb shr 16 and 255, rgb shr 8 and 255, rgb and 255).map { it / 255.0 }
            .map { if (it <= 0.04045) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
    }

    fun contrast(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    /** Whether white text reads better than near-black on [rgb]. */
    fun whiteTextOn(rgb: Int): Boolean = contrast(rgb, 0xffffff) >= contrast(rgb, 0x111111)

    /** Compass point for a direction in degrees. */
    fun cardinal(deg: Double?): String {
        if (deg == null) return ""
        val points = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return points[(((deg % 360 + 360) % 360) / 22.5).roundToInt() % 16]
    }

    fun signedMinutes(seconds: Long): String {
        val s = abs(seconds)
        return "${s / 60}m ${s % 60}s"
    }
}

/** Sort by y and push each label at least [gap] below the previous one. */
fun <T> spread(items: List<T>, y: (T) -> Float, gap: Float): List<Pair<T, Float>> {
    val out = items.map { it to y(it) }.sortedBy { it.second }.toMutableList()
    for (i in 1 until out.size) if (out[i].second - out[i - 1].second < gap) out[i] = out[i].first to out[i - 1].second + gap
    return out
}

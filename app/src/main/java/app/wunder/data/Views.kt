package app.wunder.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

const val STRIP_DAYS = 10

/** Which data the screen shows from an old cached copy, for the stale banner. */
data class StaleNote(val what: String, val fetchedAt: Long)

private val STALE_LABELS = mapOf(
    "hourly" to "forecast", "daily" to "daily forecast", "obs" to "current conditions", "history" to "temperature history",
    "aqi" to "air quality", "astro" to "astronomy", "almanac" to "station history", "almanac2" to "station history",
)

fun staleList(fetched: Map<String, Fetched>): List<StaleNote> =
    fetched.filter { it.value.stale && it.value.fetchedAt != null }
        .map { StaleNote(STALE_LABELS[it.key] ?: it.key, it.value.fetchedAt!!) }
        .distinctBy { it.what }

data class DayBounds(val date: LocalDate, val start: Long, val end: Long, val hours: Int)

/** Local calendar days starting today; hours is 23, 24 or 25 around DST changes. */
fun dayBounds(now: Long, zone: ZoneId, days: Int): List<DayBounds> {
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    return (0 until days).map { i ->
        val d = today.plusDays(i.toLong())
        val start = d.atStartOfDay(zone).toEpochSecond()
        val end = d.plusDays(1).atStartOfDay(zone).toEpochSecond()
        DayBounds(d, start, end, ((end - start) / 3600).toInt())
    }
}

// ------------------------------------------------------------------ strip

data class StripDay(
    val date: LocalDate, val dow: String, val md: String, val hours: Int, val start: Long, val end: Long,
    val hi: Double?, val lo: Double?, val icon: String, val phrase: String, val qpf: Double?, val sunrise: Long?, val sunset: Long?,
)

/** Hourly series from the current hour to the end of the strip. Keys match [CHARTS] series keys. */
data class StripHours(val t: List<Long>, val series: Map<String, List<Double?>>, val ptype: List<String?>, val wcard: List<String?>, val wdir: List<Double?>) {
    fun values(key: String): List<Double?> =
        if (key == "snow") series["pop"].orEmpty().mapIndexed { i, p -> p?.let { if (ptype.getOrNull(i) == "snow") it else 0.0 } }
        else series[key].orEmpty()
}

data class StripModel(val start: Long, val end: Long, val now: Long, val days: List<StripDay>, val hours: StripHours)

private val DOW = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val MD = DateTimeFormatter.ofPattern("M/d", Locale.US)

fun stripModel(hourly: Hourly, daily: Daily, now: Long, zone: ZoneId): StripModel {
    val bounds = dayBounds(now, zone, STRIP_DAYS)
    val start = bounds.first().start
    val end = bounds.last().end
    val days = bounds.map { b ->
        val i = daily.index(b.date.toString())
        val p = daily.firstPart(i)
        StripDay(
            b.date, b.date.format(DOW), b.date.format(MD), b.hours, b.start, b.end,
            daily.tempMax.at(i), daily.tempMin.at(i), Fmt.iconName(daily.partIcon.at(p)), daily.partPhrase.at(p).orEmpty(),
            daily.qpf.at(i), daily.sunrise.at(i), daily.sunset.at(i),
        )
    }
    val fromHour = now / 3600 * 3600 // hours before the current one stay empty, even from an old cached copy
    val idx = hourly.t.indices.filter { val t = hourly.t[it]; t in start until end && t >= fromHour }
    val src = mapOf(
        "temp" to hourly.temp, "feels" to hourly.feels, "pop" to hourly.pop, "rh" to hourly.rh, "cloud" to hourly.cloud,
        "pres" to hourly.pres, "qpf" to hourly.qpf, "wspd" to hourly.wspd,
    )
    val hours = StripHours(
        idx.map { hourly.t[it] },
        src.mapValues { (_, list) -> idx.map { list.getOrNull(it) } },
        idx.map { hourly.ptype.getOrNull(it) }, idx.map { hourly.wcard.getOrNull(it) }, idx.map { hourly.wdir.getOrNull(it) },
    )
    return StripModel(start, end, now, days, hours)
}

/** Night bands from each sunset to the next sunrise, clipped to [start, end]. */
fun nightBands(days: List<StripDay>, start: Long, end: Long): List<Pair<Long, Long>> {
    val edges = days.flatMap { d -> listOfNotNull(d.sunrise?.let { true to it }, d.sunset?.let { false to it }) }.sortedBy { it.second }
    if (edges.isEmpty()) return emptyList()
    val bands = mutableListOf<Pair<Long, Long>>()
    var from: Long? = if (edges.first().first) start else null
    for ((isRise, t) in edges) {
        if (isRise && from != null) {
            bands += from to t
            from = null
        }
        if (!isRise) from = t
    }
    from?.let { bands += it to end }
    return bands.map { (s, e) -> maxOf(s, start) to minOf(e, end) }.filter { it.second > it.first }
}

enum class Range { AUTO, ZERO, PERCENT }

data class Series(val key: String, val label: String, val color: String, val dashed: Boolean = false, val fill: Boolean = false, val steps: Boolean = false, val arrows: Boolean = false, val behind: Boolean = false)

data class Chart(val id: String, val kind: Kind, val range: Range, val series: List<Series>, val hideWhenEmpty: Boolean = false)

/** The charts, top to bottom. Colors name [app.wunder.ui.WuColors] tokens. */
val CHARTS = listOf(
    Chart("temp", Kind.TEMP, Range.AUTO, listOf(Series("temp", "Temperature", "temp"), Series("feels", "Feels Like", "feels", dashed = true, behind = true))),
    Chart("cloud", Kind.PCT, Range.PERCENT, listOf(Series("cloud", "Cloud Cover", "cloud", fill = true))),
    Chart("pop", Kind.PCT, Range.PERCENT, listOf(Series("pop", "Chance of Precip.", "pop", fill = true))),
    Chart("snow", Kind.PCT, Range.PERCENT, listOf(Series("snow", "Chance of Snow", "snow", fill = true)), hideWhenEmpty = true),
    Chart("qpf", Kind.PRECIP, Range.ZERO, listOf(Series("qpf", "Hourly Liquid Precip.", "qpf", fill = true, steps = true))),
    Chart("wind", Kind.SPEED, Range.ZERO, listOf(Series("wspd", "Wind Speed", "wind", arrows = true))),
    Chart("rh", Kind.PCT, Range.PERCENT, listOf(Series("rh", "Humidity", "rh"))),
    Chart("pres", Kind.PRES, Range.AUTO, listOf(Series("pres", "Pressure", "pres"))),
)

fun chartHasData(chart: Chart, hours: StripHours) =
    !chart.hideWhenEmpty || chart.series.any { s -> hours.values(s.key).any { (it ?: 0.0) > 0 } }

/** Series keys for the Customize switches: charts that would never show get no switch. */
fun switchKeys(hours: StripHours) = CHARTS.filter { chartHasData(it, hours) }.flatMap { c -> c.series.map { it.key } }

// ------------------------------------------------------------------ current conditions

enum class QuickieTime { TODAY, TOMORROW }
enum class QuickieKind(val word: String) { MUCH_COOLER("much cooler"), COOLER("cooler"), NEARLY_THE_SAME("nearly the same"), WARMER("warmer"), MUCH_WARMER("much warmer") }
data class Quickie(val time: QuickieTime, val kind: QuickieKind) {
    val sentence: Pair<String, String>
        get() {
            val subject = if (time == QuickieTime.TODAY) "Today's" else "Tomorrow's"
            val than = if (kind == QuickieKind.NEARLY_THE_SAME) "as" else "than"
            val other = if (time == QuickieTime.TODAY) "yesterday" else "today"
            return "$subject temperature is forecast to be " to " $than $other."
        }
}

/** Thresholds from the official app: differences are in whole display degrees. */
fun quickieKind(diff: Int, units: Units): QuickieKind {
    val (small, big) = if (units == Units.METRIC) 1 to 4 else 2 to 7
    return when {
        diff > big -> QuickieKind.MUCH_WARMER
        diff > small -> QuickieKind.WARMER
        diff < -big -> QuickieKind.MUCH_COOLER
        diff < -small -> QuickieKind.COOLER
        else -> QuickieKind.NEARLY_THE_SAME
    }
}

/**
 * Like the official app: from 03:00 to 15:00 local, today's forecast high against yesterday's observed high;
 * otherwise tomorrow's forecast high against today's observed high so far.
 */
fun quickie(daily: Daily, history: History?, now: Long, zone: ZoneId, units: Units): Quickie? {
    if (history == null) return null
    val local = Instant.ofEpochSecond(now).atZone(zone)
    val today = local.toLocalDate()
    val ti = daily.index(today.toString())
    val time: QuickieTime
    val forecast: Double?
    val past: Double?
    if (local.hour in 3 until 15) {
        time = QuickieTime.TODAY
        forecast = daily.tempMax.at(ti) ?: daily.calendarMax.at(ti)
        val yi = history.dates.indexOfFirst { it?.startsWith(today.minusDays(1).toString()) == true }
        past = history.tempMax.at(yi.takeIf { it >= 0 })
    } else {
        time = QuickieTime.TOMORROW
        forecast = daily.tempMax.at(daily.index(today.plusDays(1).toString()))
        val hi = history.dates.indexOfFirst { it?.startsWith(today.toString()) == true }
        past = history.tempMax.at(hi.takeIf { it >= 0 })
    }
    val a = Fmt.deg(forecast, units) ?: return null
    val b = Fmt.deg(past, units) ?: return null
    return Quickie(time, quickieKind(a - b, units))
}

data class Current(
    val temp: Double?, val feels: Double?, val phrase: String, val icon: String, val pop: Double?, val windSpeed: Double?, val windGust: Double?,
    val windDir: Double?, val windCard: String, val hi: Double?, val lo: Double?, val quickie: Quickie?, val observedAt: Long,
)

fun currentModel(obs: Observation, daily: Daily, history: History?, now: Long, zone: ZoneId, units: Units): Current {
    val ti = daily.index(Instant.ofEpochSecond(now).atZone(zone).toLocalDate().toString())
    return Current(
        obs.temp, obs.feels, obs.phrase, Fmt.iconName(obs.icon), daily.partPop.at(daily.firstPart(ti)), obs.windSpeed, obs.windGust,
        obs.windDir, obs.windCard.ifEmpty { Fmt.cardinal(obs.windDir) },
        // After today's peak the API drops today's high; the official app then shows tomorrow's.
        daily.tempMax.at(ti) ?: daily.tempMax.at(ti?.plus(1)), daily.tempMin.at(ti) ?: daily.calendarMin.at(ti),
        quickie(daily, history, now, zone, units), obs.time,
    )
}

// ------------------------------------------------------------------ sun

data class SunDay(val rise: Long?, val set: Long?, val tomorrowRise: Long?, val tomorrowSet: Long?, val dayStart: Long, val dayEnd: Long) {
    val length: Long? get() = if (rise != null && set != null) set - rise else null
    val tomorrowLength: Long? get() = if (tomorrowRise != null && tomorrowSet != null) tomorrowSet - tomorrowRise else null
}

fun sunDay(daily: Daily, now: Long, zone: ZoneId): SunDay {
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    val i = daily.index(today.toString())
    val j = daily.index(today.plusDays(1).toString())
    return SunDay(
        daily.sunrise.at(i), daily.sunset.at(i), daily.sunrise.at(j), daily.sunset.at(j),
        today.atStartOfDay(zone).toEpochSecond(), today.plusDays(1).atStartOfDay(zone).toEpochSecond(),
    )
}

// ------------------------------------------------------------------ single day

data class DayPart(val name: String, val isDay: Boolean, val icon: String, val temp: Double?, val pop: Double?, val qpf: Double?, val narrative: String)

data class DayHour(
    val t: Long, val icon: String, val phrase: String, val temp: Double?, val feels: Double?, val pop: Double?, val qpf: Double?,
    val cloud: Double?, val rh: Double?, val wspd: Double?, val wcard: String, val isDay: Boolean,
)

data class AlmanacDay(
    val title: String, val fcHi: Double?, val fcLo: Double?, val fcPrecip: Double?, val fcSnow: Double?,
    val avgHi: Double?, val avgLo: Double?, val recHi: Double?, val recHiYr: Int?, val recLo: Double?, val recLoYr: Int?,
    val avgPrecip: Double?, val avgSnow: Double?,
)

data class MoonPhase(val t: Long, val phase: String)

data class Astro(
    val rise: Long?, val set: Long?, val civilDawn: Long?, val civilDusk: Long?, val nauticalDawn: Long?, val nauticalDusk: Long?,
    val astroDawn: Long?, val astroDusk: Long?, val visibleH: Int, val visibleM: Int, val lengthH: Int, val lengthM: Int,
    val tomorrowLonger: Boolean, val tomorrowM: Int, val tomorrowS: Int, val moonRise: Long?, val moonSet: Long?,
    val moonPhase: String, val illum: Int?, val phases: List<MoonPhase>,
)

data class DayModel(
    val date: LocalDate, val isToday: Boolean, val prevDate: LocalDate?, val nextDate: LocalDate?, val longDate: String,
    val parts: List<DayPart>, val sunRise: Long?, val sunSet: Long?, val hours: List<DayHour>,
    val obs: Observation?, val almanac: List<AlmanacDay>, val stationName: String?, val astro: Astro?, val airQuality: AirQuality?,
)

fun validDate(date: LocalDate, now: Long, zone: ZoneId): Boolean {
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    return !date.isBefore(today) && !date.isAfter(today.plusDays((STRIP_DAYS - 1).toLong()))
}

private val LONG_DATE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
private val ALMANAC_TITLE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
private val MMDD = DateTimeFormatter.ofPattern("MMdd", Locale.US)

fun dayModel(date: LocalDate, hourly: Hourly, daily: Daily, fetched: Map<String, Fetched>, now: Long, zone: ZoneId): DayModel {
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    val d = daily.index(date.toString())
    val parts = if (d == null) emptyList() else listOf(2 * d, 2 * d + 1).filter { daily.partName.at(it) != null }.map { p ->
        DayPart(
            daily.partName[p]!!.uppercase(), (daily.partDayOrNight.at(p) ?: "D") == "D", Fmt.iconName(daily.partIcon.at(p)),
            daily.partTemp.at(p), daily.partPop.at(p), daily.partQpf.at(p), daily.partNarrative.at(p).orEmpty(),
        )
    }

    val dayStart = date.atStartOfDay(zone).toEpochSecond()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toEpochSecond()
    val fromHour = now / 3600 * 3600
    val hours = hourly.t.indices.filter { val t = hourly.t[it]; t in dayStart until dayEnd && t >= fromHour }.map { i ->
        DayHour(
            hourly.t[i], Fmt.iconName(hourly.icon.at(i)), hourly.phrase.at(i).orEmpty(), hourly.temp.at(i), hourly.feels.at(i),
            hourly.pop.at(i), hourly.qpf.at(i), hourly.cloud.at(i), hourly.rh.at(i), hourly.wspd.at(i), hourly.wcard.at(i).orEmpty(), hourly.isDay.at(i) ?: true,
        )
    }

    // Station history: today, then the selected date if different
    val summaries = listOf("almanac", "almanac2").mapNotNull { fetched[it]?.body?.optJSONArray("almanac_summaries") }
        .flatMap { a -> List(a.length()) { a.getJSONObject(it) } }
    val byDate = summaries.associateBy { it.string("almanac_dt") }
    val almanac = if (summaries.isEmpty()) emptyList() else (if (date == today) listOf(today) else listOf(today, date)).map { day ->
        val s = byDate[day.format(MMDD)]
        val i = daily.index(day.toString())
        AlmanacDay(
            day.format(ALMANAC_TITLE), daily.tempMax.at(i), daily.tempMin.at(i), daily.qpf.at(i), daily.qpfSnow.at(i)?.times(10),
            s?.double("avg_hi"), s?.double("avg_lo"), s?.double("record_hi"), s?.int("record_hi_yr"), s?.double("record_lo"), s?.int("record_lo_yr"),
            s?.double("avg_precip"), s?.double("avg_snow"), // the v1 almanac already reports snow in mm (daily qpfSnow is cm)
        )
    }
    val station = summaries.firstNotNullOfOrNull { it.string("station_name") }?.let { n -> n.split(' ').joinToString(" ") { w -> w.take(1) + w.drop(1).lowercase() } }

    val astro = fetched["astro"]?.body?.let { parseAstro(it, now) }

    return DayModel(
        date, date == today,
        date.minusDays(1).takeIf { validDate(it, now, zone) }, date.plusDays(1).takeIf { validDate(it, now, zone) },
        date.format(LONG_DATE), parts, daily.sunrise.at(d), daily.sunset.at(d), hours,
        fetched["obs"]?.body?.let { Observation(it) }, almanac, station, astro,
        fetched["aqi"]?.body?.takeIf { date == today }?.let { AirQuality(it) },
    )
}

/** A v2/astro response; moon phases are the next four after [now]. */
fun parseAstro(body: JSONObject, now: Long): Astro {
    val a = body.getJSONArray("astroData").getJSONObject(0)
    val sun = a.obj("sun")
    val tw = sun?.obj("twilight")
    val moon = a.obj("moon")?.obj("riseSet")
    val diff = a.obj("tomorrowDaylightDifference")
    val phasesArr = body.optJSONArray("astroPhases")
    val phases = (0 until (phasesArr?.length() ?: 0)).mapNotNull { k ->
        val ph = phasesArr!!.getJSONObject(k)
        val t = isoEpoch(ph.string("date")) ?: return@mapNotNull null
        if (t > now) MoonPhase(t, ph.string("moonPhase").orEmpty()) else null
    }.take(4)
    return Astro(
        isoEpoch(sun?.obj("riseSet")?.string("riseLocal")), isoEpoch(sun?.obj("riseSet")?.string("setLocal")),
        isoEpoch(tw?.obj("civil")?.string("dawnLocal")), isoEpoch(tw?.obj("civil")?.string("duskLocal")),
        isoEpoch(tw?.obj("nautical")?.string("dawnLocal")), isoEpoch(tw?.obj("nautical")?.string("duskLocal")),
        isoEpoch(tw?.obj("astronomical")?.string("dawnLocal")), isoEpoch(tw?.obj("astronomical")?.string("duskLocal")),
        a.obj("visibleLight")?.int("hours") ?: 0, a.obj("visibleLight")?.int("minutes") ?: 0,
        a.obj("lengthOfDay")?.int("hours") ?: 0, a.obj("lengthOfDay")?.int("minutes") ?: 0,
        diff?.string("sign") != "-", kotlin.math.abs(diff?.int("minutes") ?: 0), kotlin.math.abs(diff?.int("seconds") ?: 0),
        isoEpoch(moon?.string("riseLocal")), isoEpoch(moon?.string("setLocal")),
        moon?.string("risePhrase") ?: moon?.string("setPhrase") ?: "", moon?.double("percentIlluminated")?.roundToInt(), phases,
    )
}

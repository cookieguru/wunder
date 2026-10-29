package app.wunder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wunder.R
import app.wunder.data.AirQuality
import app.wunder.data.AlmanacDay
import app.wunder.data.Astro
import app.wunder.data.DayHour
import app.wunder.data.DayModel
import app.wunder.data.Fmt
import app.wunder.data.Kind
import app.wunder.data.Observation
import app.wunder.data.Units
import java.time.LocalDate
import java.time.ZoneId

private val TABLE_COLUMNS: List<Pair<String, Dp>> = listOf(
    "Time" to 76.dp, "Conditions" to 180.dp, "Temp." to 72.dp, "Feels Like" to 72.dp, "Precip" to 72.dp,
    "Amount" to 84.dp, "Cloud Cover" to 72.dp, "Humidity" to 84.dp, "Wind" to 124.dp,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayScreen(vm: MainViewModel, onHome: () -> Unit, onDay: (LocalDate) -> Unit) {
    val w = LocalWu.current
    val f = vm.forecast
    val d = vm.day
    val units = vm.settings.units
    val is24 = LocalIs24.current
    val tableScroll = rememberScrollState()
    if (f == null || d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val zone = f.zone
    val pad = Modifier.padding(horizontal = 12.dp)
    Column(Modifier.fillMaxSize()) {
        Row(pad.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onHome) { Icon(painterResource(R.drawable.ic_back), "10-Day forecast", tint = w.fg) }
            Text(f.place.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = w.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The only setting that matters here is units, so the button flips them directly.
            val other = if (units == Units.IMPERIAL) Units.METRIC else Units.IMPERIAL
            TextButton(onClick = { vm.updateSettings(vm.settings.copy(units = other)) }) {
                Text(Fmt.unit(Kind.TEMP, units), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = w.fg)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp)) {
            item { DateBar(d, onDay, pad) }
            item { StaleBanner(vm.dayStale, zone, pad.padding(vertical = 6.dp)) }
            item {
                Column(pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (p in d.parts) {
                        Panel {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(painterResource(iconRes(p.icon)), null, Modifier.size(48.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(p.name, fontWeight = FontWeight.ExtraBold, color = w.fg)
                                        Text("${d.date.dayOfWeek.name.take(3)} ${d.date.monthValue}/${d.date.dayOfMonth}", color = w.muted, fontSize = 12.8.sp)
                                        Text(
                                            "${if (p.isDay) "HIGH" else "LOW"} ${Fmt.deg(p.temp, units) ?: "--"}${Fmt.unit(Kind.TEMP, units)}",
                                            color = if (p.isDay) w.hi else w.lo, fontWeight = FontWeight.Bold,
                                        )
                                        Text("${Fmt.fmt(Kind.PCT, p.pop, units)}% Precip. / ${Fmt.fmt(Kind.PRECIP, p.qpf, units)} ${Fmt.unit(Kind.PRECIP, units)}", color = w.pop)
                                    }
                                }
                                Text(Fmt.narrative(p.narrative, units), Modifier.padding(top = 4.dp), color = w.fg)
                            }
                        }
                    }
                }
            }
            item { SunMoonLine(d, zone, pad) }
            item { SectionTitle("Hourly Forecast", pad) }
            item { HourStrip(d, units, zone, pad) }
            item { SectionTitle("Hourly Detail", pad.padding(top = 4.dp)) }
            if (d.hours.isEmpty()) {
                item { Unavailable("No more forecast hours today", "", pad) }
            } else {
                stickyHeader(key = "table-head") { TableRow(tableScroll, w.panel, header = true, cells = TABLE_COLUMNS.map { it.first }) }
                itemsIndexed(d.hours, key = { _, h -> h.t }) { i, h ->
                    val bg = when {
                        h.isDay && i % 2 == 1 -> w.dayAlt
                        h.isDay -> w.day
                        i % 2 == 1 -> w.nightAlt
                        else -> w.night
                    }
                    HourRow(h, bg, tableScroll, units, zone, is24)
                }
                // A second sticky header pushes the table header off screen once the last row has scrolled past.
                stickyHeader(key = "table-end") { Spacer(Modifier.fillMaxWidth().height(1.dp)) }
            }
            item {
                Column(pad.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (d.isToday) InfoBox("Air Quality") { AirQualityBox(d.airQuality, f.error("aqi")) }
                    InfoBox("Additional Conditions") { ObsBox(d.obs, units, f.error("obs")) }
                    InfoBox("${d.stationName ?: "Station"} History") { AlmanacBox(d.almanac, units) }
                    InfoBox("Astronomy") { AstroBox(d.astro, zone, is24) }
                }
            }
        }
    }
}

@Composable
private fun DateBar(d: DayModel, onDay: (LocalDate) -> Unit, modifier: Modifier) {
    val w = LocalWu.current
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        NavButton(d.prevDate, R.drawable.ic_chevron_left, "Previous day", onDay)
        Text(d.longDate, Modifier.padding(horizontal = 12.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = w.fg)
        NavButton(d.nextDate, R.drawable.ic_chevron_right, "Next day", onDay)
    }
}

@Composable
private fun NavButton(date: LocalDate?, icon: Int, label: String, onDay: (LocalDate) -> Unit) {
    val w = LocalWu.current
    IconButton(onClick = { date?.let(onDay) }, enabled = date != null, modifier = Modifier.size(40.dp)) {
        Box(Modifier.size(32.dp).background(if (date != null) w.panel else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), label, tint = if (date != null) w.fg else w.grid)
        }
    }
}

@Composable
private fun SunMoonLine(d: DayModel, zone: ZoneId, modifier: Modifier) {
    val w = LocalWu.current
    val is24 = LocalIs24.current
    @Composable
    fun item(icon: String?, label: String, value: String) = Row(verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Image(painterResource(iconRes(it)), null, Modifier.size(24.dp)) }
        Text("$label ", color = w.fg)
        Text(value, fontWeight = FontWeight.Bold, color = w.fg)
    }
    FlowRow(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item("sunrise", "Sunrise", Fmt.time(d.sunRise, zone, is24))
        item("sunset", "Sunset", Fmt.time(d.sunSet, zone, is24))
        d.astro?.let { a ->
            item(null, "Moonrise", Fmt.time(a.moonRise, zone, is24))
            item(null, "Moonset", Fmt.time(a.moonSet, zone, is24))
            if (a.moonPhase.isNotEmpty()) item(Fmt.moonIcon(a.moonPhase), Fmt.moonName(a.moonPhase) + ",", "${a.illum ?: "--"}% visible")
        }
    }
}

/** Hour cells with sunrise/sunset cells slotted in, over a temperature line. */
@Composable
private fun HourStrip(d: DayModel, units: Units, zone: ZoneId, modifier: Modifier) {
    val w = LocalWu.current
    val is24 = LocalIs24.current
    data class Cell(val t: Long, val hour: DayHour?, val sun: String?)
    val cells = buildList {
        for (h in d.hours) {
            add(Cell(h.t, h, null))
            for ((name, t) in listOf("Sunrise" to d.sunRise, "Sunset" to d.sunSet)) if (t != null && t >= h.t && t < h.t + 3600) add(Cell(t, null, name))
        }
    }
    // Daylight cells are lighter than night cells; the Sunrise cell starts daylight, the Sunset cell ends it.
    val isNight = { c: Cell -> if (c.sun != null) c.sun == "Sunset" else !c.hour!!.isDay }
    Box(modifier.fillMaxWidth().border(1.dp, w.grid, CardShape).background(w.panel, CardShape)) {
        if (cells.isEmpty()) {
            Text("No more forecast hours today", Modifier.padding(16.dp), color = w.muted)
            return@Box
        }
        val cellW = 58.dp
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                for (c in cells) {
                    Column(
                        Modifier.width(cellW).background(if (isNight(c)) w.night else w.day).padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(Fmt.time(c.t, zone, is24), fontWeight = FontWeight.Bold, fontSize = 12.8.sp, color = if (c.sun != null) w.muted else w.fg, maxLines = 1)
                        Image(painterResource(iconRes(c.hour?.icon ?: c.sun!!.lowercase())), null, Modifier.size(32.dp))
                        Text(c.hour?.let { Fmt.fmt(Kind.TEMP, it.temp, units) } ?: c.sun!!, fontSize = 12.8.sp, color = w.fg)
                    }
                }
            }
            val temps = cells.mapNotNull { c -> c.hour?.temp?.let { Fmt.conv(Kind.TEMP, it, units) } }
            Canvas(Modifier.width(cellW * cells.size).height(44.dp)) {
                val cw = cellW.toPx()
                cells.forEachIndexed { i, c -> drawRect(if (isNight(c)) w.night else w.day, Offset(i * cw, 0f), androidx.compose.ui.geometry.Size(cw, size.height)) }
                if (temps.isEmpty()) return@Canvas
                val lo = temps.min()
                val hi = temps.max()
                val y = { v: Double -> if (hi == lo) size.height / 2 else (6.dp.toPx() + (1 - (v - lo) / (hi - lo)) * (size.height - 12.dp.toPx())).toFloat() }
                val path = Path()
                var pen = false
                cells.forEachIndexed { i, c ->
                    val h = c.hour ?: return@forEachIndexed
                    val v = Fmt.conv(Kind.TEMP, h.temp, units)
                    if (v == null) {
                        pen = false
                        return@forEachIndexed
                    }
                    val px = i * cw + cw / 2
                    if (pen) path.lineTo(px, y(v)) else path.moveTo(px, y(v))
                    pen = true
                }
                drawPath(path, w.temp, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun TableRow(scroll: ScrollState, bg: Color, header: Boolean, cells: List<String>, content: (@Composable (Int) -> Unit)? = null) {
    val w = LocalWu.current
    Box(Modifier.fillMaxWidth().background(bg).horizontalScroll(scroll)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TABLE_COLUMNS.forEachIndexed { i, (_, width) ->
                Box(Modifier.width(width).padding(horizontal = 8.dp, vertical = if (header) 8.dp else 6.dp)) {
                    if (content != null) {
                        content(i)
                    } else {
                        Text(
                            cells[i].uppercase(), fontSize = 12.sp, letterSpacing = 0.7.sp, color = w.muted, fontWeight = FontWeight.Bold,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourRow(h: DayHour, bg: Color, scroll: ScrollState, units: Units, zone: ZoneId, is24: Boolean) {
    val w = LocalWu.current
    val tU = Fmt.unit(Kind.TEMP, units)
    TableRow(scroll, bg, header = false, cells = emptyList()) { i ->
        val text = when (i) {
            0 -> Fmt.time(h.t, zone, is24)
            2 -> "${Fmt.deg(h.temp, units) ?: "--"}$tU"
            3 -> "${Fmt.deg(h.feels, units) ?: "--"}$tU"
            4 -> "${Fmt.fmt(Kind.PCT, h.pop, units)}%"
            5 -> "${Fmt.fmt(Kind.PRECIP, h.qpf, units)} ${Fmt.unit(Kind.PRECIP, units)}"
            6 -> "${Fmt.fmt(Kind.PCT, h.cloud, units)}%"
            7 -> "${Fmt.fmt(Kind.PCT, h.rh, units)}%"
            8 -> "${Fmt.fmt(Kind.SPEED, h.wspd, units)} ${Fmt.unit(Kind.SPEED, units)} ${h.wcard}"
            else -> null
        }
        if (i == 1) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Image(painterResource(iconRes(h.icon)), null, Modifier.size(20.dp))
                Text(h.phrase, color = w.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(
                text.orEmpty(), maxLines = 1,
                color = if (i == 4 || i == 5) w.pop else w.fg, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun InfoBox(title: String, content: @Composable () -> Unit) {
    val w = LocalWu.current
    Panel {
        Column {
            Text(title.uppercase(), Modifier.padding(bottom = 12.dp), fontSize = 13.sp, letterSpacing = 1.sp, color = w.muted, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String, unit: String = "") {
    val w = LocalWu.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(key, Modifier.weight(1f), color = w.fg)
        Text(value, fontWeight = FontWeight.Bold, color = w.fg)
        if (unit.isNotEmpty()) Text(unit, color = w.fg)
    }
}

/** EPA categories, Good through Hazardous. */
private val AQI_SCALE = listOf(50 to 0xff00e400, 100 to 0xffffff00, 150 to 0xffff7e00, 200 to 0xffff0000, 300 to 0xff8f3f97, 500 to 0xff7e0023)

@Composable
private fun AirQualityBox(a: AirQuality?, error: String?) {
    val w = LocalWu.current
    if (a == null) {
        Unavailable(plainReason(error), "Unavailable")
        return
    }
    val color = a.color?.let { runCatching { Color(0xff000000 or it.toLong(16)) }.getOrNull() } ?: w.muted
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(color, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text(a.index?.toString() ?: "--", fontSize = 22.sp, fontWeight = FontWeight.Black, color = if (Fmt.whiteTextOn(color.toArgb() and 0xffffff)) Color.White else Color(0xff111111))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(a.category, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = w.fg)
                if (a.primary.isNotEmpty()) Text("Primary pollutant: ${a.pollutants.firstOrNull { it.name == a.primary }?.phrase ?: a.primary}", color = w.muted, fontSize = 13.sp)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            var start = 0f
            for ((top, argb) in AQI_SCALE) {
                val end = top / 500f * size.width
                drawRect(Color(argb), Offset(start, 0f), androidx.compose.ui.geometry.Size(end - start, size.height))
                start = end
            }
            a.index?.let { i ->
                val x = (i.coerceIn(0, 500) / 500f) * size.width
                drawLine(w.fg, Offset(x, -2f), Offset(x, size.height + 2f), 3.dp.toPx())
            }
        }
        if (a.message.isNotEmpty()) Text(a.message, color = w.fg)
        for (p in a.pollutants.sortedByDescending { it.index ?: -1 }) {
            KeyValue(p.phrase.ifEmpty { p.name }, "${p.index ?: "--"}", "  ${p.category}")
        }
    }
}

@Composable
private fun ObsBox(o: Observation?, units: Units, error: String?) {
    if (o == null) {
        Unavailable(plainReason(error), "Unavailable")
        return
    }
    Column {
        KeyValue("Pressure", Fmt.fmt(Kind.PRES, o.pressure, units), " ${Fmt.unit(Kind.PRES, units)}")
        KeyValue("Visibility", Fmt.fmt(Kind.DIST, o.visibility, units), " ${Fmt.unit(Kind.DIST, units)}")
        KeyValue("Clouds", o.clouds.ifEmpty { "--" })
        KeyValue("Dew Point", "${Fmt.deg(o.dewPoint, units) ?: "--"}", Fmt.unit(Kind.TEMP, units))
        KeyValue("Humidity", Fmt.fmt(Kind.PCT, o.humidity, units), "%")
        KeyValue("Rainfall", Fmt.fmt(Kind.PRECIP, o.precip24, units), " ${Fmt.unit(Kind.PRECIP, units)}")
    }
}

@Composable
private fun AlmanacBox(days: List<AlmanacDay>, units: Units) {
    val w = LocalWu.current
    if (days.isEmpty()) {
        Unavailable("Station history couldn't be loaded.", "Unavailable")
        return
    }
    val tU = Fmt.unit(Kind.TEMP, units)
    val cell = { v: Double?, temp: Boolean ->
        when {
            v == null -> "-"
            temp -> "${Fmt.deg(v, units)}$tU"
            else -> "${Fmt.fmt(Kind.PRECIP, v, units)} ${Fmt.unit(Kind.PRECIP, units)}"
        }
    }
    @Composable
    fun group(text: String) = Text(text, Modifier.padding(top = 10.dp, bottom = 2.dp), fontSize = 12.sp, letterSpacing = 0.7.sp, color = w.muted)
    @Composable
    fun row(cells: List<String>, header: Boolean = false, year: Int? = null) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        cells.forEachIndexed { i, s ->
            Column(Modifier.weight(if (i == 0) 1.3f else 1f), horizontalAlignment = if (i == 0) Alignment.Start else Alignment.End) {
                Text(s, fontSize = if (header) 12.sp else 14.sp, color = if (header) w.muted else w.fg, maxLines = 1)
                if (i == 3 && year != null) Text(year.toString(), fontSize = 11.sp, color = w.muted)
            }
        }
    }
    Column {
        days.forEachIndexed { k, a ->
            Text("Almanac for ${a.title}", Modifier.padding(top = if (k == 0) 0.dp else 16.dp, bottom = 6.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = w.fg)
            row(listOf("", "FORECAST", "AVERAGE", "RECORD"), header = true)
            group("TEMPERATURE")
            row(listOf("High", cell(a.fcHi, true), cell(a.avgHi, true), cell(a.recHi, true)), year = a.recHiYr)
            row(listOf("Low", cell(a.fcLo, true), cell(a.avgLo, true), cell(a.recLo, true)), year = a.recLoYr)
            group("RAIN/SNOW DEPTH")
            row(listOf("Precipitation", cell(a.fcPrecip, false), cell(a.avgPrecip, false), "-"))
            row(listOf("Snow", cell(a.fcSnow, false), cell(a.avgSnow, false), "-"))
        }
    }
}

@Composable
private fun AstroBox(a: Astro?, zone: ZoneId, is24: Boolean) {
    val w = LocalWu.current
    if (a == null) {
        Unavailable("Astronomy couldn't be loaded.", "Unavailable")
        return
    }
    val t = { v: Long? -> Fmt.time(v, zone, is24) }
    @Composable
    fun row(c: List<String>, header: Boolean = false) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        c.forEachIndexed { i, s ->
            Text(s, Modifier.weight(if (i == 0) 2f else 1f), textAlign = if (i == 0) TextAlign.Start else TextAlign.End, color = if (header) w.muted else w.fg, fontSize = if (header) 12.sp else 14.sp)
        }
    }
    Column {
        row(listOf("SUN", "RISE", "SET"), header = true)
        row(listOf("Actual Time", t(a.rise), t(a.set)))
        row(listOf("Civil Twilight", t(a.civilDawn), t(a.civilDusk)))
        row(listOf("Nautical Twilight", t(a.nauticalDawn), t(a.nauticalDusk)))
        row(listOf("Astronomical Twilight", t(a.astroDawn), t(a.astroDusk)))
        KeyValue("Length of Visible Light", Fmt.duration(a.visibleH, a.visibleM))
        KeyValue("Length of Day", Fmt.duration(a.lengthH, a.lengthM))
        Text("Tomorrow will be ${a.tomorrowM} minutes ${a.tomorrowS} seconds ${if (a.tomorrowLonger) "longer" else "shorter"}", Modifier.padding(vertical = 8.dp), color = w.muted)
        row(listOf("Moon", t(a.moonRise), t(a.moonSet)))
        if (a.moonPhase.isNotEmpty()) {
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(iconRes(Fmt.moonIcon(a.moonPhase))), null, Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(Fmt.moonName(a.moonPhase), fontWeight = FontWeight.Bold, color = w.fg)
                    Text("${a.illum ?: "--"}% of the Moon is Illuminated", color = w.fg)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            for (p in a.phases) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(iconRes(Fmt.moonIcon(p.phase))), null, Modifier.size(32.dp))
                    Text(Fmt.monthDay(p.t, zone), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = w.fg)
                    Text(Fmt.moonName(p.phase), fontSize = 12.sp, color = w.fg, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

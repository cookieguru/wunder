package app.wunder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import app.wunder.data.Astro
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wunder.R
import app.wunder.data.Current
import app.wunder.data.Fmt
import app.wunder.data.Kind
import app.wunder.data.Place
import app.wunder.data.Settings
import app.wunder.data.SunDay
import app.wunder.data.Units
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onSearch: () -> Unit, onCustomize: () -> Unit, onDay: (LocalDate) -> Unit) {
    val w = LocalWu.current
    val f = vm.forecast
    val settings = vm.settings
    Column(Modifier.fillMaxSize()) {
        LocationBar(vm.place, vm.followCurrent, onSearch, onCustomize, Modifier.background(w.bg).padding(horizontal = 12.dp, vertical = 8.dp))
        PullToRefreshBox(isRefreshing = vm.loading, onRefresh = { vm.refresh(force = true) }, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
                vm.problem?.let { Unavailable(it, "No location", Modifier.padding(12.dp)) }
                if (f == null) return@Column
                StaleBanner(f.stale, f.zone, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                val current = f.current(settings.units)
                if (current != null) {
                    CurrentCard(current, settings.units, f.zone, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                } else {
                    Unavailable(plainReason(f.error("obs")), "Current conditions unavailable", Modifier.padding(12.dp))
                }
                SectionTitle("10-Day Forecast", Modifier.padding(horizontal = 12.dp))
                if (f.strip != null) {
                    StripView(f.strip, settings, f.zone, LocalIs24.current, onDay)
                } else {
                    Unavailable(plainReason(f.error("hourly") ?: f.error("daily")), modifier = Modifier.padding(horizontal = 12.dp))
                }
                f.sun?.let {
                    SectionTitle("Sunrise & Sunset", Modifier.padding(horizontal = 12.dp))
                    SunCard(it, f.astro, f.zone, f.now, Modifier.padding(horizontal = 12.dp))
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun LocationBar(place: Place?, following: Boolean, onSearch: () -> Unit, onCustomize: () -> Unit, modifier: Modifier) {
    val w = LocalWu.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (following) {
                    Icon(painterResource(R.drawable.ic_navigation), "Current location", Modifier.size(18.dp), tint = w.link)
                    Spacer(Modifier.width(4.dp))
                }
                Text(place?.name ?: "Locating…", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = w.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!place?.detail.isNullOrEmpty()) Text(place.detail, color = w.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onSearch) { Icon(painterResource(R.drawable.ic_search), "Search locations", tint = w.fg) }
        IconButton(onClick = onCustomize) { Icon(painterResource(R.drawable.ic_tune), "Customize", tint = w.fg) }
    }
}

@Composable
private fun CurrentCard(c: Current, units: Units, zone: ZoneId, modifier: Modifier) {
    val w = LocalWu.current
    Panel(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(iconRes(c.icon)), c.phrase, Modifier.size(96.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(Fmt.fmt(Kind.TEMP, c.temp, units), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = w.fg, lineHeight = 60.sp)
                    Text(c.phrase, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = w.fg)
                    Text("Feels like ${Fmt.fmt(Kind.TEMP, c.feels, units)}", color = w.muted)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(hiLo(c.hi, c.lo, units, w), fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.wx_raindrop), null, Modifier.size(22.dp))
                    Text("${Fmt.fmt(Kind.PCT, c.pop, units)}% chance of precip.", color = w.pop, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.windDir != null) {
                        // The arrow points where the wind blows to.
                        Icon(painterResource(R.drawable.ic_navigation), null, Modifier.size(18.dp).rotate(c.windDir.toFloat() + 180), tint = w.wind)
                        Spacer(Modifier.width(4.dp))
                    }
                    val gust = c.windGust?.let { ", gusts ${Fmt.fmt(Kind.SPEED, it, units)}" }.orEmpty()
                    Text("Wind ${c.windCard} ${Fmt.fmt(Kind.SPEED, c.windSpeed, units)} ${Fmt.unit(Kind.SPEED, units)}$gust", color = w.fg)
                }
            }
            c.quickie?.let { q ->
                val (before, after) = q.sentence
                Text(
                    buildAnnotatedString {
                        append(before)
                        withStyle(SpanStyle(color = quickieColor(q.kind, w), fontWeight = FontWeight.Black)) { append(q.kind.word.uppercase()) }
                        append(after)
                    },
                    color = w.fg, fontSize = 15.sp,
                )
            }
            Text("Observed at ${Fmt.time(c.observedAt, zone, LocalIs24.current)}", color = w.muted, fontSize = 12.sp)
        }
    }
}

/** Sky stages from darkest to lightest; each band runs from its dawn to its dusk. */
private data class Stage(val name: String, val dawn: Long?, val dusk: Long?, val alpha: Float)

/**
 * Today's sunrise and sunset over twilight bands, with the sun's path drawn as a curve that crosses the horizon
 * at sunrise and sunset, and a dot for the sun's position now.
 */
@Composable
private fun SunCard(sun: SunDay, astro: Astro?, zone: ZoneId, now: Long, modifier: Modifier) {
    val w = LocalWu.current
    val is24 = LocalIs24.current
    val measurer = rememberTextMeasurer()
    val stages = listOfNotNull(
        astro?.let { Stage("Civil twilight", it.civilDawn, it.civilDusk, 0.3f) },
        Stage("Daylight", sun.rise, sun.set, 0.58f),
    )
    val sky = if (w.isDark) Color(0xff5b9bff) else Color(0xff7fb6e6)
    Panel(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.wx_sunrise), null, Modifier.size(28.dp))
                    Text("Sunrise ", color = w.fg)
                    Text(Fmt.time(sun.rise, zone, is24), fontWeight = FontWeight.Bold, color = w.fg)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.wx_sunset), null, Modifier.size(28.dp))
                    Text("Sunset ", color = w.fg)
                    Text(Fmt.time(sun.set, zone, is24), fontWeight = FontWeight.Bold, color = w.fg)
                }
            }
            val rise = sun.rise
            val set = sun.set
            if (rise == null || set == null) {
                Text("The sun doesn't rise and set today.", color = w.muted)
                return@Column
            }
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val axisH = 20.dp.toPx()
                val plotH = size.height - axisH
                val span = (sun.dayEnd - sun.dayStart).toFloat()
                val x = { t: Long -> (t - sun.dayStart) / span * size.width }
                for (st in stages) {
                    val l = x(st.dawn ?: continue)
                    val r = x(st.dusk ?: continue)
                    drawRect(sky.copy(alpha = st.alpha), Offset(l, 0f), Size(r - l, plotH))
                }

                val noon = (rise + set) / 2.0
                val k = cos(PI * (set - rise) / 86400.0)
                val horizon = plotH * 0.62f
                val up = horizon - 10.dp.toPx()
                val down = plotH - horizon - 6.dp.toPx()
                val y = { t: Double ->
                    val v = cos(2 * PI * (t - noon) / 86400.0) - k
                    (if (v >= 0) horizon - v / (1 - k) * up else horizon - v / (1 + k) * down).toFloat()
                }
                val curve = Path()
                for (s in 0..96) {
                    val t = sun.dayStart + (sun.dayEnd - sun.dayStart) * s / 96.0
                    val px = ((t - sun.dayStart) / span * size.width).toFloat()
                    if (s == 0) curve.moveTo(px, y(t)) else curve.lineTo(px, y(t))
                }
                drawLine(w.fg.copy(alpha = 0.5f), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
                drawPath(curve, w.fg, style = Stroke(2.dp.toPx()))
                for (t in listOf(rise, set)) drawCircle(w.temp, 4.dp.toPx(), Offset(x(t), horizon))
                if (now in sun.dayStart until sun.dayEnd) {
                    val sunY = y(now.toDouble())
                    val color = if (sunY <= horizon) Color(0xffffc61a) else w.muted
                    drawCircle(color.copy(alpha = 0.35f), 12.dp.toPx(), Offset(x(now), sunY))
                    drawCircle(color, 7.dp.toPx(), Offset(x(now), sunY))
                }

                // Hour axis on its own row below the plot, clear of the curve.
                drawLine(w.grid, Offset(0f, plotH), Offset(size.width, plotH), 1.dp.toPx())
                val style = TextStyle(fontSize = 12.sp, color = w.fg, fontWeight = FontWeight.Medium)
                for (h in listOf(0, 6, 12, 18, 24)) {
                    val tx = x(sun.dayStart + h * 3600L)
                    drawLine(w.muted, Offset(tx, plotH), Offset(tx, plotH + 4.dp.toPx()), 1.dp.toPx())
                    val label = measurer.measure(Fmt.time(sun.dayStart + (h % 24) * 3600L, zone, is24), style)
                    val lx = (tx - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                    drawText(label, topLeft = Offset(lx, plotH + 5.dp.toPx()))
                }
            }
            // Sunrise and sunset are above the graph; only the twilight band needs its times here.
            for (st in stages.filter { it.name != "Daylight" }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(14.dp)) { drawRoundRect(sky.copy(alpha = st.alpha), cornerRadius = CornerRadius(3.dp.toPx())) }
                    Spacer(Modifier.width(8.dp))
                    Text(st.name, Modifier.weight(1f), color = w.fg)
                    Text("${Fmt.time(st.dawn, zone, is24)} – ${Fmt.time(st.dusk, zone, is24)}", color = w.fg, fontWeight = FontWeight.SemiBold)
                }
            }
            val len = sun.length!!
            Text("Daylight ${Fmt.duration((len / 3600).toInt(), ((len % 3600) / 60).toInt())}", color = w.fg, fontWeight = FontWeight.SemiBold)
            sun.tomorrowLength?.let { t ->
                val diff = t - len
                Text(
                    if (abs(diff) < 1) "Tomorrow will be about the same length." else "Tomorrow will be ${Fmt.signedMinutes(diff)} ${if (diff < 0) "shorter" else "longer"}.",
                    color = w.muted,
                )
            }
        }
    }
}

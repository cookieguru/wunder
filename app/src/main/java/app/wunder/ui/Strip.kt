package app.wunder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wunder.R
import app.wunder.data.CHARTS
import app.wunder.data.Chart
import app.wunder.data.Fmt
import app.wunder.data.Kind
import app.wunder.data.Range
import app.wunder.data.Settings
import app.wunder.data.StripDay
import app.wunder.data.StripModel
import app.wunder.data.Units
import app.wunder.data.chartHasData
import app.wunder.data.nightBands
import app.wunder.data.spread
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

val GUTTER = 38.dp
private val PLOT_H = 140.dp
private val PAD_TOP = 10.dp
private val PAD_BOTTOM = 6.dp

data class YScale(val min: Double, val max: Double, val ticks: List<Double>)

private fun niceStep(raw: Double): Double {
    val p = 10.0.pow(floor(log10(raw)))
    val f = raw / p
    return p * when {
        f <= 1 -> 1.0
        f <= 2 -> 2.0
        f <= 2.5 -> 2.5
        f <= 5 -> 5.0
        else -> 10.0
    }
}

private fun ticks(min: Double, max: Double, step: Double): List<Double> {
    val first = ceil(min / step - 1e-9) * step
    return generateSequence(first) { it + step }.takeWhile { it <= max + step * 1e-6 }.toList()
}

fun yScale(chart: Chart, values: List<Double>, units: Units): YScale {
    if (chart.range == Range.PERCENT) return YScale(0.0, 100.0, listOf(0.0, 25.0, 50.0, 75.0, 100.0))
    val lo = values.minOrNull()
    val hi = values.maxOrNull()
    if (lo == null || hi == null) return YScale(0.0, 1.0, listOf(0.0, 1.0))
    if (chart.range == Range.ZERO) {
        val floor = if (chart.kind == Kind.PRECIP) (if (units == Units.IMPERIAL) 0.04 else 1.0) else 5.0
        val top = maxOf(hi, floor) * 1.1
        val step = niceStep(top / 3)
        val max = ceil(top / step) * step
        return YScale(0.0, max, ticks(0.0, max, step))
    }
    val pad = if (hi > lo) (hi - lo) * 0.1 else maxOf(abs(hi) * 0.05, 1.0)
    val step = niceStep((hi - lo + 2 * pad) / 4)
    val min = floor((lo - pad) / step) * step
    val max = ceil((hi + pad) / step) * step
    return YScale(min, max, ticks(min, max, step))
}

fun axisLabel(kind: Kind, v: Double, units: Units): String = when {
    kind == Kind.PRES && units == Units.IMPERIAL -> String.format(Locale.US, "%.2f", v)
    kind == Kind.PRECIP -> String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
    else -> ((v * 100).roundToInt() / 100.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
}

/** One chart's values in display units, in draw order (series marked `behind` first). */
private class ChartData(val chart: Chart, model: StripModel, units: Units) {
    val series = chart.series.filter { it.behind } + chart.series.filterNot { it.behind }
    val values: List<List<Double?>> = series.map { s ->
        model.hours.values(s.key).map { v -> if (chart.kind == Kind.PCT) v else Fmt.conv(chart.kind, v, units) }
    }
    val scale = yScale(chart, values.flatten().filterNotNull(), units)
}

@Composable
fun StripView(model: StripModel, settings: Settings, zone: ZoneId, is24: Boolean, onDay: (LocalDate) -> Unit) {
    val w = LocalWu.current
    val units = settings.units
    val col = 120.dp
    val contentWidth = col * (model.days.sumOf { it.hours } / 24f)
    val scroll = rememberScrollState()
    var cursor by remember(model) { mutableStateOf<Int?>(null) }
    var touched by remember(model) { mutableStateOf<String?>(null) }
    val bands = remember(model) { nightBands(model.days, model.start, model.end) }
    val charts = remember(model, settings) {
        CHARTS.map { c -> c.copy(series = c.series.filter { it.key !in settings.hidden }) }
            .filter { it.series.isNotEmpty() && chartHasData(it, model.hours) }
            .map { ChartData(it, model, units) }
    }
    val measurer = rememberTextMeasurer()

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(GUTTER))
            Row(Modifier.weight(1f).horizontalScroll(scroll)) {
                for (d in model.days) DayHeader(d, col * (d.hours / 24f), units, zone, is24) { onDay(d.date) }
            }
        }
        HorizontalDivider(color = w.grid, thickness = 1.dp)
        for (c in charts) {
            Legend(c.chart, units, Modifier.padding(start = GUTTER + 4.dp, top = 16.dp, bottom = 10.dp))
            Row(Modifier.fillMaxWidth().height(PLOT_H)) {
                Axis(c, units, measurer)
                Plot(
                    c, model, units, zone, is24, bands, contentWidth, scroll, measurer,
                    cursor = { cursor }, touched = { touched },
                    onCursor = { i, id -> cursor = i; touched = id },
                )
            }
        }
    }
}

@Composable
private fun DayHeader(d: StripDay, width: Dp, units: Units, zone: ZoneId, is24: Boolean, onClick: () -> Unit) {
    val w = LocalWu.current
    val font = 14.4.sp
    Column(
        Modifier.width(width)
            .drawBehind { drawLine(w.grid, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
            .clickable(onClick = onClick)
            .padding(top = 4.dp, start = 2.dp, end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("${d.dow} ${d.md}", fontSize = 12.8.sp, fontWeight = FontWeight.Bold, color = w.fg, modifier = Modifier.padding(top = 3.dp))
        Text(hiLo(d.hi, d.lo, units, w), fontSize = font, fontWeight = FontWeight.SemiBold)
        Image(painterResource(iconRes(d.icon)), contentDescription = d.phrase, modifier = Modifier.size(64.dp))
        Text(d.phrase, fontSize = font, color = w.fg, textAlign = TextAlign.Center, minLines = 2, maxLines = 2, lineHeight = font * 1.3f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.wx_raindrop), null, Modifier.size(22.dp).alpha(if ((d.qpf ?: 0.0) > 0) 1f else 0.35f))
            Text("${Fmt.fmt(Kind.PRECIP, d.qpf, units)} ${Fmt.unit(Kind.PRECIP, units)}", fontSize = 12.8.sp, color = w.fg)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            for ((icon, t) in listOf("sunrise" to d.sunrise, "sunset" to d.sunset)) {
                if (t == null || t < d.start || t >= d.end) continue
                val frac = (t - d.start).toFloat() / (d.end - d.start)
                Image(
                    painterResource(iconRes(icon)), null,
                    Modifier.offset(x = maxWidth * frac - 7.dp).size(14.dp)
                        .semantics { contentDescription = "${if (icon == "sunrise") "Sunrise" else "Sunset"} ${Fmt.time(t, zone, is24)}" },
                )
            }
        }
    }
}

/** "63° | 45°F"; a missing value shows as "--" (high) or "--°" (low). */
fun hiLo(hi: Double?, lo: Double?, units: Units, w: WuColors) = buildAnnotatedString {
    withStyle(SpanStyle(color = w.hi)) { append(if (hi != null) Fmt.fmt(Kind.TEMP, hi, units) else "--") }
    withStyle(SpanStyle(color = w.fg)) { append(" | ") }
    withStyle(SpanStyle(color = w.lo)) { append(if (lo != null) Fmt.fmt(Kind.TEMP, lo, units) else "--°") }
    withStyle(SpanStyle(color = w.fg)) { append(Fmt.unit(Kind.TEMP, units).drop(1)) }
}

@Composable
private fun Legend(chart: Chart, units: Units, modifier: Modifier) {
    val w = LocalWu.current
    val unit = if (chart.kind == Kind.PCT) "%" else Fmt.unit(chart.kind, units)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (s in chart.series) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = w.series(s.color)
                Canvas(Modifier.width(16.dp).height(3.dp)) {
                    drawLine(
                        color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height,
                        pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.dp.toPx())) else null,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text("${s.label} ($unit)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = w.fg)
            }
        }
    }
}

private fun DrawScope.yPx(v: Double, s: YScale): Float {
    val top = PAD_TOP.toPx()
    val h = size.height - top - PAD_BOTTOM.toPx()
    return top + ((1 - (v - s.min) / (s.max - s.min)) * h).toFloat()
}

@Composable
private fun Axis(c: ChartData, units: Units, measurer: TextMeasurer) {
    val w = LocalWu.current
    Canvas(Modifier.width(GUTTER).height(PLOT_H)) {
        val style = TextStyle(fontSize = 11.sp, color = w.muted)
        for (t in c.scale.ticks) {
            val layout = measurer.measure(axisLabel(c.chart.kind, t, units), style)
            val y = yPx(t, c.scale) - layout.size.height / 2f
            drawText(layout, topLeft = Offset(size.width - 6.dp.toPx() - layout.size.width, y))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Plot(
    c: ChartData, model: StripModel, units: Units, zone: ZoneId, is24: Boolean, bands: List<Pair<Long, Long>>,
    contentWidth: Dp, scroll: ScrollState, measurer: TextMeasurer,
    cursor: () -> Int?, touched: () -> String?, onCursor: (Int?, String?) -> Unit,
) {
    val w = LocalWu.current
    val xs = model.hours.t
    val span = (model.end - model.start).toFloat()
    fun nearest(px: Float, width: Int): Int? {
        if (xs.isEmpty()) return null
        val t = model.start + px / width * span
        var lo = 0
        var hi = xs.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (xs[mid] < t) lo = mid else hi = mid
        }
        val i = if (abs(xs[lo] - t) <= abs(xs[hi] - t)) lo else hi
        return i.takeIf { abs(xs[i] - t) <= 3600 }
    }
    Box(Modifier.weight(1f).horizontalScroll(scroll)) {
        Canvas(
            Modifier.width(contentWidth).height(PLOT_H)
                .pointerInput(model, c.chart.id) {
                    detectTapGestures { pos ->
                        val i = nearest(pos.x, size.width)
                        onCursor(if (i == cursor() && touched() == c.chart.id) null else i, c.chart.id)
                    }
                }
                .pointerInput(model, c.chart.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onCursor(nearest(it.x, size.width), c.chart.id) },
                        onDrag = { change, _ -> onCursor(nearest(change.position.x, size.width), c.chart.id) },
                    )
                },
        ) {
            val x = { t: Long -> (t - model.start) / span * size.width }
            val top = PAD_TOP.toPx()
            val bottom = size.height - PAD_BOTTOM.toPx()

            for ((s, e) in bands) drawRect(w.night, Offset(x(s), top), Size(x(e) - x(s), bottom - top))
            for (d in model.days.drop(1)) drawLine(w.grid, Offset(x(d.start), top), Offset(x(d.start), bottom), 1.dp.toPx())
            for (t in c.scale.ticks) drawLine(w.grid, Offset(0f, yPx(t, c.scale)), Offset(size.width, yPx(t, c.scale)), 1.dp.toPx())

            c.series.forEachIndexed { si, s ->
                val color = w.series(s.color)
                val pts = c.values[si].mapIndexed { i, v -> v?.let { Offset(x(xs[i]), yPx(it, c.scale)) } }
                for (run in runs(pts)) {
                    val path = { if (s.steps) stepPath(run, size.width) else splinePath(run) }
                    val line = path()
                    if (s.fill) {
                        val end = if (s.steps) minOf(run.last().x + (run.getOrNull(1)?.x?.minus(run[0].x) ?: 0f), size.width) else run.last().x
                        val fill = path().apply {
                            lineTo(end, bottom)
                            lineTo(run.first().x, bottom)
                            close()
                        }
                        drawPath(fill, color.copy(alpha = 0.35f), style = Fill)
                    }
                    drawPath(
                        line, color,
                        style = Stroke(2.dp.toPx(), join = StrokeJoin.Round, pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null),
                    )
                }
                if (s.arrows) {
                    for (i in 1 until xs.size step 3) {
                        val v = c.values[si][i] ?: continue
                        val dir = model.hours.wdir.getOrNull(i) ?: continue
                        windArrow(Offset(x(xs[i]), yPx(v, c.scale)), dir.toFloat(), color, w.outline)
                    }
                }
            }

            val nx = x(model.now)
            drawLine(w.line, Offset(nx, top), Offset(nx, bottom), 1.dp.toPx())

            val i = cursor() ?: return@Canvas
            val cx = x(xs[i])
            drawLine(w.line, Offset(cx, top), Offset(cx, bottom), 1.dp.toPx())
            val pills = c.series.mapIndexed { si, s ->
                val raw = c.values[si][i]
                Triple(pillText(c.chart, s.key, model, i, units), w.series(s.color), raw?.let { yPx(it, c.scale) } ?: ((top + bottom) / 2))
            }
            for ((p, y) in spread(pills, { it.third }, 18.dp.toPx())) {
                val bg = p.second
                val layout = measurer.measure(p.first, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (Fmt.whiteTextOn(bg.toArgb() and 0xffffff)) Color.White else Color(0xff111111)))
                val padX = 5.dp.toPx()
                val boxW = layout.size.width + 2 * padX
                val boxH = layout.size.height + 2.dp.toPx()
                val left = cx - 6.dp.toPx() - boxW
                drawRoundRect(bg, Offset(left, y - boxH / 2), Size(boxW, boxH), CornerRadius(3.dp.toPx()))
                drawText(layout, topLeft = Offset(left + padX, y - layout.size.height / 2f))
            }
            if (touched() == c.chart.id) {
                val t1 = measurer.measure(Fmt.time(xs[i], zone, is24), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = w.fg))
                val t2 = measurer.measure(Fmt.dayShort(xs[i], zone), TextStyle(fontSize = 11.sp, color = w.muted))
                val lx = cx + 6.dp.toPx()
                drawText(t1, topLeft = Offset(lx, 2.dp.toPx()))
                drawText(t2, topLeft = Offset(lx, 2.dp.toPx() + t1.size.height))
            }
        }
    }
}

private fun pillText(chart: Chart, key: String, model: StripModel, i: Int, units: Units): String {
    val raw = model.hours.values(key).getOrNull(i) ?: return "--"
    return when (chart.kind) {
        Kind.TEMP -> Fmt.fmt(Kind.TEMP, raw, units)
        Kind.PCT -> Fmt.fmt(Kind.PCT, raw, units) + "%"
        Kind.SPEED -> "${Fmt.fmt(Kind.SPEED, raw, units)} ${Fmt.unit(Kind.SPEED, units)} ${model.hours.wcard.getOrNull(i).orEmpty()}".trim()
        else -> "${Fmt.fmt(chart.kind, raw, units)} ${Fmt.unit(chart.kind, units)}"
    }
}

/** Contiguous stretches of non-null points. */
private fun runs(pts: List<Offset?>): List<List<Offset>> {
    val out = mutableListOf<List<Offset>>()
    var cur = mutableListOf<Offset>()
    for (p in pts) {
        if (p == null) {
            if (cur.isNotEmpty()) out += cur
            cur = mutableListOf()
        } else {
            cur += p
        }
    }
    if (cur.isNotEmpty()) out += cur
    return out
}

/** Catmull-Rom through the points, like uPlot's spline paths. */
private fun splinePath(p: List<Offset>): Path = Path().apply {
    moveTo(p[0].x, p[0].y)
    if (p.size == 1) {
        lineTo(p[0].x + 0.5f, p[0].y)
        return@apply
    }
    for (i in 0 until p.size - 1) {
        val p0 = p[maxOf(i - 1, 0)]
        val p1 = p[i]
        val p2 = p[i + 1]
        val p3 = p[minOf(i + 2, p.size - 1)]
        cubicTo(
            p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
            p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
            p2.x, p2.y,
        )
    }
}

/** Each value holds until the next hour. */
private fun stepPath(p: List<Offset>, maxX: Float): Path = Path().apply {
    val dx = if (p.size > 1) p[1].x - p[0].x else 0f
    moveTo(p[0].x, p[0].y)
    for (i in 1 until p.size) {
        lineTo(p[i].x, p[i - 1].y)
        lineTo(p[i].x, p[i].y)
    }
    lineTo(minOf(p.last().x + dx, maxX), p.last().y)
}

/** An arrow pointing where the wind blows to, outlined so it reads over the line. */
private fun DrawScope.windArrow(at: Offset, fromDeg: Float, fill: Color, outline: Color) {
    val u = 1.dp.toPx()
    val arrow = Path().apply {
        moveTo(0f, -7 * u)
        lineTo(5 * u, 5 * u)
        lineTo(0f, 2.5f * u)
        lineTo(-5 * u, 5 * u)
        close()
    }
    translate(at.x, at.y) {
        rotate(fromDeg + 180, pivot = Offset.Zero) {
            drawPath(arrow, outline, style = Stroke(3 * u, join = StrokeJoin.Round))
            drawPath(arrow, fill)
        }
    }
}

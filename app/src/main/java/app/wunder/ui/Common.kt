package app.wunder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wunder.data.Fmt
import app.wunder.data.StaleNote
import java.time.Instant
import java.time.ZoneId

val LocalIs24 = compositionLocalOf { true }

val CardShape = RoundedCornerShape(8.dp)

private fun joinList(xs: List<String>) = if (xs.size < 2) xs.joinToString("") else xs.dropLast(1).joinToString(", ") + " and " + xs.last()

@Composable
fun StaleBanner(stale: List<StaleNote>, zone: ZoneId, modifier: Modifier = Modifier) {
    if (stale.isEmpty()) return
    val w = LocalWu.current
    val t = stale.minOf { it.fetchedAt }
    val now = Instant.now().epochSecond
    val sameDay = Fmt.dayShort(t, zone) == Fmt.dayShort(now, zone)
    val whenText = (if (sameDay) "" else Fmt.dayShort(t, zone) + " ") + Fmt.time(t, zone, LocalIs24.current)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).background(w.bannerBg, CardShape).border(4.dp, w.fg, CardShape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).background(w.bannerFg, CircleShape), contentAlignment = Alignment.Center) {
            Text("!", color = w.bannerBg, fontWeight = FontWeight.Black)
        }
        Text(
            "Showing the ${joinList(stale.map { it.what })} from $whenText. The weather service isn't responding.",
            color = w.bannerFg, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        )
    }
}

fun plainReason(r: String?): String = when {
    r == null -> "No data came back."
    r == "bad data" -> "The weather service sent data this app couldn't read."
    r.startsWith("HTTP ") -> "The weather service refused the request ($r)."
    Regex("timed? ?out", RegexOption.IGNORE_CASE).containsMatchIn(r) -> "The weather service didn't respond in time."
    else -> "The weather service couldn't be reached ($r)."
}

@Composable
fun Unavailable(reason: String, title: String = "Forecast unavailable", modifier: Modifier = Modifier) {
    val w = LocalWu.current
    Column(modifier.fillMaxWidth().background(w.panel, CardShape).border(1.dp, w.muted, CardShape).padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = w.fg)
        Text(reason, color = w.fg)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier.padding(top = 20.dp, bottom = 8.dp),
        fontSize = 13.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, color = LocalWu.current.muted,
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val w = LocalWu.current
    Box(modifier.fillMaxWidth().background(w.panel, CardShape).border(1.dp, w.grid, CardShape).padding(16.dp)) { content() }
}

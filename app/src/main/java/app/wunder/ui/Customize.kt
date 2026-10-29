package app.wunder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wunder.data.CHARTS
import app.wunder.data.Settings
import app.wunder.data.Units

/**
 * Units and which charts show, like the web page's Customize panel.
 * [seriesKeys] is empty on screens without charts. [needsBackgroundLocation] shows how to let the widget
 * follow the device while the app is closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeSheet(
    settings: Settings, seriesKeys: List<String>, needsBackgroundLocation: Boolean,
    onChange: (Settings) -> Unit, onWidgetLocation: () -> Unit, onDismiss: () -> Unit,
) {
    val w = LocalWu.current
    val names = CHARTS.flatMap { c -> c.series.map { it.key to it.label } }.toMap()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = w.panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Heading("Units")
            Segmented(listOf(Units.IMPERIAL to "°F", Units.METRIC to "°C"), settings.units) { onChange(settings.copy(units = it)) }
            if (seriesKeys.isNotEmpty()) {
                Heading("Show")
                for (k in seriesKeys) {
                    val on = k !in settings.hidden
                    Row(
                        Modifier.fillMaxWidth()
                            .toggleable(on, role = Role.Switch) { onChange(settings.copy(hidden = if (it) settings.hidden - k else settings.hidden + k)) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(names[k].orEmpty(), Modifier.weight(1f), color = w.fg)
                        Switch(checked = on, onCheckedChange = null)
                    }
                }
            }
            Heading("Location")
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(settings.cityForecast, role = Role.Switch) { onChange(settings.copy(cityForecast = it)) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("City forecast", color = w.fg)
                    Text("For your location and the widget: forecast your city's center, like the official app, instead of your neighborhood.", color = w.muted, fontSize = 13.sp)
                }
                Switch(checked = settings.cityForecast, onCheckedChange = null)
            }
            if (needsBackgroundLocation) {
                Heading("Widget location")
                Text(
                    "While the app is closed, the widget shows the last place the app found. To have it follow you, allow location \"all the time\". It stays approximate.",
                    color = w.fg,
                )
                OutlinedButton(onClick = onWidgetLocation, modifier = Modifier.padding(top = 8.dp)) { Text("Allow all the time") }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text.uppercase(), Modifier.padding(top = 16.dp, bottom = 6.dp), fontSize = 12.sp, letterSpacing = 1.sp, color = LocalWu.current.muted, fontWeight = FontWeight.Bold)
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val w = LocalWu.current
    val shape = RoundedCornerShape(6.dp)
    Row(Modifier.fillMaxWidth().border(1.dp, w.grid, shape)) {
        for ((value, label) in options) {
            val on = value == selected
            Text(
                label,
                Modifier.weight(1f)
                    .background(if (on) w.link else Color.Transparent, shape)
                    .selectable(on, role = Role.RadioButton) { onSelect(value) }
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center, color = if (on) w.bg else w.fg, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

package app.wunder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wunder.R
import app.wunder.data.Place
import kotlinx.coroutines.delay

private val PinYellow = Color(0xffffc61a)

@Composable
fun SearchScreen(vm: MainViewModel, onBack: () -> Unit, onUseCurrent: () -> Unit) {
    val w = LocalWu.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = null
            failed = false
            return@LaunchedEffect
        }
        delay(300)
        val r = vm.search(q)
        failed = r == null
        results = r.orEmpty()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Back", tint = w.fg) }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("City, ZIP code or station") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), "Clear", tint = w.muted) }
                },
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                PlaceRow(R.drawable.ic_my_location, "Current location", if (vm.followCurrent) "Showing now" else "Follow your approximate location", onClick = onUseCurrent)
                HorizontalDivider(color = w.grid)
            }
            val shown = results
            when {
                failed -> item { Text("Search isn't available right now.", Modifier.padding(16.dp), color = w.muted) }
                shown == null -> {
                    if (vm.pinned.isNotEmpty()) item { SectionTitle("Pinned", Modifier.padding(horizontal = 16.dp)) }
                    items(vm.pinned, key = { "p" + it.geocode }) { p ->
                        PlaceRow(R.drawable.ic_place, p.name, p.detail, star = true, onStar = { vm.unpin(p) }) { vm.choose(p) }
                    }
                    if (vm.recents.isNotEmpty()) item { SectionTitle("Recent", Modifier.padding(horizontal = 16.dp)) }
                    items(vm.recents, key = { "r" + it.geocode }) { p ->
                        PlaceRow(R.drawable.ic_place, p.name, p.detail, star = false, onStar = { vm.pin(p) }) { vm.choose(p) }
                    }
                    if (vm.recents.isNotEmpty()) item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = vm::clearRecents) { Text("Clear recent", fontSize = 13.sp, color = w.muted) }
                        }
                    }
                }
                shown.isEmpty() -> item { Text("No places match \"${query.trim()}\".", Modifier.padding(16.dp), color = w.muted) }
                else -> items(shown, key = { it.geocode }) { p -> PlaceRow(R.drawable.ic_place, p.name, p.detail) { vm.choose(p) } }
            }
        }
    }
}

@Composable
/** [star] null shows no star; true is a pinned place (filled), false a recent one (outline). */
private fun PlaceRow(icon: Int, title: String, detail: String, star: Boolean? = null, onStar: () -> Unit = {}, onClick: () -> Unit) {
    val w = LocalWu.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = w.link)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = w.fg)
            if (detail.isNotEmpty()) Text(detail, fontSize = 13.sp, color = w.muted)
        }
        if (star != null) {
            IconButton(onClick = onStar, modifier = Modifier.size(32.dp)) {
                Icon(
                    painterResource(if (star) R.drawable.ic_star else R.drawable.ic_star_border),
                    if (star) "Unpin $title" else "Pin $title",
                    Modifier.size(18.dp),
                    // Yellow reads well on dark backgrounds but fails contrast on white, so light mode uses the primary color.
                    tint = if (!star) w.muted else if (w.isDark) PinYellow else w.link,
                )
            }
        }
    }
}

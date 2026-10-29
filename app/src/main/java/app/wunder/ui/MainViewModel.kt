package app.wunder.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wunder.data.Current
import app.wunder.data.Daily
import app.wunder.data.DayModel
import app.wunder.data.Fetched
import app.wunder.data.History
import app.wunder.data.Hourly
import app.wunder.data.Observation
import app.wunder.data.Place
import app.wunder.data.Repository
import app.wunder.data.Settings
import app.wunder.data.StaleNote
import app.wunder.data.StripModel
import app.wunder.data.SunDay
import app.wunder.data.currentModel
import app.wunder.data.dayModel
import app.wunder.data.parseAstro
import app.wunder.data.staleList
import app.wunder.data.stripModel
import app.wunder.data.sunDay
import app.wunder.data.widgetSnapshot
import app.wunder.widget.WeatherWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

sealed interface Screen {
    data object Home : Screen
    data class Day(val date: LocalDate) : Screen
    data object Search : Screen
}

/** Everything the home screen shows for one place. */
class Forecast(val place: Place, val fetched: Map<String, Fetched>, val now: Long) {
    val zone: ZoneId = ZoneId.of(place.tz)
    val hourly = fetched["hourly"]?.body?.let { Hourly(it) }
    val daily = fetched["daily"]?.body?.let { Daily(it) }
    val obs = fetched["obs"]?.body?.let { Observation(it) }
    val history = fetched["history"]?.body?.let { History(it) }
    val strip: StripModel? = if (hourly != null && daily != null) stripModel(hourly, daily, now, zone) else null
    val sun: SunDay? = daily?.let { sunDay(it, now, zone) }
    val astro = fetched["astro"]?.body?.let { parseAstro(it, now) }
    val stale: List<StaleNote> = staleList(fetched)
    fun current(units: app.wunder.data.Units): Current? =
        if (obs != null && daily != null) currentModel(obs, daily, history, now, zone, units) else null
    fun error(key: String): String? = fetched[key]?.error
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)

    var settings by mutableStateOf(repo.prefs.settings)
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)
    var place by mutableStateOf(repo.prefs.place)
        private set
    /** A searched place shown only inside the app until the user returns to their location; never saved, never on the widget. */
    var viewing by mutableStateOf<Place?>(null)
        private set
    val followCurrent: Boolean get() = viewing == null
    var forecast by mutableStateOf<Forecast?>(null)
        private set
    var day by mutableStateOf<DayModel?>(null)
        private set
    var dayStale by mutableStateOf<List<StaleNote>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    /** Set when nothing could be shown at all, e.g. no location and no saved place. */
    var problem by mutableStateOf<String?>(null)
        private set

    private var loadJob: Job? = null
    private var dayJob: Job? = null

    fun updateSettings(s: Settings) {
        val relocate = s.cityForecast != settings.cityForecast
        settings = s
        repo.prefs.settings = s
        WeatherWidget.updateAll(getApplication())
        if (relocate) refresh(force = true)
    }

    fun refresh(force: Boolean = false) {
        if (!force && loadJob?.isActive == true) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = true
            val now = System.currentTimeMillis() / 1000
            val temporary = viewing
            val p = temporary ?: repo.resolvePlace(now)
            if (p == null) {
                problem = "Couldn't find your location. Search for a place instead."
                loading = false
                return@launch
            }
            place = p
            val fetched = repo.forecast(p, now, force)
            forecast = Forecast(p, fetched, now)
            problem = null
            loading = false
            if (temporary == null) {
                val snapshot = widgetSnapshot(p, fetched, now)
                val ok = snapshot != null && fetched["obs"]?.stale == false
                if (ok) repo.prefs.widget = snapshot
                repo.prefs.widgetFailed = !ok
                WeatherWidget.updateAll(getApplication())
            }
            (screen as? Screen.Day)?.let { loadDay(it.date) }
        }
    }

    fun openDay(date: LocalDate) {
        screen = Screen.Day(date)
        loadDay(date)
    }

    private fun loadDay(date: LocalDate) {
        val f = forecast ?: return
        val hourly = f.hourly ?: return
        val daily = f.daily ?: return
        day = null
        dayJob?.cancel()
        dayJob = viewModelScope.launch {
            val extras = repo.dayExtras(f.place, date, f.now)
            val all = f.fetched + extras
            day = dayModel(date, hourly, daily, all, f.now, f.zone)
            dayStale = staleList(all)
        }
    }

    fun choose(p: Place) {
        viewing = p
        repo.remember(p)
        reloadPlaces()
        place = p
        forecast = null
        screen = Screen.Home
        refresh(force = true)
    }

    fun useCurrentLocation() {
        viewing = null
        place = repo.prefs.place
        forecast = null
        screen = Screen.Home
        refresh(force = true)
    }

    var recents by mutableStateOf(repo.prefs.recents)
        private set
    var pinned by mutableStateOf(repo.prefs.pinned)
        private set

    private fun reloadPlaces() {
        recents = repo.prefs.recents
        pinned = repo.prefs.pinned
    }

    fun pin(p: Place) = repo.pin(p).also { reloadPlaces() }
    fun unpin(p: Place) = repo.unpin(p).also { reloadPlaces() }
    fun clearRecents() = repo.clearRecents().also { reloadPlaces() }

    suspend fun search(q: String): List<Place>? = repo.search(q)
}

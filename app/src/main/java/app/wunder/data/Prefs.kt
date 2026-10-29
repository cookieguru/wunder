package app.wunder.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class Settings(
    val units: Units = Units.IMPERIAL,
    val hidden: Set<String> = emptySet(),
    /** Forecast the center of the current city (like the official app) rather than the device's neighborhood. */
    val cityForecast: Boolean = false,
)

/** What the widget last showed; temperatures are stored in °C. */
data class WidgetSnapshot(
    val place: String, val phrase: String, val icon: String, val temp: Double?, val hi: Double?, val lo: Double?, val pop: Double?,
    val updatedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().put("place", place).put("phrase", phrase).put("icon", icon).put("temp", temp).put("hi", hi)
        .put("lo", lo).put("pop", pop).put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject) = WidgetSnapshot(
            o.optString("place"), o.optString("phrase"), o.optString("icon", "not-available"), o.double("temp"), o.double("hi"),
            o.double("lo"), o.double("pop"), o.optLong("updatedAt"),
        )
    }
}

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("wunder", Context.MODE_PRIVATE)

    var settings: Settings
        get() = Settings(
            enumOr(sp.getString("units", null), Units.IMPERIAL),
            sp.getStringSet("hidden", emptySet())!!.toSet(),
            sp.getBoolean("cityForecast", false),
        )
        set(s) = sp.edit {
            putString("units", s.units.name)
            putStringSet("hidden", s.hidden)
            putBoolean("cityForecast", s.cityForecast)
        }

    /** The last place found for the device; the widget falls back to it when the location can't be read. */
    var place: Place?
        get() = Place.fromJson(sp.getString("place", null)?.let { runCatching { JSONObject(it) }.getOrNull() })
        set(p) = sp.edit { putString("place", p?.toJson()?.toString()) }

    var recents: List<Place>
        get() = places("recents")
        set(list) = sp.edit { putString("recents", JSONArray(list.take(8).map { it.toJson() }).toString()) }

    var pinned: List<Place>
        get() = places("pinned")
        set(list) = sp.edit { putString("pinned", JSONArray(list.map { it.toJson() }).toString()) }

    private fun places(key: String): List<Place> = runCatching {
        val a = JSONArray(sp.getString(key, "[]"))
        List(a.length()) { Place.fromJson(a.getJSONObject(it)) }.filterNotNull()
    }.getOrDefault(emptyList())

    /** Set when the last refresh couldn't reach the weather service, so the widget shows old data. */
    var widgetFailed: Boolean
        get() = sp.getBoolean("widgetFailed", false)
        set(v) = sp.edit { putBoolean("widgetFailed", v) }

    /** Whether the app has asked for background location before; Android stops showing the prompt after a refusal. */
    var askedBackground: Boolean
        get() = sp.getBoolean("askedBackground", false)
        set(v) = sp.edit { putBoolean("askedBackground", v) }

    var widget: WidgetSnapshot?
        get() = sp.getString("widget", null)?.let { runCatching { WidgetSnapshot.fromJson(JSONObject(it)) }.getOrNull() }
        set(w) = sp.edit { putString("widget", w?.toJson()?.toString()) }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}

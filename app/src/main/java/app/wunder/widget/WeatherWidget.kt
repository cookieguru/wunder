package app.wunder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.RemoteViews
import app.wunder.MainActivity
import app.wunder.R
import app.wunder.data.Fmt
import app.wunder.data.Kind
import app.wunder.data.Prefs
import app.wunder.ui.iconRes
import java.util.Date

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WeatherWidget.render(context, manager, ids)
        RefreshWorker.schedule(context)
        if (Prefs(context).widget == null) RefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) = RefreshWorker.schedule(context)

    override fun onDisabled(context: Context) = RefreshWorker.cancel(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WeatherWidget.ACTION_REFRESH) {
            WeatherWidget.showUpdating(context)
            RefreshWorker.refreshNow(context)
        }
    }
}

object WeatherWidget {
    const val ACTION_REFRESH = "app.wunder.action.REFRESH_WIDGET"

    /** Data older than this missed at least one scheduled refresh. */
    private const val STALE_AFTER = 3 * 3600L

    private fun ids(context: Context, manager: AppWidgetManager) =
        manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        render(context, manager, ids(context, manager))
    }

    fun showUpdating(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.widget).apply { setTextViewText(R.id.widget_updated, "Updating…") }
        manager.partiallyUpdateAppWidget(ids(context, manager), views)
    }

    fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val views = views(context)
        for (id in ids) manager.updateAppWidget(id, views)
    }

    private fun views(context: Context): RemoteViews {
        val prefs = Prefs(context)
        val units = prefs.settings.units
        val snap = prefs.widget
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_FROM_WIDGET, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val refresh = PendingIntent.getBroadcast(
            context, 0, Intent(context, WeatherWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteViews(context.packageName, R.layout.widget).apply {
            setOnClickPendingIntent(R.id.widget_root, open)
            setOnClickPendingIntent(R.id.widget_refresh, refresh)
            if (snap == null) {
                setTextViewText(R.id.widget_place, prefs.place?.name ?: "Weather")
                setTextViewText(R.id.widget_temp, "--°")
                setTextViewText(R.id.widget_phrase, "Tap to open")
                setImageViewResource(R.id.widget_icon, R.drawable.wx_not_available)
                setTextViewText(R.id.widget_hi, "--°")
                setTextViewText(R.id.widget_lo, "--°")
                setTextViewText(R.id.widget_pop, "RAIN --%")
                setTextViewText(R.id.widget_updated, "Tap to update")
                return@apply
            }
            setTextViewText(R.id.widget_place, snap.place)
            setTextViewText(R.id.widget_temp, Fmt.fmt(Kind.TEMP, snap.temp, units))
            setTextViewText(R.id.widget_phrase, snap.phrase)
            setImageViewResource(R.id.widget_icon, iconRes(snap.icon))
            setContentDescription(R.id.widget_icon, snap.phrase)
            setTextViewText(R.id.widget_hi, Fmt.fmt(Kind.TEMP, snap.hi, units))
            setTextViewText(R.id.widget_lo, Fmt.fmt(Kind.TEMP, snap.lo, units))
            setTextViewText(R.id.widget_pop, "RAIN ${Fmt.fmt(Kind.PCT, snap.pop, units)}%")
            val stale = prefs.widgetFailed || System.currentTimeMillis() / 1000 - snap.updatedAt > STALE_AFTER
            setTextViewText(R.id.widget_updated, (if (stale) "⚠ " else "") + "Updated ${updatedText(context, snap.updatedAt)}")
            setTextColor(R.id.widget_updated, context.getColor(if (stale) R.color.widget_stale else R.color.widget_muted))
            setContentDescription(R.id.widget_refresh, if (stale) "Weather may be out of date. Update now" else "Update now")
        }
    }

    private fun updatedText(context: Context, epoch: Long): String {
        val date = Date(epoch * 1000)
        val time = DateFormat.getTimeFormat(context).format(date)
        return if (DateUtils.isToday(epoch * 1000)) time else DateFormat.format("EEE", date).toString() + " " + time
    }
}

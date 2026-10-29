package app.wunder.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.wunder.data.Locate
import app.wunder.data.Repository
import app.wunder.data.widgetSnapshot
import java.util.concurrent.TimeUnit

/** Re-reads the location (when allowed in the background) and the current conditions for the widget. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = Repository(applicationContext)
        val now = System.currentTimeMillis() / 1000
        // Without "all the time" location, reading it from the background fails, so keep the last place the app found.
        val place = if (Locate.hasBackgroundPermission(applicationContext)) repo.resolvePlace(now) else repo.prefs.place
        if (place == null) {
            WeatherWidget.updateAll(applicationContext)
            return Result.success()
        }
        val fetched = repo.forecast(place, now, force = inputData.getBoolean(KEY_FORCE, false))
        val snapshot = widgetSnapshot(place, fetched, now)
        val ok = snapshot != null && fetched["obs"]?.stale == false
        if (ok) repo.prefs.widget = snapshot
        repo.prefs.widgetFailed = !ok
        WeatherWidget.updateAll(applicationContext)
        return if (fetched["obs"]?.stale == false || runAttemptCount >= 3) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC = "widget-refresh"
        private const val NOW = "widget-refresh-now"
        private const val KEY_FORCE = "force"
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(2, TimeUnit.HOURS, 20, TimeUnit.MINUTES).setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(online).setInputData(workDataOf(KEY_FORCE to true)).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
        }
    }
}

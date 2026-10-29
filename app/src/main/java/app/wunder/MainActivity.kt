package app.wunder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.wunder.data.Locate
import app.wunder.data.switchKeys
import app.wunder.ui.CustomizeSheet
import app.wunder.ui.DayScreen
import app.wunder.ui.HomeScreen
import app.wunder.ui.LocalIs24
import app.wunder.ui.LocalWu
import app.wunder.ui.MainViewModel
import app.wunder.ui.Screen
import app.wunder.ui.SearchScreen
import app.wunder.ui.WuTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_FROM_WIDGET = "app.wunder.extra.FROM_WIDGET"
    }

    private val vm: MainViewModel by viewModels()
    private var askedForLocation = false

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    private val askLocationThenFollow = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.useCurrentLocation()
    }
    private val askBackground = registerForActivityResult(ActivityResultContracts.RequestPermission()) { backgroundAllowed = it }

    /** Re-read on resume, since the permission is usually changed on the system settings page. */
    private var backgroundAllowed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWidgetPlaceIfFromWidget(intent)
        enableEdgeToEdge()
        setContent {
            WuTheme {
                CompositionLocalProvider(LocalIs24 provides DateFormat.is24HourFormat(this)) {
                    var customizing by remember { mutableStateOf(false) }
                    // Content starts below the status bar, which keeps a solid background so its icons stay readable.
                    Box(Modifier.fillMaxSize().background(LocalWu.current.bg).statusBarsPadding()) {
                        when (val s = vm.screen) {
                            Screen.Home -> HomeScreen(vm, onSearch = { vm.screen = Screen.Search }, onCustomize = { customizing = true }, onDay = vm::openDay)
                            is Screen.Day -> DayScreen(vm, onHome = { vm.screen = Screen.Home }, onDay = vm::openDay)
                            Screen.Search -> SearchScreen(vm, onBack = { vm.screen = Screen.Home }, onUseCurrent = ::followCurrentLocation)
                        }
                    }
                    BackHandler(enabled = vm.screen != Screen.Home) { vm.screen = Screen.Home }
                    // A searched place is only a temporary view; back returns to your location.
                    BackHandler(enabled = vm.screen == Screen.Home && !vm.followCurrent) { vm.useCurrentLocation() }
                    if (customizing) {
                        CustomizeSheet(
                            vm.settings,
                            seriesKeys = vm.forecast?.strip?.let { switchKeys(it.hours) }.orEmpty(),
                            needsBackgroundLocation = Locate.hasPermission(this) && !backgroundAllowed,
                            onChange = vm::updateSettings,
                            onWidgetLocation = ::askBackgroundLocation,
                            onDismiss = { customizing = false },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showWidgetPlaceIfFromWidget(intent)
    }

    /** Opening from the widget shows the widget's place: the device's location. */
    private fun showWidgetPlaceIfFromWidget(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_FROM_WIDGET, false)) return
        vm.screen = Screen.Home
        if (!vm.followCurrent) vm.useCurrentLocation()
    }

    override fun onStart() {
        super.onStart()
        if (vm.followCurrent && !Locate.hasPermission(this) && !askedForLocation) {
            askedForLocation = true
            askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            vm.refresh()
        }
    }

    private fun followCurrentLocation() {
        if (Locate.hasPermission(this)) vm.useCurrentLocation() else askLocationThenFollow.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    override fun onResume() {
        super.onResume()
        backgroundAllowed = Locate.hasBackgroundPermission(this)
    }

    /** Android shows the prompt only until it's refused; after that the choice lives on the app's settings page. */
    private fun askBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
        if (vm.repo.prefs.askedBackground && !shouldShowRequestPermissionRationale(permission)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            vm.repo.prefs.askedBackground = true
            askBackground.launch(permission)
        }
    }
}

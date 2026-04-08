package okoge.house.throttling_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okoge.house.throttling_app.data.TargetAppRepository
import okoge.house.throttling_app.ui.AppListScreen
import okoge.house.throttling_app.ui.LicenseScreen
import okoge.house.throttling_app.ui.MainScreen
import okoge.house.throttling_app.ui.VpnMode
import okoge.house.throttling_app.ui.kbpsToKBps
import okoge.house.throttling_app.ui.sliderToKbps
import okoge.house.throttling_app.ui.theme.ThrottlingappTheme

@Serializable
data object MainRoute

@Serializable
data object AppListRoute

@Serializable
data object LicenseRoute

class MainActivity : ComponentActivity() {

    private var isVpnRunning by mutableStateOf(false)
    private var isTransitioning by mutableStateOf(false)
    private var selectedMode by mutableStateOf(VpnMode.Throttle)
    private var sliderValue by mutableFloatStateOf(0.5f)

    private lateinit var repository: TargetAppRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed to VPN permission flow regardless of notification permission result
        requestVpnPermission()
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            lifecycleScope.launch {
                delay(1000L)
                isTransitioning = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = TargetAppRepository(applicationContext)
        setContent {
            ThrottlingappTheme {
                val backStack = remember { mutableStateListOf<Any>(MainRoute) }
                val targetApps by repository.targetApps.collectAsState(initial = emptySet())

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    NavDisplay(
                        modifier = Modifier.padding(innerPadding),
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = entryProvider {
                            entry<MainRoute> {
                                MainScreen(
                                    isVpnRunning = isVpnRunning,
                                    isTransitioning = isTransitioning,
                                    selectedMode = selectedMode,
                                    sliderValue = sliderValue,
                                    targetAppCount = targetApps.size,
                                    onModeChange = { mode ->
                                        selectedMode = mode
                                        if (isVpnRunning) sendSpeed()
                                    },
                                    onSliderChange = { sliderValue = it },
                                    onSliderChangeFinished = {
                                        if (isVpnRunning && selectedMode == VpnMode.Throttle) sendSpeed()
                                    },
                                    onStartStop = {
                                        if (isTransitioning) return@MainScreen
                                        isTransitioning = true
                                        if (isVpnRunning) {
                                            stopVpnService()
                                        } else {
                                            if (targetApps.isEmpty()) {
                                                isTransitioning = false
                                                return@MainScreen
                                            }
                                            requestPermissionsAndStart()
                                        }
                                    },
                                    onNavigateToAppList = { backStack.add(AppListRoute) },
                                    onNavigateToLicenses = { backStack.add(LicenseRoute) },
                                )
                            }
                             entry<LicenseRoute> {
                                 LicenseScreen(
                                     onBack = { backStack.removeLastOrNull() },
                                     modifier = Modifier.padding(innerPadding),
                                 )
                             }
                            entry<AppListRoute> {
                                AppListScreen(
                                    targetApps = targetApps,
                                    onAddApp = { pkg ->
                                        lifecycleScope.launch { repository.addApp(pkg) }
                                    },
                                    onRemoveApp = { pkg ->
                                        lifecycleScope.launch { repository.removeApp(pkg) }
                                    },
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    // Speed value passed to Go layer (KB/s). -1=Block, 0=Unlimited, >0=Throttle
    private fun currentSpeedKBps(): Int = when (selectedMode) {
        VpnMode.Block -> -1
        VpnMode.Throttle -> kbpsToKBps(sliderToKbps(sliderValue))
        VpnMode.Unlimited -> 0
    }

    private fun requestPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestVpnPermission()
        }
    }

    private fun requestVpnPermission() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        lifecycleScope.launch {
            val apps = ArrayList(repository.targetApps.first())
            val intent = Intent(this@MainActivity, ThrottleVpnService::class.java).apply {
                putExtra(ThrottleVpnService.EXTRA_SPEED_KBPS, currentSpeedKBps())
                putStringArrayListExtra(ThrottleVpnService.EXTRA_TARGET_APPS, apps)
            }
            startForegroundService(intent)
            isVpnRunning = true
            delay(1000L)
            isTransitioning = false
        }
    }

    private fun stopVpnService() {
        val intent = Intent(this, ThrottleVpnService::class.java).apply {
            action = ThrottleVpnService.ACTION_STOP
        }
        startService(intent)
        isVpnRunning = false
        lifecycleScope.launch {
            delay(1000L)
            isTransitioning = false
        }
    }

    private fun sendSpeed() {
        val intent = Intent(this, ThrottleVpnService::class.java).apply {
            action = ThrottleVpnService.ACTION_SET_SPEED
            putExtra(ThrottleVpnService.EXTRA_SPEED_KBPS, currentSpeedKBps())
        }
        startService(intent)
    }

}

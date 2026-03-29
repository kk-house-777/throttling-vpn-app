package okoge.house.throttling_app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import okoge.house.throttling_app.ui.MainScreen
import okoge.house.throttling_app.ui.VpnMode
import okoge.house.throttling_app.ui.kbpsToKBps
import okoge.house.throttling_app.ui.sliderToKbps
import okoge.house.throttling_app.ui.theme.ThrottlingappTheme

@Serializable
data object MainRoute

@Serializable
data object AppListRoute

class MainActivity : ComponentActivity() {

    private var isVpnRunning by mutableStateOf(false)
    private var isTransitioning by mutableStateOf(false)
    private var selectedMode by mutableStateOf(VpnMode.Throttle)
    private var sliderValue by mutableFloatStateOf(0.5f)

    private lateinit var repository: TargetAppRepository

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            lifecycleScope.launch {
                delay(3000L)
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

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavDisplay(
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
                                            requestVpnPermission()
                                        }
                                    },
                                    onNavigateToAppList = { backStack.add(AppListRoute) },
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
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    // Go 側に渡す速度値 (KB/s)。-1=Block, 0=Unlimited, >0=制限
    private fun currentSpeedKBps(): Int = when (selectedMode) {
        VpnMode.Block -> -1
        VpnMode.Throttle -> kbpsToKBps(sliderToKbps(sliderValue))
        VpnMode.Unlimited -> 0
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
            delay(3000L)
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
            delay(3000L)
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

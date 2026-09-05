package okoge.house.throttling_app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import okoge.house.throttling_app.ui.theme.ThrottlingappTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        isVpnRunning: Boolean = false,
        isTransitioning: Boolean = false,
        selectedMode: VpnMode = VpnMode.Throttle,
        sliderValue: Float = 0.5f,
        targetAppCount: Int = 1,
        onModeChange: (VpnMode) -> Unit = {},
        onSliderChange: (Float) -> Unit = {},
        onSliderChangeFinished: () -> Unit = {},
        onStartStop: () -> Unit = {},
        onNavigateToAppList: () -> Unit = {},
        onNavigateToLicenses: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ThrottlingappTheme {
                MainScreen(
                    isVpnRunning = isVpnRunning,
                    isTransitioning = isTransitioning,
                    selectedMode = selectedMode,
                    sliderValue = sliderValue,
                    targetAppCount = targetAppCount,
                    onModeChange = onModeChange,
                    onSliderChange = onSliderChange,
                    onSliderChangeFinished = onSliderChangeFinished,
                    onStartStop = onStartStop,
                    onNavigateToAppList = onNavigateToAppList,
                    onNavigateToLicenses = onNavigateToLicenses,
                )
            }
        }
    }

    @Test
    fun vpnOff_showsOffState() {
        setContent(isVpnRunning = false)
        composeTestRule.onNodeWithText("VPN is OFF").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start VPN").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun vpnOn_throttleMode_showsSpeedInStatus() {
        setContent(isVpnRunning = true, selectedMode = VpnMode.Throttle, sliderValue = 0.5f)
        val kbps = sliderToKbps(0.5f)
        composeTestRule.onNodeWithText("VPN is ON — ${formatKbps(kbps)}").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stop VPN").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun vpnOn_blockMode_showsBlockedStatus() {
        setContent(isVpnRunning = true, selectedMode = VpnMode.Block)
        composeTestRule.onNodeWithText("VPN is ON — BLOCKED").assertIsDisplayed()
    }

    @Test
    fun vpnOn_unlimitedMode_showsUnlimitedStatus() {
        setContent(isVpnRunning = true, selectedMode = VpnMode.Unlimited)
        composeTestRule.onNodeWithText("VPN is ON — Unlimited").assertIsDisplayed()
    }

    @Test
    fun noTargetApps_startButtonDisabled() {
        setContent(isVpnRunning = false, targetAppCount = 0)
        composeTestRule.onNodeWithText("Start VPN").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Add target apps first to start VPN.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun hasTargetApps_startButtonEnabled() {
        setContent(isVpnRunning = false, targetAppCount = 2)
        composeTestRule.onNodeWithText("Target: 2 apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start VPN").performScrollTo().assertIsEnabled()
    }

    @Test
    fun isTransitioning_startStopButtonDisabled() {
        setContent(isVpnRunning = false, targetAppCount = 1, isTransitioning = true)
        composeTestRule.onNodeWithText("Start VPN").assertDoesNotExist()
    }

    @Test
    fun clickingStartStop_invokesCallback() {
        var clicked = false
        setContent(isVpnRunning = false, targetAppCount = 1, onStartStop = { clicked = true })
        composeTestRule.onNodeWithText("Start VPN").performScrollTo().performClick()
        assert(clicked)
    }

    @Test
    fun clickingModeButton_invokesCallbackWithMode() {
        var selected: VpnMode? = null
        setContent(selectedMode = VpnMode.Throttle, onModeChange = { selected = it })
        composeTestRule.onNodeWithText("Block").performClick()
        assert(selected == VpnMode.Block)
    }

    @Test
    fun clickingManage_navigatesToAppList() {
        var navigated = false
        setContent(onNavigateToAppList = { navigated = true })
        composeTestRule.onNodeWithText("Manage →").performClick()
        assert(navigated)
    }

    @Test
    fun clickingLicenses_navigatesToLicenseScreen() {
        var navigated = false
        setContent(onNavigateToLicenses = { navigated = true })
        composeTestRule.onNodeWithText("Open Source Licenses").performScrollTo().performClick()
        assert(navigated)
    }
}

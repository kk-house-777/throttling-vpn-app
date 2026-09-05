package okoge.house.throttling_app

import android.Manifest
import androidx.datastore.preferences.core.edit
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okoge.house.throttling_app.data.TargetAppRepository
import okoge.house.throttling_app.data.dataStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowVpnService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Application-level pass: launch -> configure a target app -> start VPN,
 * driven end to end through the real MainActivity + Compose UI.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityFlowTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    /** Persisting a target app round-trips through DataStore's IO dispatcher,
     *  so a single waitForIdle() can race the UI update. Poll instead. */
    private fun waitUntilTextShown(text: String, timeoutMillis: Long = 5_000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // null == VPN consent already granted, so VpnService.prepare() skips the system dialog.
        ShadowVpnService.setPrepareResult(null)
        runBlocking { app.dataStore.edit { it.clear() } }
    }

    @Test
    fun launchConfigureAndStart_endToEndPass() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        composeTestRule.waitForIdle()

        // Debug builds default to one target app (the bundled test-app package).
        composeTestRule.onNodeWithText("Target: 1 apps").assertExists()

        // Navigate to the app list and register another target package.
        composeTestRule.onNodeWithText("Manage →").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Target Apps").assertExists()
        composeTestRule.onNodeWithText("Application ID").performTextInput("com.example.target")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add").performClick()
        waitUntilTextShown("com.example.target")

        // Back to the main screen: target count now reflects the added app.
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        waitUntilTextShown("Target: 2 apps")

        val repository = TargetAppRepository(controller.get().applicationContext)
        val targetApps = runBlocking {
            withTimeout(5_000) {
                repository.targetApps.first { it.contains("com.example.target") }
            }
        }
        assert(targetApps.contains("com.example.target"))

        // Start VPN: with permissions already granted, this should reach the
        // VPN-running state without needing user interaction with system dialogs.
        composeTestRule.onNodeWithText("Start VPN").performScrollTo().performClick()
        waitUntilTextShown("Stop VPN")

        controller.destroy()
    }
}

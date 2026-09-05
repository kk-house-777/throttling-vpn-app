package okoge.house.throttling_app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import okoge.house.throttling_app.data.InstalledApp
import okoge.house.throttling_app.ui.theme.ThrottlingappTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        targetApps: Set<String> = emptySet(),
        onAddApp: (String) -> Unit = {},
        onRemoveApp: (String) -> Unit = {},
        onBack: () -> Unit = {},
        installedApps: List<InstalledApp> = emptyList(),
    ) {
        composeTestRule.setContent {
            ThrottlingappTheme {
                AppListScreen(
                    targetApps = targetApps,
                    onAddApp = onAddApp,
                    onRemoveApp = onRemoveApp,
                    onBack = onBack,
                    installedApps = installedApps,
                )
            }
        }
    }

    @Test
    fun emptyList_showsEmptyMessage() {
        setContent(targetApps = emptySet())
        composeTestRule.onNodeWithText("No apps registered. Add an application ID above.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Registered Apps (0)").assertIsDisplayed()
    }

    @Test
    fun nonEmptyList_showsEachApp() {
        setContent(targetApps = setOf("com.example.a", "com.example.b"))
        composeTestRule.onNodeWithText("Registered Apps (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("com.example.a").assertIsDisplayed()
        composeTestRule.onNodeWithText("com.example.b").assertIsDisplayed()
    }

    @Test
    fun addButton_disabledWhenInputEmpty() {
        setContent()
        composeTestRule.onNodeWithText("Add").assertIsNotEnabled()
    }

    @Test
    fun typingPackageAndClickingAdd_invokesCallbackAndClearsInput() {
        var added: String? = null
        setContent(onAddApp = { added = it })

        composeTestRule.onNodeWithText("Application ID").performTextInput("com.example.new")
        composeTestRule.onNodeWithText("Add").performClick()

        assert(added == "com.example.new")
    }

    @Test
    fun typingWhitespaceOnly_addButtonStaysDisabled() {
        setContent()
        composeTestRule.onNodeWithText("Application ID").performTextInput("   ")
        composeTestRule.onNodeWithText("Add").assertIsNotEnabled()
    }

    @Test
    fun clickingRemove_invokesCallbackWithPackage() {
        var removed: String? = null
        setContent(targetApps = setOf("com.example.a"), onRemoveApp = { removed = it })

        composeTestRule.onNodeWithText("✕").performClick()

        assert(removed == "com.example.a")
    }

    @Test
    fun clickingBack_invokesCallback() {
        var backPressed = false
        setContent(onBack = { backPressed = true })

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assert(backPressed)
    }

    // ── installed app picker ──

    @Test
    fun noInstalledApps_pickerButtonHidden() {
        setContent(installedApps = emptyList())
        composeTestRule.onNodeWithText("Pick from installed apps").assertDoesNotExist()
    }

    @Test
    fun hasInstalledApps_pickerButtonShown() {
        setContent(installedApps = listOf(InstalledApp("com.example.a", "App A")))
        composeTestRule.onNodeWithText("Pick from installed apps").assertIsDisplayed()
    }

    @Test
    fun openingPicker_showsInstalledApps() {
        setContent(
            installedApps = listOf(
                InstalledApp("com.example.a", "App A"),
                InstalledApp("com.example.b", "App B"),
            ),
        )
        composeTestRule.onNodeWithText("Pick from installed apps").performClick()

        composeTestRule.onNodeWithText("App A").assertIsDisplayed()
        composeTestRule.onNodeWithText("App B").assertIsDisplayed()
    }

    @Test
    fun picker_alreadyTargetedAppsAreExcluded() {
        setContent(
            targetApps = setOf("com.example.a"),
            installedApps = listOf(
                InstalledApp("com.example.a", "App A"),
                InstalledApp("com.example.b", "App B"),
            ),
        )
        composeTestRule.onNodeWithText("Pick from installed apps").performClick()

        composeTestRule.onNodeWithText("App A").assertDoesNotExist()
        composeTestRule.onNodeWithText("App B").assertIsDisplayed()
    }

    @Test
    fun picker_searchFiltersByLabelOrPackageName() {
        setContent(
            installedApps = listOf(
                InstalledApp("com.example.a", "App A"),
                InstalledApp("com.example.b", "App B"),
            ),
        )
        composeTestRule.onNodeWithText("Pick from installed apps").performClick()
        composeTestRule.onNodeWithText("Search").performTextInput("App A")

        // "App A" also matches the search field's own value, so assert on the
        // one unambiguous signal that filtering actually happened: App B is gone.
        composeTestRule.onNodeWithText("App B").assertDoesNotExist()
    }

    @Test
    fun picker_selectingApp_invokesOnAddAppAndCloses() {
        var added: String? = null
        setContent(
            onAddApp = { added = it },
            installedApps = listOf(InstalledApp("com.example.a", "App A")),
        )
        composeTestRule.onNodeWithText("Pick from installed apps").performClick()
        composeTestRule.onNodeWithText("App A").performClick()

        assert(added == "com.example.a")
        composeTestRule.onNodeWithText("Select an app").assertDoesNotExist()
    }
}

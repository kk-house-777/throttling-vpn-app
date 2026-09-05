package okoge.house.throttling_app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    ) {
        composeTestRule.setContent {
            ThrottlingappTheme {
                AppListScreen(
                    targetApps = targetApps,
                    onAddApp = onAddApp,
                    onRemoveApp = onRemoveApp,
                    onBack = onBack,
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
}

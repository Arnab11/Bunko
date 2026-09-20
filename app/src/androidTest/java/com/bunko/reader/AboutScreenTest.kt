package com.bunko.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bunko.reader.settings.AboutScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Regression guard: AboutScreen must compose without crashing
// (painterResource rejects mipmap asset types at runtime).
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aboutScreenComposes() {
        composeRule.setContent {
            AboutScreen()
        }
        composeRule.onNodeWithText("Bunko").assertExists()
        composeRule.onNodeWithText("Device info").assertExists()
    }
}

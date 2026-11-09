package com.claude.wear.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.wear.compose.material3.AppScaffold
import com.claude.wear.ChatUiState
import com.claude.wear.UiMessage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen on a round-watch configuration, checks the key content
 * is shown, and saves a PNG to app/build/screenshots for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w225dp-h225dp-small-notlong-round-watch-xhdpi-keyshidden-nonav")
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun conversation() {
        render("chat") {
            ChatScreen(
                state = ChatUiState(
                    hasApiKey = true,
                    messages = listOf(
                        UiMessage(0, fromUser = true, text = "How far is the Moon?"),
                        UiMessage(
                            1,
                            fromUser = false,
                            text = "About 384,400 km on average, roughly 30 Earths lined up side by side.",
                        ),
                    ),
                ),
                onSend = {}, onStop = {}, onNewChat = {}, onSaveApiKey = {}, onOpenSettings = {},
            )
        }
        compose.onNodeWithText("How far is the Moon?").assertExists()
    }

    @Test
    fun streamingReply() {
        render("chat_streaming") {
            ChatScreen(
                state = ChatUiState(
                    hasApiKey = true,
                    isResponding = true,
                    messages = listOf(
                        UiMessage(0, fromUser = true, text = "Quick dinner idea?"),
                        UiMessage(1, fromUser = false, text = "", pending = true),
                    ),
                ),
                onSend = {}, onStop = {}, onNewChat = {}, onSaveApiKey = {}, onOpenSettings = {},
            )
        }
        compose.onNodeWithText("Thinking…").assertExists()
    }

    @Test
    fun emptyChat() {
        render("chat_empty") {
            ChatScreen(
                state = ChatUiState(hasApiKey = true),
                onSend = {}, onStop = {}, onNewChat = {}, onSaveApiKey = {}, onOpenSettings = {},
            )
        }
        compose.onNodeWithText("Ask Claude anything").assertExists()
    }

    @Test
    fun missingApiKey() {
        render("needs_key") {
            ChatScreen(
                state = ChatUiState(hasApiKey = false),
                onSend = {}, onStop = {}, onNewChat = {}, onSaveApiKey = {}, onOpenSettings = {},
            )
        }
        compose.onNodeWithText("Add your API key").assertExists()
    }

    @Test
    fun settings() {
        render("settings") {
            SettingsScreen(
                state = ChatUiState(hasApiKey = true, maskedApiKey = "sk-ant-…a1b2"),
                onSaveApiKey = {}, onClearApiKey = {},
            )
        }
        compose.onNodeWithText("Remove key").assertExists()
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        compose.setContent { ClaudeTheme { AppScaffold { content() } } }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

package com.claude.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.claude.wear.ChatViewModel

private object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}

@Composable
fun ClaudeWearApp(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberSwipeDismissableNavController()

    ClaudeTheme {
        AppScaffold {
            SwipeDismissableNavHost(navController = navController, startDestination = Routes.CHAT) {
                composable(Routes.CHAT) {
                    ChatScreen(
                        state = state,
                        onSend = viewModel::send,
                        onStop = viewModel::stop,
                        onNewChat = viewModel::newChat,
                        onSaveApiKey = viewModel::saveApiKey,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        state = state,
                        onSaveApiKey = viewModel::saveApiKey,
                        onClearApiKey = viewModel::clearApiKey,
                    )
                }
            }
        }
    }
}

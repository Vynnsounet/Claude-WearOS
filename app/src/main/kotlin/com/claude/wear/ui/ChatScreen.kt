package com.claude.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.tooling.preview.devices.WearDevices
import com.claude.wear.ChatUiState
import com.claude.wear.R
import com.claude.wear.UiMessage

@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val voice = rememberVoiceInput(onSend)
    val keyboard = rememberTextInput(stringResource(R.string.speak_prompt), onSend)
    val keyInput = rememberTextInput(stringResource(R.string.api_key), onSaveApiKey)
    val needsKey = state.hasApiKey == false

    // Keep the newest text in view while a reply streams in.
    val last = state.messages.lastOrNull()
    LaunchedEffect(state.messages.size, last?.text?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size) // +1 for the header
        }
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            when {
                needsKey -> EdgeButton(onClick = { keyInput.launch() }) {
                    Text(stringResource(R.string.set_api_key))
                }
                state.isResponding -> EdgeButton(
                    onClick = onStop,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.stop))
                }
                else -> EdgeButton(onClick = { voice.launch() }, enabled = state.hasApiKey == true) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = stringResource(R.string.ask))
                }
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.app_name)) }
            }

            if (needsKey) {
                item {
                    TitleCard(
                        onClick = { keyInput.launch() },
                        title = { Text(stringResource(R.string.needs_key_title)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.needs_key_body))
                    }
                }
            } else if (state.messages.isEmpty() && state.hasApiKey == true) {
                item {
                    Text(
                        text = stringResource(R.string.empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
            }

            items(state.messages.size, key = { state.messages[it].id }) { index ->
                MessageCard(state.messages[index])
            }

            if (state.hasApiKey == true && !state.isResponding) {
                item {
                    FilledTonalButton(
                        onClick = { keyboard.launch() },
                        icon = { Icon(painterResource(R.drawable.ic_keyboard), contentDescription = null) },
                        label = { Text(stringResource(R.string.type)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.messages.isNotEmpty()) {
                    item {
                        FilledTonalButton(
                            onClick = onNewChat,
                            icon = { Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) },
                            label = { Text(stringResource(R.string.new_chat)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.outlinedButtonColors(),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                    icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                    label = { Text(stringResource(R.string.settings)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MessageCard(message: UiMessage) {
    val title = stringResource(if (message.fromUser) R.string.you else R.string.claude)
    val colors = if (message.fromUser) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            titleColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors()
    }
    val body = when {
        message.pending && message.text.isEmpty() -> stringResource(R.string.thinking)
        else -> message.text
    }

    TitleCard(
        onClick = {},
        title = { Text(title) },
        colors = colors,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = body,
            color = if (message.isError) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
        )
        message.note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun ChatScreenPreview() {
    ClaudeTheme {
        ChatScreen(
            state = ChatUiState(
                hasApiKey = true,
                messages = listOf(
                    UiMessage(0, fromUser = true, text = "How far is the Moon?"),
                    UiMessage(1, fromUser = false, text = "About 384,400 km on average, roughly 30 Earths lined up."),
                ),
            ),
            onSend = {},
            onStop = {},
            onNewChat = {},
            onSaveApiKey = {},
            onOpenSettings = {},
        )
    }
}

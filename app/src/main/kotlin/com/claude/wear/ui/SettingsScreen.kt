package com.claude.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.claude.wear.ChatUiState
import com.claude.wear.R

@Composable
fun SettingsScreen(
    state: ChatUiState,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val keyInput = rememberTextInput(stringResource(R.string.api_key), onSaveApiKey)

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.settings)) } }
            item {
                Text(
                    text = state.maskedApiKey?.let { stringResource(R.string.api_key_set, it) }
                        ?: stringResource(R.string.api_key_missing),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = { keyInput.launch() },
                    label = { Text(stringResource(R.string.set_api_key)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.hasApiKey == true) {
                item {
                    FilledTonalButton(
                        onClick = onClearApiKey,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        label = { Text(stringResource(R.string.clear_api_key)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.model_label, state.model),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

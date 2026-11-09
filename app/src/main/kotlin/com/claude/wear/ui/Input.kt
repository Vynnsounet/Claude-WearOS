package com.claude.wear.ui

import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.claude.wear.R

private const val REMOTE_INPUT_KEY = "claude_text_input"

/** Launches the system speech recognizer and returns the recognised text. */
class VoiceInput(
    private val launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    private val prompt: String,
    private val onUnavailable: () -> Unit,
) {
    fun launch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            onUnavailable()
        }
    }
}

@Composable
fun rememberVoiceInput(onResult: (String) -> Unit): VoiceInput {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(onResult)
        }
    }
    val prompt = stringResource(R.string.speak_prompt)
    return remember(launcher, prompt) {
        VoiceInput(launcher, prompt) {
            Toast.makeText(context, R.string.no_speech, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Opens the Wear OS text entry screen (keyboard, handwriting or the paired
 * phone's keyboard, depending on the watch) and returns what was entered.
 */
class TextInput(
    private val launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    private val label: String,
) {
    fun launch() {
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel(label)
            .wearableExtender {
                setEmojisAllowed(true)
                setInputActionType(EditorInfo.IME_ACTION_SEND)
            }
            .build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        launcher.launch(intent)
    }
}

@Composable
fun rememberTextInput(label: String, onResult: (String) -> Unit): TextInput {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        RemoteInput.getResultsFromIntent(data)
            ?.getCharSequence(REMOTE_INPUT_KEY)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let(onResult)
    }
    return remember(launcher, label) { TextInput(launcher, label) }
}

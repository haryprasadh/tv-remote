package com.hari.tvremote.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.hari.tvremote.R
import com.hari.tvremote.protocol.TvTextField
import com.hari.tvremote.remote.TvRemote

/**
 * Live typing into the TV's focused text field. Enabled only while the TV reports its keyboard open;
 * each edit sends the whole text (remote.py send_text semantics). 🎤 uses the phone's speech
 * recognizer activity, so the app needs no microphone permission.
 */
@Composable
fun TypeTab(remote: TvRemote, linked: Boolean, field: TvTextField?, imeSeq: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var text by remember { mutableStateOf(TextFieldValue("")) }
    val active = linked && field != null

    fun replace(newText: String) {
        text = TextFieldValue(newText, TextRange(newText.length))
        if (active) remote.type(newText)
    }

    // TV keyboard opened (again): take the TV field's text, focus, show the phone keyboard.
    LaunchedEffect(imeSeq, field != null) {
        if (field == null) {
            text = TextFieldValue("")
            return@LaunchedEffect
        }
        if (field.value != text.text) text = TextFieldValue(field.value, TextRange(field.value.length))
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) replace(if (text.text.isBlank()) spoken else "${text.text.trimEnd()} $spoken")
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize().imePadding().padding(20.dp),
    ) {
        Text(
            if (active) "Type here. It appears on the TV as you type." else "Open a search box on the TV first",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                val changed = new.text != text.text
                text = new
                if (changed && active) remote.type(new.text)
            },
            enabled = active,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(if (active) (field?.label?.takeIf { it.isNotBlank() } ?: "Search") else "Open a search box on the TV first")
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { remote.enter() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { remote.enter() },
                enabled = active,
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Icon(painterResource(R.drawable.ic_enter), null, Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text("Enter", style = MaterialTheme.typography.titleMedium)
            }
            FilledTonalButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what to search for")
                    try {
                        voice.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "Voice typing isn't available on this phone", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = active,
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Icon(painterResource(R.drawable.ic_mic), null, Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text("Speak", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (active && text.text.isNotEmpty()) {
            FilledTonalButton(
                onClick = { replace("") },
                modifier = Modifier.align(Alignment.End).height(52.dp),
            ) { Text("Clear", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

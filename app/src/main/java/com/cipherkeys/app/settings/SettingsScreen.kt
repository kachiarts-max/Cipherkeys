package com.cipherkeys.app.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cipherkeys.app.data.KeyboardMode
import com.cipherkeys.app.data.KeyboardTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("CipherKeys Settings") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }) {
                    Text("Enable CipherKeys keyboard")
                }
            }

            item {
                SectionTitle("Default mode")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyboardMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.defaultMode == mode,
                            onClick = { viewModel.setDefaultMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }

            item {
                ToggleRow(
                    label = "Auto encode",
                    checked = settings.autoEncodeEnabled,
                    onCheckedChange = viewModel::setAutoEncode
                )
            }
            item {
                ToggleRow(
                    label = "Auto decode on focus",
                    checked = settings.autoDecodeEnabled,
                    onCheckedChange = viewModel::setAutoDecode
                )
            }
            item {
                ToggleRow(
                    label = "Vibration",
                    checked = settings.vibrationEnabled,
                    onCheckedChange = viewModel::setVibration
                )
            }
            item {
                ToggleRow(
                    label = "Key sound",
                    checked = settings.keySoundEnabled,
                    onCheckedChange = viewModel::setKeySound
                )
            }

            item {
                SectionTitle("Keyboard height")
                Slider(
                    value = settings.keyboardHeightScale,
                    onValueChange = { viewModel.setHeightScale(it) },
                    valueRange = 0.8f..1.3f
                )
            }

            item {
                SectionTitle("Theme")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyboardTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = settings.theme == theme,
                            onClick = { viewModel.setTheme(theme) },
                            label = { Text(theme.label) }
                        )
                    }
                }
            }

            item {
                Divider()
                SectionTitle("Custom leet mappings")
                Text("Override a letter's substitution(s). Comma-separate multiple options.")
            }

            item {
                CustomMappingEditor(
                    existing = settings.customMappings,
                    onSave = viewModel::setCustomMapping
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CustomMappingEditor(
    existing: Map<Char, List<String>>,
    onSave: (Char, String) -> Unit
) {
    var letterInput by remember { mutableStateOf("") }
    var tokensInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = letterInput,
                onValueChange = { if (it.length <= 1) letterInput = it },
                label = { Text("Letter") },
                modifier = Modifier.weight(1f)
            )
            TextField(
                value = tokensInput,
                onValueChange = { tokensInput = it },
                label = { Text("Replacement(s)") },
                modifier = Modifier.weight(2f)
            )
        }
        Button(
            onClick = {
                val letter = letterInput.trim().lowercase().firstOrNull()
                if (letter != null) {
                    onSave(letter, tokensInput)
                    letterInput = ""
                    tokensInput = ""
                }
            }
        ) {
            Text("Save mapping")
        }

        if (existing.isNotEmpty()) {
            Text("Current custom mappings:", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            existing.forEach { (letter, tokens) ->
                Text("$letter -> ${tokens.joinToString(", ")}")
            }
        }
    }
}

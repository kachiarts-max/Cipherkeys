package com.cipherkeys.app.settings

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cipherkeys.app.data.KeyboardMode
import com.cipherkeys.app.data.KeyboardTheme
import com.cipherkeys.app.data.ThemeColorSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                // Copy into the app's own private storage immediately - the picker's
                // URI grant isn't guaranteed to remain readable later (especially from
                // the keyboard's separate service process), but our own file always is.
                val savedPath = withContext(Dispatchers.IO) {
                    try {
                        val outFile = File(context.filesDir, "cipherkeys_background.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            outFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        outFile.absolutePath
                    } catch (e: Exception) {
                        null
                    }
                }
                if (savedPath != null) {
                    viewModel.setBackgroundImagePath(savedPath)
                    viewModel.setUseImageBackground(true)
                }
            }
        }
    }

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
                    label = "Autocorrect (NORMAL/DECODE only)",
                    checked = settings.autocorrectEnabled,
                    onCheckedChange = viewModel::setAutocorrect
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
                SectionTitle("Custom theme colors")
                Text("Enter 6-digit hex codes (no #). Select \"Custom\" above to use these.")
            }

            item {
                CustomColorEditor(
                    existing = settings.customColors,
                    onSave = viewModel::setCustomColors
                )
            }

            item {
                Divider()
                SectionTitle("Background image")
                Text("Pick a photo to show behind the keys. A dark overlay keeps key text readable.")
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Text(if (settings.backgroundImagePath != null) "Change image" else "Choose image")
                    }
                    if (settings.backgroundImagePath != null) {
                        Button(onClick = {
                            viewModel.setUseImageBackground(false)
                            viewModel.setBackgroundImagePath(null)
                        }) {
                            Text("Remove")
                        }
                    }
                }
            }

            if (settings.backgroundImagePath != null) {
                item {
                    ToggleRow(
                        label = "Use as keyboard background",
                        checked = settings.useImageBackground,
                        onCheckedChange = viewModel::setUseImageBackground
                    )
                }
                item {
                    SectionTitle("Background darkness")
                    Slider(
                        value = settings.backgroundOverlayAlpha,
                        onValueChange = { viewModel.setBackgroundOverlayAlpha(it) },
                        valueRange = 0f..0.9f
                    )
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

private fun colorToHex(color: Int): String = String.format("%06X", 0xFFFFFF and color)

@Composable
private fun CustomColorEditor(
    existing: ThemeColorSet,
    onSave: (ThemeColorSet) -> Unit
) {
    var bgHex by remember { mutableStateOf(colorToHex(existing.background)) }
    var keyBgHex by remember { mutableStateOf(colorToHex(existing.keyBackground)) }
    var keyTextHex by remember { mutableStateOf(colorToHex(existing.keyText)) }
    var accentHex by remember { mutableStateOf(colorToHex(existing.accent)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = bgHex,
                onValueChange = { bgHex = it },
                label = { Text("Background") },
                modifier = Modifier.weight(1f)
            )
            TextField(
                value = keyBgHex,
                onValueChange = { keyBgHex = it },
                label = { Text("Key bg") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = keyTextHex,
                onValueChange = { keyTextHex = it },
                label = { Text("Key text") },
                modifier = Modifier.weight(1f)
            )
            TextField(
                value = accentHex,
                onValueChange = { accentHex = it },
                label = { Text("Accent") },
                modifier = Modifier.weight(1f)
            )
        }
        Button(
            onClick = {
                val parsed = runCatching {
                    ThemeColorSet(
                        background = Color.parseColor("#$bgHex"),
                        keyBackground = Color.parseColor("#$keyBgHex"),
                        keyText = Color.parseColor("#$keyTextHex"),
                        accent = Color.parseColor("#$accentHex")
                    )
                }
                if (parsed.isSuccess) {
                    errorMessage = null
                    onSave(parsed.getOrThrow())
                } else {
                    errorMessage = "Invalid hex code - use 6 digits, e.g. 121212"
                }
            }
        ) {
            Text("Save custom theme")
        }
        errorMessage?.let { message ->
            Text(message, color = androidx.compose.ui.graphics.Color.Red)
        }
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

package com.cipherkeys.app.settings

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cipherkeys.app.data.KeyboardMode
import com.cipherkeys.app.data.KeyboardTheme
import com.cipherkeys.app.data.ThemeColorSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {

    val settings by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    /*
     * IMAGE PICKER
     *
     * The selected image is copied into CipherKeys'
     * private storage so the keyboard service can access it later.
     */

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->

        if (uri != null) {

            coroutineScope.launch {

                val savedPath = withContext(Dispatchers.IO) {

                    try {

                        val outFile = File(
                            context.filesDir,
                            "cipherkeys_background.jpg"
                        )

                        context.contentResolver
                            .openInputStream(uri)
                            ?.use { input ->

                                outFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
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

    /*
     * APP UI
     *
     * This screen intentionally uses CipherKeysTheme
     * from the application's root theme.
     *
     * It does NOT create a separate keyboard color theme.
     */

    Scaffold(

        containerColor = MaterialTheme.colorScheme.background,

        topBar = {

            TopAppBar(

                title = {

                    Column {

                        Text(
                            text = "CipherKeys",
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "CONTROL CENTER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }

    ) { padding ->

        LazyColumn(

            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),

            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 40.dp
            ),

            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            /*
             * ==================================================
             * HERO
             * ==================================================
             */

            item {

                Column(
                    modifier = Modifier.padding(
                        top = 8.dp,
                        bottom = 8.dp
                    )
                ) {

                    Text(
                        text = "Your keyboard.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "Your rules.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "Configure how CipherKeys behaves while you type.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            /*
             * ==================================================
             * QUICK SETUP
             * ==================================================
             */

            item {

                SectionHeader(
                    title = "QUICK SETUP",
                    subtitle = "Get CipherKeys ready"
                )
            }

            item {

                SettingCard {

                    Text(
                        text = "CipherKeys Keyboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Text(
                        text = "Enable CipherKeys as an Android keyboard to start using it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    Button(
                        modifier = Modifier.fillMaxWidth(),

                        onClick = {

                            context.startActivity(
                                Intent(
                                    Settings.ACTION_INPUT_METHOD_SETTINGS
                                )
                            )
                        },

                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {

                        Text(
                            text = "Enable CipherKeys",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            /*
             * ==================================================
             * KEYBOARD
             * ==================================================
             */

            item {

                SectionHeader(
                    title = "KEYBOARD",
                    subtitle = "Control your typing experience"
                )
            }

            item {

                SettingCard {

                    Text(
                        text = "Default mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Text(
                        text = "Choose the mode CipherKeys starts with.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {

                        KeyboardMode.entries.forEach { mode ->

                            FilterChip(

                                selected = settings.defaultMode == mode,

                                onClick = {
                                    viewModel.setDefaultMode(mode)
                                },

                                label = {
                                    Text(mode.label)
                                },

                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.primary,

                                    selectedLabelColor =
                                        MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }

            item {

                SettingCard {

                    Text(
                        text = "Keyboard height",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = "Adjust the size of the keyboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Slider(

                        value = settings.keyboardHeightScale,

                        onValueChange = {
                            viewModel.setHeightScale(it)
                        },

                        valueRange = 0.8f..1.3f
                    )
                }
            }

            item {

                SettingCard {

                    Text(
                        text = "Keyboard theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {

                        KeyboardTheme.entries.forEach { theme ->

                            FilterChip(

                                selected = settings.theme == theme,

                                onClick = {
                                    viewModel.setTheme(theme)
                                },

                                label = {
                                    Text(theme.label)
                                },

                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.primary,

                                    selectedLabelColor =
                                        MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }

            /*
             * ==================================================
             * AUTOMATION
             * ==================================================
             */

            item {

                SectionHeader(
                    title = "AUTOMATION",
                    subtitle = "Let CipherKeys work automatically"
                )
            }

            item {

                SettingCard {

                    ToggleSetting(
                        title = "Auto encode",
                        description =
                            "Automatically transform text while typing.",

                        checked = settings.autoEncodeEnabled,

                        onCheckedChange =
                            viewModel::setAutoEncode
                    )

                    SettingDivider()

                    ToggleSetting(
                        title = "Auto decode on focus",
                        description =
                            "Decode supported text when a field receives focus.",

                        checked = settings.autoDecodeEnabled,

                        onCheckedChange =
                            viewModel::setAutoDecode
                    )

                    SettingDivider()

                    ToggleSetting(
                        title = "Autocorrect",
                        description =
                            "Normal and Decode modes only.",

                        checked = settings.autocorrectEnabled,

                        onCheckedChange =
                            viewModel::setAutocorrect
                    )
                }
            }

            /*
             * ==================================================
             * EXPERIENCE
             * ==================================================
             */

            item {

                SectionHeader(
                    title = "EXPERIENCE",
                    subtitle = "Control feedback while typing"
                )
            }

            item {

                SettingCard {

                    ToggleSetting(
                        title = "Vibration",
                        description =
                            "Feel a small response when keys are pressed.",

                        checked = settings.vibrationEnabled,

                        onCheckedChange =
                            viewModel::setVibration
                    )

                    SettingDivider()

                    ToggleSetting(
                        title = "Key sound",
                        description =
                            "Play a sound when typing.",

                        checked = settings.keySoundEnabled,

                        onCheckedChange =
                            viewModel::setKeySound
                    )
                }
            }

            /*
             * ==================================================
             * CUSTOMIZATION
             * ==================================================
             */

            item {

                SectionHeader(
                    title = "CUSTOMIZATION",
                    subtitle = "Personalize your keyboard"
                )
            }

            /*
             * KEYBOARD COLORS
             */

            item {

                SettingCard {

                    Text(
                        text = "Custom keyboard colors",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            "These settings affect the keyboard appearance, not this app.",

                        style = MaterialTheme.typography.bodySmall,

                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    CustomColorEditor(
                        existing = settings.customColors,
                        onSave = viewModel::setCustomColors
                    )
                }
            }

            /*
             * BACKGROUND IMAGE
             */

            item {

                SettingCard {

                    Text(
                        text = "Background image",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Text(
                        text =
                            "Choose an image to display behind your keyboard keys.",

                        style = MaterialTheme.typography.bodySmall,

                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Button(

                            onClick = {

                                pickImageLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts
                                            .PickVisualMedia
                                            .ImageOnly
                                    )
                                )
                            },

                            colors = ButtonDefaults.buttonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.primary,

                                contentColor =
                                    MaterialTheme.colorScheme.onPrimary
                            )
                        ) {

                            Text(
                                if (
                                    settings.backgroundImagePath != null
                                ) {
                                    "Change image"
                                } else {
                                    "Choose image"
                                }
                            )
                        }

                        if (
                            settings.backgroundImagePath != null
                        ) {

                            TextButton(
                                onClick = {

                                    viewModel
                                        .setUseImageBackground(false)

                                    viewModel
                                        .setBackgroundImagePath(null)
                                }
                            ) {

                                Text("Remove")
                            }
                        }
                    }

                    if (
                        settings.backgroundImagePath != null
                    ) {

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        SettingDivider()

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        ToggleSetting(

                            title =
                                "Use as keyboard background",

                            description =
                                "Show the selected image behind the keys.",

                            checked =
                                settings.useImageBackground,

                            onCheckedChange =
                                viewModel::setUseImageBackground
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        Text(
                            text = "Background darkness",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Slider(

                            value =
                                settings.backgroundOverlayAlpha,

                            onValueChange = {
                                viewModel
                                    .setBackgroundOverlayAlpha(it)
                            },

                            valueRange = 0f..0.9f
                        )
                    }
                }
            }

            /*
             * LEET MAPPINGS
             */

            item {

                SettingCard {

                    Text(
                        text = "Custom leet mappings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Text(
                        text =
                            "Override the substitutions used for individual letters.",

                        style = MaterialTheme.typography.bodySmall,

                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    CustomMappingEditor(
                        existing = settings.customMappings,
                        onSave = viewModel::setCustomMapping
                    )
                }
            }

            /*
             * ==================================================
             * FOOTER
             * ==================================================
             */

            item {

                Column(

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 18.dp,
                            bottom = 10.dp
                        ),

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "CipherKeys",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(
                        modifier = Modifier.height(3.dp)
                    )

                    Text(
                        text = "Your keyboard. Your rules.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


/*
 * ============================================================
 * SECTION HEADER
 * ============================================================
 */

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {

    Column(
        modifier = Modifier.padding(
            start = 4.dp,
            top = 6.dp,
            bottom = 2.dp
        )
    ) {

        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(
            modifier = Modifier.height(2.dp)
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


/*
 * ============================================================
 * GENERIC SETTING CARD
 * ============================================================
 */

@Composable
private fun SettingCard(
    content: @Composable ColumnScope.() -> Unit
) {

    Card(

        modifier = Modifier.fillMaxWidth(),

        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface
        ),

        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),

        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {

        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}


/*
 * ============================================================
 * TOGGLE SETTING
 * ============================================================
 */

@Composable
private fun ToggleSetting(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {

    Row(

        modifier = Modifier.fillMaxWidth(),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(
            modifier = Modifier.height(1.dp)
        )

        Switch(

            checked = checked,

            onCheckedChange =
                onCheckedChange,

            colors = SwitchDefaults.colors(

                checkedThumbColor =
                    MaterialTheme.colorScheme.onPrimary,

                checkedTrackColor =
                    MaterialTheme.colorScheme.primary,

                uncheckedThumbColor =
                    MaterialTheme.colorScheme.onSurfaceVariant,

                uncheckedTrackColor =
                    MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}


/*
 * ============================================================
 * DIVIDER
 * ============================================================
 */

@Composable
private fun SettingDivider() {

    Divider(
        modifier = Modifier.padding(
            vertical = 12.dp
        ),

        color =
            MaterialTheme.colorScheme.outlineVariant
    )
}


/*
 * ============================================================
 * CUSTOM KEYBOARD COLORS
 *
 * IMPORTANT:
 * These colors affect the KEYBOARD,
 * not the CipherKeys app.
 * ============================================================
 */

private fun colorToHex(color: Int): String =
    String.format(
        "%06X",
        0xFFFFFF and color
    )


@Composable
private fun CustomColorEditor(
    existing: ThemeColorSet,
    onSave: (ThemeColorSet) -> Unit
) {

    var bgHex by remember {
        mutableStateOf(
            colorToHex(existing.background)
        )
    }

    var keyBgHex by remember {
        mutableStateOf(
            colorToHex(existing.keyBackground)
        )
    }

    var keyTextHex by remember {
        mutableStateOf(
            colorToHex(existing.keyText)
        )
    }

    var accentHex by remember {
        mutableStateOf(
            colorToHex(existing.accent)
        )
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            TextField(
                value = bgHex,
                onValueChange = {
                    bgHex = it
                },
                label = {
                    Text("Background")
                },
                modifier = Modifier.weight(1f)
            )

            TextField(
                value = keyBgHex,
                onValueChange = {
                    keyBgHex = it
                },
                label = {
                    Text("Key bg")
                },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            TextField(
                value = keyTextHex,
                onValueChange = {
                    keyTextHex = it
                },
                label = {
                    Text("Key text")
                },
                modifier = Modifier.weight(1f)
            )

            TextField(
                value = accentHex,
                onValueChange = {
                    accentHex = it
                },
                label = {
                    Text("Accent")
                },
                modifier = Modifier.weight(1f)
            )
        }

        Button(

            onClick = {

                val parsed = runCatching {

                    ThemeColorSet(

                        background =
                            Color.parseColor(
                                "#$bgHex"
                            ),

                        keyBackground =
                            Color.parseColor(
                                "#$keyBgHex"
                            ),

                        keyText =
                            Color.parseColor(
                                "#$keyTextHex"
                            ),

                        accent =
                            Color.parseColor(
                                "#$accentHex"
                            )
                    )
                }

                if (parsed.isSuccess) {

                    errorMessage = null

                    onSave(
                        parsed.getOrThrow()
                    )

                } else {

                    errorMessage =
                        "Invalid hex code. Use 6 digits, e.g. 121212"
                }
            },

            colors = ButtonDefaults.buttonColors(
                containerColor =
                    MaterialTheme.colorScheme.primary,

                contentColor =
                    MaterialTheme.colorScheme.onPrimary
            )
        ) {

            Text("Save keyboard theme")
        }

        errorMessage?.let { message ->

            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


/*
 * ============================================================
 * CUSTOM LEET MAPPINGS
 * ============================================================
 */

@Composable
private fun CustomMappingEditor(
    existing: Map<Char, List<String>>,
    onSave: (Char, String) -> Unit
) {

    var letterInput by remember {
        mutableStateOf("")
    }

    var tokensInput by remember {
        mutableStateOf("")
    }

    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            TextField(

                value = letterInput,

                onValueChange = {

                    if (it.length <= 1) {
                        letterInput = it
                    }
                },

                label = {
                    Text("Letter")
                },

                modifier = Modifier.weight(1f)
            )

            TextField(

                value = tokensInput,

                onValueChange = {
                    tokensInput = it
                },

                label = {
                    Text("Replacement(s)")
                },

                modifier = Modifier.weight(2f)
            )
        }

        Button(

            onClick = {

                val letter =
                    letterInput
                        .trim()
                        .lowercase()
                        .firstOrNull()

                if (letter != null) {

                    onSave(
                        letter,
                        tokensInput
                    )

                    letterInput = ""
                    tokensInput = ""
                }
            },

            colors = ButtonDefaults.buttonColors(
                containerColor =
                    MaterialTheme.colorScheme.primary,

                contentColor =
                    MaterialTheme.colorScheme.onPrimary
            )
        ) {

            Text("Save mapping")
        }

        if (existing.isNotEmpty()) {

            Text(
                text = "Current custom mappings",
                style =
                    MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            existing.forEach { (letter, tokens) ->

                Text(
                    text =
                        "$letter → ${tokens.joinToString(", ")}",

                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,

                    style =
                        MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cipherkeys.app.settings

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.darkColorScheme
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

/* ============================================================
   CIPHERKEYS APP COLORS
   ============================================================ */

private val CipherBlack = ComposeColor(0xFF080B10)
private val CipherBackground = ComposeColor(0xFFF5F7FA)

private val CipherWhite = ComposeColor(0xFFFFFFFF)

private val CipherBlue = ComposeColor(0xFF1976D2)
private val CipherBlueDark = ComposeColor(0xFF0D47A1)
private val CipherBlueLight = ComposeColor(0xFF42A5F5)

private val CipherTextDark = ComposeColor(0xFF111827)
private val CipherText = ComposeColor(0xFFF7F9FC)
private val CipherMuted = ComposeColor(0xFF667085)

private val CipherCard = ComposeColor(0xFFFFFFFF)
private val CipherCardDark = ComposeColor(0xFF111827)

private val CipherBorder = ComposeColor(0xFFE2E6EC)
private val CipherBorderDark = ComposeColor(0xFF273244)

private val CipherSoftBlue = ComposeColor(0xFFEAF3FF)

/* ============================================================
   APP COLOR SCHEME
   ============================================================ */

private val CipherKeysColors = darkColorScheme(
    primary = CipherBlue,
    onPrimary = ComposeColor.White,

    secondary = CipherBlueLight,
    onSecondary = ComposeColor.White,

    background = CipherBlack,
    onBackground = CipherText,

    surface = CipherCardDark,
    onSurface = CipherText,

    surfaceVariant = ComposeColor(0xFF1A2230),
    onSurfaceVariant = ComposeColor(0xFFB5BFCE),

    outline = CipherBorderDark
)

/* ============================================================
   SETTINGS SCREEN
   ============================================================ */

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {

    val settings by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    /*
     * IMAGE PICKER
     */

    val pickImageLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->

            if (uri != null) {

                coroutineScope.launch {

                    val savedPath =
                        withContext(Dispatchers.IO) {

                            try {

                                val outFile =
                                    File(
                                        context.filesDir,
                                        "cipherkeys_background.jpg"
                                    )

                                context.contentResolver
                                    .openInputStream(uri)
                                    ?.use { input ->

                                        outFile
                                            .outputStream()
                                            .use { output ->

                                                input.copyTo(output)
                                            }
                                    }

                                outFile.absolutePath

                            } catch (e: Exception) {

                                null
                            }
                        }

                    if (savedPath != null) {

                        viewModel.setBackgroundImagePath(
                            savedPath
                        )

                        viewModel.setUseImageBackground(
                            true
                        )
                    }
                }
            }
        }

    /*
     * APP UI
     */

    MaterialTheme(
        colorScheme = CipherKeysColors
    ) {

        Scaffold(

            containerColor = CipherBlack,

            topBar = {

                TopAppBar(

                    title = {

                        Column {

                            Text(
                                text = "CipherKeys",
                                fontWeight = FontWeight.Bold,
                                color = CipherWhite
                            )

                            Text(
                                text = "Control Center",
                                style = MaterialTheme.typography.labelSmall,
                                color = CipherMuted
                            )
                        }
                    },

                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = CipherBlack,
                            titleContentColor = CipherWhite
                        )
                )
            }

        ) { padding ->

            LazyColumn(

                modifier = Modifier
                    .fillMaxWidth()
                    .background(CipherBlack)
                    .padding(padding),

                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 14.dp,
                        bottom = 40.dp
                    ),

                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                /* ====================================================
                   HEADER
                   ==================================================== */

                item {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 4.dp,
                                bottom = 8.dp
                            )
                    ) {

                        Text(
                            text = "CipherKeys",
                            style =
                                MaterialTheme.typography
                                    .headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = CipherWhite
                        )

                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )

                        Text(
                            text =
                                "Customize your keyboard experience.",
                            style =
                                MaterialTheme.typography
                                    .bodyMedium,
                            color = CipherMuted
                        )
                    }
                }

                /* ====================================================
                   QUICK SETUP
                   ==================================================== */

                item {

                    SectionHeader(
                        title = "QUICK SETUP",
                        subtitle =
                            "Get your keyboard ready"
                    )
                }

                item {

                    Card(

                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            androidx.compose.foundation
                                .shape.RoundedCornerShape(
                                    18.dp
                                ),

                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    CipherBlue
                            )
                    ) {

                        Column(
                            modifier =
                                Modifier.padding(18.dp)
                        ) {

                            Text(
                                text =
                                    "CipherKeys Keyboard",
                                style =
                                    MaterialTheme.typography
                                        .titleMedium,
                                fontWeight =
                                    FontWeight.Bold,
                                color = CipherWhite
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(4.dp)
                            )

                            Text(
                                text =
                                    "Enable CipherKeys as an Android keyboard.",
                                style =
                                    MaterialTheme.typography
                                        .bodySmall,
                                color =
                                    CipherWhite.copy(
                                        alpha = 0.82f
                                    )
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(14.dp)
                            )

                            Button(

                                modifier =
                                    Modifier.fillMaxWidth(),

                                onClick = {

                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_INPUT_METHOD_SETTINGS
                                        )
                                    )
                                },

                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor =
                                            CipherWhite,
                                        contentColor =
                                            CipherBlueDark
                                    ),

                                shape =
                                    androidx.compose.foundation
                                        .shape.RoundedCornerShape(
                                            12.dp
                                        )
                            ) {

                                Text(
                                    text =
                                        "Enable CipherKeys",
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                /* ====================================================
                   KEYBOARD
                   ==================================================== */

                item {

                    SectionHeader(
                        title = "KEYBOARD",
                        subtitle =
                            "Control your typing experience"
                    )
                }

                item {

                    SettingCard {

                        SettingTitle(
                            title = "Default mode",
                            description =
                                "Choose the mode CipherKeys starts with."
                        )

                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )

                        Row(

                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {

                            KeyboardMode.entries
                                .forEach { mode ->

                                    FilterChip(

                                        selected =
                                            settings.defaultMode ==
                                                mode,

                                        onClick = {
                                            viewModel
                                                .setDefaultMode(
                                                    mode
                                                )
                                        },

                                        label = {
                                            Text(
                                                mode.label
                                            )
                                        },

                                        colors =
                                            FilterChipDefaults
                                                .filterChipColors(

                                                    selectedContainerColor =
                                                        CipherBlue,

                                                    selectedLabelColor =
                                                        CipherWhite,

                                                    containerColor =
                                                        CipherBlack,

                                                    labelColor =
                                                        CipherText
                                                )
                                    )
                                }
                        }
                    }
                }

                item {

                    SettingCard {

                        SettingTitle(
                            title =
                                "Keyboard height",
                            description =
                                "Adjust the size of the keyboard."
                        )

                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )

                        Slider(

                            value =
                                settings.keyboardHeightScale,

                            onValueChange = {
                                viewModel
                                    .setHeightScale(it)
                            },

                            valueRange =
                                0.8f..1.3f
                        )
                    }
                }

                item {

                    SettingCard {

                        SettingTitle(
                            title =
                                "Keyboard theme",
                            description =
                                "Choose the keyboard's appearance."
                        )

                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )

                        Row(

                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {

                            KeyboardTheme.entries
                                .forEach { theme ->

                                    FilterChip(

                                        selected =
                                            settings.theme ==
                                                theme,

                                        onClick = {
                                            viewModel
                                                .setTheme(
                                                    theme
                                                )
                                        },

                                        label = {
                                            Text(
                                                theme.label
                                            )
                                        },

                                        colors =
                                            FilterChipDefaults
                                                .filterChipColors(

                                                    selectedContainerColor =
                                                        CipherBlue,

                                                    selectedLabelColor =
                                                        CipherWhite,

                                                    containerColor =
                                                        CipherBlack,

                                                    labelColor =
                                                        CipherText
                                                )
                                    )
                                }
                        }
                    }
                }

                /* ====================================================
                   AUTOMATION
                   ==================================================== */

                item {

                    SectionHeader(
                        title = "AUTOMATION",
                        subtitle =
                            "Let CipherKeys handle tasks automatically"
                    )
                }

                item {

                    SettingCard {

                        ToggleSetting(
                            title =
                                "Auto encode",
                            description =
                                "Automatically transform text while typing.",
                            checked =
                                settings.autoEncodeEnabled,
                            onCheckedChange =
                                viewModel::setAutoEncode
                        )

                        SettingDivider()

                        ToggleSetting(
                            title =
                                "Auto decode on focus",
                            description =
                                "Decode supported text when a field receives focus.",
                            checked =
                                settings.autoDecodeEnabled,
                            onCheckedChange =
                                viewModel::setAutoDecode
                        )

                        SettingDivider()

                        ToggleSetting(
                            title =
                                "Autocorrect",
                            description =
                                "Normal and Decode modes only.",
                            checked =
                                settings.autocorrectEnabled,
                            onCheckedChange =
                                viewModel::setAutocorrect
                        )
                    }
                }

                /* ====================================================
                   EXPERIENCE
                   ==================================================== */

                item {

                    SectionHeader(
                        title = "EXPERIENCE",
                        subtitle =
                            "Control feedback while typing"
                    )
                }

                item {

                    SettingCard {

                        ToggleSetting(
                            title =
                                "Vibration",
                            description =
                                "Feel a small response when keys are pressed.",
                            checked =
                                settings.vibrationEnabled,
                            onCheckedChange =
                                viewModel::setVibration
                        )

                        SettingDivider()

                        ToggleSetting(
                            title =
                                "Key sound",
                            description =
                                "Play a sound when typing.",
                            checked =
                                settings.keySoundEnabled,
                            onCheckedChange =
                                viewModel::setKeySound
                        )
                    }
                }

                /* ====================================================
                   CUSTOMIZATION
                   ==================================================== */

                item {

                    SectionHeader(
                        title = "CUSTOMIZATION",
                        subtitle =
                            "Personalize your keyboard"
                    )
                }

                item {

                    SettingCard {

                        SettingTitle(
                            title =
                                "Custom keyboard colors",
                            description =
                                "These colors affect the keyboard, not this app."
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        CustomColorEditor(
                            existing =
                                settings.customColors,
                            onSave =
                                viewModel::setCustomColors
                        )
                    }
                }

                item {

                    SettingCard {

                        SettingTitle(
                            title =
                                "Background image",
                            description =
                                "Choose an image to display behind your keyboard keys."
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp)
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

                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor =
                                            CipherBlue
                                    ),

                                shape =
                                    androidx.compose.foundation
                                        .shape.RoundedCornerShape(
                                            12.dp
                                        )
                            ) {

                                Text(
                                    if (
                                        settings.backgroundImagePath !=
                                            null
                                    ) {
                                        "Change image"
                                    } else {
                                        "Choose image"
                                    }
                                )
                            }

                            if (
                                settings.backgroundImagePath !=
                                    null
                            ) {

                                TextButton(

                                    onClick = {

                                        viewModel
                                            .setUseImageBackground(
                                                false
                                            )

                                        viewModel
                                            .setBackgroundImagePath(
                                                null
                                            )
                                    }
                                ) {

                                    Text(
                                        text = "Remove",
                                        color =
                                            CipherBlueLight
                                    )
                                }
                            }
                        }

                        if (
                            settings.backgroundImagePath !=
                                null
                        ) {

                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )

                            SettingDivider()

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
                                modifier =
                                    Modifier.height(12.dp)
                            )

                            Text(
                                text =
                                    "Background darkness",
                                style =
                                    MaterialTheme.typography
                                        .titleSmall,
                                color =
                                    CipherText
                            )

                            Slider(

                                value =
                                    settings.backgroundOverlayAlpha,

                                onValueChange = {
                                    viewModel
                                        .setBackgroundOverlayAlpha(
                                            it
                                        )
                                },

                                valueRange =
                                    0f..0.9f
                            )
                        }
                    }
                }

                /* ====================================================
                   LEET MAPPINGS
                   ==================================================== */

                item {

                    SettingCard {

                        SettingTitle(
                            title =
                                "Custom leet mappings",
                            description =
                                "Override substitutions used for individual letters."
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        CustomMappingEditor(
                            existing =
                                settings.customMappings,
                            onSave =
                                viewModel::setCustomMapping
                        )
                    }
                }

                /* ====================================================
                   FOOTER
                   ==================================================== */

                item {

                    Column(

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = 12.dp,
                                    bottom = 10.dp
                                ),

                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            text =
                                "CIPHERKEYS",
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                CipherBlue
                        )

                        Spacer(
                            modifier =
                                Modifier.height(3.dp)
                        )

                        Text(
                            text =
                                "Your keyboard. Your rules.",
                            style =
                                MaterialTheme.typography
                                    .bodySmall,
                            color =
                                CipherMuted
                        )
                    }
                }
            }
        }
    }
}

/* ================================================================
   SECTION HEADER
   ================================================================ */

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {

    Column(

        modifier =
            Modifier.padding(
                start = 4.dp,
                top = 6.dp,
                bottom = 2.dp
            )
    ) {

        Text(
            text = title,
            style =
                MaterialTheme.typography.labelLarge,
            fontWeight =
                FontWeight.Bold,
            color =
                CipherBlueLight
        )

        Text(
            text = subtitle,
            style =
                MaterialTheme.typography.bodySmall,
            color =
                CipherMuted
        )
    }
}

/* ================================================================
   SETTING CARD
   ================================================================ */

@Composable
private fun SettingCard(
    content: @Composable ColumnScope.() -> Unit
) {

    Card(

        modifier =
            Modifier.fillMaxWidth(),

        shape =
            androidx.compose.foundation
                .shape.RoundedCornerShape(
                    16.dp
                ),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    CipherCardDark
            ),

        border =
            BorderStroke(
                1.dp,
                CipherBorderDark
            )
    ) {

        Column(

            modifier =
                Modifier.padding(16.dp),

            content = content
        )
    }
}

/* ================================================================
   SETTING TITLE
   ================================================================ */

@Composable
private fun SettingTitle(
    title: String,
    description: String
) {

    Text(
        text = title,
        style =
            MaterialTheme.typography.titleMedium,
        fontWeight =
            FontWeight.SemiBold,
        color =
            CipherText
    )

    Spacer(
        modifier =
            Modifier.height(4.dp)
    )

    Text(
        text = description,
        style =
            MaterialTheme.typography.bodySmall,
        color =
            CipherMuted
    )
}

/* ================================================================
   TOGGLE
   ================================================================ */

@Composable
private fun ToggleSetting(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {

    Row(

        modifier =
            Modifier.fillMaxWidth(),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = title,
                style =
                    MaterialTheme.typography.bodyLarge,
                fontWeight =
                    FontWeight.Medium,
                color =
                    CipherText
            )

            Spacer(
                modifier =
                    Modifier.height(3.dp)
            )

            Text(
                text = description,
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    CipherMuted
            )
        }

        Spacer(
            modifier =
                Modifier.width(10.dp)
        )

        Switch(

            checked = checked,

            onCheckedChange =
                onCheckedChange,

            colors =
                SwitchDefaults.colors(

                    checkedThumbColor =
                        CipherWhite,

                    checkedTrackColor =
                        CipherBlue,

                    checkedBorderColor =
                        CipherBlue,

                    uncheckedThumbColor =
                        ComposeColor(0xFF98A2B3),

                    uncheckedTrackColor =
                        ComposeColor(0xFF29313D),

                    uncheckedBorderColor =
                        ComposeColor(0xFF475467)
                )
        )
    }
}

/* ================================================================
   DIVIDER
   ================================================================ */

@Composable
private fun SettingDivider() {

    HorizontalDivider(

        modifier =
            Modifier.padding(
                vertical = 11.dp
            ),

        color =
            CipherBorderDark
    )
}

/* ================================================================
   CUSTOM KEYBOARD COLORS
   ================================================================ */

private fun colorToHex(
    color: Int
): String =
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
            colorToHex(
                existing.background
            )
        )
    }

    var keyBgHex by remember {
        mutableStateOf(
            colorToHex(
                existing.keyBackground
            )
        )
    }

    var keyTextHex by remember {
        mutableStateOf(
            colorToHex(
                existing.keyText
            )
        )
    }

    var accentHex by remember {
        mutableStateOf(
            colorToHex(
                existing.accent
            )
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

                modifier =
                    Modifier.weight(1f),

                singleLine = true
            )

            TextField(

                value = keyBgHex,

                onValueChange = {
                    keyBgHex = it
                },

                label = {
                    Text("Key bg")
                },

                modifier =
                    Modifier.weight(1f),

                singleLine = true
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

                modifier =
                    Modifier.weight(1f),

                singleLine = true
            )

            TextField(

                value = accentHex,

                onValueChange = {
                    accentHex = it
                },

                label = {
                    Text("Accent")
                },

                modifier =
                    Modifier.weight(1f),

                singleLine = true
            )
        }

        Button(

            modifier =
                Modifier.fillMaxWidth(),

            onClick = {

                val parsed =
                    runCatching {

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
                        "Invalid hex code. Use 6 digits, e.g. 1976D2."
                }
            },

            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        CipherBlue
                ),

            shape =
                androidx.compose.foundation
                    .shape.RoundedCornerShape(
                        12.dp
                    )
        ) {

            Text(
                text =
                    "Save keyboard theme",
                fontWeight =
                    FontWeight.SemiBold
            )
        }

        errorMessage?.let { message ->

            Text(
                text = message,
                color =
                    ComposeColor(0xFFFF6B6B),
                style =
                    MaterialTheme.typography.bodySmall
            )
        }
    }
}

/* ================================================================
   CUSTOM LEET MAPPINGS
   ================================================================ */

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

                value =
                    letterInput,

                onValueChange = {

                    if (it.length <= 1) {
                        letterInput = it
                    }
                },

                label = {
                    Text("Letter")
                },

                modifier =
                    Modifier.weight(1f),

                singleLine = true
            )

            TextField(

                value =
                    tokensInput,

                onValueChange = {
                    tokensInput = it
                },

                label = {
                    Text("Replacement(s)")
                },

                modifier =
                    Modifier.weight(2f),

                singleLine = true
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

            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        CipherBlue
                ),

            shape =
                androidx.compose.foundation
                    .shape.RoundedCornerShape(
                        12.dp
                    )
        ) {

            Text(
                text =
                    "Save mapping"
            )
        }

        if (existing.isNotEmpty()) {

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Text(
                text =
                    "Current custom mappings",
                style =
                    MaterialTheme.typography
                        .labelLarge,
                fontWeight =
                    FontWeight.Bold,
                color =
                    CipherText
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            existing
                .toSortedMap()
                .forEach { (letter, tokens) ->

                    Text(
                        text =
                            "$letter → ${
                                tokens.joinToString(", ")
                            }",

                        color =
                            CipherMuted,

                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )
                }
        }
    }
}

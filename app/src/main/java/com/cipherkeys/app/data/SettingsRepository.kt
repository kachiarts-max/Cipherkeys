package com.cipherkeys.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "cipherkeys_settings")

/** Immutable snapshot of everything the keyboard needs to render/behave. */
data class KeyboardSettings(
    val defaultMode: KeyboardMode = KeyboardMode.default(),
    val autoEncodeEnabled: Boolean = true,
    val autoDecodeEnabled: Boolean = false,
    val autocorrectEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val keySoundEnabled: Boolean = false,
    val keyboardHeightScale: Float = 1.0f, // 0.8 - 1.3
    val theme: KeyboardTheme = KeyboardTheme.default(),
    val customMappings: Map<Char, List<String>> = emptyMap(),
    val customColors: ThemeColorSet = ThemeColorSet.default(),
    // Background image lives in the app's own private storage (see SettingsScreen's
    // picker flow) - this is an absolute file path, never a content:// URI, since
    // picker-granted URIs aren't guaranteed to remain readable across restarts.
    val backgroundImagePath: String? = null,
    val useImageBackground: Boolean = false,
    val backgroundOverlayAlpha: Float = 0.5f
)

/**
 * Reads and writes keyboard configuration via Jetpack DataStore (no SQLite database -
 * this is plain key/value preference data, so a database would be overkill per spec).
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val AUTO_ENCODE = booleanPreferencesKey("auto_encode")
        val AUTO_DECODE = booleanPreferencesKey("auto_decode")
        val AUTOCORRECT = booleanPreferencesKey("autocorrect")
        val VIBRATION = booleanPreferencesKey("vibration")
        val KEY_SOUND = booleanPreferencesKey("key_sound")
        val HEIGHT_SCALE = floatPreferencesKey("height_scale")
        val THEME = stringPreferencesKey("theme")
        val CUSTOM_MAPPINGS_JSON = stringPreferencesKey("custom_mappings_json")
        val CUSTOM_BG = intPreferencesKey("custom_theme_bg")
        val CUSTOM_KEY_BG = intPreferencesKey("custom_theme_key_bg")
        val CUSTOM_KEY_TEXT = intPreferencesKey("custom_theme_key_text")
        val CUSTOM_ACCENT = intPreferencesKey("custom_theme_accent")
        val BACKGROUND_IMAGE_PATH = stringPreferencesKey("background_image_path")
        val USE_IMAGE_BACKGROUND = booleanPreferencesKey("use_image_background")
        val BACKGROUND_OVERLAY_ALPHA = floatPreferencesKey("background_overlay_alpha")
    }

    val settingsFlow: Flow<KeyboardSettings> = c

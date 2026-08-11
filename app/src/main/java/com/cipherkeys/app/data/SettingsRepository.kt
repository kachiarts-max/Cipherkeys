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
    val customColors: ThemeColorSet = ThemeColorSet.default()
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
    }

    val settingsFlow: Flow<KeyboardSettings> = context.dataStore.data.map { prefs ->
        val defaults = ThemeColorSet.default()
        KeyboardSettings(
            defaultMode = prefs[Keys.DEFAULT_MODE]
                ?.let { name -> KeyboardMode.entries.firstOrNull { it.name == name } }
                ?: KeyboardMode.default(),
            autoEncodeEnabled = prefs[Keys.AUTO_ENCODE] ?: true,
            autoDecodeEnabled = prefs[Keys.AUTO_DECODE] ?: false,
            autocorrectEnabled = prefs[Keys.AUTOCORRECT] ?: false,
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
            keySoundEnabled = prefs[Keys.KEY_SOUND] ?: false,
            keyboardHeightScale = prefs[Keys.HEIGHT_SCALE] ?: 1.0f,
            theme = KeyboardTheme.fromName(prefs[Keys.THEME]),
            customMappings = decodeMappings(prefs[Keys.CUSTOM_MAPPINGS_JSON]),
            customColors = ThemeColorSet(
                background = prefs[Keys.CUSTOM_BG] ?: defaults.background,
                keyBackground = prefs[Keys.CUSTOM_KEY_BG] ?: defaults.keyBackground,
                keyText = prefs[Keys.CUSTOM_KEY_TEXT] ?: defaults.keyText,
                accent = prefs[Keys.CUSTOM_ACCENT] ?: defaults.accent
            )
        )
    }

    suspend fun current(): KeyboardSettings = settingsFlow.first()

    suspend fun setDefaultMode(mode: KeyboardMode) {
        context.dataStore.edit { it[Keys.DEFAULT_MODE] = mode.name }
    }

    suspend fun setAutoEncode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_ENCODE] = enabled }
    }

    suspend fun setAutoDecode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DECODE] = enabled }
    }

    suspend fun setAutocorrect(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTOCORRECT] = enabled }
    }

    suspend fun setVibration(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATION] = enabled }
    }

    suspend fun setKeySound(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEY_SOUND] = enabled }
    }

    suspend fun setKeyboardHeightScale(scale: Float) {
        context.dataStore.edit { it[Keys.HEIGHT_SCALE] = scale }
    }

    suspend fun setTheme(theme: KeyboardTheme) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setCustomMappings(mappings: Map<Char, List<String>>) {
        context.dataStore.edit { it[Keys.CUSTOM_MAPPINGS_JSON] = encodeMappings(mappings) }
    }

    suspend fun setCustomColors(colors: ThemeColorSet) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_BG] = colors.background
            prefs[Keys.CUSTOM_KEY_BG] = colors.keyBackground
            prefs[Keys.CUSTOM_KEY_TEXT] = colors.keyText
            prefs[Keys.CUSTOM_ACCENT] = colors.accent
        }
    }

    private fun encodeMappings(mappings: Map<Char, List<String>>): String {
        val json = JSONObject()
        mappings.forEach { (letter, tokens) ->
            json.put(letter.toString(), tokens.joinToString(","))
        }
        return json.toString()
    }

    private fun decodeMappings(raw: String?): Map<Char, List<String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = mutableMapOf<Char, List<String>>()
            json.keys().forEach { key ->
                if (key.isNotEmpty()) {
                    result[key[0]] = json.getString(key).split(",").filter { it.isNotEmpty() }
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

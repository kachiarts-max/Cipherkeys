package com.cipherkeys.app.data

/**
 * Four colors that fully describe a theme's palette. [KeyboardTheme]'s built-in entries
 * carry their colors as fixed properties; this is the equivalent bundle for the
 * user-editable CUSTOM theme, persisted via [SettingsRepository].
 */
data class ThemeColorSet(
    val background: Int,
    val keyBackground: Int,
    val keyText: Int,
    val accent: Int
) {
    companion object {
        fun default(): ThemeColorSet = ThemeColorSet(
            background = KeyboardTheme.DARK.background,
            keyBackground = KeyboardTheme.DARK.keyBackground,
            keyText = KeyboardTheme.DARK.keyText,
            accent = KeyboardTheme.DARK.accent
        )
    }
}

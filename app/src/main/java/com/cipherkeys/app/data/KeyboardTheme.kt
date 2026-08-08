package com.cipherkeys.app.data

enum class KeyboardTheme(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    NEON("Neon");

    companion object {
        fun default(): KeyboardTheme = DARK
        fun fromName(name: String?): KeyboardTheme =
            entries.firstOrNull { it.name == name } ?: default()
    }
}

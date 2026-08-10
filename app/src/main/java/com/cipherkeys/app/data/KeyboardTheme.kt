package com.cipherkeys.app.data

import android.graphics.Color

/**
 * A theme is fully self-describing: label plus the 4 colors the keyboard needs
 * (background, key background, key text, accent/highlight). Adding a new built-in
 * theme is a one-line addition here - nothing in [com.cipherkeys.app.keyboard.CipherKeysKeyboardView]
 * needs to change, since it just reads these properties off whichever theme is active.
 */
enum class KeyboardTheme(
    val label: String,
    val background: Int,
    val keyBackground: Int,
    val keyText: Int,
    val accent: Int
) {
    DARK(
        "Dark",
        Color.parseColor("#121212"),
        Color.parseColor("#2A2A2A"),
        Color.parseColor("#ECECEC"),
        Color.parseColor("#BB86FC")
    ),
    LIGHT(
        "Light",
        Color.parseColor("#FFFFFF"),
        Color.parseColor("#E8E8E8"),
        Color.parseColor("#1B1B1B"),
        Color.parseColor("#6750A4")
    ),
    NEON(
        "Neon",
        Color.parseColor("#0D0221"),
        Color.parseColor("#1B0B3B"),
        Color.parseColor("#39FF14"),
        Color.parseColor("#FF00E5")
    ),
    OCEAN(
        "Ocean",
        Color.parseColor("#042A3C"),
        Color.parseColor("#0B4F6C"),
        Color.parseColor("#E0FBFC"),
        Color.parseColor("#01BAEF")
    ),
    SUNSET(
        "Sunset",
        Color.parseColor("#1B1035"),
        Color.parseColor("#6A2C70"),
        Color.parseColor("#FFE8D6"),
        Color.parseColor("#F76B1C")
    ),
    FOREST(
        "Forest",
        Color.parseColor("#0B2E13"),
        Color.parseColor("#1F4E2C"),
        Color.parseColor("#E8F5E9"),
        Color.parseColor("#A4DE02")
    );

    companion object {
        fun default(): KeyboardTheme = DARK
        fun fromName(name: String?): KeyboardTheme =
            entries.firstOrNull { it.name == name } ?: default()
    }
}

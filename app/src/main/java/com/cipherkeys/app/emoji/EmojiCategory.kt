package com.cipherkeys.app.emoji

/**
 * Categories shown as tabs at the top of the emoji panel. [RECENT] is special-cased by
 * the keyboard view/IME (backed by [RecentEmojiStore] rather than [EmojiRepository]).
 */
enum class EmojiCategory(val label: String, val icon: String) {
    RECENT("Recent", "\uD83D\uDD52"),
    SMILEYS("Smileys", "\uD83D\uDE00"),
    ANIMALS("Animals", "\uD83D\uDC36"),
    FOOD("Food", "\uD83C\uDF54"),
    ACTIVITIES("Activities", "\u26BD"),
    TRAVEL("Travel", "\u2708\uFE0F"),
    OBJECTS("Objects", "\uD83D\uDCA1"),
    SYMBOLS("Symbols", "\u2764\uFE0F")
}

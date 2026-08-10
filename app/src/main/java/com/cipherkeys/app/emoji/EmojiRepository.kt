package com.cipherkeys.app.emoji

/**
 * Static emoji lists, grouped by [EmojiCategory]. [EmojiCategory.RECENT] is handled
 * separately by [RecentEmojiStore] / the IME - this repository only covers the fixed
 * categories.
 *
 * Emoji are written as \u escape sequences rather than literal characters: this file
 * gets copy/pasted through a plain browser text box during setup, and escape sequences
 * survive that far more reliably than raw multi-byte emoji characters do.
 */
object EmojiRepository {

    fun emojisFor(category: EmojiCategory): List<String> = when (category) {
        EmojiCategory.RECENT -> emptyList()
        EmojiCategory.SMILEYS -> smileys
        EmojiCategory.ANIMALS -> animals
        EmojiCategory.FOOD -> food
        EmojiCategory.ACTIVITIES -> activities
        EmojiCategory.TRAVEL -> travel
        EmojiCategory.OBJECTS -> objects
        EmojiCategory.SYMBOLS -> symbols
    }

    private val smileys = listOf(
        "\uD83D\uDE00", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE01", "\uD83D\uDE06",
        "\uD83D\uDE05", "\uD83E\uDD23", "\uD83D\uDE02", "\uD83D\uDE42", "\uD83D\uDE43",
        "\uD83D\uDE09", "\uD83D\uDE0A", "\uD83D\uDE07", "\uD83E\uDD70", "\uD83D\uDE0D",
        "\uD83E\uDD29", "\uD83D\uDE18", "\uD83D\uDE0B", "\uD83D\uDE1B", "\uD83D\uDE1C",
        "\uD83E\uDD2A", "\uD83E\uDD11", "\uD83E\uDD17", "\uD83E\uDD14", "\uD83D\uDE10",
        "\uD83D\uDE0F", "\uD83D\uDE12", "\uD83D\uDE44", "\uD83D\uDE2C", "\uD83D\uDE0C",
        "\uD83D\uDE14", "\uD83D\uDE34", "\uD83D\uDE37", "\uD83E\uDD12", "\uD83E\uDD22",
        "\uD83E\uDD75", "\uD83E\uDD76", "\uD83D\uDE35", "\uD83E\uDD2F", "\uD83D\uDE0E",
        "\uD83E\uDD73", "\uD83D\uDE22", "\uD83D\uDE2D", "\uD83D\uDE31", "\uD83D\uDE21"
    )

    private val animals = listOf(
        "\uD83D\uDC36", "\uD83D\uDC31", "\uD83D\uDC2D", "\uD83D\uDC39", "\uD83D\uDC30",
        "\uD83E\uDD8A", "\uD83D\uDC3B", "\uD83D\uDC3C", "\uD83D\uDC28", "\uD83D\uDC2F",
        "\uD83E\uDD81", "\uD83D\uDC2E", "\uD83D\uDC37", "\uD83D\uDC38", "\uD83D\uDC35",
        "\uD83D\uDC14", "\uD83D\uDC27", "\uD83D\uDC26", "\uD83D\uDC24", "\uD83E\uDD86",
        "\uD83E\uDD89", "\uD83D\uDC34", "\uD83E\uDD84", "\uD83D\uDC1D"
    )

    private val food = listOf(
        "\uD83C\uDF4E", "\uD83C\uDF4A", "\uD83C\uDF4B", "\uD83C\uDF4C", "\uD83C\uDF49",
        "\uD83C\uDF47", "\uD83C\uDF53", "\uD83C\uDF52", "\uD83C\uDF51", "\uD83C\uDF4D",
        "\uD83E\uDD5D", "\uD83C\uDF45", "\uD83E\uDD51", "\uD83C\uDF55", "\uD83C\uDF54",
        "\uD83C\uDF5F", "\uD83C\uDF2D", "\uD83C\uDF7F", "\uD83E\uDD6A", "\uD83C\uDF2E",
        "\uD83C\uDF63", "\uD83C\uDF66", "\uD83C\uDF69", "\uD83C\uDF6A", "\u2615"
    )

    private val activities = listOf(
        "\u26BD", "\uD83C\uDFC0", "\uD83C\uDFC8", "\u26BE", "\uD83C\uDFBE",
        "\uD83C\uDFD0", "\uD83C\uDFC9", "\uD83C\uDFB1", "\uD83C\uDFD3", "\uD83C\uDFF8",
        "\uD83E\uDD4A", "\uD83C\uDFAF", "\uD83C\uDFAE", "\uD83C\uDFB8", "\uD83C\uDFB9",
        "\uD83C\uDFA8", "\uD83C\uDFA4", "\uD83C\uDFAC", "\uD83C\uDFB3", "\uD83C\uDFC6"
    )

    private val travel = listOf(
        "\u2708\uFE0F", "\uD83D\uDE97", "\uD83D\uDE95", "\uD83D\uDE8C", "\uD83D\uDE93",
        "\uD83D\uDE91", "\uD83D\uDE92", "\uD83D\uDEB2", "\uD83D\uDE80", "\uD83D\uDE81",
        "\u26F5", "\uD83D\uDEA2", "\uD83D\uDDFD", "\uD83D\uDDFC", "\uD83C\uDFF0",
        "\u26FA"
    )

    private val objects = listOf(
        "\uD83D\uDCA1", "\uD83D\uDCF1", "\uD83D\uDCBB", "\u231A", "\uD83D\uDCF7",
        "\uD83C\uDFA7", "\uD83D\uDCDA", "\u270F\uFE0F", "\uD83D\uDCCC", "\uD83D\uDD11",
        "\uD83D\uDD12", "\uD83D\uDCB0", "\uD83D\uDCB3", "\uD83C\uDF81", "\u23F0",
        "\uD83D\uDD26", "\uD83E\uDDE9", "\uD83D\uDED2", "\uD83D\uDC8A", "\uD83E\uDDF8"
    )

    private val symbols = listOf(
        "\u2764\uFE0F", "\uD83E\uDDE1", "\uD83D\uDC9B", "\uD83D\uDC9A", "\uD83D\uDC99",
        "\uD83D\uDC9C", "\uD83D\uDDA4", "\uD83E\uDD0D", "\uD83D\uDC94", "\u2705",
        "\u274C", "\u2753", "\u2757", "\u2B50", "\uD83D\uDD25",
        "\u2728", "\uD83C\uDF89", "\u267B\uFE0F", "\uD83C\uDD97"
    )
}

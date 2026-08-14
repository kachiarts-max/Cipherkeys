package com.cipherkeys.app.dictionary

/**
 * Dictionary contract used by CipherKeys for:
 * - word validation
 * - autocomplete suggestions
 * - spelling corrections
 * - learning words typed by the user
 */
interface Dictionary {

    /** True if [word] is a recognized or learned word. */
    fun isValidWord(word: String): Boolean

    /**
     * Returns likely completions for [prefix].
     * Results should prioritize learned/frequently used words.
     */
    fun suggestCompletions(prefix: String, limit: Int = 3): List<String>

    /**
     * Returns likely corrections for a misspelled word.
     */
    fun suggestCorrections(word: String, limit: Int = 3): List<String>

    /**
     * Learns a word locally so it can become a future suggestion.
     */
    fun learnWord(word: String)
}

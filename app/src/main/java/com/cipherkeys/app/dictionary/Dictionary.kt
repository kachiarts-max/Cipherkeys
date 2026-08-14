package com.cipherkeys.app.dictionary

/**
 * Everything CipherKeys needs from a word source.
 *
 * The dictionary supports:
 *
 * - Normal English words
 * - Word completions
 * - Spelling corrections
 * - User-learned words
 * - Personal vocabulary
 * - Contractions such as don't, can't, wouldn't, etc.
 */
interface Dictionary {

    /**
     * Returns true when [word] is recognized.
     */
    fun isValidWord(word: String): Boolean

    /**
     * Returns words beginning with [prefix].
     *
     * Example:
     *
     * "ciph" -> CipherKeys
     */
    fun suggestCompletions(
        prefix: String,
        limit: Int = 3
    ): List<String>

    /**
     * Returns likely corrections for a misspelled word.
     */
    fun suggestCorrections(
        word: String,
        limit: Int = 3
    ): List<String>

    /**
     * Teach CipherKeys a word.
     *
     * This is what allows the keyboard to learn words
     * that aren't present in the bundled dictionary.
     *
     * Example:
     *
     * CipherKeys
     * KachiArts
     * crypto
     * Bigi
     */
    fun learnWord(
        word: String
    )
}

package com.cipherkeys.app.dictionary

/**
 * Everything the keyboard's suggestion system and spell-checker
 * need from a word source.
 *
 * The dictionary supports:
 *
 * - Word validation
 * - Autocomplete
 * - Spelling corrections
 * - Learning new words
 *
 * Implementations can be backed by a bundled dictionary,
 * user vocabulary, or a future on-device language model.
 */
interface Dictionary {

    /**
     * Returns true when [word] is recognized as a valid word.
     */
    fun isValidWord(word: String): Boolean

    /**
     * Returns words beginning with [prefix].
     *
     * Results should be ranked by usefulness/frequency where possible.
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
     * Teaches the dictionary a word the user has actually used.
     *
     * This allows CipherKeys to gradually learn personal vocabulary
     * that may not exist in the bundled dictionary.
     */
    fun learnWord(word: String)
}

package com.cipherkeys.app.dictionary

/**
 * Everything CipherKeys needs from a word source.
 *
 * The dictionary supports:
 *
 * - Word validation
 * - Live word completion
 * - Spelling corrections
 * - Learning new words
 *
 * Implementations can use a bundled dictionary, locally learned
 * vocabulary, or a future on-device language model.
 */
interface Dictionary {

    /**
     * Returns true when [word] is recognized.
     */
    fun isValidWord(word: String): Boolean

    /**
     * Returns words that begin with [prefix].
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
     * Teaches CipherKeys a new word.
     *
     * Learned words are stored locally on the device so they
     * remain available the next time the keyboard is opened.
     */
    fun learnWord(word: String)
}

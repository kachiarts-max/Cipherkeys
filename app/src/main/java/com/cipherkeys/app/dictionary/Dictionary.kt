package com.cipherkeys.app.dictionary

/**
 * Everything the CipherKeys suggestion system needs from a word source.
 *
 * The dictionary can contain:
 *
 * 1. Bundled English words
 * 2. User-learned words
 * 3. Contractions such as don't, can't, wouldn't
 * 4. Future language-model words
 */
interface Dictionary {

    /**
     * Returns true when [word] is recognized.
     */
    fun isValidWord(word: String): Boolean

    /**
     * Returns words beginning with [prefix].
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
     * Teach the dictionary a new word.
     *
     * This is what allows CipherKeys to learn words
     * that aren't in the bundled dictionary.
     */
    fun learnWord(word: String)

    /**
     * Returns true when this word was specifically
     * learned from the user.
     */
    fun isUserLearnedWord(word: String): Boolean
}

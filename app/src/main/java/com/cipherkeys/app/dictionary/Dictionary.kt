package com.cipherkeys.app.dictionary

/**
 * Dictionary interface used by CipherKeys.
 *
 * The dictionary can combine:
 *
 * 1. Built-in English words
 * 2. User-learned words
 * 3. Contractions
 * 4. Future dictionaries/models
 */
interface Dictionary {

    /**
     * Returns true when the word is recognized.
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
     * This is what allows CipherKeys to develop
     * a personal vocabulary over time.
     */
    fun learnWord(word: String)
}

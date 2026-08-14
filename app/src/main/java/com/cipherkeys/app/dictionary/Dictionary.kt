package com.cipherkeys.app.dictionary

/**
 * Unified dictionary interface used by CipherKeys.
 *
 * The dictionary combines:
 *
 * 1. Bundled English words
 * 2. User-learned vocabulary
 * 3. Word completion
 * 4. Spell correction
 *
 * Implementations must keep learning local to the device.
 */
interface Dictionary {

    /**
     * Returns true when the word is recognized.
     *
     * This includes both bundled dictionary words
     * and words learned by the user.
     */
    fun isValidWord(word: String): Boolean

    /**
     * Returns completion suggestions for the
     * currently typed prefix.
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
     * Teach CipherKeys a new word.
     *
     * This is what allows the keyboard to learn
     * names, slang, technical terms, personal words,
     * etc.
     */
    fun learnWord(word: String)

    /**
     * Returns whether the word was specifically
     * learned by the user.
     */
    fun isLearnedWord(word: String): Boolean

    /**
     * Returns how many times the user has taught
     * or used a particular learned word.
     */
    fun usageCount(word: String): Int

    /**
     * Returns the user's learned vocabulary.
     */
    fun learnedWords(): List<String>

    /**
     * Remove one word from the personal vocabulary.
     */
    fun removeLearnedWord(word: String)

    /**
     * Clear all user-learned vocabulary.
     */
    fun clearLearnedWords()
}

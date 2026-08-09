
package com.cipherkeys.app.dictionary

/**
 * Everything the keyboard's suggestion strip and spell-checker need from a word source.
 * Kept intentionally small and swappable: today it's backed by a bundled word list
 * (see [EnglishLexicon]) plus locally-learned words (see [UserVocabulary]), but nothing
 * about the IME depends on that - a future, larger on-device model could implement this
 * same interface without touching [com.cipherkeys.app.keyboard.CipherKeysIME].
 */
interface Dictionary {

    /** True if [word] (case-insensitive) is a recognized word. */
    fun isValidWord(word: String): Boolean

    /**
     * Words that start with [prefix] (case-insensitive), for the live suggestion strip
     * as the user types. Shortest/most common matches first where possible.
     */
    fun suggestCompletions(prefix: String, limit: Int = 3): List<String>

    /**
     * Likely intended words for a misspelled [word] (case-insensitive), ranked by edit
     * distance. Empty if [word] is already valid or nothing close enough was found.
     */
    fun suggestCorrections(word: String, limit: Int = 3): List<String>
}

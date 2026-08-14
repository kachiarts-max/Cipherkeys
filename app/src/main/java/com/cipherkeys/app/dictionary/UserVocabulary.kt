package com.cipherkeys.app.dictionary

import android.content.Context

/**
 * Personal vocabulary for CipherKeys.
 *
 * Learns words that are not present in the bundled English dictionary.
 *
 * Examples:
 *
 * User repeatedly types:
 *   "CipherKeys"
 *   "KachiArts"
 *   "Bigi"
 *   "crypto"
 *
 * CipherKeys can learn them and later suggest them automatically.
 *
 * Everything is stored locally on the device.
 */
class UserVocabulary(context: Context) {

    private val preferences =
        context.getSharedPreferences(
            "cipherkeys_user_vocabulary",
            Context.MODE_PRIVATE
        )

    /**
     * Word -> number of times the user has typed it.
     */
    private val words =
        mutableMapOf<String, Int>()

    init {
        load()
    }

    /**
     * Teach CipherKeys a word.
     *
     * The word is normalized before storage.
     */
    fun learnWord(word: String) {

        val normalized =
            normalize(word)

        if (!isUsableWord(normalized)) {
            return
        }

        words[normalized] =
            (words[normalized] ?: 0) + 1

        save()
    }

    /**
     * Returns true if CipherKeys has learned this word.
     */
    fun contains(word: String): Boolean {

        return words.containsKey(
            normalize(word)
        )
    }

    /**
     * Returns how many times the user has typed
     * this particular word.
     */
    fun usageCount(word: String): Int {

        return words[
            normalize(word)
        ] ?: 0
    }

    /**
     * Find learned words that begin with [prefix].
     *
     * More frequently used words are ranked first.
     */
    fun suggest(
        prefix: String,
        limit: Int = 3
    ): List<String> {

        val normalizedPrefix =
            normalize(prefix)

        if (normalizedPrefix.isBlank()) {
            return emptyList()
        }

        return words
            .entries
            .asSequence()
            .filter {
                it.key.startsWith(
                    normalizedPrefix
                ) &&
                it.key != normalizedPrefix
            }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> {
                    it.value
                }.thenBy {
                    it.key.length
                }
            )
            .take(limit)
            .map {
                it.key
            }
            .toList()
    }

    /**
     * Returns all learned words.
     *
     * Useful later for a settings screen where
     * the user can view their personal dictionary.
     */
    fun allWords(): List<String> {

        return words
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> {
                    it.value
                }.thenBy {
                    it.key
                }
            )
            .map {
                it.key
            }
    }

    /**
     * Remove one learned word.
     */
    fun removeWord(word: String) {

        words.remove(
            normalize(word)
        )

        save()
    }

    /**
     * Completely reset the personal dictionary.
     */
    fun clear() {

        words.clear()

        preferences.edit()
            .clear()
            .apply()
    }

    /**
     * Save the vocabulary locally.
     *
     * Format:
     *
     * word|usageCount
     */
    private fun save() {

        val encoded =
            mutableSetOf<String>()

        words.forEach { (word, count) ->

            encoded.add(
                "$word|$count"
            )
        }

        preferences.edit()
            .putStringSet(
                "words",
                encoded
            )
            .apply()
    }

    /**
     * Load previously learned vocabulary.
     */
    private fun load() {

        val saved =
            preferences.getStringSet(
                "words",
                emptySet()
            ) ?: emptySet()

        saved.forEach { entry ->

            val parts =
                entry.split(
                    "|",
                    limit = 2
                )

            if (parts.size != 2) {
                return@forEach
            }

            val word =
                parts[0]

            val count =
                parts[1].toIntOrNull()
                    ?: return@forEach

            if (
                word.isBlank() ||
                count <= 0
            ) {
                return@forEach
            }

            words[word] = count
        }
    }

    /**
     * Normalize words consistently.
     */
    private fun normalize(
        word: String
    ): String {

        return word
            .trim()
            .lowercase()
    }

    /**
     * Prevent random punctuation, spaces,
     * and extremely long strings from entering
     * the personal dictionary.
     *
     * Apostrophes are allowed so contractions such as:
     *
     * don't
     * can't
     * wouldn't
     * we're
     *
     * remain intact.
     */
    private fun isUsableWord(
        word: String
    ): Boolean {

        if (word.length < 2) {
            return false
        }

        if (word.length > 40) {
            return false
        }

        return word.matches(
            Regex("[a-zA-Z]+(?:'[a-zA-Z]+)*")
        )
    }
}

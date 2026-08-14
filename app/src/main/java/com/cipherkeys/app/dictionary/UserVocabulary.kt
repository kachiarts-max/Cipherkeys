package com.cipherkeys.app.dictionary

import android.content.Context

/**
 * Personal vocabulary for CipherKeys.
 *
 * Learns words that are not present in the bundled dictionary.
 *
 * Examples:
 *
 * CipherKeys
 * KachiArts
 * Bigi
 * crypto
 * don't
 * can't
 * wouldn't
 * we're
 *
 * Everything is stored locally on the device.
 *
 * The more frequently a word is used, the higher
 * it is ranked in the suggestion system.
 */
class UserVocabulary(context: Context) {

    private val preferences =
        context.getSharedPreferences(
            "cipherkeys_user_vocabulary",
            Context.MODE_PRIVATE
        )

    /**
     * Word -> usage count.
     */
    private val words =
        mutableMapOf<String, Int>()

    init {
        load()
    }

    /**
     * Teach CipherKeys a word.
     *
     * Every time the user completes a word,
     * its usage count increases.
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
     * Returns true if this word has been learned.
     */
    fun contains(word: String): Boolean {

        return words.containsKey(
            normalize(word)
        )
    }

    /**
     * Returns how many times the user has used
     * a particular word.
     */
    fun usageCount(word: String): Int {

        return words[
            normalize(word)
        ] ?: 0
    }

    /**
     * Returns learned words beginning with [prefix].
     *
     * Ranking:
     *
     * 1. Most frequently used
     * 2. Shorter word
     * 3. Alphabetical order
     *
     * This makes frequently used personal words
     * appear naturally in the suggestion strip.
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
            .filter { entry ->

                entry.key.startsWith(
                    normalizedPrefix
                ) &&
                        entry.key != normalizedPrefix
            }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> {
                    it.value
                }
                    .thenBy {
                        it.key.length
                    }
                    .thenBy {
                        it.key
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
     * Used later for:
     *
     * - Personal dictionary settings
     * - Export/import
     * - Dictionary management
     * - Debugging
     */
    fun allWords(): List<String> {

        return words
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> {
                    it.value
                }
                    .thenBy {
                        it.key
                    }
            )
            .map {
                it.key
            }
    }

    /**
     * Remove a single learned word.
     */
    fun removeWord(word: String) {

        words.remove(
            normalize(word)
        )

        save()
    }

    /**
     * Completely reset learned vocabulary.
     */
    fun clear() {

        words.clear()

        preferences.edit()
            .clear()
            .apply()
    }

    /**
     * Save learned vocabulary locally.
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
                parts[0].trim()

            val count =
                parts[1]
                    .toIntOrNull()
                    ?: return@forEach

            if (
                word.isBlank() ||
                count <= 0
            ) {
                return@forEach
            }

            if (
                isUsableWord(
                    word.lowercase()
                )
            ) {

                words[
                    word.lowercase()
                ] = count
            }
        }
    }

    /**
     * Normalize a word consistently.
     *
     * CipherKeys currently stores vocabulary
     * case-insensitively.
     */
    private fun normalize(
        word: String
    ): String {

        return word
            .trim()
            .lowercase()
    }

    /**
     * Determines whether something is suitable
     * for the personal dictionary.
     *
     * Apostrophes are deliberately supported.
     *
     * Examples:
     *
     * don't
     * can't
     * won't
     * wouldn't
     * I'm
     * I've
     * I'll
     * you're
     * we're
     * they're
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

        /*
         * Allow:
         *
         * letters
         * one or more apostrophe-separated parts
         *
         * Examples:
         *
         * hello
         * don't
         * wouldn't
         * we're
         */
        return word.matches(
            Regex(
                "[a-z]+(?:'[a-z]+)*"
            )
        )
    }
}

package com.cipherkeys.app.dictionary

import android.content.Context

/**
 * Local context-learning engine for CipherKeys.
 *
 * Learns relationships between words as the user types.
 *
 * Examples:
 *
 * "I am going"
 *      I -> am
 *      am -> going
 *
 * "thank you"
 *      thank -> you
 *
 * "I love this"
 *      I -> love
 *      love -> this
 *
 * Everything is stored locally on the device.
 */
class ContextPredictor(context: Context) {

    private val preferences =
        context.getSharedPreferences(
            "cipherkeys_context",
            Context.MODE_PRIVATE
        )

    /**
     * Stores:
     *
     * previousWord -> nextWord -> frequency
     */
    private val predictions =
        mutableMapOf<String, MutableMap<String, Int>>()

    init {
        loadPredictions()
    }

    /**
     * Teach the predictor that [nextWord] commonly follows [previousWord].
     */
    fun learn(previousWord: String, nextWord: String) {

        val previous =
            normalize(previousWord)

        val next =
            normalize(nextWord)

        if (previous.isBlank() || next.isBlank()) {
            return
        }

        if (previous.length < 1 || next.length < 1) {
            return
        }

        val nextWords =
            predictions.getOrPut(previous) {
                mutableMapOf()
            }

        nextWords[next] =
            (nextWords[next] ?: 0) + 1

        savePredictions()
    }

    /**
     * Learn a complete sentence/sequence of words.
     *
     * Example:
     *
     * "I am going home"
     *
     * learns:
     *
     * I -> am
     * am -> going
     * going -> home
     */
    fun learnSequence(text: String) {

        val words =
            tokenize(text)

        if (words.size < 2) {
            return
        }

        for (i in 0 until words.lastIndex) {

            learn(
                words[i],
                words[i + 1]
            )
        }
    }

    /**
     * Returns words that commonly follow [previousWord].
     */
    fun suggestNextWords(
        previousWord: String,
        limit: Int = 3
    ): List<String> {

        val previous =
            normalize(previousWord)

        if (previous.isBlank()) {
            return emptyList()
        }

        val candidates =
            predictions[previous]
                ?: return emptyList()

        return candidates
            .entries
            .sortedByDescending {
                it.value
            }
            .take(limit)
            .map {
                it.key
            }
    }

    /**
     * Returns words that commonly follow the previous TWO words.
     *
     * This gives CipherKeys stronger context.
     *
     * Example:
     *
     * "how are" -> you
     *
     * rather than only:
     *
     * "are" -> you
     */
    fun suggestAfterPhrase(
        previousWords: List<String>,
        limit: Int = 3
    ): List<String> {

        if (previousWords.isEmpty()) {
            return emptyList()
        }

        /*
         * For now the predictor uses the most recent word.
         *
         * This keeps the first implementation lightweight.
         *
         * We will add true phrase-level prediction after
         * this version is confirmed working.
         */
        return suggestNextWords(
            previousWords.last(),
            limit
        )
    }

    /**
     * Returns whether the predictor has learned anything
     * about a particular word.
     */
    fun hasPredictionFor(
        word: String
    ): Boolean {

        return predictions.containsKey(
            normalize(word)
        )
    }

    /**
     * Clear all learned context.
     *
     * Useful later for a "Reset learned vocabulary"
     * button in CipherKeys settings.
     */
    fun clear() {

        predictions.clear()

        preferences.edit()
            .clear()
            .apply()
    }

    /**
     * Save learned relationships locally.
     *
     * Format:
     *
     * previous|next|count
     */
    private fun savePredictions() {

        val encoded =
            mutableSetOf<String>()

        predictions.forEach { (previous, nextWords) ->

            nextWords.forEach { (next, count) ->

                encoded.add(
                    "$previous|$next|$count"
                )
            }
        }

        preferences.edit()
            .putStringSet(
                "predictions",
                encoded
            )
            .apply()
    }

    /**
     * Load previously learned relationships.
     */
    private fun loadPredictions() {

        val saved =
            preferences.getStringSet(
                "predictions",
                emptySet()
            ) ?: emptySet()

        saved.forEach { entry ->

            val parts =
                entry.split(
                    "|",
                    limit = 3
                )

            if (parts.size != 3) {
                return@forEach
            }

            val previous =
                parts[0]

            val next =
                parts[1]

            val count =
                parts[2].toIntOrNull()
                    ?: return@forEach

            if (
                previous.isBlank() ||
                next.isBlank()
            ) {
                return@forEach
            }

            predictions
                .getOrPut(previous) {
                    mutableMapOf()
                }[next] = count
        }
    }

    /**
     * Convert text into usable words.
     *
     * Apostrophes are deliberately preserved so:
     *
     * don't
     * can't
     * wouldn't
     *
     * remain single words.
     */
    private fun tokenize(
        text: String
    ): List<String> {

        return text
            .lowercase()
            .split(
                Regex("[^a-zA-Z']+")
            )
            .map {
                it.trim(
                    '\'',
                    ' '
                )
            }
            .filter {
                it.isNotBlank()
            }
    }

    /**
     * Normalize a word for storage/comparison.
     */
    private fun normalize(
        word: String
    ): String {

        return word
            .trim()
            .lowercase()
    }
}

package com.cipherkeys.app.dictionary

import android.content.Context

/**
 * Local context-learning engine for CipherKeys.
 *
 * Learns how the user naturally combines words.
 *
 * Examples:
 *
 * "I am going"
 *
 * I -> am
 * am -> going
 *
 * It also keeps frequency counts:
 *
 * thank -> you       47
 * thank -> god        8
 * thank -> goodness   3
 *
 * The most frequently used combinations are suggested first.
 *
 * Everything is stored locally on the device.
 */
class ContextPredictor(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            "cipherkeys_context",
            Context.MODE_PRIVATE
        )

    /**
     * previous word -> next word -> frequency
     */
    private val predictions =
        mutableMapOf<String, MutableMap<String, Int>>()

    init {
        loadPredictions()
    }

    /**
     * Teaches CipherKeys that [nextWord] follows [previousWord].
     *
     * Every time the same combination is learned,
     * its frequency increases.
     */
    fun learn(
        previousWord: String,
        nextWord: String
    ) {

        val previous =
            normalize(previousWord)

        val next =
            normalize(nextWord)

        if (
            !isUsableWord(previous) ||
            !isUsableWord(next)
        ) {
            return
        }

        val nextWords =
            predictions.getOrPut(previous) {
                mutableMapOf()
            }

        val currentCount =
            nextWords[next] ?: 0

        /*
         * Prevent integer overflow if a combination
         * somehow gets learned an enormous number of times.
         */
        nextWords[next] =
            if (currentCount < MAX_FREQUENCY) {
                currentCount + 1
            } else {
                MAX_FREQUENCY
            }

        savePredictions()
    }

    /**
     * Learns an entire sequence of words.
     *
     * Example:
     *
     * "I am going home"
     *
     * becomes:
     *
     * I -> am
     * am -> going
     * going -> home
     */
    fun learnSequence(
        text: String
    ) {

        val words =
            tokenize(text)

        if (words.size < 2) {
            return
        }

        for (
            i in 0 until words.lastIndex
        ) {

            learn(
                words[i],
                words[i + 1]
            )
        }
    }

    /**
     * Returns the words most frequently used after
     * [previousWord].
     *
     * Frequency is the primary ranking factor.
     */
    fun suggestNextWords(
        previousWord: String,
        limit: Int = 3
    ): List<String> {

        if (limit <= 0) {
            return emptyList()
        }

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
            .sortedWith(
                compareByDescending<
                    Map.Entry<String, Int>
                > {
                    it.value
                }.thenBy {
                    it.key.length
                }.thenBy {
                    it.key
                }
            )
            .take(limit)
            .map {
                it.key
            }
    }

    /**
     * Returns context predictions after a phrase.
     *
     * The current lightweight model uses the final word
     * as the strongest signal.
     *
     * Example:
     *
     * "how are" -> predictions based on "are"
     *
     * This method is intentionally kept compatible with
     * the current CipherKeysIME implementation.
     */
    fun suggestAfterPhrase(
        previousWords: List<String>,
        limit: Int = 3
    ): List<String> {

        if (previousWords.isEmpty()) {
            return emptyList()
        }

        /*
         * Try the most recent word first.
         */
        val lastWord =
            previousWords
                .lastOrNull()
                ?: return emptyList()

        return suggestNextWords(
            lastWord,
            limit
        )
    }

    /**
     * Checks whether CipherKeys has learned
     * any following words for [word].
     */
    fun hasPredictionFor(
        word: String
    ): Boolean {

        val normalized =
            normalize(word)

        return predictions[
            normalized
        ]?.isNotEmpty() == true
    }

    /**
     * Returns the frequency with which [nextWord]
     * has followed [previousWord].
     *
     * Useful later for advanced ranking.
     */
    fun getFrequency(
        previousWord: String,
        nextWord: String
    ): Int {

        val previous =
            normalize(previousWord)

        val next =
            normalize(nextWord)

        return predictions[
            previous
        ]?.get(next) ?: 0
    }

    /**
     * Returns all learned predictions for a word,
     * sorted from most frequently used to least.
     *
     * Useful later for debugging or a
     * "Personal Dictionary" settings screen.
     */
    fun getLearnedPredictions(
        previousWord: String
    ): List<Pair<String, Int>> {

        val previous =
            normalize(previousWord)

        return predictions[
            previous
        ]
            ?.entries
            ?.sortedByDescending {
                it.value
            }
            ?.map {
                it.key to it.value
            }
            ?: emptyList()
    }

    /**
     * Removes one specific learned relationship.
     *
     * Example:
     *
     * removePrediction("thank", "you")
     */
    fun removePrediction(
        previousWord: String,
        nextWord: String
    ) {

        val previous =
            normalize(previousWord)

        val next =
            normalize(nextWord)

        val nextWords =
            predictions[previous]
            ?: return

        nextWords.remove(next)

        if (nextWords.isEmpty()) {
            predictions.remove(previous)
        }

        savePredictions()
    }

    /**
     * Completely resets the learned context.
     *
     * This will later be useful for:
     *
     * Settings ->
     * Privacy ->
     * Clear learned suggestions
     */
    fun clear() {

        predictions.clear()

        preferences
            .edit()
            .clear()
            .apply()
    }

    /**
     * Saves all learned relationships locally.
     *
     * Format:
     *
     * previous|next|frequency
     */
    private fun savePredictions() {

        val encoded =
            mutableSetOf<String>()

        predictions.forEach { (
            previous,
            nextWords
        ) ->

            nextWords.forEach { (
                next,
                count
            ) ->

                encoded.add(
                    "$previous|$next|$count"
                )
            }
        }

        preferences
            .edit()
            .putStringSet(
                "predictions",
                encoded
            )
            .apply()
    }

    /**
     * Loads previously learned relationships.
     */
    private fun loadPredictions() {

        val saved =
            preferences.getStringSet(
                "predictions",
                emptySet()
            )
                ?: emptySet()

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
                normalize(parts[0])

            val next =
                normalize(parts[1])

            val count =
                parts[2]
                    .toIntOrNull()
                    ?: return@forEach

            if (
                !isUsableWord(previous) ||
                !isUsableWord(next) ||
                count <= 0
            ) {
                return@forEach
            }

            predictions
                .getOrPut(previous) {
                    mutableMapOf()
                }[next] =
                count.coerceAtMost(
                    MAX_FREQUENCY
                )
        }
    }

    /**
     * Converts normal text into words.
     *
     * Apostrophes are preserved.
     *
     * Examples:
     *
     * don't
     * can't
     * wouldn't
     * I'm
     * you're
     */
    private fun tokenize(
        text: String
    ): List<String> {

        return text
            .lowercase()
            .split(
                Regex(
                    "[^a-zA-Z']+"
                )
            )
            .map {
                it.trim(
                    '\'',
                    ' '
                )
            }
            .filter {
                isUsableWord(it)
            }
    }

    /**
     * Normalizes words for storage and comparison.
     *
     * Curly apostrophes are converted to normal
     * apostrophes so:
     *
     * don't
     *
     * and:
     *
     * don’t
     *
     * are treated as the same word.
     */
    private fun normalize(
        word: String
    ): String {

        return word
            .trim()
            .lowercase()
            .replace(
                '’',
                '\''
            )
    }

    /**
     * Determines whether something looks like
     * an actual word rather than random symbols.
     */
    private fun isUsableWord(
        word: String
    ): Boolean {

        if (word.isBlank()) {
            return false
        }

        if (word.length > MAX_WORD_LENGTH) {
            return false
        }

        return word.all {
            it.isLetter() ||
                    it == '\'' ||
                    it == '-'
        }
    }

    companion object {

        /**
         * Prevents unrealistic frequency overflow.
         */
        private const val MAX_FREQUENCY =
            1_000_000

        /**
         * Prevents accidental storage of
         * enormous strings as learned words.
         */
        private const val MAX_WORD_LENGTH =
            64
    }
}

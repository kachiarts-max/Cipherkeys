package com.cipherkeys.app.dictionary

import android.content.Context

/**
 * Phrase-level learning engine for CipherKeys.
 *
 * Learns relationships between short sequences of words.
 *
 * Examples:
 *
 * "thank you" -> "for"
 * "thank you for" -> "everything"
 * "how are" -> "you"
 * "how are you" -> "doing"
 *
 * Everything is stored locally on the device.
 */
class PhrasePredictor(context: Context) {

    private val preferences =
        context.getSharedPreferences(
            "cipherkeys_phrase_predictions",
            Context.MODE_PRIVATE
        )

    /**
     * phrase -> nextWord -> frequency
     */
    private val predictions =
        mutableMapOf<String, MutableMap<String, Int>>()

    init {
        loadPredictions()
    }

    /**
     * Teach CipherKeys that [nextWord] commonly follows
     * the supplied phrase.
     *
     * Example:
     *
     * phrase = "thank you"
     * nextWord = "for"
     */
    fun learn(
        phrase: List<String>,
        nextWord: String
    ) {

        val normalizedPhrase =
            normalizePhrase(phrase)

        val normalizedNext =
            normalizeWord(nextWord)

        if (
            normalizedPhrase.isBlank() ||
            normalizedNext.isBlank()
        ) {
            return
        }

        val nextWords =
            predictions.getOrPut(
                normalizedPhrase
            ) {
                mutableMapOf()
            }

        nextWords[normalizedNext] =
            (nextWords[normalizedNext] ?: 0) + 1

        savePredictions()
    }

    /**
     * Learn every phrase relationship contained
     * within a sequence.
     *
     * Example:
     *
     * "I am going home"
     *
     * learns:
     *
     * "i" -> "am"
     * "i am" -> "going"
     * "am going" -> "home"
     * "i am going" -> "home"
     * etc.
     *
     * [maxPhraseWords] controls the longest phrase
     * stored by the predictor.
     */
    fun learnSequence(
        text: String,
        maxPhraseWords: Int = 3
    ) {

        if (maxPhraseWords < 1) {
            return
        }

        val words =
            tokenize(text)

        if (words.size < 2) {
            return
        }

        for (i in words.indices) {

            val nextIndex =
                i + 1

            if (nextIndex >= words.size) {
                break
            }

            val maximumPhraseLength =
                minOf(
                    maxPhraseWords,
                    i + 1
                )

            for (
                phraseLength in 1..maximumPhraseLength
            ) {

                val phraseStart =
                    i - phraseLength + 1

                if (phraseStart < 0) {
                    continue
                }

                val phrase =
                    words.subList(
                        phraseStart,
                        i + 1
                    )

                learn(
                    phrase,
                    words[nextIndex]
                )
            }
        }
    }

    /**
     * Returns words commonly following a phrase.
     *
     * Results are ranked by learned frequency.
     */
    fun suggestNextWords(
        phrase: List<String>,
        limit: Int = 3
    ): List<String> {

        val normalizedPhrase =
            normalizePhrase(phrase)

        if (
            normalizedPhrase.isBlank() ||
            limit <= 0
        ) {
            return emptyList()
        }

        val candidates =
            predictions[
                normalizedPhrase
            ]
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
     * Convenience function for a phrase represented
     * as a string.
     *
     * Example:
     *
     * suggestNextWords("thank you")
     */
    fun suggestNextWords(
        phrase: String,
        limit: Int = 3
    ): List<String> {

        return suggestNextWords(
            tokenize(phrase),
            limit
        )
    }

    /**
     * Returns the strongest prediction for a phrase.
     */
    fun strongestPrediction(
        phrase: List<String>
    ): String? {

        return suggestNextWords(
            phrase,
            1
        ).firstOrNull()
    }

    /**
     * Returns whether CipherKeys has learned
     * anything about this phrase.
     */
    fun hasPredictionFor(
        phrase: List<String>
    ): Boolean {

        val normalized =
            normalizePhrase(phrase)

        return normalized.isNotBlank() &&
                predictions.containsKey(
                    normalized
                )
    }

    /**
     * Returns the usage frequency of a specific
     * phrase -> next-word relationship.
     */
    fun usageCount(
        phrase: List<String>,
        nextWord: String
    ): Int {

        val normalizedPhrase =
            normalizePhrase(phrase)

        val normalizedNext =
            normalizeWord(nextWord)

        return predictions[
            normalizedPhrase
        ]?.get(
            normalizedNext
        ) ?: 0
    }

    /**
     * Remove everything the phrase predictor
     * has learned.
     */
    fun clear() {

        predictions.clear()

        preferences.edit()
            .clear()
            .apply()
    }

    /**
     * Save all phrase relationships locally.
     *
     * Format:
     *
     * phrase|nextWord|count
     */
    private fun savePredictions() {

        val encoded =
            mutableSetOf<String>()

        predictions.forEach { (phrase, nextWords) ->

            nextWords.forEach { (nextWord, count) ->

                encoded.add(
                    "$phrase|$nextWord|$count"
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
     * Load previously learned phrase relationships.
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

            val phrase =
                parts[0]

            val nextWord =
                parts[1]

            val count =
                parts[2].toIntOrNull()
                    ?: return@forEach

            if (
                phrase.isBlank() ||
                nextWord.isBlank() ||
                count <= 0
            ) {
                return@forEach
            }

            predictions
                .getOrPut(phrase) {
                    mutableMapOf()
                }[nextWord] = count
        }
    }

    /**
     * Normalize a phrase represented as words.
     */
    private fun normalizePhrase(
        phrase: List<String>
    ): String {

        return phrase
            .map {
                normalizeWord(it)
            }
            .filter {
                it.isNotBlank()
            }
            .joinToString(" ")
    }

    /**
     * Normalize a single word.
     */
    private fun normalizeWord(
        word: String
    ): String {

        return word
            .trim()
            .lowercase()
    }

    /**
     * Convert text into usable words.
     *
     * Apostrophes are preserved so:
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
}

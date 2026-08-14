package com.cipherkeys.app.dictionary

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs

/**
 * Smart local dictionary for CipherKeys.
 *
 * Sources:
 * 1. Bundled English dictionary
 * 2. Common English contractions
 * 3. User-learned vocabulary
 *
 * User words are stored locally on the device.
 */
class EnglishLexicon(
    context: Context,
    assetPath: String = "dictionary/common_words.txt"
) : Dictionary {

    private val preferences =
        context.getSharedPreferences(
            "cipherkeys_vocabulary",
            Context.MODE_PRIVATE
        )

    private val words: Set<String> =
        loadWords(context, assetPath)

    /**
     * Common English contractions.
     */
    private val contractions = setOf(
        "I'm", "I've", "I'll", "I'd",
        "you're", "you've", "you'll", "you'd",
        "he's", "he'll", "he'd",
        "she's", "she'll", "she'd",
        "it's", "it'll", "it'd",
        "we're", "we've", "we'll", "we'd",
        "they're", "they've", "they'll", "they'd",
        "that's", "that'll", "that'd",
        "there's", "there'll", "there'd",
        "what's", "what'll", "what'd",
        "who's", "who'll", "who'd",
        "where's", "where'll", "where'd",
        "when's", "when'd",
        "why's",
        "how's",

        "isn't",
        "aren't",
        "wasn't",
        "weren't",

        "haven't",
        "hasn't",
        "hadn't",

        "don't",
        "doesn't",
        "didn't",

        "can't",
        "couldn't",

        "won't",
        "wouldn't",

        "shouldn't",
        "mustn't",
        "mightn't",
        "needn't",
        "shan't",

        "let's"
    ).map { it.lowercase() }

    /**
     * Word -> usage count.
     *
     * Example:
     *
     * cipherkeys = 20
     * hello = 12
     * bro = 7
     */
    private val learnedWords: MutableMap<String, Int> =
        loadLearnedWords()

    /**
     * Loads the bundled dictionary.
     */
    private fun loadWords(
        context: Context,
        assetPath: String
    ): Set<String> {

        return try {

            context.assets.open(assetPath).use { stream ->

                BufferedReader(
                    InputStreamReader(stream)
                ).useLines { lines ->

                    lines
                        .map {
                            it.trim().lowercase()
                        }
                        .filter {
                            it.isNotEmpty()
                        }
                        .toSet()
                }
            }

        } catch (e: Exception) {

            emptySet()
        }
    }

    /**
     * Loads previously learned words.
     */
    private fun loadLearnedWords(): MutableMap<String, Int> {

        val saved =
            preferences.getStringSet(
                "learned_words",
                emptySet()
            ) ?: emptySet()

        val result =
            mutableMapOf<String, Int>()

        saved.forEach { entry ->

            val parts =
                entry.split("|", limit = 2)

            if (parts.size == 2) {

                val word = parts[0]

                val count =
                    parts[1].toIntOrNull() ?: 1

                if (word.isNotBlank()) {
                    result[word] = count
                }
            }
        }

        return result
    }

    /**
     * Saves learned vocabulary.
     */
    private fun saveLearnedWords() {

        val encoded =
            learnedWords.map { (word, count) ->
                "$word|$count"
            }.toSet()

        preferences.edit()
            .putStringSet(
                "learned_words",
                encoded
            )
            .apply()
    }

    /**
     * Determines whether a word is known.
     */
    override fun isValidWord(word: String): Boolean {

        if (word.isBlank()) return false

        val normalized =
            word.lowercase()

        return normalized in words ||
                normalized in contractions ||
                learnedWords.containsKey(normalized)
    }

    /**
     * Teach CipherKeys a new word.
     *
     * Every time the user completes a word,
     * its frequency increases.
     */
    override fun learnWord(word: String) {

        val normalized =
            word
                .trim()
                .lowercase()

        if (normalized.length < 2) return

        /*
         * Don't learn something that contains no letters.
         */
        if (!normalized.any { it.isLetter() }) {
            return
        }

        val current =
            learnedWords[normalized] ?: 0

        learnedWords[normalized] =
            current + 1

        saveLearnedWords()
    }

    /**
     * Generate intelligent autocomplete suggestions.
     */
    override fun suggestCompletions(
        prefix: String,
        limit: Int
    ): List<String> {

        if (prefix.isBlank()) {
            return emptyList()
        }

        val lowerPrefix =
            prefix.lowercase()

        /*
         * Candidate information.
         *
         * Each candidate gets:
         *
         * frequency
         * source bonus
         * length preference
         */
        data class Candidate(
            val word: String,
            val frequency: Int,
            val sourceBonus: Int
        )

        val candidates =
            mutableMapOf<String, Candidate>()

        /*
         * ----------------------------------------------------
         * BUNDLED DICTIONARY
         * ----------------------------------------------------
         */

        words
            .asSequence()
            .filter {
                it.startsWith(lowerPrefix) &&
                        it != lowerPrefix
            }
            .forEach { word ->

                candidates[word] =
                    Candidate(
                        word = word,
                        frequency = 0,
                        sourceBonus = 10
                    )
            }

        /*
         * ----------------------------------------------------
         * CONTRACTIONS
         * ----------------------------------------------------
         */

        contractions
            .asSequence()
            .filter {
                it.startsWith(lowerPrefix) &&
                        it != lowerPrefix
            }
            .forEach { word ->

                candidates[word] =
                    Candidate(
                        word = word,
                        frequency = 0,
                        sourceBonus = 25
                    )
            }

        /*
         * ----------------------------------------------------
         * USER LEARNED WORDS
         * ----------------------------------------------------
         *
         * Learned words receive a large ranking advantage.
         */

        learnedWords
            .filterKeys {
                it.startsWith(lowerPrefix) &&
                        it != lowerPrefix
            }
            .forEach { (word, frequency) ->

                candidates[word] =
                    Candidate(
                        word = word,
                        frequency = frequency,
                        sourceBonus = 100
                    )
            }

        /*
         * ----------------------------------------------------
         * SMART RANKING
         * ----------------------------------------------------
         *
         * Score =
         *
         * frequency
         * + source bonus
         * + prefix match quality
         * - length penalty
         */

        return candidates.values
            .map { candidate ->

                val remainingLength =
                    candidate.word.length -
                            lowerPrefix.length

                val lengthPenalty =
                    remainingLength.coerceAtMost(10)

                val score =
                    (candidate.frequency * 20) +
                            candidate.sourceBonus -
                            lengthPenalty

                candidate.word to score
            }
            .sortedWith(
                compareByDescending<Pair<String, Int>> {
                    it.second
                }.thenBy {
                    it.first.length
                }
            )
            .take(limit)
            .map {
                it.first
            }
    }

    /**
     * Find spelling corrections.
     */
    override fun suggestCorrections(
        word: String,
        limit: Int
    ): List<String> {

        if (word.isBlank()) {
            return emptyList()
        }

        if (isValidWord(word)) {
            return emptyList()
        }

        val lowerWord =
            word.lowercase()

        val allCandidates =
            words +
                    contractions +
                    learnedWords.keys

        return allCandidates
            .asSequence()

            /*
             * Avoid ridiculous corrections.
             */
            .filter {
                abs(
                    it.length -
                            lowerWord.length
                ) <= 2
            }

            /*
             * Calculate edit distance.
             */
            .map {
                val distance =
                    levenshteinDistance(
                        lowerWord,
                        it
                    )

                Triple(
                    it,
                    distance,
                    learnedWords[it] ?: 0
                )
            }

            /*
             * Only reasonably close words.
             */
            .filter {
                it.second <= 2
            }

            /*
             * Rank:
             *
             * 1. Closest spelling
             * 2. Most frequently learned
             * 3. Shorter word
             */
            .sortedWith(
                compareBy<
                    Triple<String, Int, Int>
                    > {
                    it.second
                }.thenByDescending {
                    it.third
                }.thenBy {
                    it.first.length
                }
            )

            .take(limit)

            .map {
                it.first
            }

            .toList()
    }

    /**
     * Standard Levenshtein edit distance.
     */
    private fun levenshteinDistance(
        a: String,
        b: String
    ): Int {

        val dp =
            Array(a.length + 1) {
                IntArray(
                    b.length + 1
                )
            }

        for (i in 0..a.length) {
            dp[i][0] = i
        }

        for (j in 0..b.length) {
            dp[0][j] = j
        }

        for (i in 1..a.length) {

            for (j in 1..b.length) {

                val cost =
                    if (
                        a[i - 1] ==
                        b[j - 1]
                    ) {
                        0
                    } else {
                        1
                    }

                dp[i][j] =
                    minOf(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost
                    )
            }
        }

        return dp[a.length][b.length]
    }
}

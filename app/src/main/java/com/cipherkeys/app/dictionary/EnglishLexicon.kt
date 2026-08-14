package com.cipherkeys.app.dictionary

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * CipherKeys English dictionary.
 *
 * Combines:
 *
 * 1. Built-in English vocabulary
 * 2. User-learned vocabulary
 *
 * This allows CipherKeys to gradually develop
 * a personal dictionary based on the user's
 * typing habits.
 */
class EnglishLexicon(
    context: Context,
    assetPath: String = "dictionary/common_words.txt"
) : Dictionary {

    /**
     * Built-in English words.
     */
    private val words: Set<String> =
        loadWords(
            context,
            assetPath
        )

    /**
     * Personal vocabulary learned by the user.
     */
    private val userVocabulary =
        UserVocabulary(context)

    /**
     * Built-in words grouped by first letter.
     */
    private val byFirstLetter: Map<Char, List<String>> =
        words.groupBy { it.first() }

    /**
     * Returns true when a word exists in either
     * the built-in dictionary or the user's
     * learned vocabulary.
     */
    override fun isValidWord(
        word: String
    ): Boolean {

        if (word.isBlank()) {
            return false
        }

        val normalized =
            word.lowercase()

        return words.contains(normalized) ||
                userVocabulary.contains(normalized)
    }

    /**
     * Returns completion suggestions.
     *
     * Learned words are given priority because
     * they represent the user's personal vocabulary.
     */
    override fun suggestCompletions(
        prefix: String,
        limit: Int
    ): List<String> {

        if (prefix.isBlank()) {
            return emptyList()
        }

        val normalizedPrefix =
            prefix.lowercase()

        /*
         * Words learned by the user.
         */
        val personalSuggestions =
            userVocabulary.suggest(
                normalizedPrefix,
                limit
            )

        /*
         * Words from the built-in dictionary.
         */
        val builtInSuggestions =
            byFirstLetter[
                normalizedPrefix.first()
            ]
                ?.filter { word ->
                    word.startsWith(normalizedPrefix) &&
                            word != normalizedPrefix
                }
                ?.sortedBy { word ->
                    word.length
                }
                ?.take(limit)
                ?: emptyList()

        /*
         * Personal vocabulary first,
         * followed by normal dictionary words.
         */
        return (
            personalSuggestions +
                    builtInSuggestions
            )
            .distinct()
            .take(limit)
    }

    /**
     * Teach CipherKeys a new word.
     */
    override fun learnWord(
        word: String
    ) {

        val normalized =
            word.trim().lowercase()

        if (normalized.isBlank()) {
            return
        }

        /*
         * Only store words that aren't already
         * contained in the bundled dictionary.
         */
        if (!words.contains(normalized)) {

            userVocabulary.learnWord(
                normalized
            )
        }
    }

    /**
     * Returns likely corrections for a misspelled word.
     *
     * Corrections can come from both the built-in
     * dictionary and learned vocabulary.
     */
    override fun suggestCorrections(
        word: String,
        limit: Int
    ): List<String> {

        if (
            word.isBlank() ||
            isValidWord(word)
        ) {
            return emptyList()
        }

        val normalized =
            word.lowercase()

        /*
         * Built-in candidates.
         */
        val builtInCandidates =
            words.filter { candidate ->
                kotlin.math.abs(
                    candidate.length -
                            normalized.length
                ) <= 2
            }

        /*
         * User-learned candidates.
         */
        val learnedCandidates =
            userVocabulary
                .allWords()
                .filter { candidate ->
                    kotlin.math.abs(
                        candidate.length -
                                normalized.length
                    ) <= 2
                }

        /*
         * Combine both sources.
         */
        val candidates =
            (
                builtInCandidates +
                        learnedCandidates
                )
                .distinct()

        return candidates
            .map { candidate ->
                candidate to levenshteinDistance(
                    normalized,
                    candidate
                )
            }
            .filter { pair ->
                pair.second <= 2
            }
            .sortedBy { pair ->
                pair.second
            }
            .take(limit)
            .map { pair ->
                pair.first
            }
    }

    /**
     * Loads the bundled dictionary from:
     *
     * assets/dictionary/common_words.txt
     */
    private fun loadWords(
        context: Context,
        assetPath: String
    ): Set<String> {

        return try {

            context.assets
                .open(assetPath)
                .use { stream ->

                    BufferedReader(
                        InputStreamReader(stream)
                    ).useLines { lines ->

                        lines
                            .map { line ->
                                line.trim().lowercase()
                            }
                            .filter { word ->
                                word.isNotEmpty()
                            }
                            .toSet()
                    }
                }

        } catch (e: Exception) {

            emptySet()
        }
    }

    /**
     * Standard Levenshtein edit-distance calculation.
     *
     * Counts:
     *
     * - insertion
     * - deletion
     * - substitution
     */
    private fun levenshteinDistance(
        a: String,
        b: String
    ): Int {

        val dp =
            Array(
                a.length + 1
            ) {
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

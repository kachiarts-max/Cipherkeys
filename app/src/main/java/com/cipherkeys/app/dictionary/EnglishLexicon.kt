package com.cipherkeys.app.dictionary

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs

/**
 * Main CipherKeys dictionary.
 *
 * Combines:
 *
 * 1. Bundled English dictionary
 * 2. User-learned vocabulary
 *
 * This means CipherKeys can recognize normal English
 * while also learning words that aren't in the original
 * dictionary.
 */
class EnglishLexicon(
    context: Context,
    assetPath: String = "dictionary/common_words.txt"
) : Dictionary {

    private val words: Set<String> =
        loadWords(
            context,
            assetPath
        )

    private val userVocabulary =
        UserVocabulary(context)

    /**
     * Words grouped by first letter.
     *
     * This makes normal dictionary completion
     * faster.
     */
    private val byFirstLetter: Map<Char, List<String>> =
        words.groupBy {
            it.first()
        }

    /**
     * Returns true if the word exists either in
     * the bundled dictionary OR the user's personal
     * vocabulary.
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
     * Returns completion suggestions from both
     * the normal dictionary and learned vocabulary.
     *
     * Learned words receive priority because they
     * represent the user's personal language.
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

        val learnedSuggestions =
            userVocabulary.suggest(
                normalizedPrefix,
                limit
            )

        val dictionarySuggestions =
            if (normalizedPrefix.isNotEmpty()) {

                val candidates =
                    byFirstLetter[
                        normalizedPrefix.first()
                    ]

                candidates
                    ?.asSequence()
                    ?.filter {
                        it.startsWith(
                            normalizedPrefix
                        ) &&
                        it != normalizedPrefix
                    }
                    ?.sortedBy {
                        it.length
                    }
                    ?.take(limit)
                    ?.toList()
                    ?: emptyList()

            } else {
                emptyList()
            }

        return (
            learnedSuggestions +
                    dictionarySuggestions
            )
            .distinct()
            .take(limit)
    }

    /**
     * Returns spelling corrections from the
     * bundled dictionary and learned vocabulary.
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
         * Combine normal and learned words.
         */
        val allCandidates =
            (
                words +
                        userVocabulary
                            .allWords()
                            .toSet()
                )
                .filter {
                    abs(
                        it.length -
                                normalized.length
                    ) <= 2
                }

        return allCandidates
            .asSequence()
            .map {
                it to levenshteinDistance(
                    normalized,
                    it
                )
            }
            .filter {
                (_, distance) ->
                distance <= 2
            }
            .sortedBy {
                it.second
            }
            .take(limit)
            .map {
                it.first
            }
            .toList()
    }

    /**
     * Teach CipherKeys a new word.
     *
     * The actual persistence is handled by
     * UserVocabulary.
     */
    override fun learnWord(
        word: String
    ) {

        userVocabulary.learnWord(
            word
        )
    }

    /**
     * Returns true when a word exists specifically
     * inside the user's personal vocabulary.
     */
    override fun isLearnedWord(
        word: String
    ): Boolean {

        return userVocabulary.contains(
            word
        )
    }

    /**
     * Returns the number of times a learned word
     * has been used.
     */
    override fun usageCount(
        word: String
    ): Int {

        return userVocabulary.usageCount(
            word
        )
    }

    /**
     * Returns all personal vocabulary words.
     */
    override fun learnedWords(): List<String> {

        return userVocabulary.allWords()
    }

    /**
     * Remove one learned word.
     */
    override fun removeLearnedWord(
        word: String
    ) {

        userVocabulary.removeWord(
            word
        )
    }

    /**
     * Remove all learned vocabulary.
     */
    override fun clearLearnedWords() {

        userVocabulary.clear()
    }

    /**
     * Loads the bundled English dictionary.
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
                        InputStreamReader(
                            stream
                        )
                    ).useLines { lines ->

                        lines
                            .map {
                                it.trim()
                                    .lowercase()
                            }
                            .filter {
                                it.isNotEmpty()
                            }
                            .toSet()
                    }
                }

        } catch (
            e: Exception
        ) {

            emptySet()
        }
    }

    /**
     * Standard Levenshtein edit distance.
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

        for (
            i in 0..a.length
        ) {
            dp[i][0] = i
        }

        for (
            j in 0..b.length
        ) {
            dp[0][j] = j
        }

        for (
            i in 1..a.length
        ) {

            for (
                j in 1..b.length
            ) {

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
                        dp[i - 1][j - 1] +
                                cost
                    )
            }
        }

        return dp[a.length][b.length]
    }
}

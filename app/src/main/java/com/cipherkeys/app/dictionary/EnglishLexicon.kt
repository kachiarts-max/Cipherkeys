package com.cipherkeys.app.dictionary

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs

/**
 * Main English dictionary for CipherKeys.
 *
 * Combines:
 *
 * - Bundled English dictionary
 * - Smart contractions
 * - Personal user vocabulary
 */
class EnglishLexicon(
    context: Context,
    assetPath: String = "dictionary/common_words.txt"
) : Dictionary {

    private val userVocabulary =
        UserVocabulary(context)

    private val words: Set<String> =
        loadWords(
            context,
            assetPath
        )

    /**
     * Words grouped by their first letter.
     */
    private val byFirstLetter: Map<Char, List<String>> =
        words.groupBy {
            it.first()
        }

    /**
     * Load the bundled English dictionary.
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
     * Checks whether CipherKeys recognizes a word.
     */
    override fun isValidWord(
        word: String
    ): Boolean {

        if (word.isBlank()) {
            return false
        }

        val normalized =
            word.lowercase()

        /*
         * Normal dictionary.
         */
        if (words.contains(normalized)) {
            return true
        }

        /*
         * User vocabulary.
         */
        if (userVocabulary.contains(normalized)) {
            return true
        }

        /*
         * Natural contractions.
         *
         * Example:
         *
         * don't
         * can't
         * wouldn't
         */
        if (
            SmartContractions.hasContraction(
                normalized
            )
        ) {
            return true
        }

        /*
         * Also recognize the unpunctuated version.
         *
         * Example:
         *
         * dont
         * cant
         * wouldnt
         */
        if (
            SmartContractions.convert(
                normalized
            ) != null
        ) {
            return true
        }

        return false
    }

    /**
     * Teach CipherKeys a new word.
     */
    override fun learnWord(
        word: String
    ) {

        userVocabulary.learnWord(
            word
        )
    }

    /**
     * Check whether the user specifically
     * learned this word.
     */
    override fun isUserLearnedWord(
        word: String
    ): Boolean {

        return userVocabulary.contains(
            word
        )
    }

    /**
     * Suggest words while the user types.
     *
     * Combines:
     *
     * 1. Personal vocabulary
     * 2. Smart contractions
     * 3. Normal English dictionary
     */
    override fun suggestCompletions(
        prefix: String,
        limit: Int
    ): List<String> {

        if (prefix.isBlank()) {
            return emptyList()
        }

        val lower =
            prefix.lowercase()

        /*
         * Personal words.
         */
        val learned =
            userVocabulary.suggest(
                lower,
                limit
            )

        /*
         * Normal dictionary.
         */
        val dictionarySuggestions =
            if (lower.isNotEmpty()) {

                val candidates =
                    byFirstLetter[
                        lower.first()
                    ] ?: emptyList()

                candidates
                    .asSequence()
                    .filter {
                        it.startsWith(lower) &&
                                it != lower
                    }
                    .sortedWith(
                        compareBy<String> {
                            it.length
                        }.thenBy {
                            it
                        }
                    )
                    .take(limit)
                    .toList()

            } else {

                emptyList()
            }

        /*
         * Smart contraction suggestions.
         *
         * Example:
         *
         * "dont" -> "don't"
         * "cant" -> "can't"
         * "woul" -> "wouldn't"
         */
        val contractionSuggestions =
            SmartContractions
                .all()
                .asSequence()
                .filter { contraction ->

                    contraction.startsWith(
                        lower
                    ) ||
                            contraction
                                .replace(
                                    "'",
                                    ""
                                )
                                .startsWith(
                                    lower
                                )
                }
                .distinct()
                .take(limit)
                .toList()

        /*
         * Combine all sources.
         */
        return (
            learned +
                    contractionSuggestions +
                    dictionarySuggestions
            )
            .distinct()
            .take(limit)
    }

    /**
     * Find likely spelling corrections.
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

        val lower =
            word.lowercase()

        /*
         * If the word is actually an unpunctuated
         * contraction, prefer the contraction.
         *
         * Example:
         *
         * dont -> don't
         * cant -> can't
         */
        val contraction =
            SmartContractions
                .convertPreservingCase(
                    word
                )

        if (contraction != null) {

            return listOf(
                contraction
            )
        }

        /*
         * Search normal + learned vocabulary.
         */
        val candidates =
            (
                words +
                        userVocabulary.allWords() +
                        SmartContractions.all()
                )
                .asSequence()
                .filter {
                    abs(
                        it.length -
                                lower.length
                    ) <= 2
                }
                .distinct()

        return candidates
            .map {
                it to levenshteinDistance(
                    lower,
                    it
                )
            }
            .filter {
                (_, distance) ->
                distance <= 2
            }
            .sortedWith(
                compareBy<Pair<String, Int>> {
                    it.second
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

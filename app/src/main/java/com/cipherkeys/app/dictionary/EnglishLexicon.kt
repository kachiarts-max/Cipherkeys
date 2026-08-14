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
 * - Common contractions
 * - Personal user vocabulary
 *
 * User vocabulary is stored locally on the device.
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
     * Common English contractions.
     *
     * These are included so CipherKeys recognizes
     * natural everyday typing.
     */
    private val contractions =
        setOf(
            "don't",
            "doesn't",
            "didn't",
            "can't",
            "cannot",
            "couldn't",
            "wouldn't",
            "shouldn't",
            "won't",
            "isn't",
            "aren't",
            "wasn't",
            "weren't",
            "haven't",
            "hasn't",
            "hadn't",
            "mustn't",
            "mightn't",
            "needn't",
            "i'm",
            "you're",
            "he's",
            "she's",
            "it's",
            "we're",
            "they're",
            "i've",
            "you've",
            "we've",
            "they've",
            "i'd",
            "you'd",
            "he'd",
            "she'd",
            "we'd",
            "they'd",
            "i'll",
            "you'll",
            "he'll",
            "she'll",
            "we'll",
            "they'll",
            "that's",
            "there's",
            "here's",
            "what's",
            "who's",
            "let's"
        )

    /**
     * Words grouped by first character.
     *
     * This avoids scanning the entire dictionary
     * for normal completion searches.
     */
    private val byFirstLetter: Map<Char, List<String>> =
        words
            .groupBy {
                it.first()
            }

    /**
     * Load the bundled dictionary.
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
     * Check whether a word is recognized.
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
                contractions.contains(normalized) ||
                userVocabulary.contains(normalized)
    }

    /**
     * Teach CipherKeys a word.
     */
    override fun learnWord(
        word: String
    ) {

        userVocabulary.learnWord(word)
    }

    /**
     * Check whether a word came from the user's
     * personal vocabulary.
     */
    override fun isUserLearnedWord(
        word: String
    ): Boolean {

        return userVocabulary.contains(word)
    }

    /**
     * Find words that begin with the supplied prefix.
     *
     * Ranking:
     *
     * 1. Frequently used personal words
     * 2. Bundled dictionary words
     * 3. Contractions
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
         * Personal vocabulary gets priority.
         */
        val learnedSuggestions =
            userVocabulary.suggest(
                lower,
                limit
            )

        /*
         * Normal dictionary completions.
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
         * Contraction completions.
         */
        val contractionSuggestions =
            contractions
                .asSequence()
                .filter {
                    it.startsWith(lower) &&
                            it != lower
                }
                .sortedBy {
                    it.length
                }
                .take(limit)
                .toList()

        /*
         * Combine everything and remove duplicates.
         */
        return (
            learnedSuggestions +
                    contractionSuggestions +
                    dictionarySuggestions
            )
            .distinct()
            .take(limit)
    }

    /**
     * Find likely corrections.
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
         * Combine bundled words, contractions,
         * and learned vocabulary.
         */
        val candidates =
            (
                words +
                        contractions +
                        userVocabulary.allWords()
                )
                .asSequence()
                .filter {
                    abs(
                        it.length - lower.length
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

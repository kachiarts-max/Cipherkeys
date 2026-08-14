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
 * This means CipherKeys can start with the bundled dictionary
 * and gradually build a personal dictionary based on the
 * user's typing habits.
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
     *
     * This makes normal completion searches faster.
     */
    private val byFirstLetter:
            Map<Char, List<String>> =
        words.groupBy {
            it.first()
        }

    /**
     * Checks both the built-in dictionary
     * and the user's personal vocabulary.
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
     * Returns suggestions from BOTH:
     *
     * - Built-in English dictionary
     * - User's learned vocabulary
     *
     * User vocabulary gets priority when appropriate.
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
         * Suggestions learned from the user's
         * personal vocabulary.
         */
        val personalSuggestions =
            userVocabulary.suggest(
                normalizedPrefix,
                limit
            )

        /*
         * Built-in dictionary suggestions.
         */
        val builtInSuggestions =
            byFirstLetter[
                normalizedPrefix.firstOrNull()
            ]
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

        /*
         * Combine both sources.
         *
         * Personal vocabulary comes first because
         * it represents words the user actually uses.
         */
        return (
            personalSuggestions +
                    builtInSuggestions
            )
            .distinct()
            .take(limit)
    }

    /**
     * Learn a new word.
     *
     * The personal vocabulary decides whether
     * the word is suitable for learning.
     */
    override fun learnWord(
        word: String
    ) {

        val normalized =
            word.trim()

        if (normalized.isBlank()) {
            return
        }

        /*
         * Only learn words that are not already
         * part of the built-in dictionary.
         *
         * This keeps the personal dictionary
         * focused on the user's own vocabulary.
         */
        if (!words.contains(
                normalized.lowercase()
            )
        ) {

            userVocabulary.learnWord(
                normalized
            )
        }
    }

    /**
     * Suggest likely corrections for a misspelled word.
     *
     * Corrections are currently calculated against
     * both the built-in and learned vocabulary.
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
            words.filter {
                kotlin.math.abs(
                    it.length -
                            normalized.length
                ) <= 2
            }

        /*
         * Learned vocabulary candidates.
         */
        val learnedCandidates =
            userVocabulary
                .allWords()
                .filter {
                    kotlin.math.abs(
                        it.length -
                                normalized.length
                    ) <= 2
                }

        /*
         * Combine both dictionaries.
         */
        val candidates =
            (
                builtInCandidates +
                        learnedCandidates
                )
                .distinct()

        return candidates
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
    }

    /**
     * Load the bundled dictionary from assets.
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
            )

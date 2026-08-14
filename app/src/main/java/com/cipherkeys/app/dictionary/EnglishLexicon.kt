package com.cipherkeys.app.dictionary

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs

/**
 * CipherKeys English dictionary.
 *
 * Combines:
 *
 * 1. Bundled English words
 * 2. Common English contractions
 * 3. Locally learned words
 *
 * Learned words are stored on the device and survive
 * keyboard restarts.
 */
class EnglishLexicon(
    context: Context,
    assetPath: String = "dictionary/common_words.txt"
) : Dictionary {

    private val appContext = context.applicationContext

    private val preferences =
        appContext.getSharedPreferences(
            "cipherkeys_dictionary",
            Context.MODE_PRIVATE
        )

    /**
     * Words included with the application.
     */
    private val bundledWords: Set<String> =
        loadBundledWords(
            appContext,
            assetPath
        )

    /**
     * Common contractions that should always be recognized.
     *
     * This is important because normal English typing contains
     * contractions such as:
     *
     * don't
     * can't
     * won't
     * wouldn't
     * couldn't
     * shouldn't
     * I'm
     * I've
     * I'll
     * we're
     * they're
     */
    private val contractions: Set<String> = setOf(

        "aren't",
        "can't",
        "couldn't",
        "didn't",
        "doesn't",
        "don't",
        "hadn't",
        "hasn't",
        "haven't",
        "he'd",
        "he'll",
        "he's",
        "how'd",
        "how'll",
        "how's",
        "I'd",
        "I'll",
        "I'm",
        "I've",
        "isn't",
        "it'd",
        "it'll",
        "it's",
        "let's",
        "mightn't",
        "mustn't",
        "needn't",
        "shan't",
        "she'd",
        "she'll",
        "she's",
        "shouldn't",
        "that'd",
        "that's",
        "there'd",
        "there'll",
        "there's",
        "they'd",
        "they'll",
        "they're",
        "they've",
        "wasn't",
        "we'd",
        "we'll",
        "we're",
        "we've",
        "weren't",
        "what'd",
        "what's",
        "when's",
        "where'd",
        "where's",
        "who'd",
        "who'll",
        "who's",
        "why'd",
        "why's",
        "won't",
        "wouldn't",
        "you'd",
        "you'll",
        "you're",
        "you've"
    )

    /**
     * Words learned by the user.
     *
     * They are stored as a Set<String> in SharedPreferences.
     */
    private val learnedWords: MutableSet<String> =
        preferences
            .getStringSet(
                "learned_words",
                emptySet()
            )
            ?.map {
                it.lowercase()
            }
            ?.toMutableSet()
            ?: mutableSetOf()

    /**
     * Combined dictionary.
     */
    private val allWords: Set<String>
        get() =
            bundledWords +
                    contractions +
                    learnedWords

    /**
     * Words grouped by their first character.
     *
     * This prevents every suggestion request from scanning
     * the entire dictionary.
     */
    private val byFirstLetter: Map<Char, List<String>>
        get() =
            allWords
                .filter {
                    it.isNotEmpty()
                }
                .groupBy {
                    it.first()
                }

    override fun isValidWord(
        word: String
    ): Boolean {

        val normalized =
            normalize(word)

        if (normalized.isEmpty()) {
            return false
        }

        return allWords.contains(
            normalized
        )
    }

    override fun suggestCompletions(
        prefix: String,
        limit: Int
    ): List<String> {

        if (prefix.isBlank()) {
            return emptyList()
        }

        val normalizedPrefix =
            normalize(prefix)

        if (normalizedPrefix.isEmpty()) {
            return emptyList()
        }

        val candidates =
            byFirstLetter[
                normalizedPrefix.first()
            ]
                ?: return emptyList()

        return candidates
            .asSequence()
            .filter {
                it.startsWith(
                    normalizedPrefix
                )
            }
            .filter {
                it != normalizedPrefix
            }
            .sortedWith(
                compareBy<String> {

                    /*
                     * Learned words get priority.
                     *
                     * This makes words the user repeatedly
                     * types appear more naturally.
                     */
                    if (
                        learnedWords.contains(it)
                    ) {
                        0
                    } else {
                        1
                    }

                }.thenBy {

                    /*
                     * Prefer shorter completions first.
                     */
                    it.length

                }
            )
            .take(limit)
            .toList()
    }

    override fun suggestCorrections(
        word: String,
        limit: Int
    ): List<String> {

        if (word.isBlank()) {
            return emptyList()
        }

        val normalized =
            normalize(word)

        if (isValidWord(normalized)) {
            return emptyList()
        }

        return allWords
            .asSequence()
            .filter {
                abs(
                    it.length -
                            normalized.length
                ) <= 2
            }
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
            .sortedWith(
                compareBy<Pair<String, Int>> {

                    it.second

                }.thenBy {

                    /*
                     * Learned words are preferred
                     * when correction distance is equal.
                     */
                    if (
                        learnedWords.contains(
                            it.first
                        )
                    ) {
                        0
                    } else {
                        1
                    }
                }
            )
            .map {
                it.first
            }
            .distinct()
            .take(limit)
            .toList()
    }

    /**
     * Teach CipherKeys a new word.
     *
     * This is called when the user repeatedly types a word
     * that wasn't originally in the bundled dictionary.
     */
    override fun learnWord(
        word: String
    ) {

        val normalized =
            normalize(word)

        /*
         * Don't learn extremely short fragments.
         */
        if (
            normalized.length < 2
        ) {
            return
        }

        /*
         * Don't store something already present
         * in the bundled dictionary.
         */
        if (
            bundledWords.contains(
                normalized
            ) ||
            contractions.contains(
                normalized
            )
        ) {
            return
        }

        /*
         * Only learn words containing letters,
         * apostrophes or hyphens.
         */
        if (
            !normalized.all {
                it.isLetter() ||
                        it == '\'' ||
                        it == '-'
            }
        ) {
            return
        }

        learnedWords.add(
            normalized
        )

        preferences
            .edit()
            .putStringSet(
                "learned_words",
                learnedWords
            )
            .apply()
    }

    /**
     * Normalizes dictionary words without destroying
     * apostrophes used by contractions.
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
     * Loads the bundled dictionary.
     */
    private fun loadBundledWords(
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
                                normalize(it)
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
                        dp[i - 1][j - 1] + cost
                    )
            }
        }

        return dp[
            a.length
        ][
            b.length
        ]
    }
}

package com.cipherkeys.app.dictionary

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs

/**
 * CipherKeys English dictionary.
 *
 * Combines:
 * 1. Bundled English words
 * 2. Common contractions
 * 3. User-learned words
 *
 * Everything is stored locally on the device.
 * Nothing is uploaded to a server.
 */
class EnglishLexicon(
    context: Context,
    assetPath: String = "dictionary/common_words.txt"
) : Dictionary {

    private val preferences =
        context.getSharedPreferences("cipherkeys_vocabulary", Context.MODE_PRIVATE)

    private val words: Set<String> = loadWords(context, assetPath)

    /**
     * Common English contractions.
     *
     * These are included separately because a compact dictionary file may not
     * contain them.
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
     * User vocabulary with usage counts.
     *
     * Example:
     * hello=5
     * cipherkeys=12
     * bro=7
     */
    private val learnedWords: MutableMap<String, Int> = loadLearnedWords()

    private fun loadWords(
        context: Context,
        assetPath: String
    ): Set<String> {
        return try {
            context.assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream)).useLines { lines ->
                    lines
                        .map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                }
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun loadLearnedWords(): MutableMap<String, Int> {
        val saved = preferences.getStringSet(
            "learned_words",
            emptySet()
        ) ?: emptySet()

        val result = mutableMapOf<String, Int>()

        saved.forEach { entry ->
            val parts = entry.split("|", limit = 2)

            if (parts.size == 2) {
                val word = parts[0]
                val count = parts[1].toIntOrNull() ?: 1

                if (word.isNotBlank()) {
                    result[word] = count
                }
            }
        }

        return result
    }

    private fun saveLearnedWords() {
        val encoded = learnedWords.map { (word, count) ->
            "$word|$count"
        }.toSet()

        preferences.edit()
            .putStringSet("learned_words", encoded)
            .apply()
    }

    override fun isValidWord(word: String): Boolean {
        if (word.isBlank()) return false

        val normalized = word.lowercase()

        return normalized in words ||
                normalized in contractions ||
                learnedWords.containsKey(normalized)
    }

    override fun learnWord(word: String) {
        val normalized = word
            .trim()
            .lowercase()

        // Ignore very short/non-word entries.
        if (normalized.length < 2) return

        // Only learn words containing letters.
        if (!normalized.any { it.isLetter() }) return

        val newCount = (learnedWords[normalized] ?: 0) + 1

        learnedWords[normalized] = newCount

        saveLearnedWords()
    }

    override fun suggestCompletions(
        prefix: String,
        limit: Int
    ): List<String> {

        if (prefix.isBlank()) return emptyList()

        val lowerPrefix = prefix.lowercase()

        val candidates = mutableMapOf<String, Int>()

        // Bundled dictionary words.
        words
            .asSequence()
            .filter { it.startsWith(lowerPrefix) && it != lowerPrefix }
            .forEach {
                candidates[it] = 0
            }

        // Contractions.
        contractions
            .asSequence()
            .filter { it.startsWith(lowerPrefix) && it != lowerPrefix }
            .forEach {
                candidates[it] = 0
            }

        // Learned words get their actual usage count.
        learnedWords
            .filterKeys {
                it.startsWith(lowerPrefix) && it != lowerPrefix
            }
            .forEach { (word, count) ->
                candidates[word] = count
            }

        return candidates
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key.length }
            )
            .take(limit)
            .map { it.key }
    }

    override fun suggestCorrections(
        word: String,
        limit: Int
    ): List<String> {

        if (word.isBlank() || isValidWord(word)) {
            return emptyList()
        }

        val lowerWord = word.lowercase()

        val allCandidates =
            words + contractions + learnedWords.keys

        return allCandidates
            .asSequence()
            .filter {
                abs(it.length - lowerWord.length) <= 2
            }
            .map {
                it to levenshteinDistance(lowerWord, it)
            }
            .filter { (_, distance) ->
                distance <= 2
            }
            .sortedWith(
                compareBy<Pair<String, Int>> { it.second }
                    .thenByDescending {
                        learnedWords[it.first] ?: 0
                    }
            )
            .take(limit)
            .map { it.first }
            .toList()
    }

    /**
     * Standard Levenshtein edit distance.
     */
    private fun levenshteinDistance(
        a: String,
        b: String
    ): Int {

        val dp = Array(a.length + 1) {
            IntArray(b.length + 1)
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
                    if (a[i - 1] == b[j - 1]) 0 else 1

                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[a.length][b.length]
    }
}

package com.cipherkeys.app.dictionary

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads the bundled word list from assets/dictionary/common_words.txt once, entirely
 * on-device (no network call, ever), and answers validation/completion/correction
 * queries against it in memory.
 *
 * The list is intentionally compact for now (a curated set of the most common English
 * words) to keep the app lightweight; it's swappable for a larger list later without
 * changing anything that calls into [Dictionary].
 */
class EnglishLexicon(context: Context, assetPath: String = "dictionary/common_words.txt") : Dictionary {

    private val words: Set<String> = loadWords(context, assetPath)

    /** Words grouped by first letter, so completion lookups don't scan the whole set. */
    private val byFirstLetter: Map<Char, List<String>> = words.groupBy { it.first() }

    private fun loadWords(context: Context, assetPath: String): Set<String> {
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

    override fun isValidWord(word: String): Boolean {
        if (word.isEmpty()) return false
        return words.contains(word.lowercase())
    }

    override fun suggestCompletions(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val lower = prefix.lowercase()
        val candidates = byFirstLetter[lower.first()] ?: return emptyList()
        return candidates
            .asSequence()
            .filter { it.startsWith(lower) && it != lower }
            .sortedBy { it.length }
            .take(limit)
            .toList()
    }

    override fun suggestCorrections(word: String, limit: Int): List<String> {
        if (word.isEmpty() || isValidWord(word)) return emptyList()
        val lower = word.lowercase()
        // Only compare against words of similar length - keeps this cheap even though
        // it's a full scan, and avoids nonsense corrections like "a" -> "abandonment".
        val candidates = words.filter { kotlin.math.abs(it.length - lower.length) <= 2 }
        return candidates
            .map { it to levenshteinDistance(lower, it) }
            .filter { (_, distance) -> distance <= 2 }
            .sortedBy { (_, distance) -> distance }
            .map { (candidate, _) -> candidate }
            .take(limit)
    }

    /** Standard edit-distance calculation: insert/delete/substitute cost 1 each. */
    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
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

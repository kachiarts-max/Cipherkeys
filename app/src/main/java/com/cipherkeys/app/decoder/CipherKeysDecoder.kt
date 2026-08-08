package com.cipherkeys.app.decoder

import com.cipherkeys.app.data.LeetMappings

/**
 * Decodes text produced by any of CipherKeys' encoding modes (CLASSIC_LEET, ELITE,
 * HACKER, ULTRA) back to plain English.
 *
 * Architecture: all four encoding tables are merged into one reverse lookup
 * (replacement token -> source letter). Because tokens vary in length (e.g. "1" vs
 * "|-|" vs "(_)"), decoding is done via **longest-match-first tokenizing**: at each
 * position in the input, the decoder tries the longest known replacement token first
 * before falling back to shorter ones, so multi-character sequences like "|-|" decode
 * as a single "h" rather than three separate unmapped symbols.
 *
 * Ambiguity note: some tokens map to more than one letter (e.g. "1" is used for both
 * "i" and "l" across the encoding tables). LeetMappings orders 'l' before 'i' for those
 * shared entries specifically so the last-wins merge below resolves "1" -> 'i' (the far
 * more common source letter in practice, and what CipherKeys' own DECODE example
 * requires: "7h15 15 4w350m3" -> "this is awesome" has no 'l' in it at all). This means
 * decoding is "best effort" and not guaranteed to perfectly invert every encoded string
 * that used the ambiguous letter - a real limitation of a many-to-one visual cipher.
 */
class CipherKeysDecoder(
    customMappings: Map<Char, List<String>> = emptyMap()
) : Decoder {

    /** token (e.g. "4", "|-|") -> source letter, longest tokens first for matching. */
    private val reverseLookup: List<Pair<String, Char>>

    init {
        // Later tables in this list win ties for the same token, giving us a
        // deterministic, documented preference order (classic < elite < hacker < ultra).
        val merged = LinkedHashMap<String, Char>()
        listOf(LeetMappings.classic, LeetMappings.elite, LeetMappings.hacker, LeetMappings.ultra, customMappings)
            .forEach { table ->
                table.forEach { (letter, tokens) ->
                    tokens.forEach { token -> merged[token.lowercase()] = letter }
                }
            }
        reverseLookup = merged.entries
            .map { it.key to it.value }
            .sortedByDescending { it.first.length }
    }

    override fun decode(input: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < input.length) {
            val match = findLongestMatch(input, i)
            if (match != null) {
                val (token, letter) = match
                result.append(letter)
                i += token.length
            } else {
                result.append(input[i])
                i += 1
            }
        }
        return result.toString()
    }

    private fun findLongestMatch(input: String, position: Int): Pair<String, Char>? {
        for ((token, letter) in reverseLookup) {
            if (token.isEmpty()) continue
            val end = position + token.length
            if (end <= input.length && input.regionMatches(position, token, 0, token.length, ignoreCase = true)) {
                return token to letter
            }
        }
        return null
    }
}

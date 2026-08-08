package com.cipherkeys.app.encoder

import com.cipherkeys.app.data.LeetMappings

/**
 * "Access Granted" -> "4CC355 GR4N73D"
 *
 * More aggressive than CLASSIC_LEET: uses multi-character symbol substitutions
 * (e.g. h -> "|-|") and forces any letter that ISN'T substituted to uppercase,
 * matching the spec's example output.
 */
class HackerEncoder(
    customMappings: Map<Char, List<String>> = emptyMap()
) : TableEncoder(LeetMappings.hacker + customMappings) {

    override fun encode(input: String): String {
        val builder = StringBuilder(input.length)
        for (ch in input) {
            val lower = ch.lowercaseChar()
            val replacements = table[lower]
            if (replacements.isNullOrEmpty()) {
                builder.append(if (ch.isLetter()) ch.uppercaseChar() else ch)
            } else {
                builder.append(pickReplacement(replacements, ch))
            }
        }
        return builder.toString()
    }
}

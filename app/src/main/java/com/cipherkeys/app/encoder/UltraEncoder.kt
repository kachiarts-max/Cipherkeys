package com.cipherkeys.app.encoder

import com.cipherkeys.app.data.LeetMappings
import kotlin.random.Random

/**
 * "HELLO" -> randomly one of "H3LL0", "#3LL0", "|-|3LL0", "H£LLØ", etc.
 *
 * Picks a random valid replacement per matched character on every call, so the
 * same input can produce different (but still legible) output across calls -
 * as required by spec. The random source is injectable for deterministic tests.
 */
class UltraEncoder(
    customMappings: Map<Char, List<String>> = emptyMap(),
    private val random: Random = Random.Default
) : TableEncoder(LeetMappings.ultra + customMappings) {

    override fun pickReplacement(options: List<String>, original: Char): String {
        return options[random.nextInt(options.size)]
    }
}

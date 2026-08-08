package com.cipherkeys.app.encoder

/**
 * Shared implementation for encoders driven by a static lowercase-char -> replacement
 * table (CLASSIC_LEET, ELITE, HACKER). ULTRA overrides selection since it must pick
 * randomly per-call rather than always using index 0.
 */
abstract class TableEncoder(
    protected open val table: Map<Char, List<String>>
) : Encoder {

    override fun encode(input: String): String {
        val builder = StringBuilder(input.length)
        for (ch in input) {
            val lower = ch.lowercaseChar()
            val replacements = table[lower]
            if (replacements == null || replacements.isEmpty()) {
                builder.append(ch)
            } else {
                builder.append(pickReplacement(replacements, ch))
            }
        }
        return builder.toString()
    }

    /**
     * Chooses which replacement string to use for a matched character.
     * Default: always the first (deterministic) option. Subclasses (ULTRA) override
     * this to randomize.
     */
    protected open fun pickReplacement(options: List<String>, original: Char): String {
        return options.first()
    }
}

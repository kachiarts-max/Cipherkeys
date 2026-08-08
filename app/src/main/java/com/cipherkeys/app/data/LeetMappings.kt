package com.cipherkeys.app.data

/**
 * Central, configurable character-substitution tables. Encoders read from these maps
 * instead of hard-coding substitutions, so users can override entries via Settings
 * (custom mappings are merged on top of the defaults at load time).
 *
 * Each map is keyed by lowercase source character -> one or more possible replacement
 * strings. A list of alternatives (rather than a single string) is what lets ULTRA mode
 * pick randomly among valid substitutions for the same letter.
 */
object LeetMappings {

    /**
     * a,b,e,g,l,i,o,s,t,z -> digits, as specified for CLASSIC_LEET.
     * Note: 'l' is listed before 'i' deliberately - both map to the same token "1",
     * and the decoder's merge is last-wins, so this ordering makes "1" decode back
     * to 'i' (the far more common source letter for "1" in practice).
     */
    val classic: Map<Char, List<String>> = mapOf(
        'a' to listOf("4"),
        'b' to listOf("8"),
        'e' to listOf("3"),
        'g' to listOf("9"),
        'l' to listOf("1"),
        'i' to listOf("1"),
        'o' to listOf("0"),
        's' to listOf("5"),
        't' to listOf("7"),
        'z' to listOf("2")
    )

    /** Superset of classic, with a couple of extra letters substituted for ELITE. */
    val elite: Map<Char, List<String>> = classic + mapOf(
        'c' to listOf("("),
        'n' to listOf("^")
    )

    /**
     * Aggressive symbol/letter mix for HACKER mode. Includes multi-character
     * replacements (e.g. "|-|" for h) and case flipping is applied separately
     * by the encoder.
     */
    val hacker: Map<Char, List<String>> = mapOf(
        'a' to listOf("4"),
        'b' to listOf("8"),
        'c' to listOf("("),
        'd' to listOf("|)"),
        'e' to listOf("3"),
        'f' to listOf("|="),
        'g' to listOf("9"),
        'h' to listOf("|-|"),
        'l' to listOf("1"),
        'i' to listOf("1"),
        'k' to listOf("|<"),
        'n' to listOf("^"),
        'o' to listOf("0"),
        's' to listOf("5"),
        't' to listOf("7"),
        'u' to listOf("(_)"),
        'v' to listOf("\\/"),
        'w' to listOf("\\/\\/"),
        'x' to listOf("><"),
        'z' to listOf("2")
    )

    /**
     * Wide alternative set for ULTRA mode: each letter maps to several visually
     * distinct options so repeated runs produce different (but still legible)
     * output, as required by spec ("HELLO" -> "H3LL0" / "#3LL0" / "|-|3LL0" / "H£LLØ").
     */
    val ultra: Map<Char, List<String>> = mapOf(
        'a' to listOf("4", "@", "/\\"),
        'b' to listOf("8", "ß"),
        'c' to listOf("(", "©"),
        'd' to listOf("|)", "Ð"),
        'e' to listOf("3", "€", "£"),
        'f' to listOf("|=", "ƒ"),
        'g' to listOf("9", "6"),
        'h' to listOf("#", "|-|"),
        'l' to listOf("1", "|_"),
        'i' to listOf("1", "!", "¡"),
        'k' to listOf("|<", "K"),
        'n' to listOf("^", "ñ"),
        'o' to listOf("0", "Ø", "ø"),
        's' to listOf("5", "$", "§"),
        't' to listOf("7", "+"),
        'u' to listOf("(_)", "ü"),
        'v' to listOf("\\/"),
        'w' to listOf("\\/\\/", "vv"),
        'x' to listOf("><", "×"),
        'z' to listOf("2", "z")
    )
}

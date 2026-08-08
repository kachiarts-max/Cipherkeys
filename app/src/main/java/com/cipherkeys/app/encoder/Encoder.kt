package com.cipherkeys.app.encoder

/**
 * Transforms plain input text into a stylized/encoded representation.
 * Implementations must not mutate whitespace or characters they don't have a
 * mapping for (they pass those through unchanged) so words stay recognizable
 * and, where possible, decodable.
 */
interface Encoder {
    fun encode(input: String): String
}

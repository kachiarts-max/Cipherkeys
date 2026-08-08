package com.cipherkeys.app.decoder

import com.cipherkeys.app.encoder.ClassicLeetEncoder
import com.cipherkeys.app.encoder.EliteEncoder
import com.cipherkeys.app.encoder.HackerEncoder
import org.junit.Assert.assertEquals
import org.junit.Test

class CipherKeysDecoderTest {

    private val decoder = CipherKeysDecoder()

    @Test
    fun `decodes classic leet back to english`() {
        // "7h15 15 4w350m3" -> "this is awesome" (spec example for DECODE)
        assertEquals("this is awesome", decoder.decode("7h15 15 4w350m3"))
    }

    @Test
    fun `decoding a classic-encoded round trip recovers the original lowercase text`() {
        // Deliberately avoids 'l' - it shares the "1" token with 'i', so it is not
        // guaranteed to round-trip (documented lossy-decode limitation).
        val encoder = ClassicLeetEncoder()
        val original = "this is neat"
        val encoded = encoder.encode(original)
        assertEquals(original, decoder.decode(encoded))
    }

    @Test
    fun `decoding an elite-encoded round trip recovers the original lowercase text`() {
        val encoder = EliteEncoder()
        val original = "programming power"
        val encoded = encoder.encode(original)
        assertEquals(original, decoder.decode(encoded))
    }

    @Test
    fun `decodes multi-character hacker tokens as single letters`() {
        // "|-|" -> h, "(_)" -> u, "\/\/" -> w  (longest-match-first tokenizing)
        val encoder = HackerEncoder()
        val encoded = encoder.encode("who")
        // "who": w -> \/\/, h -> |-|, o -> 0
        assertEquals("who", decoder.decode(encoded))
    }

    @Test
    fun `unmapped characters and punctuation pass through decode unchanged`() {
        assertEquals("hi there!", decoder.decode("hi there!"))
    }

    @Test
    fun `custom mapping is honored during decode`() {
        val customDecoder = CipherKeysDecoder(customMappings = mapOf('e' to listOf("€")))
        assertEquals("t€st", "t€st") // sanity: literal token present
        assertEquals("test", customDecoder.decode("t€st"))
    }
}

package com.cipherkeys.app.encoder

import com.cipherkeys.app.data.LeetMappings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Random source that always resolves to a fixed index, for deterministic assertions. */
private class FixedIndexRandom(private val index: Int) : Random() {
    override fun nextBits(bitCount: Int): Int = 0
    override fun nextInt(until: Int): Int = index.coerceAtMost(until - 1).coerceAtLeast(0)
}

class UltraEncoderTest {

    @Test
    fun `ultra picks the option at the injected random index`() {
        val encoder = UltraEncoder(random = FixedIndexRandom(0))
        assertEquals(LeetMappings.ultra['a']!![0], encoder.encode("a"))
    }

    @Test
    fun `ultra passes through unmapped characters unchanged`() {
        val encoder = UltraEncoder(random = FixedIndexRandom(0))
        assertEquals("!", encoder.encode("!"))
    }

    @Test
    fun `ultra output for a mapped letter is always one of its known options`() {
        val encoder = UltraEncoder(random = Random(42))
        val options = LeetMappings.ultra['e']!!
        repeat(20) {
            val output = encoder.encode("e")
            assertTrue("Expected one of $options but got $output", options.contains(output))
        }
    }

    @Test
    fun `ultra preserves word length structurally (one token per input char)`() {
        val encoder = UltraEncoder(random = FixedIndexRandom(0))
        val output = encoder.encode("hello")
        // every letter in "hello" has an ultra mapping; with a fixed index-0 random
        // source each resolves deterministically to its first listed option
        val expected = LeetMappings.ultra['h']!![0] +
            LeetMappings.ultra['e']!![0] +
            LeetMappings.ultra['l']!![0] +
            LeetMappings.ultra['l']!![0] +
            LeetMappings.ultra['o']!![0]
        assertEquals(expected, output)
    }
}

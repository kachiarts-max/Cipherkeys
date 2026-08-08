package com.cipherkeys.app.encoder

import org.junit.Assert.assertEquals
import org.junit.Test

class HackerEncoderTest {

    private val encoder = HackerEncoder()

    @Test
    fun `mapped letters use symbol substitutions`() {
        // c -> ( , a -> 4, t -> 7
        assertEquals("(47", encoder.encode("cat"))
    }

    @Test
    fun `unmapped letters are forced uppercase`() {
        // r, u, n: only u has a mapping ( "(_)" ); r and n... n IS mapped (^) so use a
        // word with a genuinely unmapped letter like 'r'.
        assertEquals("R(_)^", encoder.encode("run"))
    }

    @Test
    fun `dog encodes with multi-character symbol tokens`() {
        // d -> |), o -> 0, g -> 9
        assertEquals("|)09", encoder.encode("dog"))
    }

    @Test
    fun `punctuation and spaces are preserved`() {
        // h -> |-|, i -> 1, ! untouched, space untouched, p unmapped -> P, a -> 4, l -> 1
        assertEquals("|-|1! P41", encoder.encode("hi! pal"))
    }
}

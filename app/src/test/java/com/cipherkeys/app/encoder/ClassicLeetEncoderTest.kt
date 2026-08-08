package com.cipherkeys.app.encoder

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassicLeetEncoderTest {

    private val encoder = ClassicLeetEncoder()

    @Test
    fun `hello encodes using the full classic table (e,l,l,o all substitute)`() {
        // With l -> 1 in the table, both l's substitute: h-e-l-l-o -> h-3-1-1-0
        assertEquals("h3110", encoder.encode("hello"))
    }

    @Test
    fun `Hello World preserves case and unmapped letters`() {
        assertEquals("H3110 W0r1d", encoder.encode("Hello World"))
    }

    @Test
    fun `this is cool produces expected substitutions`() {
        assertEquals("7h15 15 c001", encoder.encode("this is cool"))
    }

    @Test
    fun `unmapped characters pass through unchanged`() {
        assertEquals("hxy!", encoder.encode("hxy!"))
    }

    @Test
    fun `custom mapping overrides default`() {
        // Only 'e' is overridden; 'l' and 'o' still use the built-in classic mapping (1, 0).
        val custom = ClassicLeetEncoder(customMappings = mapOf('e' to listOf("€")))
        assertEquals("h€110", custom.encode("hello"))
    }
}

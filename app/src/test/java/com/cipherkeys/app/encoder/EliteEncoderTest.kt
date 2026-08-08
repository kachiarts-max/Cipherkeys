package com.cipherkeys.app.encoder

import org.junit.Assert.assertEquals
import org.junit.Test

class EliteEncoderTest {

    private val encoder = EliteEncoder()

    @Test
    fun `knowledge is power encodes with elite substitutions`() {
        // K,w,d,p,r have no mapping and pass through unchanged (case preserved);
        // n->^, o->0, l->1, e->3, g->9, i->1, s->5
        assertEquals("K^0w13d93 15 p0w3r", encoder.encode("Knowledge is power"))
    }

    @Test
    fun `elite includes all classic substitutions`() {
        assertEquals("h3110", encoder.encode("hello"))
    }

    @Test
    fun `unmapped punctuation passes through`() {
        assertEquals("7357!", encoder.encode("test!"))
    }
}

package com.cipherkeys.app.encoder

import com.cipherkeys.app.data.LeetMappings

/**
 * "Hello World" -> "H3ll0 W0r1d"
 */
class ClassicLeetEncoder(
    customMappings: Map<Char, List<String>> = emptyMap()
) : TableEncoder(LeetMappings.classic + customMappings)

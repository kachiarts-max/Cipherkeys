package com.cipherkeys.app.encoder

import com.cipherkeys.app.data.LeetMappings

/**
 * "Knowledge is power" -> "Kn0wl3dg3 15 p0w3r"
 */
class EliteEncoder(
    customMappings: Map<Char, List<String>> = emptyMap()
) : TableEncoder(LeetMappings.elite + customMappings)

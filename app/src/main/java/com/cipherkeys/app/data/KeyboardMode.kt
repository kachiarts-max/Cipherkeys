package com.cipherkeys.app.data

/**
 * All typing modes supported by CipherKeys. Each mode (other than NORMAL and DECODE)
 * maps to an [com.cipherkeys.app.encoder.Encoder] implementation.
 */
enum class KeyboardMode(val label: String) {
    NORMAL("NORMAL"),
    CLASSIC_LEET("LEET"),
    ELITE("ELITE"),
    HACKER("HACKER"),
    ULTRA("ULTRA"),
    DECODE("DECODE");

    companion object {
        fun default(): KeyboardMode = NORMAL
    }
}

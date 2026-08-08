package com.cipherkeys.app.decoder

/** Reverses CipherKeys-encoded text back to plain English where possible. */
interface Decoder {
    fun decode(input: String): String
}

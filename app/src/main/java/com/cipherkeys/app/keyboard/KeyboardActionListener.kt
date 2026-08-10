package com.cipherkeys.app.keyboard

import com.cipherkeys.app.data.KeyboardMode

/** Callback contract the keyboard view uses to report user input to the IME. */
interface KeyboardActionListener {
    fun onCharKey(char: Char)
    fun onSpace()
    fun onBackspace()
    fun onEnter()
    fun onShiftToggle()
    fun onModeSelected(mode: KeyboardMode)
    /** Fired when the user taps a word in the suggestion strip. */
    fun onSuggestionSelected(word: String)
    /** Fired when the user taps an emoji in the emoji panel. */
    fun onEmojiSelected(emoji: String)
}

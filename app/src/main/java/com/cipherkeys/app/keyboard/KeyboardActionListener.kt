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
}

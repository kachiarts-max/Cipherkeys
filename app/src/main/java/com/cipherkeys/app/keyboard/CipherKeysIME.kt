package com.cipherkeys.app.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.inputmethod.EditorInfo
import com.cipherkeys.app.data.KeyboardMode
import com.cipherkeys.app.data.KeyboardSettings
import com.cipherkeys.app.data.KeyboardTheme
import com.cipherkeys.app.decoder.CipherKeysDecoder
import com.cipherkeys.app.encoder.ClassicLeetEncoder
import com.cipherkeys.app.encoder.Encoder
import com.cipherkeys.app.encoder.EliteEncoder
import com.cipherkeys.app.encoder.HackerEncoder
import com.cipherkeys.app.encoder.UltraEncoder
import com.cipherkeys.app.data.SettingsRepository
import com.cipherkeys.app.dictionary.Dictionary
import com.cipherkeys.app.dictionary.EnglishLexicon
import com.cipherkeys.app.emoji.RecentEmojiStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The real Android keyboard. Once installed and enabled in
 * Settings -> System -> Languages & input -> On-screen keyboard, this service is what
 * the system instantiates and shows whenever a text field is focused.
 *
 * Responsibilities kept deliberately narrow here: own the InputConnection lifecycle,
 * translate raw key events from [CipherKeysKeyboardView] into committed text using the
 * encoder/decoder for the active [KeyboardMode], and react to live settings changes.
 * All cipher logic lives in the encoder/decoder package, not in this class.
 */
class CipherKeysIME : InputMethodService(), KeyboardActionListener {

    private lateinit var keyboardView: CipherKeysKeyboardView
    private lateinit var settingsRepository: SettingsRepository

    private val serviceJob = Job()
    // Main dispatcher: settingsFlow.collect updates the keyboard View directly
    // (theme/height), so it must run on the UI thread, not a background pool.
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)

    private var currentSettings: KeyboardSettings = KeyboardSettings()
    private var currentMode: KeyboardMode = KeyboardMode.default()
    private var shiftEnabled: Boolean = false

    private lateinit var classicEncoder: Encoder
    private lateinit var eliteEncoder: Encoder
    private lateinit var hackerEncoder: Encoder
    private lateinit var ultraEncoder: Encoder
    private lateinit var decoder: CipherKeysDecoder
    private lateinit var dictionary: Dictionary
    private lateinit var recentEmojiStore: RecentEmojiStore
    // Cached decode of the current background image (re-decoded only when the saved
    // file path actually changes, not on every settings emission).
    private var backgroundBitmap: Bitmap? = null

    // Tracks the word currently being typed, in plain (unencoded) letters, plus how
    // many field-characters each of those raw letters turned into once encoded (a
    // single raw letter can become several committed characters - e.g. HACKER mode's
    // "h" -> "|-|"). This lets suggestions/corrections operate on real English while
    // still being able to correctly delete/replace exactly what was committed.
    private val rawWordBuffer = StringBuilder()
    private val encodedLengthsPerChar = mutableListOf<Int>()

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        dictionary = EnglishLexicon(applicationContext)
        recentEmojiStore = RecentEmojiStore(applicationContext)
        rebuildEncoders(emptyMap())
        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                val mappingsChanged = settings.customMappings != currentSettings.customMappings
                val backgroundPathChanged = settings.backgroundImagePath != currentSettings.backgroundImagePath
                currentSettings = settings
                if (mappingsChanged) rebuildEncoders(settings.customMappings)
                if (backgroundPathChanged) {
                    backgroundBitmap = settings.backgroundImagePath?.let { decodeBackgroundBitmap(it) }
                }
                if (::keyboardView.isInitialized) {
                    keyboardView.applyThemeAndHeight(settings.theme, settings.keyboardHeightScale, settings.customColors)
                    val bitmapToShow = if (settings.useImageBackground) backgroundBitmap else null
                    keyboardView.setBackgroundImage(bitmapToShow, settings.backgroundOverlayAlpha)
                }
            }
        }
        serviceScope.launch {
            recentEmojiStore.recentsFlow.collect { recents ->
                if (::keyboardView.isInitialized) keyboardView.setRecentEmoji(recents)
            }
        }
    }

    private fun rebuildEncoders(customMappings: Map<Char, List<String>>) {
        classicEncoder = ClassicLeetEncoder(customMappings)
        eliteEncoder = EliteEncoder(customMappings)
        hackerEncoder = HackerEncoder(customMappings)
        ultraEncoder = UltraEncoder(customMappings)
        decoder = CipherKeysDecoder(customMappings)
    }

    /**
     * Decodes the app's own private copy of the background image (see SettingsScreen's
     * picker flow - the file always lives in app-private storage, never a raw picker
     * URI, so this never needs a permission check). Downsampled aggressively since it's
     * only ever shown at keyboard size, keeping this fast and memory-safe.
     */
    private suspend fun decodeBackgroundBitmap(path: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            null
        }
    }

    override fun onCreateInputView(): View {
        val view = CipherKeysKeyboardView(this)
        view.listener = this
        keyboardView = view
        keyboardView.applyThemeAndHeight(currentSettings.theme, currentSettings.keyboardHeightScale, currentSettings.customColors)
        keyboardView.setBackgroundImage(
            if (currentSettings.useImageBackground) backgroundBitmap else null,
            currentSettings.backgroundOverlayAlpha
        )
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentMode = currentSettings.defaultMode
        shiftEnabled = false
        keyboardView.setMode(currentMode)
        keyboardView.setShiftState(false)
        finalizeWord()

        // Auto-decode: if enabled, decode whatever text already sits in the field
        // as soon as the keyboard attaches to it.
        if (currentSettings.autoDecodeEnabled) {
            decodeExistingFieldText()
        }
    }

    // ---------- KeyboardActionListener ----------

    override fun onCharKey(char: Char) {
        val ic = currentInputConnection ?: return
        val effectiveChar = if (shiftEnabled) char.uppercaseChar() else char

        val output = if (currentSettings.autoEncodeEnabled) {
            when (currentMode) {
                KeyboardMode.NORMAL, KeyboardMode.DECODE -> effectiveChar.toString()
                KeyboardMode.CLASSIC_LEET -> classicEncoder.encode(effectiveChar.toString())
                KeyboardMode.ELITE -> eliteEncoder.encode(effectiveChar.toString())
                KeyboardMode.HACKER -> hackerEncoder.encode(effectiveChar.toString())
                KeyboardMode.ULTRA -> ultraEncoder.encode(effectiveChar.toString())
            }
        } else {
            effectiveChar.toString()
        }

        ic.commitText(output, 1)
        performFeedback()

        if (effectiveChar.isLetter()) {
            rawWordBuffer.append(effectiveChar)
            encodedLengthsPerChar.add(output.length)
            updateSuggestions()
        } else {
            // Punctuation typed via a char key (,.!?) ends the current word.
            finalizeWord()
        }

        if (shiftEnabled) {
            shiftEnabled = false
            keyboardView.setShiftState(false)
        }
    }

    override fun onSpace() {
        maybeAutocorrectBeforeBoundary()
        currentInputConnection?.commitText(" ", 1)
        performFeedback()
        finalizeWord()
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else if (rawWordBuffer.isNotEmpty()) {
            // Remove the whole encoded token for the last raw letter (which may be
            // several field-characters in HACKER/ULTRA mode), not just one character,
            // so raw/encoded stay in sync and backspace doesn't need repeated taps to
            // undo a single logical letter.
            val lastEncodedLength = encodedLengthsPerChar.removeAt(encodedLengthsPerChar.lastIndex)
            rawWordBuffer.deleteCharAt(rawWordBuffer.lastIndex)
            ic.deleteSurroundingText(lastEncodedLength, 0)
            updateSuggestions()
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        performFeedback()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        maybeAutocorrectBeforeBoundary()
        val editorInfo = currentInputEditorInfo
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action != null && action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            editorInfo.imeOptions.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
        ) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
        performFeedback()
        finalizeWord()
    }

    override fun onShiftToggle() {
        shiftEnabled = !shiftEnabled
    }

    override fun onModeSelected(mode: KeyboardMode) {
        currentMode = mode
        finalizeWord()
        if (mode == KeyboardMode.DECODE) {
            decodeExistingFieldText()
        }
    }

    override fun onSuggestionSelected(word: String) {
        val ic = currentInputConnection ?: return
        replaceCurrentWord(ic, word)
        ic.commitText(" ", 1)
        finalizeWord()
        performFeedback()
    }

    /**
     * Emoji insertion deliberately bypasses the LEET/ELITE/HACKER/ULTRA encoders -
     * cipher-encoding an emoji has no meaning, so it's committed as-is regardless of
     * the active mode. Ends any in-progress word (an emoji is a word boundary) and
     * records it as a recent for next time the panel opens.
     */
    override fun onEmojiSelected(emoji: String) {
        val ic = currentInputConnection ?: return
        finalizeWord()
        ic.commitText(emoji, 1)
        performFeedback()
        serviceScope.launch { recentEmojiStore.addRecent(emoji) }
    }

    // ---------- Word-boundary / suggestion helpers ----------

    private fun updateSuggestions() {
        if (!::keyboardView.isInitialized) return
        val prefix = rawWordBuffer.toString()
        val suggestions = if (prefix.isEmpty()) emptyList() else dictionary.suggestCompletions(prefix)
        keyboardView.setSuggestions(suggestions)
    }

    /** Clears word-tracking state at a boundary (space, punctuation, enter, mode switch). */
    private fun finalizeWord() {
        rawWordBuffer.clear()
        encodedLengthsPerChar.clear()
        if (::keyboardView.isInitialized) keyboardView.setSuggestions(emptyList())
    }

    /**
     * If autocorrect is enabled and the just-typed word looks like a typo with a single
     * confident fix, silently swaps it in before the word boundary (space/enter) is
     * committed. Only fires in NORMAL/DECODE - correcting the *encoded* text in cipher
     * modes would mean spell-checking symbols, not English, which isn't meaningful.
     */
    private fun maybeAutocorrectBeforeBoundary() {
        if (!currentSettings.autocorrectEnabled) return
        if (currentMode != KeyboardMode.NORMAL && currentMode != KeyboardMode.DECODE) return
        val word = rawWordBuffer.toString()
        if (word.length < 3 || dictionary.isValidWord(word)) return
        val correction = dictionary.suggestCorrections(word, limit = 1).firstOrNull() ?: return
        val ic = currentInputConnection ?: return
        replaceCurrentWord(ic, correction)
    }

    /** Deletes exactly what's been committed for the current word and inserts [word]. */
    private fun replaceCurrentWord(ic: android.view.inputmethod.InputConnection, word: String) {
        val committedLength = encodedLengthsPerChar.sum()
        if (committedLength > 0) ic.deleteSurroundingText(committedLength, 0)

        val toInsert = if (currentSettings.autoEncodeEnabled) {
            when (currentMode) {
                KeyboardMode.NORMAL, KeyboardMode.DECODE -> word
                KeyboardMode.CLASSIC_LEET -> classicEncoder.encode(word)
                KeyboardMode.ELITE -> eliteEncoder.encode(word)
                KeyboardMode.HACKER -> hackerEncoder.encode(word)
                KeyboardMode.ULTRA -> ultraEncoder.encode(word)
            }
        } else {
            word
        }
        ic.commitText(toInsert, 1)
        rawWordBuffer.clear()
        encodedLengthsPerChar.clear()
    }

    // ---------- Helpers ----------

    /** Decodes the full current field content in place (used by DECODE mode + auto-decode). */
    private fun decodeExistingFieldText() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(4000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(4000, 0)?.toString().orEmpty()
        if (before.isEmpty() && after.isEmpty()) return

        val decodedBefore = decoder.decode(before)
        val decodedAfter = decoder.decode(after)

        ic.beginBatchEdit()
        ic.deleteSurroundingText(before.length, after.length)
        ic.commitText(decodedBefore + decodedAfter, 1)
        // Restore cursor to the boundary between the decoded "before" and "after" segments.
        val newCursorPos = decodedBefore.length
        ic.setSelection(newCursorPos, newCursorPos)
        ic.endBatchEdit()
    }

    private fun performFeedback() {
        if (currentSettings.vibrationEnabled) vibrate()
        if (currentSettings.keySoundEnabled) {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(12)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}

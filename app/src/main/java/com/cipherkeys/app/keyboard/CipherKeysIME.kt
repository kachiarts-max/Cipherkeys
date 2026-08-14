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
import android.view.inputmethod.InputConnection
import com.cipherkeys.app.data.KeyboardMode
import com.cipherkeys.app.data.KeyboardSettings
import com.cipherkeys.app.data.SettingsRepository
import com.cipherkeys.app.decoder.CipherKeysDecoder
import com.cipherkeys.app.dictionary.ContextPredictor
import com.cipherkeys.app.dictionary.Dictionary
import com.cipherkeys.app.dictionary.EnglishLexicon
import com.cipherkeys.app.dictionary.SmartSuggestionEngine
import com.cipherkeys.app.emoji.RecentEmojiStore
import com.cipherkeys.app.encoder.ClassicLeetEncoder
import com.cipherkeys.app.encoder.Encoder
import com.cipherkeys.app.encoder.EliteEncoder
import com.cipherkeys.app.encoder.HackerEncoder
import com.cipherkeys.app.encoder.UltraEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main Android Input Method Service for CipherKeys.
 *
 * Responsibilities:
 *
 * - Manage the InputConnection lifecycle
 * - Handle keyboard actions
 * - Encode typed characters
 * - Decode existing text
 * - Track the current word
 * - Learn user vocabulary
 * - Learn word-to-word context
 * - Generate smart suggestions
 * - Perform autocorrection
 * - Manage emoji recents
 * - Manage keyboard themes/backgrounds
 * - Provide key sound and vibration feedback
 *
 * All learning remains local to the device.
 */
class CipherKeysIME :
    InputMethodService(),
    KeyboardActionListener {

    // =========================================================
    // CORE COMPONENTS
    // =========================================================

    private lateinit var keyboardView: CipherKeysKeyboardView
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var dictionary: Dictionary
    private lateinit var contextPredictor: ContextPredictor
    private lateinit var smartSuggestionEngine: SmartSuggestionEngine
    private lateinit var recentEmojiStore: RecentEmojiStore

    // =========================================================
    // COROUTINES
    // =========================================================

    private val serviceJob = Job()

    private val serviceScope =
        CoroutineScope(
            serviceJob + Dispatchers.Main
        )

    // =========================================================
    // SETTINGS / MODE
    // =========================================================

    private var currentSettings: KeyboardSettings =
        KeyboardSettings()

    private var currentMode: KeyboardMode =
        KeyboardMode.default()

    private var shiftEnabled: Boolean = false

    // =========================================================
    // ENCODERS / DECODER
    // =========================================================

    private lateinit var classicEncoder: Encoder
    private lateinit var eliteEncoder: Encoder
    private lateinit var hackerEncoder: Encoder
    private lateinit var ultraEncoder: Encoder
    private lateinit var decoder: CipherKeysDecoder

    // =========================================================
    // BACKGROUND
    // =========================================================

    private var backgroundBitmap: Bitmap? = null

    // =========================================================
    // WORD TRACKING
    // =========================================================

    /**
     * Raw English word currently being typed.
     *
     * Example:
     *
     * User types:
     *
     * "hello"
     *
     * rawWordBuffer = "hello"
     */
    private val rawWordBuffer =
        StringBuilder()

    /**
     * Stores the number of encoded characters generated
     * for every raw character.
     *
     * This is important because one raw character can become
     * multiple encoded characters.
     */
    private val encodedLengthsPerChar =
        mutableListOf<Int>()

    /**
     * Most recently completed word.
     *
     * Example:
     *
     * "thank you"
     *
     * after "thank":
     * lastCompletedWord = "thank"
     *
     * after "you":
     * lastCompletedWord = "you"
     */
    private var lastCompletedWord: String = ""

    // =========================================================
    // LIFECYCLE
    // =========================================================

    override fun onCreate() {
        super.onCreate()

        settingsRepository =
            SettingsRepository(
                applicationContext
            )

        dictionary =
            EnglishLexicon(
                applicationContext
            )

        contextPredictor =
    ContextPredictor(
        applicationContext
    )

val phrasePredictor =
    PhrasePredictor(
        applicationContext
    )

smartSuggestionEngine =
    SmartSuggestionEngine(
        dictionary,
        contextPredictor,
        phrasePredictor
    )
        recentEmojiStore =
            RecentEmojiStore(
                applicationContext
            )

        rebuildEncoders(
            emptyMap()
        )

        observeSettings()

        observeRecentEmoji()
    }

    /**
     * Observe keyboard settings.
     */
    private fun observeSettings() {

        serviceScope.launch {

            settingsRepository.settingsFlow.collect { settings ->

                val mappingsChanged =
                    settings.customMappings !=
                            currentSettings.customMappings

                val backgroundPathChanged =
                    settings.backgroundImagePath !=
                            currentSettings.backgroundImagePath

                currentSettings =
                    settings

                if (mappingsChanged) {

                    rebuildEncoders(
                        settings.customMappings
                    )
                }

                if (backgroundPathChanged) {

                    backgroundBitmap =
                        settings.backgroundImagePath
                            ?.let {
                                decodeBackgroundBitmap(it)
                            }
                }

                if (::keyboardView.isInitialized) {

                    applyCurrentSettingsToKeyboard()
                }
            }
        }
    }

    /**
     * Observe recently used emoji.
     */
    private fun observeRecentEmoji() {

        serviceScope.launch {

            recentEmojiStore.recentsFlow.collect { recents ->

                if (::keyboardView.isInitialized) {

                    keyboardView.setRecentEmoji(
                        recents
                    )
                }
            }
        }
    }

    // =========================================================
    // ENCODERS
    // =========================================================

    private fun rebuildEncoders(
        customMappings: Map<Char, List<String>>
    ) {

        classicEncoder =
            ClassicLeetEncoder(
                customMappings
            )

        eliteEncoder =
            EliteEncoder(
                customMappings
            )

        hackerEncoder =
            HackerEncoder(
                customMappings
            )

        ultraEncoder =
            UltraEncoder(
                customMappings
            )

        decoder =
            CipherKeysDecoder(
                customMappings
            )
    }

    // =========================================================
    // BACKGROUND
    // =========================================================

    private suspend fun decodeBackgroundBitmap(
        path: String
    ): Bitmap? =
        withContext(Dispatchers.IO) {

            try {

                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = 4
                    }

                BitmapFactory.decodeFile(
                    path,
                    options
                )

            } catch (
                _: Exception
            ) {

                null
            }
        }

    // =========================================================
    // KEYBOARD VIEW
    // =========================================================

    override fun onCreateInputView(): View {

        val view =
            CipherKeysKeyboardView(this)

        view.listener = this

        keyboardView = view

        applyCurrentSettingsToKeyboard()

        return view
    }

    /**
     * Apply the current theme, size and background.
     */
    private fun applyCurrentSettingsToKeyboard() {

        if (!::keyboardView.isInitialized) {
            return
        }

        keyboardView.applyThemeAndHeight(
            currentSettings.theme,
            currentSettings.keyboardHeightScale,
            currentSettings.customColors
        )

        val bitmapToShow =
            if (
                currentSettings.useImageBackground
            ) {
                backgroundBitmap
            } else {
                null
            }

        keyboardView.setBackgroundImage(
            bitmapToShow,
            currentSettings.backgroundOverlayAlpha
        )

        keyboardView.setMode(
            currentMode
        )

        keyboardView.setShiftState(
            shiftEnabled
        )
    }

    // =========================================================
    // INPUT VIEW START
    // =========================================================

    override fun onStartInputView(
        info: EditorInfo?,
        restarting: Boolean
    ) {

        super.onStartInputView(
            info,
            restarting
        )

        currentMode =
            currentSettings.defaultMode

        shiftEnabled = false

        lastCompletedWord = ""

        finalizeWord()

        keyboardView.setMode(
            currentMode
        )

        keyboardView.setShiftState(
            false
        )

        /*
         * Recover the word before the cursor.
         */
        recoverPreviousWord()

        /*
         * Decode existing text when enabled.
         */
        if (
            currentSettings.autoDecodeEnabled
        ) {

            decodeExistingFieldText()
        }

        /*
         * Show context suggestions if possible.
         */
        updateContextSuggestions()
    }

    // =========================================================
    // CHARACTER KEY
    // =========================================================

    override fun onCharKey(
        char: Char
    ) {

        val ic =
            currentInputConnection
                ?: return

        val effectiveChar =
            if (shiftEnabled) {
                char.uppercaseChar()
            } else {
                char
            }

        val output =
            encodeCharacter(
                effectiveChar
            )

        ic.commitText(
            output,
            1
        )

        performFeedback()

        if (
            effectiveChar.isLetter()
        ) {

            rawWordBuffer.append(
                effectiveChar
            )

            encodedLengthsPerChar.add(
                output.length
            )

            updateSuggestions()

        } else {

            /*
             * Any non-letter ends the current word.
             */
            completeCurrentWord()

            updateContextSuggestions()
        }

        /*
         * Normal keyboard shift behavior:
         * shift automatically turns off after one character.
         */
        if (shiftEnabled) {

            shiftEnabled = false

            keyboardView.setShiftState(
                false
            )
        }
    }

    /**
     * Encode one character according to the
     * currently selected keyboard mode.
     */
    private fun encodeCharacter(
        char: Char
    ): String {

        if (
            !currentSettings.autoEncodeEnabled
        ) {
            return char.toString()
        }

        return when (currentMode) {

            KeyboardMode.NORMAL,
            KeyboardMode.DECODE -> {

                char.toString()
            }

            KeyboardMode.CLASSIC_LEET -> {

                classicEncoder.encode(
                    char.toString()
                )
            }

            KeyboardMode.ELITE -> {

                eliteEncoder.encode(
                    char.toString()
                )
            }

            KeyboardMode.HACKER -> {

                hackerEncoder.encode(
                    char.toString()
                )
            }

            KeyboardMode.ULTRA -> {

                ultraEncoder.encode(
                    char.toString()
                )
            }
        }
    }

    // =========================================================
    // SPACE
    // =========================================================

    override fun onSpace() {

        /*
         * First attempt autocorrection.
         */
        maybeAutocorrectBeforeBoundary()

        /*
         * Finish the current word.
         */
        completeCurrentWord()

        /*
         * Commit the actual space.
         */
        currentInputConnection?.commitText(
            " ",
            1
        )

        performFeedback()

        /*
         * Show predictions for the next word.
         */
        updateContextSuggestions()
    }

    // =========================================================
    // BACKSPACE
    // =========================================================

    override fun onBackspace() {

        val ic =
            currentInputConnection
                ?: return

        val selected =
            ic.getSelectedText(0)

        /*
         * Delete selected text.
         */
        if (
            !selected.isNullOrEmpty()
        ) {

            ic.commitText(
                "",
                1
            )

            rawWordBuffer.clear()
            encodedLengthsPerChar.clear()

            recoverPreviousWord()
            updateContextSuggestions()

            performFeedback()

            return
        }

        /*
         * Delete a character from the current word.
         */
        if (
            rawWordBuffer.isNotEmpty() &&
            encodedLengthsPerChar.isNotEmpty()
        ) {

            val lastEncodedLength =
                encodedLengthsPerChar.removeAt(
                    encodedLengthsPerChar.lastIndex
                )

            rawWordBuffer.deleteCharAt(
                rawWordBuffer.lastIndex
            )

            ic.deleteSurroundingText(
                lastEncodedLength,
                0
            )

            updateSuggestions()

            performFeedback()

            return
        }

        /*
         * There is no active word.
         *
         * Delete the previous character from the editor.
         */
        ic.deleteSurroundingText(
            1,
            0
        )

        recoverPreviousWord()

        updateContextSuggestions()

        performFeedback()
    }

    // =========================================================
    // ENTER
    // =========================================================

    override fun onEnter() {

        val ic =
            currentInputConnection
                ?: return

        completeCurrentWord()

        val editorInfo =
            currentInputEditorInfo

        val action =
            editorInfo
                ?.imeOptions
                ?.and(EditorInfo.IME_MASK_ACTION)

        if (
            action != null &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            editorInfo.imeOptions.and(
                EditorInfo.IME_FLAG_NO_ENTER_ACTION
            ) == 0
        ) {

            ic.performEditorAction(
                action
            )

        } else {

            ic.commitText(
                "\n",
                1
            )
        }

        performFeedback()

        finalizeWord()

        lastCompletedWord = ""
    }

    // =========================================================
    // SHIFT
    // =========================================================

    override fun onShiftToggle() {

        shiftEnabled =
            !shiftEnabled

        if (::keyboardView.isInitialized) {

            keyboardView.setShiftState(
                shiftEnabled
            )
        }
    }

    // =========================================================
    // MODE
    // =========================================================

    override fun onModeSelected(
        mode: KeyboardMode
    ) {

        /*
         * Finish anything currently being typed
         * before switching mode.
         */
        completeCurrentWord()

        currentMode =
            mode

        keyboardView.setMode(
            mode
        )

        /*
         * Decode the existing field when the
         * decode mode is selected.
         */
        if (
            mode == KeyboardMode.DECODE
        ) {

            decodeExistingFieldText()
        }

        updateContextSuggestions()
    }

    // =========================================================
    // SUGGESTION SELECTED
    // =========================================================

    override fun onSuggestionSelected(
        word: String
    ) {

        val ic =
            currentInputConnection
                ?: return

        val normalizedWord =
            word.trim()

        if (
            normalizedWord.isBlank()
        ) {
            return
        }

        /*
         * Selecting a suggestion is strong evidence
         * that this is useful vocabulary.
         */
        dictionary.learnWord(
            normalizedWord
        )

        /*
         * Teach the relationship between the previous
         * word and the selected word.
         */
        if (
            lastCompletedWord.isNotBlank()
        ) {

            contextPredictor.learn(
                lastCompletedWord,
                normalizedWord
            )
        }

        /*
         * Replace the currently typed prefix.
         */
        replaceCurrentWord(
            ic,
            normalizedWord
        )

        /*
         * The selected word becomes the new context.
         */
        lastCompletedWord =
            normalizedWord.lowercase()

        /*
         * Insert a space so the user can immediately
         * continue typing.
         */
        ic.commitText(
            " ",
            1
        )

        /*
         * Clear current typing state.
         */
        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()

        /*
         * Show the next context predictions.
         */
        updateContextSuggestions()

        performFeedback()
    }

    // =========================================================
    // EMOJI
    // =========================================================

    override fun onEmojiSelected(
        emoji: String
    ) {

        val ic =
            currentInputConnection
                ?: return

        /*
         * Finish the current word first.
         */
        completeCurrentWord()

        ic.commitText(
            emoji,
            1
        )

        performFeedback()

        /*
         * Store the emoji as recently used.
         */
        serviceScope.launch {

            recentEmojiStore.addRecent(
                emoji
            )
        }

        /*
         * Emoji does not become a word context.
         */
        updateContextSuggestions()
    }

    // =========================================================
    // SMART SUGGESTIONS
    // =========================================================

    /**
     * Update suggestions while the user is typing.
     *
     * SmartSuggestionEngine combines:
     *
     * - dictionary completions
     * - learned vocabulary
     * - context predictions
     * - usage frequency
     */
    private fun updateSuggestions() {

        if (
            !::keyboardView.isInitialized
        ) {
            return
        }

        val prefix =
            rawWordBuffer
                .toString()

        if (
            prefix.isBlank()
        ) {

            updateContextSuggestions()

            return
        }

        val suggestions =
            smartSuggestionEngine.suggest(
                prefix = prefix,
                previousWord = lastCompletedWord,
                limit = 3
            )

        keyboardView.setSuggestions(
            suggestions
        )
    }

    /**
     * Show predictions for the next word.
     *
     * Example:
     *
     * "thank "
     *
     * ->
     *
     * you | you... | ...
     */
    private fun updateContextSuggestions() {

        if (
            !::keyboardView.isInitialized
        ) {
            return
        }

        if (
            lastCompletedWord.isBlank()
        ) {

            keyboardView.setSuggestions(
                emptyList()
            )

            return
        }

        val suggestions =
            smartSuggestionEngine.suggestContext(
                previousWord = lastCompletedWord,
                limit = 3
            )

        keyboardView.setSuggestions(
            suggestions
        )
    }

    // =========================================================
    // WORD COMPLETION / LEARNING
    // =========================================================

    /**
     * Complete and learn the current word.
     */
    private fun completeCurrentWord() {

        val word =
            rawWordBuffer
                .toString()
                .trim()

        if (
            word.isNotBlank()
        ) {

            /*
             * Learn the word itself.
             */
            dictionary.learnWord(
                word
            )

            /*
             * Learn its relationship with the
             * previous completed word.
             */
            if (
                lastCompletedWord.isNotBlank()
            ) {

                contextPredictor.learn(
                    lastCompletedWord,
                    word
                )
            }

            /*
             * This word becomes the new context.
             */
            lastCompletedWord =
                word.lowercase()
        }

        finalizeWord()
    }

    /**
     * Clear active word-tracking state.
     */
    private fun finalizeWord() {

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()
    }

    // =========================================================
    // AUTOCORRECT
    // =========================================================

    /**
     * Attempts to correct the current word before
     * a space is inserted.
     */
    private fun maybeAutocorrectBeforeBoundary() {

        if (
            !currentSettings.autocorrectEnabled
        ) {
            return
        }

        /*
         * Autocorrection only makes sense when we
         * are currently writing normal English.
         */
        if (
            currentMode != KeyboardMode.NORMAL &&
            currentMode != KeyboardMode.DECODE
        ) {
            return
        }

        val word =
            rawWordBuffer
                .toString()
                .trim()

        if (
            word.length < 3
        ) {
            return
        }

        /*
         * Already valid -> no correction.
         */
        if (
            dictionary.isValidWord(
                word
            )
        ) {
            return
        }

        val correction =
            dictionary
                .suggestCorrections(
                    word,
                    limit = 1
                )
                .firstOrNull()
                ?: return

        val ic =
            currentInputConnection
                ?: return

        replaceCurrentWord(
            ic,
            correction
        )

        dictionary.learnWord(
            correction
        )

        lastCompletedWord =
            correction.lowercase()
    }

    // =========================================================
    // REPLACE CURRENT WORD
    // =========================================================

    /**
     * Replace the currently typed raw word.
     *
     * This correctly handles encoded modes where
     * one raw character may produce multiple characters.
     */
    private fun replaceCurrentWord(
        ic: InputConnection,
        word: String
    ) {

        val committedLength =
            encodedLengthsPerChar.sum()

        if (
            committedLength > 0
        ) {

            ic.deleteSurroundingText(
                committedLength,
                0
            )
        }

        val toInsert =
            encodeWholeWord(
                word
            )

        ic.commitText(
            toInsert,
            1
        )

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()
    }

    /**
     * Encode a complete word according to
     * the currently selected mode.
     */
    private fun encodeWholeWord(
        word: String
    ): String {

        if (
            !currentSettings.autoEncodeEnabled
        ) {
            return word
        }

        return when (currentMode) {

            KeyboardMode.NORMAL,
            KeyboardMode.DECODE -> {

                word
            }

            KeyboardMode.CLASSIC_LEET -> {

                classicEncoder.encode(
                    word
                )
            }

            KeyboardMode.ELITE -> {

                eliteEncoder.encode(
                    word
                )
            }

            KeyboardMode.HACKER -> {

                hackerEncoder.encode(
                    word
                )
            }

            KeyboardMode.ULTRA -> {

                ultraEncoder.encode(
                    word
                )
            }
        }
    }

    // =========================================================
    // RECOVER PREVIOUS WORD
    // =========================================================

    /**
     * Recover the word immediately before the cursor.
     */
    private fun recoverPreviousWord() {

        val ic =
            currentInputConnection
                ?: return

        val before =
            ic.getTextBeforeCursor(
                200,
                0
            )?.toString()
                ?: return

        /*
         * If there is an active unfinished word,
         * don't overwrite it.
         */
        if (
            rawWordBuffer.isNotEmpty()
        ) {
            return
        }

        /*
         * Find the last English-like word.
         */
        val match =
            Regex(
                "[A-Za-z']+$"
            ).find(
                before
            )

        lastCompletedWord =
            match
                ?.value
                ?.lowercase()
                .orEmpty()
    }

    // =========================================================
    // DECODER
    // =========================================================

    /**
     * Decode existing encoded text around the cursor.
     */
    private fun decodeExistingFieldText() {

        val ic =
            currentInputConnection
                ?: return

        val before =
            ic.getTextBeforeCursor(
                4000,
                0
            )?.toString()
                .orEmpty()

        val after =
            ic.getTextAfterCursor(
                4000,
                0
            )?.toString()
                .orEmpty()

        if (
            before.isEmpty() &&
            after.isEmpty()
        ) {
            return
        }

        val decodedBefore =
            decoder.decode(
                before
            )

        val decodedAfter =
            decoder.decode(
                after
            )

        ic.beginBatchEdit()

        try {

            ic.deleteSurroundingText(
                before.length,
                after.length
            )

            ic.commitText(
                decodedBefore + decodedAfter,
                1
            )

            val newCursorPosition =
                decodedBefore.length

            ic.setSelection(
                newCursorPosition,
                newCursorPosition
            )

        } finally {

            ic.endBatchEdit()
        }

        /*
         * Recover context after decoding.
         */
        rawWordBuffer.clear()
        encodedLengthsPerChar.clear()

        recoverPreviousWord()

        updateContextSuggestions()
    }

    // =========================================================
    // FEEDBACK
    // =========================================================

    private fun performFeedback() {

        if (
            currentSettings.vibrationEnabled
        ) {

            vibrate()
        }

        if (
            currentSettings.keySoundEnabled
        ) {

            val audioManager =
                getSystemService(
                    Context.AUDIO_SERVICE
                ) as? AudioManager

            audioManager?.playSoundEffect(
                AudioManager.FX_KEYPRESS_STANDARD
            )
        }
    }

    /**
     * Haptic feedback compatible with old and
     * newer Android versions.
     */
    private fun vibrate() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            val manager =
                getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE
                ) as? VibratorManager

            manager
                ?.defaultVibrator
                ?.vibrate(
                    VibrationEffect.createOneShot(
                        12,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )

        } else {

            @Suppress("DEPRECATION")
            val vibrator =
                getSystemService(
                    Context.VIBRATOR_SERVICE
                ) as? Vibrator

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        12,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )

            } else {

                @Suppress("DEPRECATION")
                vibrator?.vibrate(
                    12
                )
            }
        }
    }

    // =========================================================
    // DESTROY
    // =========================================================

    override fun onDestroy() {

        serviceJob.cancel()

        backgroundBitmap?.recycle()

        backgroundBitmap = null

        super.onDestroy()
    }
}

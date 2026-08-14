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
import com.cipherkeys.app.encoder.ClassicLeetEncoder
import com.cipherkeys.app.encoder.EliteEncoder
import com.cipherkeys.app.encoder.Encoder
import com.cipherkeys.app.encoder.HackerEncoder
import com.cipherkeys.app.encoder.UltraEncoder
import com.cipherkeys.app.emoji.RecentEmojiStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real Android keyboard service for CipherKeys.
 *
 * Responsibilities:
 *
 * - Own the InputConnection lifecycle
 * - Send key presses to the active encoder
 * - Track the current word
 * - Provide dictionary suggestions
 * - Learn personal vocabulary
 * - Learn word-to-word context
 * - Provide smart context-aware suggestions
 * - Handle emoji
 * - Handle themes/backgrounds/settings
 * - Handle autocorrect
 * - Handle decoding
 */
class CipherKeysIME :
    InputMethodService(),
    KeyboardActionListener {

    private lateinit var keyboardView: CipherKeysKeyboardView
    private lateinit var settingsRepository: SettingsRepository

    private val serviceJob = Job()

    private val serviceScope =
        CoroutineScope(
            serviceJob + Dispatchers.Main
        )

    private var currentSettings: KeyboardSettings =
        KeyboardSettings()

    private var currentMode: KeyboardMode =
        KeyboardMode.default()

    private var shiftEnabled = false

    // =========================================================
    // ENCODERS / DECODER
    // =========================================================

    private lateinit var classicEncoder: Encoder
    private lateinit var eliteEncoder: Encoder
    private lateinit var hackerEncoder: Encoder
    private lateinit var ultraEncoder: Encoder
    private lateinit var decoder: CipherKeysDecoder

    // =========================================================
    // DICTIONARY / LEARNING
    // =========================================================

    private lateinit var dictionary: Dictionary

    private lateinit var contextPredictor: ContextPredictor

    private lateinit var smartSuggestionEngine: SmartSuggestionEngine

    // =========================================================
    // EMOJI
    // =========================================================

    private lateinit var recentEmojiStore: RecentEmojiStore

    // =========================================================
    // BACKGROUND
    // =========================================================

    private var backgroundBitmap: Bitmap? = null

    // =========================================================
    // WORD TRACKING
    // =========================================================

    /**
     * Current raw English word being typed.
     *
     * Example:
     *
     * User types:
     * "hello"
     *
     * rawWordBuffer = "hello"
     */
    private val rawWordBuffer =
        StringBuilder()

    /**
     * Number of encoded characters produced
     * for every raw character.
     *
     * Example:
     *
     * raw character: "a"
     * encoded output: "4"
     *
     * length = 1
     *
     * If a character produces multiple encoded
     * characters, that length is stored here.
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
    private var lastCompletedWord = ""

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

        smartSuggestionEngine =
            SmartSuggestionEngine(
                dictionary = dictionary,
                contextPredictor = contextPredictor
            )

        recentEmojiStore =
            RecentEmojiStore(
                applicationContext
            )

        rebuildEncoders(
            emptyMap()
        )

        /*
         * Listen for settings changes.
         */
        serviceScope.launch {

            settingsRepository.settingsFlow.collect { settings ->

                val mappingsChanged =
                    settings.customMappings !=
                            currentSettings.customMappings

                val backgroundPathChanged =
                    settings.backgroundImagePath !=
                            currentSettings.backgroundImagePath

                currentSettings = settings

                if (mappingsChanged) {

                    rebuildEncoders(
                        settings.customMappings
                    )
                }

                if (backgroundPathChanged) {

                    backgroundBitmap =
                        settings.backgroundImagePath
                            ?.let { path ->
                                decodeBackgroundBitmap(
                                    path
                                )
                            }
                }

                if (::keyboardView.isInitialized) {

                    keyboardView.applyThemeAndHeight(
                        settings.theme,
                        settings.keyboardHeightScale,
                        settings.customColors
                    )

                    val bitmapToShow =
                        if (
                            settings.useImageBackground
                        ) {
                            backgroundBitmap
                        } else {
                            null
                        }

                    keyboardView.setBackgroundImage(
                        bitmapToShow,
                        settings.backgroundOverlayAlpha
                    )
                }
            }
        }

        /*
         * Listen for recent emoji updates.
         */
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

    /**
     * Rebuild all encoders whenever custom mappings change.
     */
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

    /**
     * Decode a background image off the main thread.
     */
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
                e: Exception
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

        keyboardView.applyThemeAndHeight(
            currentSettings.theme,
            currentSettings.keyboardHeightScale,
            currentSettings.customColors
        )

        keyboardView.setBackgroundImage(
            if (
                currentSettings.useImageBackground
            ) {
                backgroundBitmap
            } else {
                null
            },
            currentSettings.backgroundOverlayAlpha
        )

        return view
    }

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
         * Recover the word immediately before
         * the cursor.
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

        if (effectiveChar.isLetter()) {

            rawWordBuffer.append(
                effectiveChar
            )

            encodedLengthsPerChar.add(
                output.length
            )

            updateSuggestions()

        } else {

            /*
             * Punctuation ends the current word.
             */
            completeCurrentWord()

            /*
             * Keep context predictions available.
             */
            updateContextSuggestions()
        }

        /*
         * Shift behaves like a normal one-shot shift.
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
     * current keyboard mode.
     */
    private fun encodeCharacter(
        char: Char
    ): String {

        if (!currentSettings.autoEncodeEnabled) {
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
         * Finish and learn the current word.
         */
        completeCurrentWord()

        currentInputConnection?.commitText(
            " ",
            1
        )

        performFeedback()

        /*
         * Predict the next word.
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
         * If text is selected, delete it.
         */
        if (!selected.isNullOrEmpty()) {

            ic.commitText(
                "",
                1
            )

            /*
             * The local word buffer can no longer
             * be trusted after deleting a selection.
             */
            rawWordBuffer.clear()
            encodedLengthsPerChar.clear()

            recoverPreviousWord()

            updateContextSuggestions()

            performFeedback()

            return
        }

        /*
         * Delete from the currently tracked word.
         */
        if (
            rawWordBuffer.isNotEmpty() &&
            encodedLengthsPerChar.isNotEmpty()
        ) {

            val lastEncodedLength =
                encodedLengthsPerChar
                    .removeAt(
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

        } else {

            /*
             * No tracked word.
             *
             * Delete one character from the editor.
             */
            ic.deleteSurroundingText(
                1,
                0
            )

            /*
             * Recover context after deleting.
             */
            recoverPreviousWord()

            updateContextSuggestions()
        }

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
                ?.and(
                    EditorInfo.IME_MASK_ACTION
                )

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

        keyboardView.setSuggestions(
            emptyList()
        )
    }

    // =========================================================
    // SHIFT
    // =========================================================

    override fun onShiftToggle() {

        shiftEnabled =
            !shiftEnabled

        keyboardView.setShiftState(
            shiftEnabled
        )
    }

    // =========================================================
    // MODE
    // =========================================================

    override fun onModeSelected(
        mode: KeyboardMode
    ) {

        /*
         * Finish the current word before switching mode.
         */
        completeCurrentWord()

        currentMode = mode

        keyboardView.setMode(
            currentMode
        )

        if (
            mode == KeyboardMode.DECODE
        ) {

            decodeExistingFieldText()
        }

        updateContextSuggestions()
    }

    // =========================================================
    // SUGGESTION SELECTION
    // =========================================================

    override fun onSuggestionSelected(
        word: String
    ) {

        val ic =
            currentInputConnection
                ?: return

        val normalizedWord =
            word
                .trim()
                .lowercase()

        if (normalizedWord.isBlank()) {
            return
        }

        /*
         * Selecting a suggestion is strong evidence
         * that this is a useful word for the user.
         */
        dictionary.learnWord(
            normalizedWord
        )

        /*
         * Teach the previous -> selected relationship.
         *
         * Example:
         *
         * thank -> you
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
         * Replace the currently typed word.
         */
        replaceCurrentWord(
            ic,
            normalizedWord
        )

        /*
         * The selected word becomes the new context.
         */
        lastCompletedWord =
            normalizedWord

        /*
         * Add a space after the selected suggestion,
         * matching normal keyboard behavior.
         */
        ic.commitText(
            " ",
            1
        )

        finalizeWord()

        /*
         * Predict what comes next.
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
         * Finish the current word before inserting emoji.
         */
        completeCurrentWord()

        ic.commitText(
            emoji,
            1
        )

        performFeedback()

        serviceScope.launch {

            recentEmojiStore.addRecent(
                emoji
            )
        }

        updateContextSuggestions()
    }

    // =========================================================
    // SMART SUGGESTIONS
    // =========================================================

    /**
     * Updates suggestions while the user is
     * actively typing a word.
     *
     * SmartSuggestionEngine combines:
     *
     * - Dictionary completions
     * - Learned vocabulary
     * - Context predictions
     * - Personal usage frequency
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

        if (prefix.isBlank()) {

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
     * Shows context predictions when there is
     * no active word being typed.
     *
     * Example:
     *
     * "thank "
     *
     * could produce:
     *
     * you | god | goodness
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
     * Completes the current word.
     *
     * This teaches:
     *
     * 1. Personal vocabulary
     * 2. Previous-word -> current-word relationship
     */
    private fun completeCurrentWord() {

        val word =
            rawWordBuffer
                .toString()
                .trim()

        if (word.isNotBlank()) {

            /*
             * Learn the word itself.
             */
            dictionary.learnWord(
                word
            )

            /*
             * Learn the relationship between
             * the previous and current word.
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
             * Current word becomes context
             * for the next word.
             */
            lastCompletedWord =
                word.lowercase()
        }

        finalizeWord()
    }

    /**
     * Clear current word tracking.
     */
    private fun finalizeWord() {

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()
    }

    // =========================================================
    // AUTOCORRECT
    // =========================================================

    /**
     * Correct the current word before a space
     * is committed.
     */
    private fun maybeAutocorrectBeforeBoundary() {

        if (
            !currentSettings.autocorrectEnabled
        ) {
            return
        }

        /*
         * Autocorrect only applies to normal
         * text modes.
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
            word.length < 3 ||
            dictionary.isValidWord(word)
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

    /**
     * Replace the currently tracked word in
     * the editor.
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
     * the active mode.
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
     * Attempts to find the word immediately
     * before the cursor.
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
         * If the cursor is currently inside a word,
         * this extracts that word.
         */
        val match =
            Regex(
                "[A-Za-z']+$"
            ).find(before)

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
     * Decode the text currently surrounding
     * the cursor.
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

        ic.deleteSurroundingText(
            before.length,
            after.length
        )

        ic.commitText(
            decodedBefore + decodedAfter,
            1
        )

        /*
         * Restore cursor position after the
         * decoded "before" section.
         */
        val newCursorPosition =
            decodedBefore.length

        ic.setSelection(
            newCursorPosition,
            newCursorPosition
        )

        ic.endBatchEdit()
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
     * Vibration compatible with older
     * and newer Android versions.
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

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
import com.cipherkeys.app.dictionary.PhrasePredictor
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
     * hello
     */
    private val rawWordBuffer =
        StringBuilder()

    /**
     * Stores how many encoded characters were produced
     * for every raw character.
     *
     * Example:
     *
     * Raw:    a b c
     * Output: @ 8 (
     *
     * lengths: 1, 1, 1
     *
     * This becomes important when deleting characters in
     * encoded keyboard modes.
     */
    private val encodedLengthsPerChar =
        mutableListOf<Int>()

    /**
     * Most recently completed word.
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

    // =========================================================
    // SETTINGS
    // =========================================================

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
                            ?.let { path ->
                                decodeBackgroundBitmap(
                                    path
                                )
                            }
                }

                if (::keyboardView.isInitialized) {

                    applyCurrentSettingsToKeyboard()
                }
            }
        }
    }

    // =========================================================
    // RECENT EMOJI
    // =========================================================

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

        recoverPreviousWord()

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

            completeCurrentWord()

            updateContextSuggestions()
        }

        if (shiftEnabled) {

            shiftEnabled = false

            keyboardView.setShiftState(
                false
            )
        }
    }

    // =========================================================
    // CHARACTER ENCODING
    // =========================================================

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

        maybeAutocorrectBeforeBoundary()

        completeCurrentWord()

        currentInputConnection?.commitText(
            " ",
            1
        )

        performFeedback()

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

        // -----------------------------------------------------
        // Delete selected text
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // Delete character from active word
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // No active word
        // -----------------------------------------------------

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

        completeCurrentWord()

        currentMode =
            mode

        keyboardView.setMode(
            mode
        )

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

        // Learn selected vocabulary.
        dictionary.learnWord(
            normalizedWord
        )

        // Learn previous-word relationship.
        if (
            lastCompletedWord.isNotBlank()
        ) {

            contextPredictor.learn(
                lastCompletedWord,
                normalizedWord
            )
        }

        // Replace current typed prefix.
        replaceCurrentWord(
            ic,
            normalizedWord
        )

        // Selected word becomes context.
        lastCompletedWord =
            normalizedWord.lowercase()

        // Add a space automatically.
        ic.commitText(
            " ",
            1
        )

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()

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
     * Updates suggestions while the user is typing.
     *
     * This uses the real SmartSuggestionEngine.
     *
     * The temporary test suggestions that were causing
     * the compilation error have been completely removed.
     */
    private fun updateSuggestions() {

        if (!::keyboardView.isInitialized) {
            return
        }

        val prefix =
            rawWordBuffer
                .toString()
                .trim()
                .lowercase()

        if (prefix.isBlank()) {

            keyboardView.setSuggestions(
                emptyList()
            )

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
     * Shows predictions for the next word.
     *
     * Example:
     *
     * thank
     *
     * ->
     *
     * you | you... | ...
     */
    private fun updateContextSuggestions() {

        if (!::keyboardView.isInitialized) {
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

    private fun completeCurrentWord() {

        val word =
            rawWordBuffer
                .toString()
                .trim()

        if (
            word.isNotBlank()
        ) {

            // Learn the word.
            dictionary.learnWord(
                word
            )

            // Learn relationship with previous word.
            if (
                lastCompletedWord.isNotBlank()
            ) {

                contextPredictor.learn(
                    lastCompletedWord,
                    word
                )
            }

            // Current word becomes context.
            lastCompletedWord =
                word.lowercase()
        }

        finalizeWord()
    }

    private fun finalizeWord() {

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()
    }

    // =========================================================
    // AUTOCORRECT
    // =========================================================

    private fun maybeAutocorrectBeforeBoundary() {

        if (
            !currentSettings.autocorrectEnabled
        ) {
            return
        }

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
     * Replaces the currently typed raw word.
     *
     * Handles encoded modes where one raw character can
     * generate multiple output characters.
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

    // =========================================================
    // WHOLE WORD ENCODING
    // =========================================================

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

        if (
            rawWordBuffer.isNotEmpty()
        ) {
            return
        }

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

    // =========================================================
    // VIBRATION
    // =========================================================

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

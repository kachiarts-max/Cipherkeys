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
import com.cipherkeys.app.data.SettingsRepository
import com.cipherkeys.app.decoder.CipherKeysDecoder
import com.cipherkeys.app.dictionary.ContextPredictor
import com.cipherkeys.app.dictionary.Dictionary
import com.cipherkeys.app.dictionary.EnglishLexicon
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
 * The real Android keyboard service for CipherKeys.
 *
 * Responsibilities:
 *
 * - Own the InputConnection lifecycle
 * - Send key presses to the active encoder
 * - Manage word tracking
 * - Provide dictionary suggestions
 * - Learn new vocabulary
 * - Learn word-to-word context
 * - Provide smart context-aware predictions
 * - Handle emoji
 * - Handle themes/backgrounds/settings
 */
class CipherKeysIME : InputMethodService(), KeyboardActionListener {

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

    private var shiftEnabled: Boolean = false

    private lateinit var classicEncoder: Encoder
    private lateinit var eliteEncoder: Encoder
    private lateinit var hackerEncoder: Encoder
    private lateinit var ultraEncoder: Encoder
    private lateinit var decoder: CipherKeysDecoder

    /**
     * Main English dictionary.
     */
    private lateinit var dictionary: Dictionary

    /**
     * Context-learning engine.
     */
    private lateinit var contextPredictor: ContextPredictor

    /**
     * Central suggestion engine.
     *
     * Combines dictionary, learned vocabulary
     * and context predictions.
     */
    private lateinit var smartSuggestionEngine: SmartSuggestionEngine

    private lateinit var recentEmojiStore: RecentEmojiStore

    private var backgroundBitmap: Bitmap? = null

    /**
     * Current word being typed in plain English.
     */
    private val rawWordBuffer =
        StringBuilder()

    /**
     * Number of encoded characters produced
     * for each raw character.
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

        smartSuggestionEngine =
            SmartSuggestionEngine(
                dictionary,
                contextPredictor
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

                    keyboardView.applyThemeAndHeight(
                        settings.theme,
                        settings.keyboardHeightScale,
                        settings.customColors
                    )

                    val bitmapToShow =
                        if (settings.useImageBackground) {
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
     * Rebuild all encoders using the current
     * custom character mappings.
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
     * Decode a background image without blocking
     * the main thread.
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
            if (currentSettings.useImageBackground) {
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

        keyboardView.setMode(
            currentMode
        )

        keyboardView.setShiftState(
            false
        )

        finalizeWord()

        /*
         * Recover the word immediately before
         * the cursor when possible.
         */
        recoverPreviousWord()

        if (currentSettings.autoDecodeEnabled) {

            decodeExistingFieldText()
        }
    }

    // =========================================================
    // KEYBOARD ACTIONS
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
            if (currentSettings.autoEncodeEnabled) {

                when (currentMode) {

                    KeyboardMode.NORMAL,
                    KeyboardMode.DECODE ->
                        effectiveChar.toString()

                    KeyboardMode.CLASSIC_LEET ->
                        classicEncoder.encode(
                            effectiveChar.toString()
                        )

                    KeyboardMode.ELITE ->
                        eliteEncoder.encode(
                            effectiveChar.toString()
                        )

                    KeyboardMode.HACKER ->
                        hackerEncoder.encode(
                            effectiveChar.toString()
                        )

                    KeyboardMode.ULTRA ->
                        ultraEncoder.encode(
                            effectiveChar.toString()
                        )
                }

            } else {

                effectiveChar.toString()
            }

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

            updateContextSuggestions()
        }

        /*
         * Shift is normally one-shot.
         */
        if (shiftEnabled) {

            shiftEnabled = false

            keyboardView.setShiftState(
                false
            )
        }
    }

    override fun onSpace() {

        /*
         * Correct the word before committing
         * the space.
         */
        maybeAutocorrectBeforeBoundary()

        /*
         * Finish and learn the word.
         */
        completeCurrentWord()

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

    override fun onBackspace() {

        val ic =
            currentInputConnection
                ?: return

        val selected =
            ic.getSelectedText(0)

        if (!selected.isNullOrEmpty()) {

            ic.commitText(
                "",
                1
            )

        } else if (
            rawWordBuffer.isNotEmpty()
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

            ic.deleteSurroundingText(
                1,
                0
            )

            /*
             * If we backspace over a space,
             * recover the previous word.
             */
            recoverPreviousWord()

            updateContextSuggestions()
        }

        performFeedback()
    }

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

    override fun onShiftToggle() {

        shiftEnabled =
            !shiftEnabled

        if (::keyboardView.isInitialized) {

            keyboardView.setShiftState(
                shiftEnabled
            )
        }
    }

    override fun onModeSelected(
        mode: KeyboardMode
    ) {

        currentMode = mode

        completeCurrentWord()

        if (mode == KeyboardMode.DECODE) {

            decodeExistingFieldText()
        }
    }

    override fun onSuggestionSelected(
        word: String
    ) {

        val ic =
            currentInputConnection
                ?: return

        /*
         * Selecting a suggestion is strong evidence
         * that the user wanted this word.
         */
        dictionary.learnWord(
            word
        )

        /*
         * Teach the context engine too.
         */
        if (
            lastCompletedWord.isNotBlank()
        ) {

            contextPredictor.learn(
                lastCompletedWord,
                word
            )
        }

        replaceCurrentWord(
            ic,
            word
        )

        /*
         * The selected word becomes the new context.
         */
        lastCompletedWord =
            word.lowercase()

        /*
         * Keep the existing behavior:
         * selecting a suggestion commits a space.
         */
        ic.commitText(
            " ",
            1
        )

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()

        updateContextSuggestions()

        performFeedback()
    }

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
    }

    // =========================================================
    // SMART SUGGESTIONS
    // =========================================================

    /**
     * Updates suggestions while the user is
     * currently typing.
     *
     * SmartSuggestionEngine is now responsible
     * for combining dictionary and context results.
     */
    private fun updateSuggestions() {

        if (!::keyboardView.isInitialized) {
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
     * Show predictions when there is no active
     * word being typed.
     */
    private fun updateContextSuggestions() {

        if (!::keyboardView.isInitialized) {
            return
        }

        if (lastCompletedWord.isBlank()) {

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
    // WORD LEARNING
    // =========================================================

    /**
     * Completes the current word and teaches
     * both dictionary and context systems.
     */
    private fun completeCurrentWord() {

        val word =
            rawWordBuffer
                .toString()
                .trim()

        if (word.isNotBlank()) {

            /*
             * Teach the dictionary.
             */
            dictionary.learnWord(
                word
            )

            /*
             * Teach the word relationship.
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
             * This becomes the context for
             * the next word.
             */
            lastCompletedWord =
                word.lowercase()
        }

        finalizeWord()
    }

    /**
     * Clears the current word tracking state.
     */
    private fun finalizeWord() {

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()
    }

    // =========================================================
    // AUTOCORRECT
    // =========================================================

    private fun maybeAutocorrectBeforeBoundary() {

        if (!currentSettings.autocorrectEnabled) {
            return
        }

        /*
         * Do not autocorrect encoded modes.
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
     * Replaces the current word in the editor.
     */
    private fun replaceCurrentWord(
        ic: android.view.inputmethod.InputConnection,
        word: String
    ) {

        val committedLength =
            encodedLengthsPerChar.sum()

        if (committedLength > 0) {

            ic.deleteSurroundingText(
                committedLength,
                0
            )
        }

        val toInsert =
            if (currentSettings.autoEncodeEnabled) {

                when (currentMode) {

                    KeyboardMode.NORMAL,
                    KeyboardMode.DECODE ->
                        word

                    KeyboardMode.CLASSIC_LEET ->
                        classicEncoder.encode(
                            word
                        )

                    KeyboardMode.ELITE ->
                        eliteEncoder.encode(
                            word
                        )

                    KeyboardMode.HACKER ->
                        hackerEncoder.encode(
                            word
                        )

                    KeyboardMode.ULTRA ->
                        ultraEncoder.encode(
                            word
                        )
                }

            } else {

                word
            }

        ic.commitText(
            toInsert,
            1
        )

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()
    }

    // =========================================================
    // RECOVER PREVIOUS WORD
    // =========================================================

    /**
     * Attempts to discover the word immediately
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

        val newCursorPos =
            decodedBefore.length

        ic.setSelection(
            newCursorPos,
            newCursorPos
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

            val am =
                getSystemService(
                    Context.AUDIO_SERVICE
                ) as? AudioManager

            am?.playSoundEffect(
                AudioManager.FX_KEYPRESS_STANDARD
            )
        }
    }

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

        super.onDestroy()

        serviceJob.cancel()
    }
}

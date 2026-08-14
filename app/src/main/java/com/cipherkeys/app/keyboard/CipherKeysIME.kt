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
 * CipherKeys Android IME.
 *
 * Handles:
 * - Keyboard input
 * - Cipher encoding/decoding
 * - Suggestions
 * - Personal learned vocabulary
 * - Contractions such as don't, can't, wouldn't, etc.
 * - Autocorrect
 * - Emoji
 * - Themes/backgrounds
 * - Vibration/key sounds
 *
 * Learned words are stored locally using SharedPreferences, so CipherKeys
 * gradually builds a personal vocabulary based on the words the user actually
 * types.
 */
class CipherKeysIME : InputMethodService(), KeyboardActionListener {

    private lateinit var keyboardView: CipherKeysKeyboardView
    private lateinit var settingsRepository: SettingsRepository

    private val serviceJob = Job()
    private val serviceScope =
        CoroutineScope(serviceJob + Dispatchers.Main)

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

    private var backgroundBitmap: Bitmap? = null

    // ---------------------------------------------------------
    // PERSONAL VOCABULARY
    // ---------------------------------------------------------

    /**
     * Stores words learned from the user's typing.
     *
     * Example:
     *
     * typing:
     * "Bigi"
     *
     * repeatedly will eventually make:
     *
     * "Bigi"
     *
     * available as a suggestion even if it doesn't exist in the
     * bundled English dictionary.
     */
    private lateinit var vocabularyPrefs:
            android.content.SharedPreferences

    /**
     * Word -> number of times used.
     */
    private val learnedWords = LinkedHashMap<String, Int>()

    /**
     * Minimum number of times a word should be used before it becomes
     * a strong learned suggestion.
     */
    private val learningThreshold = 2

    /**
     * Current raw word being typed.
     *
     * IMPORTANT:
     * Apostrophes are allowed here so words such as:
     *
     * don't
     * can't
     * wouldn't
     * I'm
     * you're
     *
     * remain one logical word.
     */
    private val rawWordBuffer = StringBuilder()

    /**
     * Number of encoded characters produced for each logical character.
     */
    private val encodedLengthsPerChar = mutableListOf<Int>()

    // ---------------------------------------------------------
    // COMMON ENGLISH CONTRACTIONS
    // ---------------------------------------------------------

    /**
     * Common contractions are included directly so CipherKeys does not
     * depend entirely on the bundled dictionary for them.
     */
    private val commonContractions = listOf(
        "ain't",
        "aren't",
        "can't",
        "couldn't",
        "could've",
        "couldn't've",
        "didn't",
        "doesn't",
        "don't",
        "hadn't",
        "hasn't",
        "haven't",
        "he'd",
        "he'll",
        "he's",
        "how'd",
        "how'll",
        "how's",
        "I'd",
        "I'll",
        "I'm",
        "I've",
        "isn't",
        "it'd",
        "it'll",
        "it's",
        "let's",
        "might've",
        "mustn't",
        "must've",
        "needn't",
        "she'd",
        "she'll",
        "she's",
        "shouldn't",
        "should've",
        "that'd",
        "that'll",
        "that's",
        "there'd",
        "there'll",
        "there's",
        "they'd",
        "they'll",
        "they're",
        "they've",
        "wasn't",
        "we'd",
        "we'll",
        "we're",
        "we've",
        "weren't",
        "what'd",
        "what'll",
        "what's",
        "when'd",
        "when's",
        "where'd",
        "where's",
        "who'd",
        "who'll",
        "who's",
        "why'd",
        "why's",
        "won't",
        "wouldn't",
        "would've",
        "you'd",
        "you'll",
        "you're",
        "you've"
    )

    // ---------------------------------------------------------
    // LIFECYCLE
    // ---------------------------------------------------------

    override fun onCreate() {
        super.onCreate()

        settingsRepository =
            SettingsRepository(applicationContext)

        dictionary =
            EnglishLexicon(applicationContext)

        recentEmojiStore =
            RecentEmojiStore(applicationContext)

        vocabularyPrefs =
            getSharedPreferences(
                "cipherkeys_personal_vocabulary",
                Context.MODE_PRIVATE
            )

        loadLearnedWords()

        rebuildEncoders(emptyMap())

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
                        settings.backgroundImagePath?.let {
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
                        if (settings.useImageBackground)
                            backgroundBitmap
                        else
                            null

                    keyboardView.setBackgroundImage(
                        bitmapToShow,
                        settings.backgroundOverlayAlpha
                    )
                }
            }
        }

        serviceScope.launch {

            recentEmojiStore.recentsFlow.collect { recents ->

                if (::keyboardView.isInitialized) {
                    keyboardView.setRecentEmoji(recents)
                }
            }
        }
    }

    // ---------------------------------------------------------
    // ENCODERS
    // ---------------------------------------------------------

    private fun rebuildEncoders(
        customMappings: Map<Char, List<String>>
    ) {
        classicEncoder =
            ClassicLeetEncoder(customMappings)

        eliteEncoder =
            EliteEncoder(customMappings)

        hackerEncoder =
            HackerEncoder(customMappings)

        ultraEncoder =
            UltraEncoder(customMappings)

        decoder =
            CipherKeysDecoder(customMappings)
    }

    // ---------------------------------------------------------
    // BACKGROUND
    // ---------------------------------------------------------

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

            } catch (e: Exception) {

                null
            }
        }

    // ---------------------------------------------------------
    // INPUT VIEW
    // ---------------------------------------------------------

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
            if (currentSettings.useImageBackground)
                backgroundBitmap
            else
                null,
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

        keyboardView.setMode(
            currentMode
        )

        keyboardView.setShiftState(
            false
        )

        finalizeWord()

        if (currentSettings.autoDecodeEnabled) {
            decodeExistingFieldText()
        }
    }

    // ---------------------------------------------------------
    // CHARACTER INPUT
    // ---------------------------------------------------------

    override fun onCharKey(char: Char) {

        val ic =
            currentInputConnection ?: return

        val effectiveChar =
            if (shiftEnabled)
                char.uppercaseChar()
            else
                char

        /*
         * Apostrophe is special.
         *
         * It belongs inside contractions instead of ending
         * the current word.
         *
         * Example:
         *
         * don + ' + t
         *
         * remains:
         *
         * don't
         */
        val isApostrophe =
            effectiveChar == '\'' ||
                    effectiveChar == '’'

        val isLetter =
            effectiveChar.isLetter()

        val isWordCharacter =
            isLetter || isApostrophe

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

        if (isWordCharacter) {

            /*
             * Don't allow an apostrophe to start a word by itself.
             */
            if (
                isApostrophe &&
                rawWordBuffer.isEmpty()
            ) {

                finalizeWord()

            } else {

                rawWordBuffer.append(
                    effectiveChar
                )

                encodedLengthsPerChar.add(
                    output.length
                )

                updateSuggestions()
            }

        } else {

            /*
             * Punctuation ends the word.
             */
            finalizeWord()
        }

        /*
         * Normal keyboard behaviour:
         * Shift automatically turns off after one character.
         */
        if (shiftEnabled) {

            shiftEnabled = false

            keyboardView.setShiftState(
                false
            )
        }
    }

    // ---------------------------------------------------------
    // SPACE
    // ---------------------------------------------------------

    override fun onSpace() {

        maybeAutocorrectBeforeBoundary()

        learnCurrentWord()

        currentInputConnection?.commitText(
            " ",
            1
        )

        performFeedback()

        finalizeWord()
    }

    // ---------------------------------------------------------
    // BACKSPACE
    // ---------------------------------------------------------

    override fun onBackspace() {

        val ic =
            currentInputConnection ?: return

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

            val lastIndex =
                encodedLengthsPerChar.lastIndex

            if (lastIndex >= 0) {

                val encodedLength =
                    encodedLengthsPerChar.removeAt(
                        lastIndex
                    )

                rawWordBuffer.deleteCharAt(
                    rawWordBuffer.lastIndex
                )

                ic.deleteSurroundingText(
                    encodedLength,
                    0
                )

            } else {

                ic.deleteSurroundingText(
                    1,
                    0
                )
            }

            updateSuggestions()

        } else {

            ic.deleteSurroundingText(
                1,
                0
            )
        }

        performFeedback()
    }

    // ---------------------------------------------------------
    // ENTER
    // ---------------------------------------------------------

    override fun onEnter() {

        val ic =
            currentInputConnection ?: return

        maybeAutocorrectBeforeBoundary()

        learnCurrentWord()

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
    }

    // ---------------------------------------------------------
    // SHIFT
    // ---------------------------------------------------------

    override fun onShiftToggle() {

        shiftEnabled =
            !shiftEnabled
    }

    // ---------------------------------------------------------
    // MODE
    // ---------------------------------------------------------

    override fun onModeSelected(
        mode: KeyboardMode
    ) {

        currentMode =
            mode

        finalizeWord()

        if (mode == KeyboardMode.DECODE) {
            decodeExistingFieldText()
        }
    }

    // ---------------------------------------------------------
    // SUGGESTION SELECTED
    // ---------------------------------------------------------

    override fun onSuggestionSelected(
        word: String
    ) {

        val ic =
            currentInputConnection
                ?: return

        replaceCurrentWord(
            ic,
            word
        )

        learnWord(
            word
        )

        ic.commitText(
            " ",
            1
        )

        finalizeWord()

        performFeedback()
    }

    // ---------------------------------------------------------
    // EMOJI
    // ---------------------------------------------------------

    override fun onEmojiSelected(
        emoji: String
    ) {

        val ic =
            currentInputConnection
                ?: return

        learnCurrentWord()

        finalizeWord()

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
    // PERSONAL VOCABULARY SYSTEM
    // =========================================================

    /**
     * Loads the user's learned words from persistent storage.
     */
    private fun loadLearnedWords() {

        learnedWords.clear()

        val all =
            vocabularyPrefs.all

        for ((key, value) in all) {

            val count =
                value as? Int ?: continue

            if (key.isNotBlank()) {

                learnedWords[
                    key
                ] = count
            }
        }
    }

    /**
     * Saves the current learned vocabulary.
     */
    private fun saveLearnedWords() {

        val editor =
            vocabularyPrefs.edit()

        editor.clear()

        for ((word, count) in learnedWords) {

            editor.putInt(
                word,
                count
            )
        }

        editor.apply()
    }

    /**
     * Learns the current word.
     *
     * Words shorter than two characters are ignored.
     *
     * Symbols alone are ignored.
     */
    private fun learnCurrentWord() {

        val word =
            rawWordBuffer
                .toString()
                .trim()

        if (word.length < 2) {
            return
        }

        if (
            !word.any {
                it.isLetter()
            }
        ) {
            return
        }

        learnWord(
            word
        )
    }

    /**
     * Adds/increments a word in the personal vocabulary.
     */
    private fun learnWord(
        word: String
    ) {

        val normalized =
            word.trim()
                .lowercase()

        if (normalized.length < 2) {
            return
        }

        if (
            !normalized.any {
                it.isLetter()
            }
        ) {
            return
        }

        val currentCount =
            learnedWords[normalized]
                ?: 0

        learnedWords[
            normalized
        ] =
            currentCount + 1

        /*
         * Prevent unlimited growth.
         *
         * Once a word has been used many times, there is no
         * need to keep increasing its number forever.
         */
        if (
            learnedWords.size > 5000
        ) {

            val leastUsed =
                learnedWords
                    .entries
                    .minByOrNull {
                        it.value
                    }

            if (
                leastUsed != null &&
                leastUsed.value <= 2
            ) {

                learnedWords.remove(
                    leastUsed.key
                )
            }
        }

        saveLearnedWords()
    }

    /**
     * Returns true when a word is known either by:
     *
     * - Built-in dictionary
     * - Common contraction list
     * - Personal vocabulary
     */
    private fun isKnownWord(
        word: String
    ): Boolean {

        if (word.isBlank()) {
            return false
        }

        val lower =
            word.lowercase()

        if (
            dictionary.isValidWord(
                lower
            )
        ) {
            return true
        }

        if (
            commonContractions.any {
                it.equals(
                    lower,
                    ignoreCase = true
                )
            }
        ) {
            return true
        }

        return learnedWords.containsKey(
            lower
        )
    }

    // =========================================================
    // SMART SUGGESTIONS
    // =========================================================

    private fun updateSuggestions() {

        if (!::keyboardView.isInitialized) {
            return
        }

        val prefix =
            rawWordBuffer.toString()

        if (prefix.isEmpty()) {

            keyboardView.setSuggestions(
                emptyList()
            )

            return
        }

        val lowerPrefix =
            prefix.lowercase()

        /*
         * Built-in dictionary suggestions.
         */
        val dictionarySuggestions =
            dictionary
                .suggestCompletions(
                    prefix,
                    8
                )

        /*
         * Common contractions.
         */
        val contractionSuggestions =
            commonContractions
                .filter {
                    it.lowercase()
                        .startsWith(
                            lowerPrefix
                        )
                }

        /*
         * Personal vocabulary.
         *
         * Frequently used words are ranked first.
         */
        val personalSuggestions =
            learnedWords
                .filter { (word, _) ->
                    word.startsWith(
                        lowerPrefix
                    ) &&
                            word != lowerPrefix
                }
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> {
                        it.value
                    }.thenBy {
                        it.key.length
                    }
                )
                .map {
                    it.key
                }

        /*
         * Merge everything together.
         *
         * LinkedHashSet removes duplicates while preserving order.
         */
        val merged =
            LinkedHashSet<String>()

        /*
         * Personal vocabulary gets priority because it represents
         * the user's actual language.
         */
        personalSuggestions.forEach {
            merged.add(it)
        }

        contractionSuggestions.forEach {
            merged.add(it)
        }

        dictionarySuggestions.forEach {
            merged.add(it)
        }

        /*
         * Never show more than three suggestions.
         */
        val finalSuggestions =
            merged
                .take(3)

        keyboardView.setSuggestions(
            finalSuggestions
        )
    }

    // =========================================================
    // WORD BOUNDARY
    // =========================================================

    private fun finalizeWord() {

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()

        if (::keyboardView.isInitialized) {

            keyboardView.setSuggestions(
                emptyList()
            )
        }
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
            rawWordBuffer.toString()

        if (word.length < 3) {
            return
        }

        /*
         * Don't autocorrect contractions.
         */
        if (
            word.contains("'") ||
            word.contains("’")
        ) {
            return
        }

        /*
         * Don't autocorrect words the user has taught
         * CipherKeys.
         */
        if (
            isKnownWord(word)
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

        learnWord(
            correction
        )
    }

    // =========================================================
    // REPLACE CURRENT WORD
    // =========================================================

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
            if (
                currentSettings.autoEncodeEnabled
            ) {

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
    // DECODE EXISTING FIELD
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

            val audioManager =
                getSystemService(
                    Context.AUDIO_SERVICE
                ) as? AudioManager

            audioManager?.playSoundEffect(
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

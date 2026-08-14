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
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.cipherkeys.app.data.KeyboardMode
import com.cipherkeys.app.data.KeyboardSettings
import com.cipherkeys.app.data.SettingsRepository
import com.cipherkeys.app.data.KeyboardTheme
import com.cipherkeys.app.decoder.CipherKeysDecoder
import com.cipherkeys.app.encoder.ClassicLeetEncoder
import com.cipherkeys.app.encoder.Encoder
import com.cipherkeys.app.encoder.EliteEncoder
import com.cipherkeys.app.encoder.HackerEncoder
import com.cipherkeys.app.encoder.UltraEncoder
import com.cipherkeys.app.dictionary.Dictionary
import com.cipherkeys.app.dictionary.EnglishLexicon
import com.cipherkeys.app.emoji.RecentEmojiStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The main CipherKeys Android keyboard service.
 *
 * CipherKeys contains:
 *
 * - Normal typing
 * - Leet / Elite / Hacker / Ultra modes
 * - Suggestions
 * - Personal vocabulary
 * - Learned words
 * - Contextual word prediction
 * - Common contractions
 * - Autocorrect
 * - Emoji
 * - Background/theme support
 * - Vibration
 * - Key sounds
 *
 * The learning system works entirely on-device.
 *
 * CipherKeys learns:
 *
 * 1. Words the user repeatedly types.
 * 2. Which words commonly follow other words.
 *
 * Example:
 *
 *     "good morning"
 *
 * After repeated use:
 *
 *     good -> morning
 *
 * becomes a learned relationship.
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

    private var shiftEnabled = false

    private lateinit var classicEncoder: Encoder
    private lateinit var eliteEncoder: Encoder
    private lateinit var hackerEncoder: Encoder
    private lateinit var ultraEncoder: Encoder
    private lateinit var decoder: CipherKeysDecoder

    private lateinit var dictionary: Dictionary
    private lateinit var recentEmojiStore: RecentEmojiStore

    private var backgroundBitmap: Bitmap? = null

    // =========================================================
    // PERSONAL VOCABULARY
    // =========================================================

    /**
     * Stores the number of times each personal word has been used.
     *
     * Example:
     *
     * "kachiarts" -> 14
     * "bro"       -> 8
     * "onye"      -> 5
     */
    private lateinit var vocabularyPrefs:
        android.content.SharedPreferences

    private val learnedWords =
        LinkedHashMap<String, Int>()

    // =========================================================
    // CONTEXTUAL LEARNING
    // =========================================================

    /**
     * Stores word -> following word relationships.
     *
     * Example:
     *
     * good -> morning = 12
     * good -> evening = 4
     *
     * This allows CipherKeys to understand that after
     * "good", "morning" is more likely than "zebra".
     */
    private lateinit var contextPrefs:
        android.content.SharedPreferences

    /**
     * Current previous completed word.
     *
     * Example:
     *
     * User types:
     *
     * "good "
     *
     * previousWord becomes:
     *
     * "good"
     */
    private var previousWord = ""

    /**
     * Maximum number of contextual relationships stored.
     */
    private val maximumContextEntries = 10000

    // =========================================================
    // WORD BUFFER
    // =========================================================

    /**
     * Raw English word currently being typed.
     *
     * This is deliberately separate from what is actually committed
     * to the text field because CipherKeys modes may transform:
     *
     * h -> |-| 
     *
     * while the raw word remains:
     *
     * h
     */
    private val rawWordBuffer =
        StringBuilder()

    /**
     * Number of committed encoded characters produced by each
     * raw character.
     */
    private val encodedLengthsPerChar =
        mutableListOf<Int>()

    // =========================================================
    // COMMON CONTRACTIONS
    // =========================================================

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

    // =========================================================
    // CREATE
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

        recentEmojiStore =
            RecentEmojiStore(
                applicationContext
            )

        vocabularyPrefs =
            getSharedPreferences(
                "cipherkeys_personal_vocabulary",
                Context.MODE_PRIVATE
            )

        contextPrefs =
            getSharedPreferences(
                "cipherkeys_context_learning",
                Context.MODE_PRIVATE
            )

        loadLearnedWords()

        rebuildEncoders(
            emptyMap()
        )

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

                    keyboardView.setBackgroundImage(
                        if (settings.useImageBackground)
                            backgroundBitmap
                        else
                            null,
                        settings.backgroundOverlayAlpha
                    )
                }
            }
        }

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
                e: Exception
            ) {

                null
            }
        }

    // =========================================================
    // INPUT VIEW
    // =========================================================

    override fun onCreateInputView(): View {

        val view =
            CipherKeysKeyboardView(this)

        view.listener =
            this

        keyboardView =
            view

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

    // =========================================================
    // START INPUT
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

        shiftEnabled =
            false

        keyboardView.setMode(
            currentMode
        )

        keyboardView.setShiftState(
            false
        )

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()

        /*
         * Try to determine the word immediately before the cursor.
         *
         * This means if the user opens a text field containing:
         *
         * "good "
         *
         * CipherKeys can immediately understand that the previous
         * word is "good".
         */
        previousWord =
            readPreviousWordFromEditor()

        updateSuggestions()

        if (
            currentSettings.autoDecodeEnabled
        ) {

            decodeExistingFieldText()
        }
    }

    // =========================================================
    // CHARACTER INPUT
    // =========================================================

    override fun onCharKey(
        char: Char
    ) {

        val ic =
            currentInputConnection
                ?: return

        val effectiveChar =
            if (shiftEnabled)
                char.uppercaseChar()
            else
                char

        val isApostrophe =
            effectiveChar == '\'' ||
                    effectiveChar == '’'

        val isLetter =
            effectiveChar.isLetter()

        val isWordCharacter =
            isLetter ||
                    isApostrophe

        val output =
            if (
                currentSettings.autoEncodeEnabled
            ) {

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
             * Apostrophe cannot begin a word.
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

            completeCurrentWord()

            /*
             * A punctuation character is itself a boundary.
             */
            if (
                effectiveChar != '\'' &&
                effectiveChar != '’'
            ) {

                updateSuggestions()
            }
        }

        if (shiftEnabled) {

            shiftEnabled =
                false

            keyboardView.setShiftState(
                false
            )
        }
    }

    // =========================================================
    // SPACE
    // =========================================================

    override fun onSpace() {

        maybeAutocorrectBeforeBoundary()

        val completedWord =
            rawWordBuffer
                .toString()
                .trim()

        if (completedWord.isNotEmpty()) {

            /*
             * Learn the word itself.
             */
            learnWord(
                completedWord
            )

            /*
             * Learn the relationship:
             *
             * previousWord -> completedWord
             */
            if (
                previousWord.isNotEmpty()
            ) {

                learnContextPair(
                    previousWord,
                    completedWord
                )
            }

            /*
             * This word now becomes the previous word.
             */
            previousWord =
                completedWord.lowercase()
        }

        currentInputConnection?.commitText(
            " ",
            1
        )

        performFeedback()

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()

        /*
         * Immediately show contextual suggestions.
         */
        updateSuggestions()
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

        if (
            !selected.isNullOrEmpty()
        ) {

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

            /*
             * Re-read the word before the cursor after deleting.
             */
            previousWord =
                readPreviousWordFromEditor()

            updateSuggestions()
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

        maybeAutocorrectBeforeBoundary()

        val completedWord =
            rawWordBuffer
                .toString()
                .trim()

        if (
            completedWord.isNotEmpty()
        ) {

            learnWord(
                completedWord
            )

            if (
                previousWord.isNotEmpty()
            ) {

                learnContextPair(
                    previousWord,
                    completedWord
                )
            }
        }

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

        previousWord = ""

        finalizeWord()
    }

    // =========================================================
    // SHIFT
    // =========================================================

    override fun onShiftToggle() {

        shiftEnabled =
            !shiftEnabled
    }

    // =========================================================
    // MODE
    // =========================================================

    override fun onModeSelected(
        mode: KeyboardMode
    ) {

        currentMode =
            mode

        finalizeWord()

        if (
            mode == KeyboardMode.DECODE
        ) {

            decodeExistingFieldText()
        }
    }

    // =========================================================
    // SUGGESTION CLICK
    // =========================================================
override fun onSuggestionSelected(word: String) {
    val ic = currentInputConnection ?: return

    // Treat selecting a suggestion as a strong indication
    // that the user actually wanted this word.
    dictionary.learnWord(word)

    replaceCurrentWord(ic, word)

    ic.commitText(" ", 1)

    finalizeWord()

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

        updateSuggestions()
    }

    // =========================================================
    // PERSONAL VOCABULARY
    // =========================================================

    private fun loadLearnedWords() {

        learnedWords.clear()

        for (
            entry in vocabularyPrefs.all
        ) {

            val count =
                entry.value as? Int
                    ?: continue

            if (
                entry.key.isNotBlank()
            ) {

                learnedWords[
                    entry.key
                ] = count
            }
        }
    }

    private fun saveLearnedWords() {

        val editor =
            vocabularyPrefs.edit()

        editor.clear()

        for (
            (word, count) in learnedWords
        ) {

            editor.putInt(
                word,
                count
            )
        }

        editor.apply()
    }

    private fun learnWord(
        word: String
    ) {

        val normalized =
            word
                .trim()
                .lowercase()

        if (
            normalized.length < 2
        ) {
            return
        }

        if (
            !normalized.any {
                it.isLetter()
            }
        ) {
            return
        }

        learnedWords[
            normalized
        ] =
            (learnedWords[
                normalized
            ] ?: 0) + 1

        /*
         * Keep the vocabulary from becoming enormous.
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

    // =========================================================
    // CONTEXT STORAGE
    // =========================================================

    /**
     * Encodes a word safely so it can be used inside a
     * SharedPreferences key.
     */
    private fun encodeStorageWord(
        word: String
    ): String {

        return Base64.encodeToString(
            word.lowercase().toByteArray(),
            Base64.NO_WRAP
        )
    }

    /**
     * Creates a unique key for:
     *
     * previous word + next word
     */
    private fun contextKey(
        previous: String,
        next: String
    ): String {

        return "next_" +
                encodeStorageWord(previous) +
                "_" +
                encodeStorageWord(next)
    }

    /**
     * Learns:
     *
     * previous -> next
     */
    private fun learnContextPair(
        previous: String,
        next: String
    ) {

        val first =
            previous
                .trim()
                .lowercase()

        val second =
            next
                .trim()
                .lowercase()

        if (
            first.length < 1 ||
            second.length < 1
        ) {
            return
        }

        if (
            !first.any { it.isLetter() } ||
            !second.any { it.isLetter() }
        ) {
            return
        }

        val key =
            contextKey(
                first,
                second
            )

        val current =
            contextPrefs.getInt(
                key,
                0
            )

        contextPrefs.edit()
            .putInt(
                key,
                current + 1
            )
            .apply()

        trimContextStorage()
    }

    /**
     * Returns the words most frequently used after [word].
     */
    private fun getContextSuggestions(
        word: String,
        prefix: String = "",
        limit: Int = 8
    ): List<String> {

        if (
            word.isBlank()
        ) {
            return emptyList()
        }

        val encodedPrevious =
            encodeStorageWord(
                word
            )

        val searchPrefix =
            prefix.lowercase()

        val results =
            mutableListOf<Pair<String, Int>>()

        for (
            entry in contextPrefs.all
        ) {

            val key =
                entry.key

            if (
                !key.startsWith(
                    "next_${encodedPrevious}_"
                )
            ) {
                continue
            }

            val count =
                entry.value as? Int
                    ?: continue

            val encodedNext =
                key.substringAfter(
                    "next_${encodedPrevious}_"
                )

            val nextWord =
                try {

                    String(
                        Base64.decode(
                            encodedNext,
                            Base64.NO_WRAP
                        )
                    )

                } catch (
                    e: Exception
                ) {

                    continue
                }

            if (
                searchPrefix.isNotEmpty() &&
                !nextWord.startsWith(
                    searchPrefix
                )
            ) {
                continue
            }

            results.add(
                nextWord to count
            )
        }

        return results
            .sortedWith(
                compareByDescending<Pair<String, Int>> {
                    it.second
                }.thenBy {
                    it.first.length
                }
            )
            .take(limit)
            .map {
                it.first
            }
    }

    /**
     * Prevents contextual storage from growing forever.
     *
     * We keep the strongest relationships.
     */
    private fun trimContextStorage() {

        val all =
            contextPrefs.all

        if (
            all.size <= maximumContextEntries
        ) {
            return
        }

        val entries =
            all.entries
                .mapNotNull { entry ->

                    val count =
                        entry.value as? Int
                            ?: return@mapNotNull null

                    entry.key to count
                }
                .sortedBy {
                    it.second
                }

        val removeCount =
            all.size -
                    maximumContextEntries

        val editor =
            contextPrefs.edit()

        entries
            .take(removeCount)
            .forEach {
                editor.remove(
                    it.first
                )
            }

        editor.apply()
    }

    // =========================================================
    // WORD KNOWLEDGE
    // =========================================================

    private fun isKnownWord(
        word: String
    ): Boolean {

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

        if (
            !::keyboardView.isInitialized
        ) {
            return
        }

        val prefix =
            rawWordBuffer
                .toString()
                .lowercase()

        /*
         * -----------------------------------------------------
         * CASE 1:
         *
         * No word is currently being typed.
         *
         * Example:
         *
         * "good "
         *
         * Show:
         *
         * morning | evening | night
         * -----------------------------------------------------
         */
        if (
            prefix.isEmpty()
        ) {

            val contextual =
                getContextSuggestions(
                    previousWord,
                    "",
                    3
                )

            keyboardView.setSuggestions(
                contextual
            )

            return
        }

        /*
         * -----------------------------------------------------
         * CASE 2:
         *
         * User is currently typing.
         *
         * Example:
         *
         * previous = "good"
         * prefix   = "m"
         *
         * Context:
         *
         * morning
         *
         * is ranked highly.
         * -----------------------------------------------------
         */

        val contextual =
            getContextSuggestions(
                previousWord,
                prefix,
                8
            )

        val personal =
            learnedWords
                .filter {
                    it.key.startsWith(
                        prefix
                    ) &&
                            it.key != prefix
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
                .take(8)

        val contractions =
            commonContractions
                .filter {
                    it.lowercase()
                        .startsWith(
                            prefix
                        )
                }

        val dictionarySuggestions =
            dictionary
                .suggestCompletions(
                    prefix,
                    8
                )

        /*
         * Contextual predictions receive the highest priority.
         *
         * Then personal vocabulary.
         *
         * Then contractions.
         *
         * Then normal dictionary.
         */
        val merged =
            LinkedHashSet<String>()

        contextual.forEach {
            merged.add(it)
        }

        personal.forEach {
            merged.add(it)
        }

        contractions.forEach {
            merged.add(it)
        }

        dictionarySuggestions.forEach {
            merged.add(it)
        }

        keyboardView.setSuggestions(
            merged.take(3)
        )
    }

    // =========================================================
    // COMPLETE CURRENT WORD
    // =========================================================

    /**
     * Finishes the current word and teaches CipherKeys about it.
     *
     * This is used when punctuation/emoji/etc. creates a boundary.
     */
    private fun completeCurrentWord() {

        val word =
            rawWordBuffer
                .toString()
                .trim()

        if (
            word.isNotEmpty()
        ) {

            learnWord(
                word
            )

            if (
                previousWord.isNotEmpty()
            ) {

                learnContextPair(
                    previousWord,
                    word
                )
            }

            previousWord =
                word.lowercase()
        }

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()
    }

    private fun finalizeWord() {

    // Teach the dictionary what the user just typed.
    // This makes frequently used personal words available
    // as future suggestions.
    val completedWord = rawWordBuffer.toString().trim()

    if (completedWord.length >= 2) {
        dictionary.learnWord(completedWord)
    }

    rawWordBuffer.clear()
    encodedLengthsPerChar.clear()

    if (::keyboardView.isInitialized) {
        keyboardView.setSuggestions(emptyList())
    }
}

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

        if (
            word.length < 3
        ) {
            return
        }

        /*
         * Contractions should never be autocorrected using
         * normal dictionary edit distance.
         */
        if (
            word.contains("'") ||
            word.contains("’")
        ) {
            return
        }

        if (
            isKnownWord(word)
        ) {
            return
        }

        val correction =
            dictionary
                .suggestCorrections(
                    word,
                    1
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

        val output =
            encodeWordIfNecessary(
                word
            )

        ic.commitText(
            output,
            1
        )

        rawWordBuffer.clear()

        encodedLengthsPerChar.clear()
    }

    // =========================================================
    // ENCODE WORD
    // =========================================================

    private fun encodeWordIfNecessary(
        word: String
    ): String {

        if (
            !currentSettings.autoEncodeEnabled
        ) {
            return word
        }

        return when (currentMode) {

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
    }

    // =========================================================
    // READ PREVIOUS WORD
    // =========================================================

    /**
     * Reads the word immediately before the cursor.
     *
     * This makes contextual suggestions work even when the
     * keyboard is opened in the middle of an existing sentence.
     */
    private fun readPreviousWordFromEditor(): String {

        val ic =
            currentInputConnection
                ?: return ""

        val before =
            ic.getTextBeforeCursor(
                200,
                0
            )?.toString()
                .orEmpty()

        if (
            before.isBlank()
        ) {
            return ""
        }

        /*
         * Remove trailing spaces/punctuation.
         */
        val cleaned =
            before
                .trimEnd()
                .replace(
                    Regex(
                        "[\\s.,!?;:()\\[\\]{}]+$"
                    ),
                    ""
                )

        if (
            cleaned.isEmpty()
        ) {
            return ""
        }

        /*
         * Capture the last word, including apostrophes.
         */
        val match =
            Regex(
                "([A-Za-zÀ-ÿ]+(?:['’][A-Za-zÀ-ÿ]+)*)$"
            ).find(
                cleaned
            )

        return match
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
            .orEmpty()
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

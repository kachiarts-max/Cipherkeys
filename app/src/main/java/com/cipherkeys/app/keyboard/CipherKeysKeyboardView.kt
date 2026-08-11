package com.cipherkeys.app.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.cipherkeys.app.data.KeyboardMode
import com.cipherkeys.app.data.KeyboardTheme
import com.cipherkeys.app.data.ThemeColorSet
import com.cipherkeys.app.emoji.EmojiCategory
import com.cipherkeys.app.emoji.EmojiRepository

/**
 * The keyboard's visual surface: a mode-switching toolbar on top, QWERTY key rows below,
 * and a swappable emoji panel that takes over the same space as the QWERTY rows when
 * toggled (so overall keyboard height doesn't change). Built entirely with framework
 * views in code (no XML layout / KeyboardView resource format) so the row/key structure
 * is easy to read, test, and re-theme.
 *
 * This view is dumb by design: it only reports raw user actions via
 * [KeyboardActionListener]. All text-transform logic (encoding/decoding) lives in
 * [CipherKeysIME] / encoder classes, not here - and emoji insertion deliberately bypasses
 * that layer entirely (see [CipherKeysIME.onEmojiSelected]), since cipher-encoding an
 * emoji wouldn't mean anything.
 */
class CipherKeysKeyboardView(context: Context) : LinearLayout(context) {

    var listener: KeyboardActionListener? = null

    private var shiftEnabled: Boolean = false
    private var currentMode: KeyboardMode = KeyboardMode.default()
    private var currentTheme: KeyboardTheme = KeyboardTheme.default()
    private var customColors: ThemeColorSet = ThemeColorSet.default()
    private var heightScale: Float = 1.0f
    private var currentKeyTextColor: Int = Color.WHITE

    private val letterButtons = mutableListOf<Button>()
    private val modeButtons = mutableMapOf<KeyboardMode, Button>()
    private lateinit var modeLabel: TextView
    private lateinit var shiftButton: Button
    private val suggestionChips = mutableListOf<TextView>()

    private lateinit var qwertyContainer: LinearLayout
    private lateinit var emojiPanel: LinearLayout
    private lateinit var emojiGridContainer: LinearLayout
    private lateinit var emojiToggleButton: Button
    private val emojiCategoryTabs = mutableMapOf<EmojiCategory, Button>()
    private var currentEmojiCategory: EmojiCategory = EmojiCategory.SMILEYS
    private var recentEmojiList: List<String> = emptyList()

    private val row1Keys = "qwertyuiop"
    private val row2Keys = "asdfghjkl"
    private val row3Keys = "zxcvbnm"
    private val numberKeys = "1234567890"

    init {
        orientation = VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        buildToolbarRow()
        buildStatusRow()
        buildSuggestionStrip()
        buildQwertyContainer()
        buildEmojiPanel()
        applyTheme()
    }

    // ---------- Public API used by the IME ----------

    fun setMode(mode: KeyboardMode) {
        currentMode = mode
        modeButtons.forEach { (m, btn) ->
            btn.setTypeface(null, if (m == mode) Typeface.BOLD else Typeface.NORMAL)
            btn.alpha = if (m == mode) 1.0f else 0.6f
        }
        modeLabel.text = "Mode: ${mode.label}"
    }

    fun setShiftState(enabled: Boolean) {
        shiftEnabled = enabled
        shiftButton.text = if (enabled) "\u21E7 SHIFT" else "shift"
        shiftButton.setTypeface(null, if (enabled) Typeface.BOLD else Typeface.NORMAL)
        letterButtons.forEach { btn ->
            btn.text = if (enabled) btn.text.toString().uppercase() else btn.text.toString().lowercase()
        }
    }

    fun applyThemeAndHeight(theme: KeyboardTheme, heightScale: Float, customColors: ThemeColorSet) {
        currentTheme = theme
        this.heightScale = heightScale
        this.customColors = customColors
        applyTheme()
    }

    /**
     * Updates the 3 suggestion chips above the keyboard. Pass an empty list to hide
     * suggestions (chips still occupy their row, just render blank/non-clickable) -
     * this keeps the overall keyboard height stable rather than jumping around as the
     * user types, which would be jarring.
     */
    fun setSuggestions(words: List<String>) {
        suggestionChips.forEachIndexed { index, chip ->
            val word = words.getOrNull(index)
            chip.text = word.orEmpty()
            chip.isEnabled = word != null
            chip.alpha = if (word != null) 1.0f else 0.0f
        }
    }

    /** Pushes the latest recently-used emoji list; re-renders the grid if RECENT is open. */
    fun setRecentEmoji(emoji: List<String>) {
        recentEmojiList = emoji
        if (currentEmojiCategory == EmojiCategory.RECENT && emojiPanel.visibility == VISIBLE) {
            renderEmojiGrid(EmojiCategory.RECENT)
        }
    }

    // ---------- Row builders ----------

    private fun buildToolbarRow() {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
        }
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        KeyboardMode.entries.forEach { mode ->
            val btn = Button(context).apply {
                text = mode.label
                textSize = 12f
                isAllCaps = false
                setPadding(dp(12), dp(2), dp(12), dp(2))
                setOnClickListener {
                    setMode(mode)
                    listener?.onModeSelected(mode)
                }
            }
            modeButtons[mode] = btn
            row.addView(btn, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = dp(4) })
        }
        emojiToggleButton = Button(context).apply {
            text = "\uD83D\uDE00"
            textSize = 14f
            isAllCaps = false
            setPadding(dp(12), dp(2), dp(12), dp(2))
            setOnClickListener { toggleEmojiPanel() }
        }
        row.addView(emojiToggleButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { marginEnd = dp(4) })
        scroll.addView(row)
        addView(scroll)
    }

    private fun buildStatusRow() {
        modeLabel = TextView(context).apply {
            text = "Mode: ${currentMode.label}"
            textSize = 11f
            setPadding(dp(8), dp(2), dp(8), dp(4))
        }
        addView(modeLabel)
    }

    private fun buildSuggestionStrip() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32))
            gravity = Gravity.CENTER_VERTICAL
        }
        repeat(3) {
            val chip = TextView(context

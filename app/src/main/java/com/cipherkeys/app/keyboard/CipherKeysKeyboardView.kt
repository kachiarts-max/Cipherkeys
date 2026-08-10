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

    fun applyThemeAndHeight(theme: KeyboardTheme, heightScale: Float) {
        currentTheme = theme
        this.heightScale = heightScale
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
            val chip = TextView(context).apply {
                textSize = 13f
                gravity = Gravity.CENTER
                setOnClickListener {
                    val word = text.toString()
                    if (word.isNotEmpty()) listener?.onSuggestionSelected(word)
                }
            }
            suggestionChips.add(chip)
            row.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        addView(row)
    }

    /** Wraps the 5 QWERTY-area rows in one container so it can be hidden/shown as a unit
     *  when the emoji panel toggles, without disturbing their individual weights. */
    private fun buildQwertyContainer() {
        qwertyContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 5f)
        }
        addView(qwertyContainer)
        buildNumberRow(qwertyContainer)
        buildRow(qwertyContainer, row1Keys, sidePadding = 0)
        buildRow(qwertyContainer, row2Keys, sidePadding = 20)
        buildRow3WithShiftAndBackspace(qwertyContainer)
        buildBottomRow(qwertyContainer)
    }

    private fun buildNumberRow(parent: ViewGroup) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        numberKeys.forEach { c ->
            row.addView(
                makeCharKey(c.toString(), isLetter = false),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
        }
        parent.addView(row)
    }

    private fun buildRow(parent: ViewGroup, keys: String, sidePadding: Int) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setPadding(dp(sidePadding), 0, dp(sidePadding), 0)
        }
        keys.forEach { c ->
            row.addView(
                makeCharKey(c.toString(), isLetter = true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
        }
        parent.addView(row)
    }

    private fun buildRow3WithShiftAndBackspace(parent: ViewGroup) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        shiftButton = Button(context).apply {
            text = "shift"
            textSize = 12f
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setOnClickListener {
                setShiftState(!shiftEnabled)
                listener?.onShiftToggle()
            }
        }
        row.addView(shiftButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f))
        row3Keys.forEach { c ->
            row.addView(
                makeCharKey(c.toString(), isLetter = true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
        }
        val backspaceButton = Button(context).apply {
            text = "\u232B"
            textSize = 16f
            minimumWidth = 0
            minimumHeight = 0
            setOnClickListener { listener?.onBackspace() }
        }
        row.addView(backspaceButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f))
        parent.addView(row)
    }

    private fun buildBottomRow(parent: ViewGroup) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        row.addView(makeCharKey(",", isLetter = false), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        row.addView(makeCharKey("?", isLetter = false), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        row.addView(makeCharKey("!", isLetter = false), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val spaceButton = Button(context).apply {
            text = "space"
            textSize = 12f
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setOnClickListener { listener?.onSpace() }
        }
        row.addView(spaceButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f))

        row.addView(makeCharKey(".", isLetter = false), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val enterButton = Button(context).apply {
            text = "\u23CE"
            textSize = 16f
            minimumWidth = 0
            minimumHeight = 0
            setOnClickListener { listener?.onEnter() }
        }
        row.addView(enterButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f))
        parent.addView(row)
    }

    private fun makeCharKey(label: String, isLetter: Boolean): Button {
        val btn = Button(context).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            minimumWidth = 0
            minimumHeight = 0
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener {
                val txt = (it as Button).text.toString()
                listener?.onCharKey(if (txt.isNotEmpty()) txt[0] else ' ')
            }
        }
        if (isLetter) letterButtons.add(btn)
        return btn
    }

    // ---------- Emoji panel ----------

    private fun buildEmojiPanel() {
        emojiPanel = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 5f)
            visibility = GONE
        }
        addView(emojiPanel)

        val tabsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36))
        }
        val tabsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backButton = Button(context).apply {
            text = "\u2328 ABC"
            textSize = 12f
            isAllCaps = false
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener { showQwerty() }
        }
        tabsRow.addView(backButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { marginEnd = dp(6) })

        EmojiCategory.entries.forEach { category ->
            val tab = Button(context).apply {
                text = category.icon
                textSize = 14f
                isAllCaps = false
                setPadding(dp(10), dp(2), dp(10), dp(2))
                setOnClickListener {
                    currentEmojiCategory = category
                    updateEmojiTabHighlight()
                    renderEmojiGrid(category)
                }
            }
            emojiCategoryTabs[category] = tab
            tabsRow.addView(tab, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = dp(4) })
        }
        tabsScroll.addView(tabsRow)
        emojiPanel.addView(tabsScroll)

        val gridScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        emojiGridContainer = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        gridScroll.addView(emojiGridContainer)
        emojiPanel.addView(gridScroll)
    }

    private fun toggleEmojiPanel() {
        if (emojiPanel.visibility == VISIBLE) {
            showQwerty()
        } else {
            showEmojiPanel()
        }
    }

    private fun showEmojiPanel() {
        qwertyContainer.visibility = GONE
        emojiPanel.visibility = VISIBLE
        updateEmojiTabHighlight()
        renderEmojiGrid(currentEmojiCategory)
    }

    private fun showQwerty() {
        emojiPanel.visibility = GONE
        qwertyContainer.visibility = VISIBLE
    }

    private fun updateEmojiTabHighlight() {
        emojiCategoryTabs.forEach { (category, btn) ->
            btn.alpha = if (category == currentEmojiCategory) 1.0f else 0.6f
        }
    }

    private fun renderEmojiGrid(category: EmojiCategory) {
        emojiGridContainer.removeAllViews()
        val emoji = if (category == EmojiCategory.RECENT) recentEmojiList else EmojiRepository.emojisFor(category)
        if (emoji.isEmpty()) {
            val empty = TextView(context).apply {
                text = if (category == EmojiCategory.RECENT) "No recent emoji yet" else ""
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(16), dp(8), dp(16))
                setTextColor(currentKeyTextColor)
            }
            emojiGridContainer.addView(empty)
            return
        }
        val perRow = 8
        emoji.chunked(perRow).forEach { rowEmoji ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
            }
            rowEmoji.forEach { e ->
                val cell = TextView(context).apply {
                    text = e
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(currentKeyTextColor)
                    setOnClickListener { listener?.onEmojiSelected(e) }
                }
                row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }
            emojiGridContainer.addView(row)
        }
    }

    // ---------- Theming ----------

    private fun applyTheme() {
        val (bg, keyBg, keyText, accent) = when (currentTheme) {
            KeyboardTheme.DARK -> listOf(Color.parseColor("#121212"), Color.parseColor("#2A2A2A"), Color.parseColor("#ECECEC"), Color.parseColor("#BB86FC"))
            KeyboardTheme.LIGHT -> listOf(Color.parseColor("#FFFFFF"), Color.parseColor("#E8E8E8"), Color.parseColor("#1B1B1B"), Color.parseColor("#6750A4"))
            KeyboardTheme.NEON -> listOf(Color.parseColor("#0D0221"), Color.parseColor("#1B0B3B"), Color.parseColor("#39FF14"), Color.parseColor("#FF00E5"))
        }
        currentKeyTextColor = keyText
        setBackgroundColor(bg)
        modeLabel.setTextColor(keyText)
        suggestionChips.forEach { chip -> chip.setTextColor(accent) }
        forEachButton { btn -> btn.setBackgroundColor(keyBg); btn.setTextColor(keyText) }
        modeButtons[currentMode]?.setTextColor(accent)
        emojiCategoryTabs[currentEmojiCategory]?.setTextColor(accent)
        // Re-tint any already-rendered emoji cells (they're plain TextViews, not Buttons,
        // so forEachButton's walk doesn't reach them).
        for (i in 0 until emojiGridContainer.childCount) {
            val row = emojiGridContainer.getChildAt(i)
            if (row is ViewGroup) {
                for (j in 0 until row.childCount) {
                    val cell = row.getChildAt(j)
                    if (cell is TextView) cell.setTextColor(keyText)
                }
            }
        }

        val scale = heightScale.coerceIn(0.8f, 1.3f)
        // Base includes the 32dp suggestion strip added above the key rows; the
        // weighted rows themselves keep the same proportions as before.
        val baseHeightDp = 222
        layoutParams = (layoutParams ?: LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(baseHeightDp)
        )).apply {
            height = dp((baseHeightDp * scale).toInt())
        }
    }

    private fun forEachButton(action: (Button) -> Unit) {
        fun walk(view: View) {
            if (view is Button) action(view)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(this)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}

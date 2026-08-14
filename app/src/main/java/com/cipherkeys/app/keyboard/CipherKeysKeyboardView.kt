package com.cipherkeys.app.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.cipherkeys.app.data.KeyboardMode
import com.cipherkeys.app.data.KeyboardTheme
import com.cipherkeys.app.data.ThemeColorSet
import com.cipherkeys.app.emoji.EmojiCategory
import com.cipherkeys.app.emoji.EmojiRepository

/**
 * CipherKeys keyboard visual surface.
 *
 * Features:
 * - QWERTY keyboard with number row
 * - Separate Symbols keyboard
 * - Separate Emoji keyboard
 * - Mode switching
 * - Dictionary/suggestion strip
 * - Theme support
 * - Custom theme colors
 * - Optional image background
 * - Individual rounded borders around keys
 * - Pressed-key visual feedback
 * - Gboard-style key preview
 *
 * Text transformation/encoding remains outside this view.
 */
class CipherKeysKeyboardView(context: Context) : LinearLayout(context) {

    var listener: KeyboardActionListener? = null

    // -------------------------------------------------------------------------
    // GENERAL STATE
    // -------------------------------------------------------------------------

    private var shiftEnabled = false
    private var currentMode: KeyboardMode = KeyboardMode.default()
    private var currentTheme: KeyboardTheme = KeyboardTheme.default()
    private var customColors: ThemeColorSet = ThemeColorSet.default()

    private var heightScale = 1.0f
    private var currentKeyTextColor = Color.WHITE
    private var currentThemeBackground = Color.BLACK

    private var backgroundBitmap: Bitmap? = null
    private var backgroundOverlayAlpha = 0.5f

    // -------------------------------------------------------------------------
    // KEY COLLECTIONS
    // -------------------------------------------------------------------------

    private val letterButtons = mutableListOf<Button>()
    private val allKeyButtons = mutableListOf<Button>()
    private val modeButtons = mutableMapOf<KeyboardMode, Button>()

    private lateinit var modeLabel: TextView
    private lateinit var shiftButton: Button

    private val suggestionChips = mutableListOf<TextView>()

    // -------------------------------------------------------------------------
    // KEYBOARD PANELS
    // -------------------------------------------------------------------------

    private lateinit var qwertyContainer: LinearLayout

    private lateinit var emojiPanel: LinearLayout
    private lateinit var emojiGridContainer: LinearLayout
    private lateinit var emojiToggleButton: Button

    private lateinit var symbolsPanel: LinearLayout
    private lateinit var symbolsGridContainer: LinearLayout
    private lateinit var symbolsToggleButton: Button

    // -------------------------------------------------------------------------
    // EMOJI
    // -------------------------------------------------------------------------

    private val emojiCategoryTabs = mutableMapOf<EmojiCategory, Button>()
    private var currentEmojiCategory = EmojiCategory.SMILEYS
    private var recentEmojiList: List<String> = emptyList()

    // -------------------------------------------------------------------------
    // SYMBOLS
    // -------------------------------------------------------------------------

    private enum class SymbolCategory(
        val label: String
    ) {
        COMMON("123"),
        MATH("#+="),
        BRACKETS("()[]"),
        CURRENCY("€$"),
        ARROWS("←→"),
        EXTRA("©")
    }

    private val symbolCategoryTabs =
        mutableMapOf<SymbolCategory, Button>()

    private var currentSymbolCategory =
        SymbolCategory.COMMON

    // -------------------------------------------------------------------------
    // KEY PREVIEW
    // -------------------------------------------------------------------------

    private var activePreview: PopupWindow? = null

    // -------------------------------------------------------------------------
    // QWERTY DATA
    // -------------------------------------------------------------------------

    private val row1Keys = "qwertyuiop"
    private val row2Keys = "asdfghjkl"
    private val row3Keys = "zxcvbnm"
    private val numberKeys = "1234567890"

    // -------------------------------------------------------------------------
    // SYMBOL DATA
    // -------------------------------------------------------------------------

    private val commonSymbols = listOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "@", "#", "$", "_", "&", "-", "+", "(", ")",
        "/", "*", "\"", "'", ":", ";", "!", "?"
    )

    private val mathSymbols = listOf(
        "+", "−", "×", "÷", "=",
        "≠", "≈", "≤", "≥", "±",
        "∞", "√", "∑", "∏", "∫",
        "π", "∆", "∂", "∇", "∝",
        "%", "^", "~", "|", "¬"
    )

    private val bracketSymbols = listOf(
        "(", ")", "[", "]", "{", "}",
        "<", ">",
        "«", "»",
        "‹", "›",
        "⟨", "⟩",
        "⟦", "⟧",
        "⦃", "⦄",
        "/", "\\"
    )

    private val currencySymbols = listOf(
        "$", "€", "£", "¥", "₦",
        "₩", "₹", "₽", "₺", "₴",
        "₱", "₫", "฿", "₡", "₲",
        "₵", "₸", "₼", "₾", "₿"
    )

    private val arrowSymbols = listOf(
        "←", "→", "↑", "↓",
        "↔", "↕",
        "↖", "↗", "↘", "↙",
        "⇐", "⇒", "⇑", "⇓",
        "⇔", "⇕",
        "➜", "➤", "➝", "➞",
        "↩", "↪", "⤴", "⤵"
    )

    private val extraSymbols = listOf(
        "©", "®", "™", "°",
        "•", "·", "…", "‰",
        "§", "¶", "†", "‡",
        "※", "★", "☆",
        "✓", "✔", "✕", "✖",
        "♥", "♡", "♠", "♣",
        "♦", "♢"
    )

    // =========================================================================
    // INITIALIZATION
    // =========================================================================

    init {
        orientation = VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))

        buildToolbarRow()
        buildStatusRow()
        buildSuggestionStrip()
        buildQwertyContainer()
        buildSymbolsPanel()
        buildEmojiPanel()

        applyTheme()
    }

    // =========================================================================
    // PUBLIC API USED BY THE IME
    // =========================================================================

    fun setMode(mode: KeyboardMode) {

        currentMode = mode

        modeButtons.forEach { (m, btn) ->
            btn.setTypeface(
                null,
                if (m == mode) Typeface.BOLD
                else Typeface.NORMAL
            )

            btn.alpha =
                if (m == mode) 1f
                else 0.65f
        }

        modeLabel.text = "Mode: ${mode.label}"

        applyTheme()
    }

    fun setShiftState(enabled: Boolean) {

        shiftEnabled = enabled

        shiftButton.text =
            if (enabled) "\u21E7 SHIFT"
            else "shift"

        shiftButton.setTypeface(
            null,
            if (enabled) Typeface.BOLD
            else Typeface.NORMAL
        )

        letterButtons.forEach { btn ->

            val current =
                btn.text.toString()

            if (
                current.length == 1 &&
                current[0].isLetter()
            ) {

                btn.text =
                    if (enabled)
                        current.uppercase()
                    else
                        current.lowercase()
            }
        }
    }

    fun applyThemeAndHeight(
        theme: KeyboardTheme,
        heightScale: Float,
        customColors: ThemeColorSet
    ) {

        currentTheme = theme
        this.heightScale = heightScale
        this.customColors = customColors

        applyTheme()
    }

    fun setSuggestions(words: List<String>) {

        suggestionChips.forEachIndexed { index, chip ->

            val word =
                words.getOrNull(index)

            chip.text =
                word.orEmpty()

            chip.isEnabled =
                word != null

            chip.alpha =
                if (word != null) 1f
                else 0f
        }
    }

    fun setRecentEmoji(
        emoji: List<String>
    ) {

        recentEmojiList = emoji

        if (
            currentEmojiCategory ==
            EmojiCategory.RECENT &&
            emojiPanel.visibility == VISIBLE
        ) {

            renderEmojiGrid(
                EmojiCategory.RECENT
            )
        }
    }

    /** Sets or clears the keyboard background image. */
    fun setBackgroundImage(
        bitmap: Bitmap?,
        overlayAlpha: Float
    ) {

        backgroundBitmap = bitmap
        backgroundOverlayAlpha = overlayAlpha

        refreshBackgroundLayer()
    }

    // =========================================================================
    // BACKGROUND
    // =========================================================================

    private fun refreshBackgroundLayer() {

        val bmp =
            backgroundBitmap

        if (bmp != null) {

            val bitmapDrawable =
                BitmapDrawable(
                    resources,
                    bmp
                ).apply {
                    gravity = Gravity.FILL
                }

            val scrim =
                ColorDrawable(
                    Color.BLACK
                ).apply {
                    alpha =
                        (
                            backgroundOverlayAlpha
                                .coerceIn(0f, 1f) *
                                255
                            ).toInt()
                }

            background =
                LayerDrawable(
                    arrayOf(
                        bitmapDrawable,
                        scrim
                    )
                )

        } else {

            setBackgroundColor(
                currentThemeBackground
            )
        }
    }

    // =========================================================================
    // TOOLBAR
    // =========================================================================

    private fun buildToolbarRow() {

        val scroll =
            HorizontalScrollView(
                context
            ).apply {

                isHorizontalScrollBarEnabled =
                    false

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(40)
                    )
            }

        val row =
            LinearLayout(
                context
            ).apply {

                orientation =
                    HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        KeyboardMode.entries.forEach { mode ->

            val btn =
                Button(context).apply {

                    text =
                        mode.label

                    textSize =
                        12f

                    isAllCaps =
                        false

                    minimumWidth =
                        0

                    minimumHeight =
                        0

                    setPadding(
                        dp(12),
                        dp(2),
                        dp(12),
                        dp(2)
                    )

                    setOnClickListener {

                        setMode(mode)

                        listener?.onModeSelected(
                            mode
                        )
                    }
                }

            modeButtons[mode] =
                btn

            registerKeyButton(btn)

            row.addView(
                btn,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd =
                        dp(4)
                }
            )
        }

        // ---------------------------------------------------------------------
        // SYMBOLS BUTTON
        // ---------------------------------------------------------------------

        symbolsToggleButton =
            Button(context).apply {

                text =
                    "?123"

                textSize =
                    13f

                isAllCaps =
                    false

                minimumWidth =
                    0

                minimumHeight =
                    0

                setPadding(
                    dp(12),
                    dp(2),
                    dp(12),
                    dp(2)
                )

                setOnClickListener {
                    toggleSymbolsPanel()
                }
            }

        registerKeyButton(
            symbolsToggleButton
        )

        row.addView(
            symbolsToggleButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd =
                    dp(4)
            }
        )

        // ---------------------------------------------------------------------
        // EMOJI BUTTON
        // ---------------------------------------------------------------------

        emojiToggleButton =
            Button(context).apply {

                text =
                    "\uD83D\uDE00"

                textSize =
                    16f

                isAllCaps =
                    false

                minimumWidth =
                    0

                minimumHeight =
                    0

                setPadding(
                    dp(12),
                    dp(2),
                    dp(12),
                    dp(2)
                )

                setOnClickListener {
                    toggleEmojiPanel()
                }
            }

        registerKeyButton(
            emojiToggleButton
        )

        row.addView(
            emojiToggleButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd =
                    dp(4)
            }
        )

        scroll.addView(row)

        addView(scroll)
    }

    // =========================================================================
    // STATUS
    // =========================================================================

    private fun buildStatusRow() {

        modeLabel =
            TextView(context).apply {

                text =
                    "Mode: ${currentMode.label}"

                textSize =
                    11f

                setPadding(
                    dp(8),
                    dp(2),
                    dp(8),
                    dp(4)
                )
            }

        addView(modeLabel)
    }

    // =========================================================================
    // SUGGESTIONS
    // =========================================================================

    private fun buildSuggestionStrip() {

        val row =
            LinearLayout(context).apply {

                orientation =
                    HORIZONTAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(32)
                    )

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        repeat(3) {

            val chip =
                TextView(context).apply {

                    textSize =
                        13f

                    gravity =
                        Gravity.CENTER

                    setPadding(
                        dp(4),
                        0,
                        dp(4),
                        0
                    )

                    setOnClickListener {

                        val word =
                            text.toString()

                        if (
                            word.isNotEmpty()
                        ) {

                            listener?.onSuggestionSelected(
                                word
                            )
                        }
                    }
                }

            suggestionChips.add(chip)

            row.addView(
                chip,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                )
            )
        }

        addView(row)
    }

    // =========================================================================
    // QWERTY KEYBOARD
    // =========================================================================

    private fun buildQwertyContainer() {

        qwertyContainer =
            LinearLayout(context).apply {

                orientation =
                    VERTICAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        5f
                    )
            }

        addView(
            qwertyContainer
        )

        buildNumberRow(
            qwertyContainer
        )

        buildRow(
            qwertyContainer,
            row1Keys,
            0
        )

        buildRow(
            qwertyContainer,
            row2Keys,
            20
        )

        buildRow3WithShiftAndBackspace(
            qwertyContainer
        )

        buildBottomRow(
            qwertyContainer
        )
    }

    private fun buildNumberRow(
        parent: ViewGroup
    ) {

        val row =
            LinearLayout(context).apply {

                orientation =
                    HORIZONTAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
            }

        numberKeys.forEach { c ->

            row.addView(
                makeCharKey(
                    c.toString(),
                    false
                ),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    setMargins(
                        dp(1),
                        dp(1),
                        dp(1),
                        dp(1)
                    )
                }
            )
        }

        parent.addView(row)
    }

    private fun buildRow(
        parent: ViewGroup,
        keys: String,
        sidePadding: Int
    ) {

        val row =
            LinearLayout(context).apply {

                orientation =
                    HORIZONTAL

                gravity =
                    Gravity.CENTER_HORIZONTAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )

                setPadding(
                    dp(sidePadding),
                    0,
                    dp(sidePadding),
                    0
                )
            }

        keys.forEach { c ->

            row.addView(
                makeCharKey(
                    c.toString(),
                    true
                ),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    setMargins(
                        dp(1),
                        dp(1),
                        dp(1),
                        dp(1)
                    )
                }
            )
        }

        parent.addView(row)
    }

    private fun buildRow3WithShiftAndBackspace(
        parent: ViewGroup
    ) {

        val row =
            LinearLayout(context).apply {

                orientation =
                    HORIZONTAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
            }

        shiftButton =
            Button(context).apply {

                text =
                    "shift"

                textSize =
                    12f

                isAllCaps =
                    false

                minimumWidth =
                    0

                minimumHeight =
                    0

                setOnClickListener {

                    /*
                     * The IME owns the actual shift state.
                     * This prevents the previous double-toggle bug.
                     */
                    listener?.onShiftToggle()
                }
            }

        registerKeyButton(
            shiftButton
        )

        row.addView(
            shiftButton,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.5f
            ).apply {
                setMargins(
                    dp(1),
                    dp(1),
                    dp(1),
                    dp(1)
                )
            }
        )

        row3Keys.forEach { c ->

            row.addView(
                makeCharKey(
                    c.toString(),
                    true
                ),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    setMargins(
                        dp(1),
                        dp(1),
                        dp(1),
                        dp(1)
                    )
                }
            )
        }

        val backspaceButton =
            Button(context).apply {

                text =
                    "\u232B"

                textSize =
                    16f

                minimumWidth =
                    0

                minimumHeight =
                    0

                setOnClickListener {
                    listener?.onBackspace()
                }
            }

        registerKeyButton(
            backspaceButton
        )

        row.addView(
            backspaceButton,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.5f
            ).apply {
                setMargins(
                    dp(1),
                    dp(1),
                    dp(1),
                    dp(1)
                )
            }
        )

        parent.addView(row)
    }

    private fun buildBottomRow(
        parent: ViewGroup
    ) {

        val row =
            LinearLayout(context).apply {

                orientation =
                    HORIZONTAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
            }

        row.addView(
            makeCharKey(",", false),
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
        )

        row.addView(
            makeCharKey("?", false),
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
        )

        row.addView(
            makeCharKey("!", false),
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
        )

        // ---------------------------------------------------------------------
        // SPACE
        // ---------------------------------------------------------------------

        val spaceButton =
            Button(context).apply {

                text =
                    "space"

                textSize =
                    12f

                isAllCaps =
                    false

                minimumWidth =
                    0

                minimumHeight =
                    0

                setOnClickListener {
                    listener?.onSpace()
                }
            }

        registerKeyButton(
            spaceButton
        )

        row.addView(
            spaceButton,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                3f
            ).apply {
                setMargins(
                    dp(1),
                    dp(1),
                    dp(1),
                    dp(1)
                )
            }
        )

        row.addView(
            makeCharKey(".", false),
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
        )

        val enterButton =
            Button(context).apply {

                text =
                    "\u23CE"

                textSize =
                    16f

                minimumWidth =
                    0

                minimumHeight =
                    0

                setOnClickListener {
                    listener?.onEnter()
                }
            }

        registerKeyButton(
            enterButton
        )

        row.addView(
            enterButton,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.5f
            ).apply {
                setMargins(
                    dp(1),
                    dp(1),
                    dp(1),
                    dp(1)
                )
            }
        )

        parent.addView(row)
    }

    // =========================================================================
    // CHARACTER KEY
    // =========================================================================

    private fun makeCharKey(
        label: String,
        isLetter: Boolean
    ): Button {

        val btn =
            Button(context).apply {

                text =
                    label

                textSize =
                    if (isLetter) 15f
                    else 14f

                isAllCaps =
                    false

                minimumWidth =
                    0

                minimumHeight =
                    0

                setPadding(
                    dp(2),
                    0,
                    dp(2),
                    0
                )

                stateListAnimator =
                    null

                setOnClickListener {

                    val txt =
                        text.toString()

                    if (
                        txt.isNotEmpty()
                    ) {

                        listener?.onCharKey(
                            txt[0]
                        )
                    }
                }
            }

        registerKeyButton(btn)

        if (isLetter) {
            letterButtons.add(btn)
        }

        attachKeyPreview(btn)

        return btn
    }

    private fun registerKeyButton(
        button: Button
    ) {

        if (
            !allKeyButtons.contains(button)
        ) {

            allKeyButtons.add(button)
        }
    }

    // =========================================================================
    // SYMBOL KEYBOARD
    // =========================================================================

    private fun buildSymbolsPanel() {

        symbolsPanel =
            LinearLayout(context).apply {

                orientation =
                    VERTICAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        5f
                    )

                visibility =
                    GONE
            }

        addView(symbolsPanel)

        // ---------------------------------------------------------------------
        // SYMBOL CATEGORY TABS
        // ---------------------------------------------------------------------

        val tabsScroll =
            HorizontalScrollView(context).apply {

                isHorizontalScrollBarEnabled =
                    false

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(38)
                    )
            }

        val tabsRow =
            LinearLayout(context).apply {

                orientation =
                    HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val abcButton =
            Button(context).apply {

                text =
                    "ABC"

                textSize =
                    12f

                isAllCaps =
                    false

                minimumWidth =
                    0

                minimumHeight =
                    0

                setPadding(
                    dp(12),
                    dp(2),
                    dp(12),
                    dp(2)
                )

                setOnClickListener {
                    showQwerty()
                }
            }

        registerKeyButton(
            abcButton
        )

        tabsRow.addView(
            abcButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd =
                    dp(6)
            }
        )

        SymbolCategory.entries.forEach { category ->

            val tab =
                Button(context).apply {

                    text =
                        category.label

                    textSize =
                        11f

                    isAllCaps =
                        false

                    minimumWidth =
                        0

                    minimumHeight =
                        0

                    setPadding(
                        dp(10),
                        dp(2),
                        dp(10),
                        dp(2)
                    )

                    setOnClickListener {

                        currentSymbolCategory =
                            category

                        updateSymbolTabHighlight()

                        renderSymbolGrid(
                            category
                        )
                    }
                }

            symbolCategoryTabs[category] =
                tab

            registerKeyButton(tab)

            tabsRow.addView(
                tab,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd =
                        dp(4)
                }
            )
        }

        tabsScroll.addView(
            tabsRow
        )

        symbolsPanel.addView(
            tabsScroll
        )

        // ---------------------------------------------------------------------
        // SYMBOL GRID
        // ---------------------------------------------------------------------

        val gridScroll =
            ScrollView(context).apply {

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
            }

        symbolsGridContainer =
            LinearLayout(context).apply {

                orientation =
                    VERTICAL
            }

        gridScroll.addView(
            symbolsGridContainer
        )

        symbolsPanel.addView(
            gridScroll
        )
    }

    private fun toggleSymbolsPanel() {

        if (
            symbolsPanel.visibility ==
            VISIBLE
        ) {

            showQwerty()

        } else {

            showSymbolsPanel()
        }
    }

    private fun showSymbolsPanel() {

        hideKeyPreview()

        qwertyContainer.visibility =
            GONE

        emojiPanel.visibility =
            GONE

        symbolsPanel.visibility =
            VISIBLE

        updateSymbolTabHighlight()

        renderSymbolGrid(
            currentSymbolCategory
        )
    }

    private fun renderSymbolGrid(
        category: SymbolCategory
    ) {

        symbolsGridContainer.removeAllViews()

        val symbols =
            when (category) {

                SymbolCategory.COMMON ->
                    commonSymbols

                SymbolCategory.MATH ->
                    mathSymbols

                SymbolCategory.BRACKETS ->
                    bracketSymbols

                SymbolCategory.CURRENCY ->
                    currencySymbols

                SymbolCategory.ARROWS ->
                    arrowSymbols

                SymbolCategory.EXTRA ->
                    extraSymbols
            }

        val perRow =
            8

        symbols
            .chunked(perRow)
            .forEach { rowSymbols ->

                val row =
                    LinearLayout(context).apply {

                        orientation =
                            HORIZONTAL

                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                0,
                                1f
                            )
                    }

                rowSymbols.forEach { symbol ->

                    val button =
                        makeSymbolKey(
                            symbol
                        )

                    row.addView(
                        button,
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            1f
                        ).apply {

                            setMargins(
                                dp(1),
                                dp(1),
                                dp(1),
                                dp(1)
                            )
                        }
                    )
                }

                /*
                 * Keep incomplete final rows balanced.
                 */
                repeat(
                    perRow - rowSymbols.size
                ) {

                    val spacer =
                        View(context)

                    row.addView(
                        spacer,
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            1f
                        )
                    )
                }

                symbolsGridContainer.addView(
                    row
                )
            }

        applyTheme()
    }

    private fun makeSymbolKey(
        symbol: String
    ): Button {

        val button =
            Button(context).apply {

                text =
                    symbol

                textSize =
                    if (symbol.length > 1)
                        13f
                    else
                        17f

                isAllCaps =
                    false

                minimumWidth =
                    0

                minimumHeight =
                    0

                setPadding(
                    dp(2),
                    0,
                    dp(2),
                    0
                )

                stateListAnimator =
                    null

                setOnClickListener {

                    if (
                        symbol.isNotEmpty()
                    ) {

                        /*
                         * KeyboardActionListener currently
                         * accepts one Char. Every symbol in
                         * this keyboard is therefore sent as
                         * one Unicode character.
                         */
                        listener?.onCharKey(
                            symbol[0]
                        )
                    }
                }
            }

        registerKeyButton(
            button
        )

        attachKeyPreview(
            button
        )

        return button
    }

    private fun updateSymbolTabHighlight() {

        symbolCategoryTabs.forEach {
            (category, button) ->

            button.alpha =
                if (
                    category ==
                    currentSymbolCategory
                ) {
                    1f
                } else {
                    0.6f
                }
        }
    }

    // =========================================================================
    // GBOARD-STYLE KEY PREVIEW
    // =========================================================================

    private fun attachKeyPreview(
        button: Button
    ) {

        button.setOnTouchListener { view, event ->

            when (
                event.actionMasked
            ) {

                MotionEvent.ACTION_DOWN -> {

                    val value =
                        button.text.toString()

                    if (
                        value.isNotEmpty() &&
                        value.length <= 2 &&
                        value != "space"
                    ) {

                        showKeyPreview(
                            button,
                            value
                        )
                    }

                    view.isPressed =
                        true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    hideKeyPreview()

                    view.isPressed =
                        false

                    if (
                        event.actionMasked ==
                        MotionEvent.ACTION_UP
                    ) {

                        view.performClick()
                    }
                }
            }

            true
        }
    }

    private fun showKeyPreview(
        anchor: View,
        label: String
    ) {

        hideKeyPreview()

        val previewHeight =
            dp(58)

        val previewWidth =
            dp(52)

        val preview =
            TextView(context).apply {

                text =
                    label

                textSize =
                    25f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    currentKeyTextColor
                )

                background =
                    createPreviewBackground()

                elevation =
                    dp(8).toFloat()
            }

        val popup =
            PopupWindow(
                preview,
                previewWidth,
                previewHeight,
                false
            ).apply {

                isClippingEnabled =
                    false

                elevation =
                    dp(8).toFloat()

                setBackgroundDrawable(
                    ColorDrawable(
                        Color.TRANSPARENT
                    )
                )

                isOutsideTouchable =
                    false
            }

        activePreview =
            popup

        anchor.post {

            if (
                activePreview !== popup
            ) {
                return@post
            }

            val location =
                IntArray(2)

            anchor.getLocationOnScreen(
                location
            )

            val x =
                location[0] +
                    anchor.width / 2 -
                    previewWidth / 2

            val y =
                location[1] -
                    previewHeight +
                    dp(6)

            popup.showAtLocation(
                this,
                Gravity.TOP or Gravity.START,
                x.coerceAtLeast(0),
                y.coerceAtLeast(0)
            )
        }
    }

    private fun hideKeyPreview() {

        activePreview?.dismiss()

        activePreview =
            null
    }

    private fun createPreviewBackground():
        GradientDrawable {

        val fill =
            blendColor(
                currentThemeBackground,
                currentKeyTextColor,
                0.18f
            )

        return GradientDrawable().apply {

            shape =
                GradientDrawable.RECTANGLE

            cornerRadius =
                dp(16).toFloat()

            setColor(
                fill
            )

            setStroke(
                dp(1),
                currentKeyTextColor
            )
        }
    }

    // =========================================================================
    // EMOJI PANEL
    // =========================================================================

    private fun buildEmojiPanel() {

        emojiPanel =
            LinearLayout(context).apply {

                orientation =
                    VERTICAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        5f
                    )

                visibility =
                    GONE
            }

        addView(
            emojiPanel
        )

        val tabsScroll =
            HorizontalScrollView(context).apply {

                isHorizontalScrollBarEnabled =
                    false

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(36)
                    )
            }

        val tabsRow =
            LinearLayout(context).apply {

                orientation =
                    HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val backButton =
            Button(context).apply {

                text =
                    "\u2328 ABC"

                textSize =
                    12f

                isAllCaps =
                    false

                minimumWidth =
                    0

                minimumHeight =
                    0

                setPadding(
                    dp(10),
                    dp(2),
                    dp(10),
                    dp(2)
                )

                setOnClickListener {
                    showQwerty()
                }
            }

        registerKeyButton(
            backButton
        )

        tabsRow.addView(
            backButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd =
                    dp(6)
            }
        )

        EmojiCategory.entries.forEach { category ->

            val tab =
                Button(context).apply {

                    text =
                        category.icon

                    textSize =
                        14f

                    isAllCaps =
                        false

                    minimumWidth =
                        0

                    minimumHeight =
                        0

                    setPadding(
                        dp(10),
                        dp(2),
                        dp(10),
                        dp(2)
                    )

                    setOnClickListener {

                        currentEmojiCategory =
                            category

                        updateEmojiTabHighlight()

                        renderEmojiGrid(
                            category
                        )
                    }
                }

            emojiCategoryTabs[category] =
                tab

            registerKeyButton(
                tab
            )

            tabsRow.addView(
                tab,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd =
                        dp(4)
                }
            )
        }

        tabsScroll.addView(
            tabsRow
        )

        emojiPanel.addView(
            tabsScroll
        )

        val gridScroll =
            ScrollView(context).apply {

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
            }

        emojiGridContainer =
            LinearLayout(context).apply {

                orientation =
                    VERTICAL
            }

        gridScroll.addView(
            emojiGridContainer
        )

        emojiPanel.addView(
            gridScroll
        )
    }

    private fun toggleEmojiPanel() {

        if (
            emojiPanel.visibility ==
            VISIBLE
        ) {

            showQwerty()

        } else {

            showEmojiPanel()
        }
    }

    private fun showEmojiPanel() {

        hideKeyPreview()

        qwertyContainer.visibility =
            GONE

        symbolsPanel.visibility =
            GONE

        emojiPanel.visibility =
            VISIBLE

        updateEmojiTabHighlight()

        renderEmojiGrid(
            currentEmojiCategory
        )
    }

    private fun showQwerty() {

        hideKeyPreview()

        emojiPanel.visibility =
            GONE

        symbolsPanel.visibility =
            GONE

        qwertyContainer.visibility =
            VISIBLE
    }

    private fun updateEmojiTabHighlight() {

        emojiCategoryTabs.forEach {
            (category, btn) ->

            btn.alpha =
                if (
                    category ==
                    currentEmojiCategory
                ) {
                    1f
                } else {
                    0.6f
                }
        }
    }

    private fun renderEmojiGrid(
        category: EmojiCategory
    ) {

        emojiGridContainer.removeAllViews()

        val emoji =
            if (
                category ==
                EmojiCategory.RECENT
            ) {

                recentEmojiList

            } else {

                EmojiRepository.emojisFor(
                    category
                )
            }

        if (
            emoji.isEmpty()
        ) {

            val empty =
                TextView(context).apply {

                    text =
                        if (
                            category ==
                            EmojiCategory.RECENT
                        ) {
                            "No recent emoji yet"
                        } else {
                            ""
                        }

                    textSize =
                        12f

                    gravity =
                        Gravity.CENTER

                    setPadding(
                        dp(8),
                        dp(16),
                        dp(8),
                        dp(16)
                    )

                    setTextColor(
                        currentKeyTextColor
                    )
                }

            emojiGridContainer.addView(
                empty
            )

            return
        }

        val perRow =
            8

        emoji.chunked(perRow)
            .forEach { rowEmoji ->

                val row =
                    LinearLayout(context).apply {

                        orientation =
                            HORIZONTAL

                        layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(42)
                            )
                    }

                rowEmoji.forEach {
                    emojiValue ->

                    val cell =
                        TextView(context).apply {

                            text =
                                emojiValue

                            textSize =
                                20f

                            gravity =
                                Gravity.CENTER

                            setTextColor(
                                currentKeyTextColor
                            )

                            setOnClickListener {

                                listener?.onEmojiSelected(
                                    emojiValue
                                )
                            }
                        }

                    row.addView(
                        cell,
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            1f
                        )
                    )
                }

                emojiGridContainer.addView(
                    row
                )
            }
    }

    // =========================================================================
    // THEMING
    // =========================================================================

    private fun applyTheme() {

        val bg: Int
        val keyBg: Int
        val keyText: Int
        val accent: Int

        if (
            currentTheme ==
            KeyboardTheme.CUSTOM
        ) {

            bg =
                customColors.background

            keyBg =
                customColors.keyBackground

            keyText =
                customColors.keyText

            accent =
                customColors.accent

        } else {

            bg =
                currentTheme.background

            keyBg =
                currentTheme.keyBackground

            keyText =
                currentTheme.keyText

            accent =
                currentTheme.accent
        }

        currentKeyTextColor =
            keyText

        currentThemeBackground =
            bg

        refreshBackgroundLayer()

        modeLabel.setTextColor(
            keyText
        )

        suggestionChips.forEach {
            it.setTextColor(
                accent
            )
        }

        allKeyButtons.forEach { button ->

            applyKeyStyle(
                button,
                keyBg,
                keyText,
                accent
            )
        }

        modeButtons.forEach {
            (mode, btn) ->

            btn.setTextColor(
                if (
                    mode ==
                    currentMode
                ) {
                    accent
                } else {
                    keyText
                }
            )
        }

        emojiCategoryTabs[
            currentEmojiCategory
        ]?.setTextColor(
            accent
        )

        symbolCategoryTabs[
            currentSymbolCategory
        ]?.setTextColor(
            accent
        )

        // ---------------------------------------------------------------------
        // EMOJI TEXT
        // ---------------------------------------------------------------------

        for (
            i in 0 until
                emojiGridContainer.childCount
        ) {

            val row =
                emojiGridContainer
                    .getChildAt(i)

            if (
                row is ViewGroup
            ) {

                for (
                    j in 0 until
                        row.childCount
                ) {

                    val child =
                        row.getChildAt(j)

                    if (
                        child is TextView
                    ) {

                        child.setTextColor(
                            keyText
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // SYMBOL TEXT
        // ---------------------------------------------------------------------

        for (
            i in 0 until
                symbolsGridContainer.childCount
        ) {

            val row =
                symbolsGridContainer
                    .getChildAt(i)

            if (
                row is ViewGroup
            ) {

                for (
                    j in 0 until
                        row.childCount
                ) {

                    val child =
                        row.getChildAt(j)

                    if (
                        child is TextView
                    ) {

                        child.setTextColor(
                            keyText
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // HEIGHT
        // ---------------------------------------------------------------------

        val scale =
            heightScale
                .coerceIn(
                    0.8f,
                    1.3f
                )

        val baseHeightDp =
            222

        layoutParams =
            (
                layoutParams
                    ?: LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(baseHeightDp)
                    )
                ).apply {

                    height =
                        dp(
                            (
                                baseHeightDp *
                                    scale
                                ).toInt()
                        )
                }

        requestLayout()
    }

    // =========================================================================
    // KEY STYLE
    // =========================================================================

    /** Gives every keyboard key an individual border, rounded corners and pressed state. */
    private fun applyKeyStyle(
        button: Button,
        fillColor: Int,
        textColor: Int,
        accentColor: Int
    ) {

        button.setTextColor(
            textColor
        )

        val normal =
            createKeyDrawable(
                fillColor,
                mixBorderColor(
                    fillColor,
                    textColor
                ),
                dp(1),
                dp(7)
            )

        val pressed =
            createKeyDrawable(
                blendColor(
                    fillColor,
                    accentColor,
                    0.20f
                ),
                accentColor,
                dp(2),
                dp(7)
            )

        val focused =
            createKeyDrawable(
                blendColor(
                    fillColor,
                    accentColor,
                    0.10f
                ),
                accentColor,
                dp(1),
                dp(7)
            )

        val states =
            StateListDrawable().apply {

                addState(
                    intArrayOf(
                        android.R.attr.state_pressed
                    ),
                    pressed
                )

                addState(
                    intArrayOf(
                        android.R.attr.state_focused
                    ),
                    focused
                )

                addState(
                    intArrayOf(),
                    normal
                )
            }

        button.background =
            states

        button.elevation =
            0f

        button.translationZ =
            0f
    }

    private fun createKeyDrawable(
        fillColor: Int,
        borderColor: Int,
        borderWidth: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            shape =
                GradientDrawable.RECTANGLE

            cornerRadius =
                radius.toFloat()

            setColor(
                fillColor
            )

            setStroke(
                borderWidth,
                borderColor
            )
        }
    }

    private fun mixBorderColor(
        keyColor: Int,
        textColor: Int
    ): Int {

        return blendColor(
            keyColor,
            textColor,
            0.28f
        )
    }

    private fun blendColor(
        from: Int,
        to: Int,
        amount: Float
    ): Int {

        val t =
            amount.coerceIn(
                0f,
                1f
            )

        val r =
            (
                Color.red(from) +
                    (
                        Color.red(to) -
                            Color.red(from)
                        ) * t
                ).toInt()

        val g =
            (
                Color.green(from) +
                    (
                        Color.green(to) -
                            Color.green(from)
                        ) * t
                ).toInt()

        val b =
            (
                Color.blue(from) +
                    (
                        Color.blue(to) -
                            Color.blue(from)
                        ) * t
                ).toInt()

        return Color.rgb(
            r,
            g,
            b
        )
    }

    // =========================================================================
    // DP
    // =========================================================================

    private fun dp(
        value: Int
    ): Int {

        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    // =========================================================================
    // DETACH
    // =========================================================================

    override fun onDetachedFromWindow() {

        hideKeyPreview()

        super.onDetachedFromWindow()
    }
}

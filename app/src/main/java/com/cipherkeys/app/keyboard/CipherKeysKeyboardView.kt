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
import android.widget.TextView
import com.cipherkeys.app.data.KeyboardMode
import com.cipherkeys.app.data.KeyboardTheme

/**
 * The keyboard's visual surface: a mode-switching toolbar on top and QWERTY key rows
 * below. Built entirely with framework views in code (no XML layout / KeyboardView
 * resource format) so the row/key structure is easy to read, test, and re-theme.
 *
 * This view is dumb by design: it only reports raw user actions via
 * [KeyboardActionListener]. All text-transform logic (encoding/decoding) lives in
 * [CipherKeysIME] / encoder classes, not here.
 */
class CipherKeysKeyboardView(context: Context) : LinearLayout(context) {

    var listener: KeyboardActionListener? = null

    private var shiftEnabled: Boolean = false
    private var currentMode: KeyboardMode = KeyboardMode.default()
    private var currentTheme: KeyboardTheme = KeyboardTheme.default()
    private var heightScale: Float = 1.0f

    private val letterButtons = mutableListOf<Button>()
    private val modeButtons = mutableMapOf<KeyboardMode, Button>()
    private lateinit var modeLabel: TextView
    private lateinit var shiftButton: Button

    private val row1Keys = "qwertyuiop"
    private val row2Keys = "asdfghjkl"
    private val row3Keys = "zxcvbnm"
    private val numberKeys = "1234567890"

    init {
        orientation = VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        buildToolbarRow()
        buildStatusRow()
        buildNumberRow()
        buildRow(row1Keys, sidePadding = 0)
        buildRow(row2Keys, sidePadding = 20)
        buildRow3WithShiftAndBackspace()
        buildBottomRow()
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

    private fun buildNumberRow() {
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
        addView(row)
    }

    private fun buildRow(keys: String, sidePadding: Int) {
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
        addView(row)
    }

    private fun buildRow3WithShiftAndBackspace() {
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
        addView(row)
    }

    private fun buildBottomRow() {
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
        addView(row)
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

    // ---------- Theming ----------

    private fun applyTheme() {
        val (bg, keyBg, keyText, accent) = when (currentTheme) {
            KeyboardTheme.DARK -> listOf(Color.parseColor("#121212"), Color.parseColor("#2A2A2A"), Color.parseColor("#ECECEC"), Color.parseColor("#BB86FC"))
            KeyboardTheme.LIGHT -> listOf(Color.parseColor("#FFFFFF"), Color.parseColor("#E8E8E8"), Color.parseColor("#1B1B1B"), Color.parseColor("#6750A4"))
            KeyboardTheme.NEON -> listOf(Color.parseColor("#0D0221"), Color.parseColor("#1B0B3B"), Color.parseColor("#39FF14"), Color.parseColor("#FF00E5"))
        }
        setBackgroundColor(bg)
        modeLabel.setTextColor(keyText)
        val allButtons = mutableListOf<Button>()
        allButtons.addAll(letterButtons)
        allButtons.addAll(modeButtons.values)
        allButtons.add(shiftButton)
        forEachButton { btn -> btn.setBackgroundColor(keyBg); btn.setTextColor(keyText) }
        modeButtons[currentMode]?.setTextColor(accent)

        val scale = heightScale.coerceIn(0.8f, 1.3f)
        val baseHeightDp = 190
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

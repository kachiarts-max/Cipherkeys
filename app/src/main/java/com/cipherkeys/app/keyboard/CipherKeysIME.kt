package com.cipherkeys.app.keyboard

import android.content.Context
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
import com.cipherkeys.app.data.KeyboardTheme
import com.cipherkeys.app.decoder.CipherKeysDecoder
import com.cipherkeys.app.encoder.ClassicLeetEncoder
import com.cipherkeys.app.encoder.Encoder
import com.cipherkeys.app.encoder.EliteEncoder
import com.cipherkeys.app.encoder.HackerEncoder
import com.cipherkeys.app.encoder.UltraEncoder
import com.cipherkeys.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The real Android keyboard. Once installed and enabled in
 * Settings -> System -> Languages & input -> On-screen keyboard, this service is what
 * the system instantiates and shows whenever a text field is focused.
 *
 * Responsibilities kept deliberately narrow here: own the InputConnection lifecycle,
 * translate raw key events from [CipherKeysKeyboardView] into committed text using the
 * encoder/decoder for the active [KeyboardMode], and react to live settings changes.
 * All cipher logic lives in the encoder/decoder package, not in this class.
 */
class CipherKeysIME : InputMethodService(), KeyboardActionListener {

    private lateinit var keyboardView: CipherKeysKeyboardView
    private lateinit var settingsRepository: SettingsRepository

    private val serviceJob = Job()
    // Main dispatcher: settingsFlow.collect updates the keyboard View directly
    // (theme/height), so it must run on the UI thread, not a background pool.
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)

    private var currentSettings: KeyboardSettings = KeyboardSettings()
    private var currentMode: KeyboardMode = KeyboardMode.default()
    private var shiftEnabled: Boolean = false

    private lateinit var classicEncoder: Encoder
    private lateinit var eliteEncoder: Encoder
    private lateinit var hackerEncoder: Encoder
    private lateinit var ultraEncoder: Encoder
    private lateinit var decoder: CipherKeysDecoder

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        rebuildEncoders(emptyMap())
        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                val mappingsChanged = settings.customMappings != currentSettings.customMappings
                currentSettings = settings
                if (mappingsChanged) rebuildEncoders(settings.customMappings)
                if (::keyboardView.isInitialized) {
                    keyboardView.applyThemeAndHeight(settings.theme, settings.keyboardHeightScale)
                }
            }
        }
    }

    private fun rebuildEncoders(customMappings: Map<Char, List<String>>) {
        classicEncoder = ClassicLeetEncoder(customMappings)
        eliteEncoder = EliteEncoder(customMappings)
        hackerEncoder = HackerEncoder(customMappings)
        ultraEncoder = UltraEncoder(customMappings)
        decoder = CipherKeysDecoder(customMappings)
    }

    override fun onCreateInputView(): View {
        val view = CipherKeysKeyboardView(this)
        view.listener = this
        keyboardView = view
        keyboardView.applyThemeAndHeight(currentSettings.theme, currentSettings.keyboardHeightScale)
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentMode = currentSettings.defaultMode
        shiftEnabled = false
        keyboardView.setMode(currentMode)
        keyboardView.setShiftState(false)

        // Auto-decode: if enabled, decode whatever text already sits in the field
        // as soon as the keyboard attaches to it.
        if (currentSettings.autoDecodeEnabled) {
            decodeExistingFieldText()
        }
    }

    // ---------- KeyboardActionListener ----------

    override fun onCharKey(char: Char) {
        val ic = currentInputConnection ?: return
        val effectiveChar = if (shiftEnabled) char.uppercaseChar() else char

        val output = if (currentSettings.autoEncodeEnabled) {
            when (currentMode) {
                KeyboardMode.NORMAL, KeyboardMode.DECODE -> effectiveChar.toString()
                KeyboardMode.CLASSIC_LEET -> classicEncoder.encode(effectiveChar.toString())
                KeyboardMode.ELITE -> eliteEncoder.encode(effectiveChar.toString())
                KeyboardMode.HACKER -> hackerEncoder.encode(effectiveChar.toString())
                KeyboardMode.ULTRA -> ultraEncoder.encode(effectiveChar.toString())
            }
        } else {
            effectiveChar.toString()
        }

        ic.commitText(output, 1)
        performFeedback()

        if (shiftEnabled) {
            shiftEnabled = false
            keyboardView.setShiftState(false)
        }
    }

    override fun onSpace() {
        currentInputConnection?.commitText(" ", 1)
        performFeedback()
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        performFeedback()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action != null && action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            editorInfo.imeOptions.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
        ) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
        performFeedback()
    }

    override fun onShiftToggle() {
        shiftEnabled = !shiftEnabled
    }

    override fun onModeSelected(mode: KeyboardMode) {
        currentMode = mode
        if (mode == KeyboardMode.DECODE) {
            decodeExistingFieldText()
        }
    }

    // ---------- Helpers ----------

    /** Decodes the full current field content in place (used by DECODE mode + auto-decode). */
    private fun decodeExistingFieldText() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(4000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(4000, 0)?.toString().orEmpty()
        if (before.isEmpty() && after.isEmpty()) return

        val decodedBefore = decoder.decode(before)
        val decodedAfter = decoder.decode(after)

        ic.beginBatchEdit()
        ic.deleteSurroundingText(before.length, after.length)
        ic.commitText(decodedBefore + decodedAfter, 1)
        // Restore cursor to the boundary between the decoded "before" and "after" segments.
        val newCursorPos = decodedBefore.length
        ic.setSelection(newCursorPos, newCursorPos)
        ic.endBatchEdit()
    }

    private fun performFeedback() {
        if (currentSettings.vibrationEnabled) vibrate()
        if (currentSettings.keySoundEnabled) {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(12)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}

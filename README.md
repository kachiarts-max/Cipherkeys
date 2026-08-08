# CipherKeys

A custom Android soft keyboard (Input Method Service) that can type normally or
auto-transform text into leet/cipher styles as you type: NORMAL, CLASSIC LEET, ELITE,
HACKER, ULTRA, and DECODE.

## ⚠️ Important: this was written outside Android Studio

This project was generated in a sandbox with **no Android SDK, no Gradle, and no
internet access** - so while every file was written carefully and reasoned through line
by line (including working through encode/decode examples by hand to catch bugs), it has
**not been compiled or run**. Open it in Android Studio and build it before relying on
it; treat this as a strong first draft, not a verified build.

## Project structure

```
CipherKeys/
  app/src/main/java/com/cipherkeys/app/
    keyboard/   CipherKeysIME (the InputMethodService) + CipherKeysKeyboardView
    encoder/    Encoder interface + ClassicLeet/Elite/Hacker/UltraEncoder
    decoder/    Decoder interface + CipherKeysDecoder (reverses all encode modes)
    data/       KeyboardMode, KeyboardTheme, LeetMappings (configurable tables),
                SettingsRepository (DataStore)
    settings/   SettingsActivity + Compose settings screen
    ui/         Compose Material 3 theme
  app/src/test/...  JUnit tests for every encoder + the decoder
```

## Build instructions

1. Install **Android Studio** (Koala/2024.1 or newer recommended).
2. `File -> Open` and select the `CipherKeys/` folder.
3. Android Studio will offer to generate the Gradle wrapper (`gradlew`/`gradlew.bat`) -
   accept it. (The wrapper binary isn't included here since it requires downloading
   `gradle-wrapper.jar` from the network, which wasn't available while building this.)
4. Let Gradle sync - it will pull the Android Gradle Plugin 8.5.2, Kotlin 1.9.24, and
   the Compose/DataStore dependencies listed in `app/build.gradle.kts`.
5. Run **Build -> Make Project**. Fix anything Android Studio flags (see Limitations
   below for the most likely spots) and re-build.
6. Run the JUnit tests: right-click `app/src/test` -> **Run 'Tests in...'**, or
   `./gradlew testDebugUnitTest` once the wrapper is generated.

## Building an APK

- **Debug APK** (for testing on a device/emulator): `Build -> Build Bundle(s)/APK(s) ->
  Build APK(s)`, or `./gradlew assembleDebug`. Output lands in
  `app/build/outputs/apk/debug/app-debug.apk`.
- **Release APK**: you'll need to configure signing first (`Build -> Generate Signed
  Bundle / APK`, create or select a keystore), then `./gradlew assembleRelease`.

## Enabling CipherKeys as your keyboard

1. Install the APK on a device/emulator.
2. Open the CipherKeys app - tap **"Enable CipherKeys keyboard"**, which opens Android's
   input method settings. Alternatively: **Settings -> System -> Languages & input ->
   On-screen keyboard -> Manage keyboards**, and toggle CipherKeys on.
3. In any text field, switch to it via the keyboard-switcher icon in the navigation bar,
   or long-press the spacebar of your current keyboard and pick CipherKeys.
4. Tap a mode in the toolbar (NORMAL / LEET / ELITE / HACKER / ULTRA / DECODE) to change
   how subsequent typing behaves. DECODE also immediately decodes whatever text is
   already in the focused field.

## Known limitations / things worth double-checking

- **Not compiled or tested on a device** - see the warning above. Some Android API
  nuances (permission checks, `InputConnection` edge cases on certain apps/keyboards
  types) may need small fixes once you build it for real.
- **Your spec's worked examples don't match your spec's own substitution table**, and I
  implemented the table literally rather than the examples:
  - `l -> 1` is in your CLASSIC_LEET table, so "Hello World" actually encodes to
    `H3110 W0r1d`, not the `H3ll0 W0r1d` shown in your example (which would require *not*
    substituting `l`).
  - Your HACKER example ("Access Granted" -> "4CC355 GR4N73D") doesn't substitute `c`,
    `g`, or `n`, but a genuinely "more aggressive" table (as HACKER is described) should
    arguably substitute *more* than CLASSIC, not less. I built HACKER with a richer
    symbol table (multi-char tokens like `|-|` for `h`, `(_)` for `u`, etc.) that's
    intentionally more aggressive, at the cost of not reproducing that exact example
    string. If you want literal reproduction of your example strings instead, that's a
    quick change to `LeetMappings.kt` - just tell me which behavior you actually want.
- **DECODE is lossy by design.** Several modes map multiple letters to the same token
  (`i` and `l` both naturally want `"1"`). The decoder resolves shared tokens with a
  documented, deterministic preference (`i` wins over `l`, since that's what your own
  DECODE example requires) - but that means text that used the losing letter (`l`) won't
  round-trip perfectly. This is inherent to a many-to-one visual cipher, not a bug to
  "fix" without changing the encoding tables themselves.
- **No symbol/number toggle row** - punctuation is limited to `, . ? !` plus a always-on
  number row, rather than a dedicated switchable symbols panel. Straightforward to add
  if you want fuller punctuation coverage.
- **Shift behaves like "one-shot" shift** (auto-turns-off after one letter), not a full
  caps-lock. Standard on most keyboards but worth confirming that's what you want.
- **ULTRA's randomization isn't persisted per-keystroke-in-flight** - if you're mid-word
  and switch modes, only future characters are affected, which matches spec but is worth
  knowing.
- App icon is a placeholder vector, not real artwork.

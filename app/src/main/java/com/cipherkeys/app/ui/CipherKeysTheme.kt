package com.cipherkeys.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ============================================================
// CIPHERKEYS APP COLORS
// These colors are for the APP UI, not the keyboard itself.
// ============================================================

private val CipherKeysDarkColors = darkColorScheme(
    primary = Color(0xFF9C6CFF),
    onPrimary = Color(0xFFFFFFFF),

    primaryContainer = Color(0xFF35156B),
    onPrimaryContainer = Color(0xFFE9DDFF),

    secondary = Color(0xFF5DE1D0),
    onSecondary = Color(0xFF003731),

    secondaryContainer = Color(0xFF0D4F48),
    onSecondaryContainer = Color(0xFF80F8E8),

    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF462A00),

    background = Color(0xFF0B0A0F),
    onBackground = Color(0xFFF2EDF7),

    surface = Color(0xFF141219),
    onSurface = Color(0xFFF2EDF7),

    surfaceVariant = Color(0xFF24202B),
    onSurfaceVariant = Color(0xFFC9C1D0),

    outline = Color(0xFF918899),
    outlineVariant = Color(0xFF403A46),

    error = Color(0xFFFF6B6B),
    onError = Color(0xFF5C0000)
)

private val CipherKeysLightColors = lightColorScheme(
    primary = Color(0xFF6F3CC3),
    onPrimary = Color(0xFFFFFFFF),

    primaryContainer = Color(0xFFEBDCFF),
    onPrimaryContainer = Color(0xFF26005A),

    secondary = Color(0xFF087F73),
    onSecondary = Color(0xFFFFFFFF),

    secondaryContainer = Color(0xFF9DF3E6),
    onSecondaryContainer = Color(0xFF00201C),

    tertiary = Color(0xFF925F00),
    onTertiary = Color(0xFFFFFFFF),

    background = Color(0xFFFAF8FC),
    onBackground = Color(0xFF1C1A20),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1A20),

    surfaceVariant = Color(0xFFE9E2ED),
    onSurfaceVariant = Color(0xFF4B454F),

    outline = Color(0xFF79727D),
    outlineVariant = Color(0xFFCAC3CE),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun CipherKeysTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (useDarkTheme) {
        CipherKeysDarkColors
    } else {
        CipherKeysLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

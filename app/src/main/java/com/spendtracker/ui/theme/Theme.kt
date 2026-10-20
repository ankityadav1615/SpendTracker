package com.spendtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6C4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6F2CF),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4D6357),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9D9),
    onSecondaryContainer = Color(0xFF0A1F16),
    tertiary = Color(0xFF3D6373),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E8FB),
    onTertiaryContainer = Color(0xFF001F29),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF5),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF5),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFBFC9C2),
    surfaceDim = Color(0xFFD6DBD6),
    surfaceBright = Color(0xFFF5FBF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5EF),
    surfaceContainer = Color(0xFFE9EFEA),
    surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDEE4DE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD6B4),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFFA6F2CF),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCFE9D9),
    tertiary = Color(0xFFA5CCDF),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF244C5B),
    onTertiaryContainer = Color(0xFFC1E8FB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF404944),
    surfaceDim = Color(0xFF0F1512),
    surfaceBright = Color(0xFF343B37),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
)

@Composable
fun SpendTrackerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

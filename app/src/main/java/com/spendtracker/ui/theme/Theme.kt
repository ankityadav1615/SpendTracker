package com.spendtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.spendtracker.R
import com.spendtracker.model.Category

@OptIn(ExperimentalTextApi::class)
private fun baloo(weight: Int) = Font(
    R.font.baloo2_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Rounded display face for headings and amounts. */
val Baloo = FontFamily(baloo(500), baloo(600), baloo(700))

/** Humanist body face; both families also cover Devanagari for a future Hindi UI. */
val Mukta = FontFamily(
    Font(R.font.mukta_regular, FontWeight.Normal),
    Font(R.font.mukta_medium, FontWeight.Medium),
    Font(R.font.mukta_semibold, FontWeight.SemiBold),
)

/** Colours that Material 3 has no role for. */
@Immutable
data class SpendColors(
    val card: Color,
    val cardBorder: Color,
    val tint: Color,
    val line: Color,
    val actionText: Color,
    val credit: Color,
    val muted: Color,
    val stripLeftA: Color,
    val stripLeftB: Color,
    val isDark: Boolean,
) {
    /** Category colour adjusted so it stays readable on this theme's cards. */
    fun category(category: Category): Color {
        val base = Color(category.color)
        return if (isDark) lerp(base, Color.White, 0.35f) else base
    }

    /** Soft tile behind a category icon. */
    fun categoryTile(category: Category): Color =
        category(category).copy(alpha = if (isDark) 0.20f else 0.14f).compositeOver(card)
}

private val LightSpend = SpendColors(
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFF3E7D8),
    tint = Color(0xFFF8ECDD),
    line = Color(0xFFEEDFCC),
    actionText = Color(0xFFB53C0F),
    credit = Color(0xFF2F7A4B),
    muted = Color(0xFF7C6F66),
    stripLeftA = Color(0xFFF6EADB),
    stripLeftB = Color(0xFFFFF8EF),
    isDark = false,
)

private val DarkSpend = SpendColors(
    card = Color(0xFF28211D),
    cardBorder = Color(0xFF342A24),
    tint = Color(0xFF3A2F29),
    line = Color(0xFF41362F),
    actionText = Color(0xFFFF9C70),
    credit = Color(0xFF7BCB95),
    muted = Color(0xFFA8998C),
    stripLeftA = Color(0xFF3A2F29),
    stripLeftB = Color(0xFF2E2621),
    isDark = true,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFCC4614),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFBE3D6),
    onPrimaryContainer = Color(0xFF5C1D05),
    inversePrimary = Color(0xFFFFB59A),
    secondary = Color(0xFF6A5D54),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF2B241F),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF2F7A4B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCEFE2),
    onTertiaryContainer = Color(0xFF0F3B20),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFFF6EC),
    onBackground = Color(0xFF2B241F),
    surface = Color(0xFFFFF6EC),
    onSurface = Color(0xFF2B241F),
    surfaceVariant = Color(0xFFF8ECDD),
    onSurfaceVariant = Color(0xFF6A5D54),
    surfaceTint = Color(0xFFCC4614),
    inverseSurface = Color(0xFF3A312B),
    inverseOnSurface = Color(0xFFFBEFE3),
    outline = Color(0xFF7C6F66),
    outlineVariant = Color(0xFFEEDFCC),
    surfaceBright = Color(0xFFFFF6EC),
    surfaceDim = Color(0xFFEFE2D3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF6EC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFF6EC),
    surfaceContainerHighest = Color(0xFFF8ECDD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9C70),
    onPrimary = Color(0xFF3D1300),
    primaryContainer = Color(0xFF5A2A14),
    onPrimaryContainer = Color(0xFFFFDBCC),
    inversePrimary = Color(0xFFCC4614),
    secondary = Color(0xFFCBBBAE),
    onSecondary = Color(0xFF2B241F),
    secondaryContainer = Color(0xFFF3E9DF),
    onSecondaryContainer = Color(0xFF2B241F),
    tertiary = Color(0xFF7BCB95),
    onTertiary = Color(0xFF00391A),
    tertiaryContainer = Color(0xFF1F4A2E),
    onTertiaryContainer = Color(0xFFBDEBC9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1C1714),
    onBackground = Color(0xFFF3E9DF),
    surface = Color(0xFF1C1714),
    onSurface = Color(0xFFF3E9DF),
    surfaceVariant = Color(0xFF3A2F29),
    onSurfaceVariant = Color(0xFFCBBBAE),
    surfaceTint = Color(0xFFFF9C70),
    inverseSurface = Color(0xFFF3E9DF),
    inverseOnSurface = Color(0xFF2B241F),
    outline = Color(0xFFA8998C),
    outlineVariant = Color(0xFF41362F),
    surfaceBright = Color(0xFF3A2F29),
    surfaceDim = Color(0xFF1C1714),
    surfaceContainerLowest = Color(0xFF28211D),
    surfaceContainerLow = Color(0xFF221C18),
    surfaceContainer = Color(0xFF28211D),
    surfaceContainerHigh = Color(0xFF221C18),
    surfaceContainerHighest = Color(0xFF3A2F29),
)

private fun display(size: Int, line: Int, weight: Int = 600, tracking: Double = 0.0) = TextStyle(
    fontFamily = Baloo,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

private fun body(size: Double, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Mukta,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

private val AppTypography = Typography(
    displayLarge = display(52, 56, tracking = -0.02),
    displayMedium = display(44, 48, tracking = -0.02),
    displaySmall = display(36, 42, tracking = -0.015),
    headlineLarge = display(32, 38, tracking = -0.015),
    headlineMedium = display(28, 34, tracking = -0.015),
    headlineSmall = display(24, 30),
    titleLarge = display(20, 26),
    titleMedium = body(16.0, 22, FontWeight.SemiBold),
    titleSmall = body(14.0, 20, FontWeight.SemiBold),
    bodyLarge = body(16.0, 24),
    bodyMedium = body(14.0, 20),
    bodySmall = body(13.0, 18),
    labelLarge = body(16.0, 20, FontWeight.SemiBold),
    labelMedium = body(13.0, 16, FontWeight.Medium),
    labelSmall = body(12.0, 16, FontWeight.Medium),
)

/** Amount text in list rows: rounded display face, same size as row titles. */
val AmountStyle = display(17, 22)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LocalSpendColors = staticCompositionLocalOf { LightSpend }

object SpendTheme {
    val colors: SpendColors
        @Composable @ReadOnlyComposable
        get() = LocalSpendColors.current
}

@Composable
fun SpendTrackerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpendColors provides if (darkTheme) DarkSpend else LightSpend) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

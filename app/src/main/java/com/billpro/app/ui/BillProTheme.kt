package com.billpro.app.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

private val defaultTypography = Typography()
val BillProTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = FontFamily.SansSerif),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = FontFamily.SansSerif),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = FontFamily.SansSerif),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = FontFamily.SansSerif),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = FontFamily.SansSerif),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = FontFamily.SansSerif),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = FontFamily.SansSerif),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = FontFamily.SansSerif),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = FontFamily.SansSerif),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = FontFamily.SansSerif),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = FontFamily.SansSerif)
)

@Immutable
data class BillProPalette(
    // Reference Image Design Tokens
    val primaryYellow: Color = Color(0xFFFFC928),
    val secondaryYellow: Color = Color(0xFFFFD84D),
    val darkBackground: Color = Color(0xFF191919),
    val darkCard: Color = Color(0xFF222222),
    val cardBackground: Color = Color(0xFFFFFFFF),
    val secondaryBackground: Color = Color(0xFFF7F6F2),
    val primaryText: Color = Color(0xFF171717),
    val secondaryText: Color = Color(0xFF777777),
    val mutedText: Color = Color(0xFFA0A0A0),
    val cardBorder: Color = Color(0xFFEBE8E0),
    val dividerColor: Color = Color(0xFFEFEFEA),
    val success: Color = Color(0xFF24A36A),
    val successContainer: Color = Color(0x1F24A36A),
    val warning: Color = Color(0xFFE99A28),
    val warningContainer: Color = Color(0x1FE99A28),
    val error: Color = Color(0xFFE34B4B),
    val errorContainer: Color = Color(0x1FE34B4B),

    // Backward-compatible mappings
    val background: Color = Color(0xFFF7F6F2),
    val surface: Color = Color(0xFFFFFFFF),
    val surfaceVariant: Color = Color(0xFFF1EFE9),
    val accent: Color = Color(0xFFFFC928),
    val accentSecondary: Color = Color(0xFFFFD84D),
    val text: Color = Color(0xFF171717),
    val textSecondary: Color = Color(0xFF777777),
    val muted: Color = Color(0xFFA0A0A0),
    val info: Color = Color(0xFF1976D2),
    val infoContainer: Color = Color(0x1F1976D2)
) {
    val yellowGradient: Brush = Brush.horizontalGradient(
        colors = listOf(Color(0xFFFFC928), Color(0xFFFFD84D))
    )
    val yellowHeaderGradient: Brush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFCF33), Color(0xFFFFC928))
    )
    val darkCardGradient: Brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF262626), Color(0xFF191919))
    )
    val revenueCardGradient: Brush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFFAF9F5))
    )
    val accentGradient: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFFFFC928), Color(0xFFFFD84D))
    )
}

object BillProTheme {
    val colors = BillProPalette()
}

@Composable
fun BillProTheme(content: @Composable () -> Unit) {
    val palette = BillProTheme.colors
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = palette.primaryYellow,
            onPrimary = palette.primaryText,
            secondary = palette.darkBackground,
            onSecondary = Color.White,
            background = palette.secondaryBackground,
            onBackground = palette.primaryText,
            surface = palette.cardBackground,
            onSurface = palette.primaryText,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.secondaryText,
            error = palette.error,
            onError = Color.White
        ),
        typography = BillProTypography
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif)
        ) {
            content()
        }
    }
}

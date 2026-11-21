package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.i18n.ArabicStrings
import com.example.i18n.EnglishStrings
import com.example.i18n.LocalWassilStrings

private val WassilLightColorScheme = lightColorScheme(
    primary = WassilBluePrimary,
    onPrimary = WassilSurface,
    primaryContainer = WassilBlueLight,
    onPrimaryContainer = WassilBlueDark,
    secondary = WassilSkyAccent,
    onSecondary = WassilSurface,
    secondaryContainer = WassilCyanSoft,
    onSecondaryContainer = WassilBlueDark,
    background = WassilBackground,
    onBackground = WassilTextPrimary,
    surface = WassilSurface,
    onSurface = WassilTextPrimary,
    surfaceVariant = WassilSurfaceVariant,
    onSurfaceVariant = WassilTextSecondary,
    outline = WassilBorder,
    error = WassilErrorRed,
    onError = WassilSurface,
    errorContainer = WassilErrorBg,
    onErrorContainer = WassilErrorRed
)

val WassilShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun WassilTheme(
    language: AppLanguage = AppLanguage.ARABIC,
    content: @Composable () -> Unit
) {
    val strings = if (language == AppLanguage.ARABIC) ArabicStrings else EnglishStrings
    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalWassilStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = WassilLightColorScheme,
            typography = wassilTypography(isArabic = language == AppLanguage.ARABIC),
            shapes = WassilShapes,
            content = content
        )
    }
}

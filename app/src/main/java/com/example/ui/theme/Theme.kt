package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTeal,
    onPrimary = TextOnPrimary,
    primaryContainer = PrimaryTealContainer,
    onPrimaryContainer = PrimaryTealDark,
    secondary = PrimaryTealMid,
    onSecondary = TextOnPrimary,
    secondaryContainer = PrimaryTealLight,
    onSecondaryContainer = PrimaryTealDark,
    background = SurfaceWarm,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceOverlay,
    onSurfaceVariant = TextSecondary,
    outline = BorderMuted,
    outlineVariant = BorderSoft,
    error = ErrorRose,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorRose,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryTealMid,
    onPrimary = DarkBackground,
    primaryContainer = PrimaryTealDark,
    onPrimaryContainer = PrimaryTealLight,
    secondary = PrimaryTeal,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkSurfaceVariant,
    error = ErrorRose,
    onError = Color.White,
    errorContainer = Color(0xFF4A1122),
    onErrorContainer = Color(0xFFFFB3C1),
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

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

private val MyPrevDarkColorScheme = darkColorScheme(
    primary = BrandIndigoLight,
    onPrimary = Color.White,
    primaryContainer = BrandIndigoContainerDark,
    onPrimaryContainer = BrandIndigoContainerLight,

    secondary = BrandTealLight,
    onSecondary = Color.Black,
    secondaryContainer = BrandTealContainerDark,
    onSecondaryContainer = BrandTealContainerLight,

    tertiary = BrandAmber,
    onTertiary = Color.Black,
    tertiaryContainer = BrandAmberContainerDark,
    onTertiaryContainer = BrandAmberContainerLight,

    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainerHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,

    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorRed,
    errorContainer = ErrorContainerDark,
    onError = Color.White,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val MyPrevLightColorScheme = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = BrandIndigoContainerLight,
    onPrimaryContainer = BrandIndigoDark,

    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = BrandTealContainerLight,
    onSecondaryContainer = BrandTealDark,

    tertiary = BrandAmber,
    onTertiary = Color.White,
    tertiaryContainer = BrandAmberContainerLight,
    onTertiaryContainer = Color(0xFF78350F),

    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceContainerHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,

    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = ErrorRed,
    errorContainer = ErrorContainerLight,
    onError = Color.White,
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun MyPrevTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Default to brand colors as specified in requirements
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MyPrevDarkColorScheme
        else -> MyPrevLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

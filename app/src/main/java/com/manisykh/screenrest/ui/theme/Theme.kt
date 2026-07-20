package com.manisykh.screenrest.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = Primary80,
    secondary = Secondary80,
    tertiary = Tertiary80,
    background = RestNight,
    surface = RestNightSoft,
    surfaceVariant = Color(0xFF233C6E),
    outlineVariant = Color(0xFF3A527E),
    primaryContainer = Color(0xFF2F4F9E),
    onPrimaryContainer = Color(0xFFF0F4FF),
    secondaryContainer = Color(0xFF1E5F55),
    tertiaryContainer = Color(0xFF4E3E1F),
    errorContainer = Color(0xFF633333),
    onBackground = Color(0xFFF7FAFF),
    onSurface = Color(0xFFF7FAFF),
    onSurfaceVariant = Color(0xFFC7D0E2),
)

private val LightColorScheme = lightColorScheme(
    primary = Primary40,
    secondary = Secondary40,
    tertiary = Tertiary40,
    background = AppBackground,
    surface = AppSurface,
    surfaceVariant = AppMuted,
    outlineVariant = AppBorder,
    primaryContainer = RestBlueSoft,
    onPrimaryContainer = RestNight,
    secondaryContainer = Color(0xFFE6F6F2),
    tertiaryContainer = RestCream,
    errorContainer = Color(0xFFFFE8E5),
    onBackground = AppForeground,
    onSurface = AppForeground,
    onSurfaceVariant = AppMutedForeground

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun ScreenTimeManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
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

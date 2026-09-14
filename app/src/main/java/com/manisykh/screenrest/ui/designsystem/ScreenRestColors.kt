package com.manisykh.screenrest.ui.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Neutral Family Rhythm color tokens.
 *
 * The policy engine must never depend on these values. They translate an already resolved
 * product state into a consistent visual treatment only.
 */
object ScreenRestPalette {
    val Navy = Color(0xFF102A52)
    val NavyStrong = Color(0xFF071C3A)
    val NavySoft = Color(0xFF52627D)

    val Cobalt = Color(0xFF246BFD)
    val CobaltPressed = Color(0xFF1554D8)
    val CobaltSoft = Color(0xFFEAF1FF)

    val Teal = Color(0xFF0F9F98)
    val TealStrong = Color(0xFF087D78)
    val TealSoft = Color(0xFFE4F7F4)

    val Amber = Color(0xFFE88912)
    val AmberStrong = Color(0xFFB96300)
    val AmberSoft = Color(0xFFFFF3DE)

    val Coral = Color(0xFFE84C4C)
    val CoralStrong = Color(0xFFBB2E35)
    val CoralSoft = Color(0xFFFFEAEA)

    val Indigo = Color(0xFF635BDB)
    val IndigoSoft = Color(0xFFEFEDFF)

    val WarmBackground = Color(0xFFFFFCF8)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSubtle = Color(0xFFF6F8FC)
    val SurfaceStrong = Color(0xFFEEF2F8)
    val Border = Color(0xFFDDE3EC)
    val BorderStrong = Color(0xFFC8D1DF)
    val Disabled = Color(0xFF9AA5B5)

    val DarkBackground = Color(0xFF071831)
    val DarkSurface = Color(0xFF0E2445)
    val DarkSurfaceSubtle = Color(0xFF152C4E)
    val DarkBorder = Color(0xFF304866)
    val DarkText = Color(0xFFF7FAFF)
    val DarkMutedText = Color(0xFFBEC9D9)
}

@Immutable
data class ScreenRestSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val blocked: Color,
    val onBlocked: Color,
    val blockedContainer: Color,
    val onBlockedContainer: Color,
    val schedule: Color,
    val scheduleContainer: Color,
    val divider: Color,
    val progressTrack: Color,
    val scrimStrong: Color,
)

internal val LightScreenRestColors = ScreenRestSemanticColors(
    success = ScreenRestPalette.Teal,
    onSuccess = Color.White,
    successContainer = ScreenRestPalette.TealSoft,
    onSuccessContainer = ScreenRestPalette.TealStrong,
    warning = ScreenRestPalette.Amber,
    onWarning = Color.White,
    warningContainer = ScreenRestPalette.AmberSoft,
    onWarningContainer = ScreenRestPalette.AmberStrong,
    blocked = ScreenRestPalette.Coral,
    onBlocked = Color.White,
    blockedContainer = ScreenRestPalette.CoralSoft,
    onBlockedContainer = ScreenRestPalette.CoralStrong,
    schedule = ScreenRestPalette.Indigo,
    scheduleContainer = ScreenRestPalette.IndigoSoft,
    divider = ScreenRestPalette.Border,
    progressTrack = ScreenRestPalette.SurfaceStrong,
    scrimStrong = Color(0xB3071831),
)

internal val DarkScreenRestColors = ScreenRestSemanticColors(
    success = Color(0xFF42D2C8),
    onSuccess = ScreenRestPalette.NavyStrong,
    successContainer = Color(0xFF123F43),
    onSuccessContainer = Color(0xFF9BE9E2),
    warning = Color(0xFFFFB84F),
    onWarning = ScreenRestPalette.NavyStrong,
    warningContainer = Color(0xFF4D3615),
    onWarningContainer = Color(0xFFFFD796),
    blocked = Color(0xFFFF7979),
    onBlocked = ScreenRestPalette.NavyStrong,
    blockedContainer = Color(0xFF542A31),
    onBlockedContainer = Color(0xFFFFB4B4),
    schedule = Color(0xFFB4ACFF),
    scheduleContainer = Color(0xFF302D61),
    divider = ScreenRestPalette.DarkBorder,
    progressTrack = Color(0xFF263D5D),
    scrimStrong = Color(0xD9000B1E),
)

internal val ScreenRestLightColorScheme: ColorScheme = lightColorScheme(
    primary = ScreenRestPalette.Cobalt,
    onPrimary = Color.White,
    primaryContainer = ScreenRestPalette.CobaltSoft,
    onPrimaryContainer = ScreenRestPalette.Navy,
    secondary = ScreenRestPalette.Teal,
    onSecondary = Color.White,
    secondaryContainer = ScreenRestPalette.TealSoft,
    onSecondaryContainer = ScreenRestPalette.TealStrong,
    tertiary = ScreenRestPalette.Amber,
    onTertiary = Color.White,
    tertiaryContainer = ScreenRestPalette.AmberSoft,
    onTertiaryContainer = ScreenRestPalette.AmberStrong,
    error = ScreenRestPalette.Coral,
    onError = Color.White,
    errorContainer = ScreenRestPalette.CoralSoft,
    onErrorContainer = ScreenRestPalette.CoralStrong,
    background = ScreenRestPalette.WarmBackground,
    onBackground = ScreenRestPalette.Navy,
    surface = ScreenRestPalette.Surface,
    onSurface = ScreenRestPalette.Navy,
    surfaceVariant = ScreenRestPalette.SurfaceSubtle,
    onSurfaceVariant = ScreenRestPalette.NavySoft,
    outline = ScreenRestPalette.BorderStrong,
    outlineVariant = ScreenRestPalette.Border,
    scrim = Color(0x66071831),
)

internal val ScreenRestDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF80A9FF),
    onPrimary = ScreenRestPalette.NavyStrong,
    primaryContainer = Color(0xFF173D7A),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF70DAD2),
    onSecondary = ScreenRestPalette.NavyStrong,
    secondaryContainer = Color(0xFF174A4D),
    onSecondaryContainer = Color(0xFFB8F1EC),
    tertiary = Color(0xFFFFBD5B),
    onTertiary = ScreenRestPalette.NavyStrong,
    tertiaryContainer = Color(0xFF503614),
    onTertiaryContainer = Color(0xFFFFD99E),
    error = Color(0xFFFF8585),
    onError = ScreenRestPalette.NavyStrong,
    errorContainer = Color(0xFF5A2930),
    onErrorContainer = Color(0xFFFFC2C2),
    background = ScreenRestPalette.DarkBackground,
    onBackground = ScreenRestPalette.DarkText,
    surface = ScreenRestPalette.DarkSurface,
    onSurface = ScreenRestPalette.DarkText,
    surfaceVariant = ScreenRestPalette.DarkSurfaceSubtle,
    onSurfaceVariant = ScreenRestPalette.DarkMutedText,
    outline = Color(0xFF526A89),
    outlineVariant = ScreenRestPalette.DarkBorder,
    scrim = Color(0xB3000714),
)

internal val LocalScreenRestSemanticColors = staticCompositionLocalOf {
    LightScreenRestColors
}

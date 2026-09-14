package com.manisykh.screenrest.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val ScreenRestShapes = Shapes(
    small = RoundedCornerShape(ScreenRestRadii().control),
    medium = RoundedCornerShape(ScreenRestRadii().row),
    large = RoundedCornerShape(ScreenRestRadii().card),
)

/**
 * Theme for redesigned surfaces.
 *
 * The app shell applies this theme globally while individual screens migrate in stages.
 * Policy behavior remains independent from these presentation tokens.
 */
@Composable
fun ScreenRestDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val semanticColors = if (darkTheme) DarkScreenRestColors else LightScreenRestColors
    val colorScheme = if (darkTheme) ScreenRestDarkColorScheme else ScreenRestLightColorScheme

    CompositionLocalProvider(
        LocalScreenRestSemanticColors provides semanticColors,
        LocalScreenRestSpacing provides ScreenRestSpacing(),
        LocalScreenRestRadii provides ScreenRestRadii(),
        LocalScreenRestSizes provides ScreenRestSizes(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ScreenRestTypography,
            shapes = ScreenRestShapes,
            content = content,
        )
    }
}

object ScreenRestTheme {
    val colors: ScreenRestSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalScreenRestSemanticColors.current

    val spacing: ScreenRestSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalScreenRestSpacing.current

    val radii: ScreenRestRadii
        @Composable
        @ReadOnlyComposable
        get() = LocalScreenRestRadii.current

    val sizes: ScreenRestSizes
        @Composable
        @ReadOnlyComposable
        get() = LocalScreenRestSizes.current
}

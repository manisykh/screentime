package com.manisykh.screenrest.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ScreenRestSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val section: Dp = 40.dp,
)

@Immutable
data class ScreenRestRadii(
    val chip: Dp = 999.dp,
    val control: Dp = 14.dp,
    val button: Dp = 16.dp,
    val row: Dp = 18.dp,
    val card: Dp = 22.dp,
    val dialog: Dp = 24.dp,
)

@Immutable
data class ScreenRestSizes(
    val minimumTouchTarget: Dp = 48.dp,
    val compactIcon: Dp = 20.dp,
    val icon: Dp = 24.dp,
    val prominentIcon: Dp = 32.dp,
    val listIconContainer: Dp = 44.dp,
    val buttonHeight: Dp = 54.dp,
    val contentMaxWidth: Dp = 920.dp,
    val phoneHorizontalPadding: Dp = 20.dp,
)

internal val LocalScreenRestSpacing = staticCompositionLocalOf { ScreenRestSpacing() }
internal val LocalScreenRestRadii = staticCompositionLocalOf { ScreenRestRadii() }
internal val LocalScreenRestSizes = staticCompositionLocalOf { ScreenRestSizes() }

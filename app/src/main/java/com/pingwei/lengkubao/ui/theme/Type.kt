package com.pingwei.lengkubao.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
private const val SCALE = 0.88f

private fun Typography.scale(): Typography {
    return Typography(
        displayLarge = displayLarge.scale(),
        displayMedium = displayMedium.scale(),
        displaySmall = displaySmall.scale(),
        headlineLarge = headlineLarge.scale(),
        headlineMedium = headlineMedium.scale(),
        headlineSmall = headlineSmall.scale(),
        titleLarge = titleLarge.scale(),
        titleMedium = titleMedium.scale(),
        titleSmall = titleSmall.scale(),
        bodyLarge = bodyLarge.scale(),
        bodyMedium = bodyMedium.scale(),
        bodySmall = bodySmall.scale(),
        labelLarge = labelLarge.scale(),
        labelMedium = labelMedium.scale(),
        labelSmall = labelSmall.scale()
    )
}

private fun TextStyle.scale(): TextStyle {
    return copy(
        fontSize = fontSize * SCALE,
        lineHeight = lineHeight * SCALE,
        letterSpacing = letterSpacing * SCALE
    )
}

val AppTypography: Typography = Typography().scale()

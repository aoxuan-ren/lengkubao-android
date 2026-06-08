//com.pingwei.lengkubao.ui.theme.LengkubaoTheme.kt
package com.pingwei.lengkubao.ui.theme

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

// 自定义颜色值（显式初始化，避免未初始化错误）
private val Purple40 = Color(0xFF6650a4)
private val Purple80 = Color(0xFF3700b3)
private val PurpleGrey40 = Color(0xFF625b71)
private val PurpleGrey80 = Color(0xFF3e2f68)
private val Pink40 = Color(0xFFd0bcff)
private val Pink80 = Color(0xFF7e57c2)
private val LightBackground = Color(0xFFFFFBFE)
private val LightSurface = Color(0xFFFFFBFE)
private val DarkBackground = Color(0xFF1C1B1F)
private val DarkSurface = Color(0xFF1C1B1F)

// 深色主题配色
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = DarkBackground,
    surface = DarkSurface
)

// 浅色主题配色（默认）
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = LightBackground,
    surface = LightSurface
)

@Composable
fun LengkubaoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 动态配色（Android 12+）
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 规范Compose主题应用（传入外部content，而非内部直接调用）
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
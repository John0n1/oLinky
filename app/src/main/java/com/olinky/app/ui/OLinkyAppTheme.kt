package com.olinky.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    secondary = Color(0xFF38BDF8),
    tertiary = Color(0xFF22D3EE)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    secondary = Color(0xFF0EA5E9),
    tertiary = Color(0xFF06B6D4)
)

@Composable
fun OLinkyAppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}

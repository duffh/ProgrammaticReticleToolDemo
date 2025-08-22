package com.example.programmaticreticletooldemo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BlueButtonBackground,
)

@Composable
fun ProgrammaticReticleToolDemoTheme(
    content: @Composable () -> Unit
) {
    // Limit the color scheme to light mode for simplicity.
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
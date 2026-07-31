package com.recode.clashcraft.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF2E8),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4A635F),
    background = Color(0xFFF7F9F8),
    surface = Color(0xFFF7F9F8),
    surfaceVariant = Color(0xFFDAE5E2),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5CC),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFF9CF2E8),
    secondary = Color(0xFFB1CCC7),
)

@Composable
fun ClashCraftTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}


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

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF9CD67E),
    onPrimary = Color(0xFF0F3900),
    primaryContainer = Color(0xFF20510B),
    onPrimaryContainer = Color(0xFFB8F397),
    secondary = Color(0xFFB8CBB0),
    onSecondary = Color(0xFF243421),
    background = Color(0xFF11140E),
    onBackground = Color(0xFFE2E3DC),
    surface = Color(0xFF11140E),
    onSurface = Color(0xFFE2E3DC),
    surfaceVariant = Color(0xFF43493E),
    onSurfaceVariant = Color(0xFFC5C8BA),
    outline = Color(0xFF8F9285),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = SageContainer,
    onPrimaryContainer = ForestGreen,
    secondary = ForestGreen,
    onSecondary = Color.White,
    secondaryContainer = LightSage,
    onSecondaryContainer = DarkCharcoal,
    background = SoftCreamBg,
    onBackground = DarkCharcoal,
    surface = SoftCreamBg,
    onSurface = DarkCharcoal,
    surfaceVariant = LightSage,
    onSurfaceVariant = DarkSageText,
    outline = OutlineSage,
    outlineVariant = GraySage,
    error = AlertRed,
    onError = Color.White,
    errorContainer = AlertAmberBg,
    onErrorContainer = AlertRed
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to preserve the professional green theme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

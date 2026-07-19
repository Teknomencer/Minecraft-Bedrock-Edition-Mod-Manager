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
    primary = MinimalDarkPrimary,
    secondary = MinimalDarkSecondary,
    tertiary = MinimalDarkTertiary,
    background = MinimalDarkBackground,
    surface = MinimalDarkSurface,
    surfaceVariant = MinimalDarkSurfaceVariant,
    onPrimary = MinimalDarkOnPrimaryContainer,
    onSecondary = MinimalDarkOnPrimaryContainer,
    onTertiary = Color.Black,
    onBackground = MinimalDarkOnBackground,
    onSurface = MinimalDarkOnSurface,
    onSurfaceVariant = MinimalDarkOnSurfaceVariant,
    outline = MinimalDarkOutline,
    primaryContainer = MinimalDarkPrimaryContainer,
    onPrimaryContainer = MinimalDarkOnPrimaryContainer
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MinimalPrimary,
    secondary = MinimalSecondary,
    tertiary = MinimalTertiary,
    background = MinimalBackground,
    surface = MinimalSurface,
    surfaceVariant = MinimalSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = MinimalOnBackground,
    onSurface = MinimalOnSurface,
    onSurfaceVariant = MinimalOnSurfaceVariant,
    outline = MinimalOutline,
    primaryContainer = MinimalPrimaryContainer,
    onPrimaryContainer = MinimalOnPrimaryContainer
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+ (set default to false to stick to our beautiful Minecraft branding by default)
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

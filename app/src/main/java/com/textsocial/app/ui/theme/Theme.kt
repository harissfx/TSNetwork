package com.textsocial.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(

  primary = BrandBlue,
  onPrimary = Color.White,
  primaryContainer = Color(0xFF232F72),
  onPrimaryContainer = Color(0xFFDCE1FF),
  secondary = BrandCyanSoft,
  onSecondary = Color(0xFF00363D),
  secondaryContainer = Color(0xFF1E3A3D),
  onSecondaryContainer = Color(0xFFB8EEF5),
  tertiary = BrandVioletLight,
  onTertiary = Color(0xFF001769),
  tertiaryContainer = Color(0xFF1E2F86),
  onTertiaryContainer = Color(0xFFEADDFF),
  background = DarkBackground,
  onBackground = Color(0xFFE7E7F0),
  surface = DarkSurface,
  onSurface = Color(0xFFE7E7F0),
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = Color(0xFFC7C7DA),
  outline = DarkOutline,
  outlineVariant = Color(0xFF3A3A50),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
  primary = BrandBlueDeep,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDCE1FF),
  onPrimaryContainer = Color(0xFF00174D),
  secondary = BrandCyanDeep,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFC8F5FC),
  onSecondaryContainer = Color(0xFF001F24),
  tertiary = BrandViolet,
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFB1C4FC),
  onTertiaryContainer = Color(0xFF001E59),
  background = LightBackground,
  onBackground = Color(0xFF14141F),
  surface = LightSurface,
  onSurface = Color(0xFF14141F),
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = Color(0xFF44445A),
  outline = LightOutline,
  outlineVariant = Color(0xFFD4D5E5),
  error = Color(0xFFBA1A1A),
  onError = Color.White,
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002),
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),

  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
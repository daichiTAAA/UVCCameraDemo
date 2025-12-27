package com.example.uvccamerademo.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = TealDark,
    onPrimary = Color(0xFF00332E),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCFFDF6),
    secondary = AmberDark,
    onSecondary = Color(0xFF3A1C00),
    secondaryContainer = Color(0xFF5C3B1A),
    onSecondaryContainer = Color(0xFFFFE9D2),
    tertiary = BlueDark,
    onTertiary = Color(0xFF0B2A6B),
    tertiaryContainer = Color(0xFF1B3B7A),
    onTertiaryContainer = Color(0xFFD8E4FF),
    background = SandDark,
    onBackground = InkDark,
    surface = Color(0xFF1A1916),
    onSurface = InkDark,
    surfaceVariant = ClayDark,
    onSurfaceVariant = Color(0xFFD6D0C7),
    outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFECE6),
    onPrimaryContainer = Color(0xFF003C36),
    secondary = AmberLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE2C7),
    onSecondaryContainer = Color(0xFF4A2400),
    tertiary = BlueLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE6FF),
    onTertiaryContainer = Color(0xFF102A60),
    background = SandLight,
    onBackground = InkLight,
    surface = Color(0xFFFFFFFF),
    onSurface = InkLight,
    surfaceVariant = ClayLight,
    onSurfaceVariant = Color(0xFF4E463C),
    outline = OutlineLight

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun UVCCameraDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

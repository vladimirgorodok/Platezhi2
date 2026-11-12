package ru.platezhi2.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import ru.platezhi2.app.data.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = VioletLight,
    onPrimaryContainer = VioletDark,
    secondary = Mint,
    onSecondary = Color.White,
    secondaryContainer = MintLight,
    onSecondaryContainer = Color(0xFF00513F),
    tertiary = Coral,
    onTertiary = Color.White,
    tertiaryContainer = CoralLight,
    onTertiaryContainer = Color(0xFF7A1F1E),
    background = PaperLight,
    onBackground = Ink,
    surface = CardLight,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFEFF6),
    onSurfaceVariant = InkSoft,
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9C8CFF),
    onPrimary = Color(0xFF1B1440),
    primaryContainer = VioletDark,
    onPrimaryContainer = VioletLight,
    secondary = Color(0xFF4FD1B0),
    onSecondary = Color(0xFF00382C),
    secondaryContainer = Color(0xFF00513F),
    onSecondaryContainer = MintLight,
    tertiary = Color(0xFFFFA6A5),
    onTertiary = Color(0xFF5A1413),
    tertiaryContainer = Color(0xFF7A1F1E),
    onTertiaryContainer = CoralLight,
    background = PaperDark,
    onBackground = Color(0xFFECECF5),
    surface = CardDark,
    onSurface = Color(0xFFECECF5),
    surfaceVariant = Color(0xFF262634),
    onSurfaceVariant = Color(0xFFB9B9C9),
    outline = OutlineDark,
)

@Composable
fun Platezhi2Theme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

package ru.platezhi2.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ru.platezhi2.app.data.settings.ThemeStyle

// Базовые фирменные цвета (стиль «Классика»)
val Violet = Color(0xFF6C5CE7)
val VioletDark = Color(0xFF4B3FD1)
val VioletLight = Color(0xFFE9E5FF)
val Mint = Color(0xFF00B894)
val MintLight = Color(0xFFD6F5EC)
val Coral = Color(0xFFFF7675)
val CoralLight = Color(0xFFFFE3E2)
val Amber = Color(0xFFF2B705)

// Нейтральные
val Ink = Color(0xFF16162A)
val InkSoft = Color(0xFF4A4A5C)
val PaperLight = Color(0xFFF6F6FB)
val CardLight = Color(0xFFFFFFFF)
val PaperDark = Color(0xFF0F0F18)
val CardDark = Color(0xFF1B1B27)
val OutlineLight = Color(0xFFE3E3EE)
val OutlineDark = Color(0xFF2C2C3B)

/**
 * Акценты стиля, которые нужны вне Material-схемы:
 * градиент верхнего блока, рамки состояния оплаты, иконки разделов.
 */
@Immutable
data class AppPalette(
    /** Основной акцент: кнопки, «к оплате», градиент верхнего блока. */
    val accent: Color,
    /** Тёмный оттенок акцента для градиента. */
    val accentDark: Color,
    /** Оплачено. */
    val success: Color,
    /** Просрочено. */
    val danger: Color,
)

val LocalAppPalette = staticCompositionLocalOf {
    AppPalette(accent = Violet, accentDark = VioletDark, success = Mint, danger = Coral)
}

/** Затемнение цвета для градиента: доля от 0 до 1. */
fun Color.darken(fraction: Float): Color = Color(
    red = red * (1f - fraction),
    green = green * (1f - fraction),
    blue = blue * (1f - fraction),
    alpha = alpha,
)

/** Цвет-образец стиля для выбора в настройках. */
fun ThemeStyle.accentColor(): Color = when (this) {
    ThemeStyle.CLASSIC -> Violet
    ThemeStyle.OCEAN -> Color(0xFF1E88E5)
    ThemeStyle.SUNSET -> Color(0xFFF0563C)
    ThemeStyle.FOREST -> Color(0xFF2E9E6B)
}

fun ThemeStyle.secondaryColor(): Color = when (this) {
    ThemeStyle.CLASSIC -> Mint
    ThemeStyle.OCEAN -> Color(0xFF12B5A5)
    ThemeStyle.SUNSET -> Color(0xFFFFB020)
    ThemeStyle.FOREST -> Color(0xFF6BBF59)
}

/** Светлая и тёмная схемы выбранного стиля. */
@Immutable
data class StyleSchemes(val light: ColorScheme, val dark: ColorScheme, val palette: AppPalette)

private fun lightScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    background: Color,
    surfaceVariant: Color,
    onSurfaceVariant: Color,
    outline: Color,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onPrimary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onPrimary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = Ink,
    surface = CardLight,
    onSurface = Ink,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
)

private fun darkScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    onSurfaceVariant: Color,
    outline: Color,
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onPrimary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onPrimary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = Color(0xFFECECF5),
    surface = surface,
    onSurface = Color(0xFFECECF5),
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
)

fun ThemeStyle.schemes(): StyleSchemes = when (this) {
    ThemeStyle.CLASSIC -> StyleSchemes(
        light = lightScheme(
            primary = Violet,
            onPrimary = Color.White,
            primaryContainer = VioletLight,
            onPrimaryContainer = VioletDark,
            secondary = Mint,
            secondaryContainer = MintLight,
            onSecondaryContainer = Color(0xFF00513F),
            tertiary = Coral,
            tertiaryContainer = CoralLight,
            onTertiaryContainer = Color(0xFF7A1F1E),
            background = PaperLight,
            surfaceVariant = Color(0xFFEFEFF6),
            onSurfaceVariant = InkSoft,
            outline = OutlineLight,
        ),
        dark = darkScheme(
            primary = Color(0xFF9C8CFF),
            onPrimary = Color(0xFF1B1440),
            primaryContainer = VioletDark,
            onPrimaryContainer = VioletLight,
            secondary = Color(0xFF4FD1B0),
            secondaryContainer = Color(0xFF00513F),
            onSecondaryContainer = MintLight,
            tertiary = Color(0xFFFFA6A5),
            tertiaryContainer = Color(0xFF7A1F1E),
            onTertiaryContainer = CoralLight,
            background = PaperDark,
            surface = CardDark,
            surfaceVariant = Color(0xFF262634),
            onSurfaceVariant = Color(0xFFB9B9C9),
            outline = OutlineDark,
        ),
        palette = AppPalette(
            accent = Violet,
            accentDark = VioletDark,
            success = Mint,
            danger = Coral,
        ),
    )

    // Синий «Океан»: холодные бирюзовые акценты
    ThemeStyle.OCEAN -> StyleSchemes(
        light = lightScheme(
            primary = Color(0xFF1E88E5),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEBFF),
            onPrimaryContainer = Color(0xFF0B4C8C),
            secondary = Color(0xFF12B5A5),
            secondaryContainer = Color(0xFFCFF5F0),
            onSecondaryContainer = Color(0xFF04443D),
            tertiary = Color(0xFFFFB020),
            tertiaryContainer = Color(0xFFFFF0D1),
            onTertiaryContainer = Color(0xFF6B4400),
            background = Color(0xFFF2F7FC),
            surfaceVariant = Color(0xFFE8F0F8),
            onSurfaceVariant = Color(0xFF46586B),
            outline = Color(0xFFD6E3EF),
        ),
        dark = darkScheme(
            primary = Color(0xFF6FB6FF),
            onPrimary = Color(0xFF06294A),
            primaryContainer = Color(0xFF0B4C8C),
            onPrimaryContainer = Color(0xFFDCEBFF),
            secondary = Color(0xFF3FD8C6),
            secondaryContainer = Color(0xFF04443D),
            onSecondaryContainer = Color(0xFFCFF5F0),
            tertiary = Color(0xFFFFCB5C),
            tertiaryContainer = Color(0xFF6B4400),
            onTertiaryContainer = Color(0xFFFFF0D1),
            background = Color(0xFF08131C),
            surface = Color(0xFF10202C),
            surfaceVariant = Color(0xFF1B2E3C),
            onSurfaceVariant = Color(0xFFAFC3D4),
            outline = Color(0xFF24404F),
        ),
        palette = AppPalette(
            accent = Color(0xFF1E88E5),
            accentDark = Color(0xFF0B4C8C),
            success = Color(0xFF12B5A5),
            danger = Color(0xFFE2574C),
        ),
    )

    // Тёплый «Закат»: кораллово-янтарные акценты
    ThemeStyle.SUNSET -> StyleSchemes(
        light = lightScheme(
            primary = Color(0xFFF0563C),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE2DA),
            onPrimaryContainer = Color(0xFF8C2A15),
            secondary = Color(0xFFEF9A00),
            secondaryContainer = Color(0xFFFFF0D1),
            onSecondaryContainer = Color(0xFF6B4400),
            tertiary = Color(0xFF8E5BFF),
            tertiaryContainer = Color(0xFFEDE4FF),
            onTertiaryContainer = Color(0xFF3F2B8C),
            background = Color(0xFFFFF6F2),
            surfaceVariant = Color(0xFFFBE9E2),
            onSurfaceVariant = Color(0xFF6B4A41),
            outline = Color(0xFFF0D8CF),
        ),
        dark = darkScheme(
            primary = Color(0xFFFF9A7B),
            onPrimary = Color(0xFF4A1508),
            primaryContainer = Color(0xFF8C2A15),
            onPrimaryContainer = Color(0xFFFFE2DA),
            secondary = Color(0xFFFFC85C),
            secondaryContainer = Color(0xFF6B4400),
            onSecondaryContainer = Color(0xFFFFF0D1),
            tertiary = Color(0xFFBFA6FF),
            tertiaryContainer = Color(0xFF3F2B8C),
            onTertiaryContainer = Color(0xFFEDE4FF),
            background = Color(0xFF170F0C),
            surface = Color(0xFF241713),
            surfaceVariant = Color(0xFF33221C),
            onSurfaceVariant = Color(0xFFD9C0B8),
            outline = Color(0xFF43291F),
        ),
        palette = AppPalette(
            accent = Color(0xFFF0563C),
            accentDark = Color(0xFFB93A24),
            success = Color(0xFF12B5A5),
            danger = Color(0xFFD6392F),
        ),
    )

    // Зелёный «Лес»
    ThemeStyle.FOREST -> StyleSchemes(
        light = lightScheme(
            primary = Color(0xFF2E9E6B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD8F5E6),
            onPrimaryContainer = Color(0xFF10593A),
            secondary = Color(0xFF5FA84E),
            secondaryContainer = Color(0xFFE6F7DC),
            onSecondaryContainer = Color(0xFF2F5A1E),
            tertiary = Color(0xFFE4804A),
            tertiaryContainer = Color(0xFFFDE6D8),
            onTertiaryContainer = Color(0xFF7A3A12),
            background = Color(0xFFF3FAF5),
            surfaceVariant = Color(0xFFE6F1E9),
            onSurfaceVariant = Color(0xFF43584B),
            outline = Color(0xFFD3E5D8),
        ),
        dark = darkScheme(
            primary = Color(0xFF6FD3A0),
            onPrimary = Color(0xFF08361F),
            primaryContainer = Color(0xFF10593A),
            onPrimaryContainer = Color(0xFFD8F5E6),
            secondary = Color(0xFFA5DA8C),
            secondaryContainer = Color(0xFF2F5A1E),
            onSecondaryContainer = Color(0xFFE6F7DC),
            tertiary = Color(0xFFFFB183),
            tertiaryContainer = Color(0xFF7A3A12),
            onTertiaryContainer = Color(0xFFFDE6D8),
            background = Color(0xFF0A1410),
            surface = Color(0xFF122019),
            surfaceVariant = Color(0xFF1D2F25),
            onSurfaceVariant = Color(0xFFB4C9BB),
            outline = Color(0xFF27402F),
        ),
        palette = AppPalette(
            accent = Color(0xFF2E9E6B),
            accentDark = Color(0xFF10593A),
            success = Color(0xFF3FAE77),
            danger = Color(0xFFDC5340),
        ),
    )
}

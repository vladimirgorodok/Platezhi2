package ru.platezhi2.app.ui.adapt

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ширина, начиная с которой экран считаем широким: планшет или телефон в альбомной ориентации. */
const val WIDE_SCREEN_DP = 600

/** Комфортная ширина колонки: дальше растягивать содержимое не нужно. */
const val CONTENT_MAX_DP = 720

/** Минимальный боковой отступ содержимого. */
const val MIN_SIDE_PADDING_DP = 16

/** Планшет или телефон в альбомной ориентации. */
fun isWideWidth(widthDp: Int): Boolean = widthDp >= WIDE_SCREEN_DP

/**
 * Боковые отступы содержимого в точках.
 *
 * На широком экране они растут, поэтому колонка остаётся по центру и не
 * растягивается на весь планшет — так же, как в Автобазе.
 */
fun sidePaddingDp(widthDp: Int): Int {
    val extra = (widthDp - CONTENT_MAX_DP) / 2
    return if (extra > MIN_SIDE_PADDING_DP) extra else MIN_SIDE_PADDING_DP
}

@Composable
fun isWideScreen(): Boolean = isWideWidth(LocalConfiguration.current.screenWidthDp)

@Composable
fun contentSidePadding(): Dp = sidePaddingDp(LocalConfiguration.current.screenWidthDp).dp

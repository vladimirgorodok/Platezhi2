package ru.platezhi2.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ru.platezhi2.app.data.model.AppIcons
import java.io.File

/** Модель картинки для Coil: путь из файла или content://-адрес. */
private fun imageModel(imagePath: String): Any =
    if (imagePath.startsWith("content:") || imagePath.startsWith("file:")) {
        android.net.Uri.parse(imagePath)
    } else {
        File(imagePath)
    }

/**
 * Иконка платежа: картинка из файла либо стандартный значок.
 *
 * Картинка из файла показывается целиком (без обрезки) и без рамки,
 * подложки и отступов — только само изображение, максимально крупно.
 * Для стандартных значков рисуется скруглённая подложка.
 */
@Composable
fun PaymentIcon(
    imagePath: String?,
    icon: String,
    size: Dp = 46.dp,
) {
    if (!imagePath.isNullOrBlank()) {
        AsyncImage(
            model = imageModel(imagePath),
            contentDescription = null,
            // Fit показывает картинку целиком: ничего не обрезается и не дополняется.
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
        return
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = AppIcons.normalize(icon), fontSize = (size.value * 0.5f).sp)
    }
}

/**
 * Фон строки платежа — иконка платежа, растянутая на всю строку.
 *
 * [transparency] — прозрачность в процентах: 90 — иконка едва видна,
 * 100 — фон полностью прозрачный (не рисуется).
 */
@Composable
fun PaymentBackdrop(
    imagePath: String?,
    icon: String,
    transparency: Int,
    modifier: Modifier = Modifier,
) {
    val alpha = (100 - transparency.coerceIn(0, 100)) / 100f
    if (alpha <= 0.005f) return

    Box(modifier = modifier.fillMaxSize()) {
        if (!imagePath.isNullOrBlank()) {
            // Crop заполняет всю строку, сохраняя пропорции картинки.
            AsyncImage(
                model = imageModel(imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha),
            )
        } else {
            Text(
                text = AppIcons.normalize(icon),
                fontSize = 54.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 44.dp)
                    .alpha(alpha),
            )
        }
    }
}
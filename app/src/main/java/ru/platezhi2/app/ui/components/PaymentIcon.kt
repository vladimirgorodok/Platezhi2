package ru.platezhi2.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ru.platezhi2.app.data.model.AppIcons
import java.io.File

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
        val model: Any = if (imagePath.startsWith("content:") || imagePath.startsWith("file:")) {
            android.net.Uri.parse(imagePath)
        } else {
            File(imagePath)
        }
        AsyncImage(
            model = model,
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

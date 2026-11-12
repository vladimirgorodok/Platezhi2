package ru.platezhi2.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ru.platezhi2.app.data.model.Category
import ru.platezhi2.app.logos.LogoSpec
import java.io.File

/**
 * Аватар платежа: загруженный логотип, монограмма или эмодзи категории.
 */
@Composable
fun LogoAvatar(
    path: String?,
    name: String,
    category: Category,
    size: Dp = 48.dp,
) {
    val spec = LogoSpec.parse(path)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when (spec) {
                    is LogoSpec.Monogram -> Color(spec.color)
                    is LogoSpec.Image -> Color(0xFFEDEDF5)
                    LogoSpec.None -> Color(category.color).copy(alpha = 0.18f)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (spec) {
            is LogoSpec.Image -> {
                val model: Any = if (spec.path.startsWith("content:") || spec.path.startsWith("file:")) {
                    android.net.Uri.parse(spec.path)
                } else {
                    File(spec.path)
                }
                AsyncImage(
                    model = model,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )
            }

            is LogoSpec.Monogram -> Text(
                text = spec.text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp,
            )

            LogoSpec.None -> Text(
                text = category.emoji,
                fontSize = (size.value * 0.45f).sp,
            )
        }
    }
}

package ru.platezhi2.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.platezhi2.app.R
import ru.platezhi2.app.ui.theme.LocalAppPalette
import ru.platezhi2.app.ui.theme.darken

private const val SPLASH_DURATION_MS = 3000L

/**
 * Анимированная заставка: 3 секунды, живой градиент, парящие блики,
 * появление логотипа с мягким свечением и полоса загрузки.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val logoScale = remember { Animatable(0.45f) }
    val logoAlpha = remember { Animatable(0f) }
    val logoRotate = remember { Animatable(-16f) }
    val glow = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffset = remember { Animatable(30f) }
    val barProgress = remember { Animatable(0f) }
    val bgShift = remember { Animatable(0f) }

    // Постоянное «дыхание» фона и бликов.
    val ambient = rememberInfiniteTransition(label = "splash-ambient")
    val drift by ambient.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )
    val spin by ambient.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(520)) }
        launch { logoScale.animateTo(1f, spring(dampingRatio = 0.52f, stiffness = 200f)) }
        launch { logoRotate.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 180f)) }
        launch { glow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        launch { bgShift.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)) }
        launch { barProgress.animateTo(1f, tween(2500, easing = FastOutSlowInEasing)) }
        delay(520)
        launch { textAlpha.animateTo(1f, tween(700)) }
        launch { textOffset.animateTo(0f, tween(700, easing = FastOutSlowInEasing)) }
        delay(SPLASH_DURATION_MS - 520)
        onFinished()
    }

    // Цвета заставки берём из выбранного стиля оформления.
    val palette = LocalAppPalette.current
    val start = palette.accent
    val middle = palette.accent.darken(0.12f)
    val end = palette.accentDark.darken(0.32f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(start, middle, end),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(
                        1080f * (0.6f + 0.4f * bgShift.value),
                        1920f,
                    ),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Цветные блики
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (70 + 26 * drift).dp, y = (-50 - 18 * drift).dp)
                .size(250.dp)
                .clip(CircleShape)
                .background(palette.success.copy(alpha = 0.22f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-80 - 20 * drift).dp, y = (60 + 16 * drift).dp)
                .size(280.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFB020).copy(alpha = 0.18f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(320.dp)
                .scale(0.9f + 0.15f * drift)
                .rotate(spin / 3f)
                .clip(RoundedCornerShape(90.dp))
                .background(Color.White.copy(alpha = 0.05f)),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(190.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Мягкое свечение вокруг логотипа
                Box(
                    modifier = Modifier
                        .size(120.dp + 54.dp * drift)
                        .clip(CircleShape)
                        .alpha(0.22f * glow.value)
                        .background(Color.White),
                )
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .alpha(0.30f * glow.value)
                        .background(Color.White),
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(124.dp)
                        .scale(logoScale.value)
                        .rotate(logoRotate.value)
                        .alpha(logoAlpha.value),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Платежи 2.0",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .offset(y = textOffset.value.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Все счета под контролем",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .offset(y = textOffset.value.dp),
            )

            Spacer(Modifier.height(36.dp))
            Box(
                modifier = Modifier
                    .width(170.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barProgress.value)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(palette.success, Color(0xFFFFD166))
                            )
                        ),
                )
            }
        }

        Text(
            text = "учёт платежей и напоминания",
            color = Color.White.copy(alpha = 0.6f * textAlpha.value),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 42.dp),
        )
    }
}

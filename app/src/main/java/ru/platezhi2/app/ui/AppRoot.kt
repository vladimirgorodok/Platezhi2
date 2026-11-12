package ru.platezhi2.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ru.platezhi2.app.core.AppContainer
import ru.platezhi2.app.ui.nav.AppNavHost
import ru.platezhi2.app.ui.splash.SplashScreen

/** Корень UI: анимированная заставка, затем основная навигация. */
@Composable
fun AppRoot(container: AppContainer) {
    var splashDone by remember { mutableStateOf(false) }

    AnimatedVisibility(visible = splashDone, enter = fadeIn(tween(400)), exit = fadeOut(tween(200))) {
        AppNavHost(container = container)
    }
    AnimatedVisibility(visible = !splashDone, enter = fadeIn(tween(200)), exit = fadeOut(tween(400))) {
        SplashScreen(onFinished = { splashDone = true })
    }
}

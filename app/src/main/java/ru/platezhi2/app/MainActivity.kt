package ru.platezhi2.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ru.platezhi2.app.core.AppContainer
import ru.platezhi2.app.data.settings.AppSettings
import ru.platezhi2.app.ui.AppRoot
import ru.platezhi2.app.ui.theme.Platezhi2Theme

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val container = (application as Platezhi2App).container
        this.container = container

        setContent {
            val settingsFlow = remember { container.settingsRepository.settings }
            val settings by settingsFlow.collectAsState(initial = AppSettings())

            Platezhi2Theme(
                themeMode = settings.themeMode,
                themeStyle = settings.themeStyle,
                dynamicColor = settings.dynamicColor,
            ) {
                AppRoot(container = container)
            }
        }

        maybeRequestNotifications()
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStop() {
        super.onStop()
        // Уходя в фон, сохраняем изменения в облако (если включена автосинхронизация).
        container.cloudCoordinator.onAppStop()
    }
}

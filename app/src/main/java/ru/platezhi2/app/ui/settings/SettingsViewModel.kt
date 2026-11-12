package ru.platezhi2.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.platezhi2.app.core.AppContainer
import ru.platezhi2.app.data.settings.AppSettings
import ru.platezhi2.app.data.settings.ThemeMode
import ru.platezhi2.app.reminders.Reminders

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setDynamicColor(enabled) }
    }

    fun setDefaultReminderDays(days: Int) {
        viewModelScope.launch {
            container.settingsRepository.setDefaultReminderDaysBefore(days.coerceIn(0, 60))
        }
    }

    fun rescheduleAllReminders() {
        viewModelScope.launch {
            Reminders.rescheduleAll(container.appContext, container.paymentRepository.getAll())
        }
    }
}

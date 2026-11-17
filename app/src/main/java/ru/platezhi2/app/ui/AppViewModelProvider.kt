package ru.platezhi2.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.platezhi2.app.Platezhi2App
import ru.platezhi2.app.ui.data.CloudFolderViewModel
import ru.platezhi2.app.ui.data.DataViewModel
import ru.platezhi2.app.ui.edit.PaymentEditViewModel
import ru.platezhi2.app.ui.home.HomeViewModel
import ru.platezhi2.app.ui.settings.SettingsViewModel

/** Фабрика всех ViewModel приложения, связанных с общим контейнером зависимостей. */
object AppViewModelProvider {

    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(app().container) }
        initializer { PaymentEditViewModel(app().container, createSavedStateHandle()) }
        initializer { DataViewModel(app().container) }
        initializer { CloudFolderViewModel(app().container) }
        initializer { SettingsViewModel(app().container) }
    }
}

private fun CreationExtras.app(): Platezhi2App =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Platezhi2App

package ru.platezhi2.app.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.platezhi2.app.core.AppContainer
import ru.platezhi2.app.data.archive.ArchiveFiles
import ru.platezhi2.app.data.cloud.CloudSyncCoordinator
import ru.platezhi2.app.data.settings.AppSettings
import ru.platezhi2.app.reminders.Reminders

class DataViewModel(private val container: AppContainer) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ------------------------------------------------------------------
    // Архив на устройстве
    // ------------------------------------------------------------------

    private val _archiveInfo = MutableStateFlow<ArchiveFiles.Info?>(null)
    val archiveInfo: StateFlow<ArchiveFiles.Info?> = _archiveInfo.asStateFlow()

    private val _archivePreviousInfo = MutableStateFlow<ArchiveFiles.Info?>(null)
    val archivePreviousInfo: StateFlow<ArchiveFiles.Info?> = _archivePreviousInfo.asStateFlow()

    fun refreshArchiveInfo() {
        viewModelScope.launch { loadArchiveInfo() }
    }

    /** Копия на устройстве. */
    fun createArchive() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { container.localArchive.create(force = true) }
                .onSuccess { created ->
                    loadArchiveInfo()
                    _message.value = if (created) "Копия сохранена на устройстве" else "Сохранять пока нечего"
                }
                .onFailure { _message.value = "Не удалось сделать копию: ${it.message}" }
            _busy.value = false
        }
    }

    /** Возврат данных из копии. */
    fun restoreArchive() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { container.localArchive.restore() }
                .onSuccess { restored ->
                    loadArchiveInfo()
                    if (restored) {
                        rescheduleReminders()
                        _message.value = "Данные возвращены из копии"
                    } else {
                        _message.value = "Копии на устройстве нет"
                    }
                }
                .onFailure { _message.value = "Не удалось вернуть данные: ${it.message}" }
            _busy.value = false
        }
    }

    /** Возврат состояния, сохранённого перед последним восстановлением. */
    fun restorePreviousArchive() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { container.localArchive.restorePrevious() }
                .onSuccess { restored ->
                    loadArchiveInfo()
                    if (restored) {
                        rescheduleReminders()
                        _message.value = "Возвращено состояние до восстановления"
                    } else {
                        _message.value = "Такой копии нет"
                    }
                }
                .onFailure { _message.value = "Не удалось вернуть данные: ${it.message}" }
            _busy.value = false
        }
    }

    private suspend fun loadArchiveInfo() {
        _archiveInfo.value = container.localArchive.info()
        _archivePreviousInfo.value = container.localArchive.previousInfo()
    }

    // ------------------------------------------------------------------
    // Яндекс Диск
    // ------------------------------------------------------------------

    val cloudSettings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val cloudStatus: StateFlow<CloudSyncCoordinator.Status> = container.cloudCoordinator.status

    /** Поля ввода держим отдельно, чтобы ввод не «дёргался» при сохранении. */
    private val _login = MutableStateFlow("")
    val login: StateFlow<String> = _login.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private var fieldsLoaded = false
    private var loginSave: Job? = null
    private var passwordSave: Job? = null

    init {
        viewModelScope.launch {
            val current = container.settingsRepository.settings.first()
            if (!fieldsLoaded) {
                _login.value = current.yandexLogin
                _password.value = current.yandexPassword
                fieldsLoaded = true
            }
        }
        viewModelScope.launch { loadArchiveInfo() }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun setLogin(value: String) {
        _login.value = value
        loginSave?.cancel()
        loginSave = viewModelScope.launch {
            delay(500)
            container.settingsRepository.setYandexLogin(value)
        }
    }

    fun setPassword(value: String) {
        _password.value = value
        passwordSave?.cancel()
        passwordSave = viewModelScope.launch {
            delay(500)
            container.settingsRepository.setYandexPassword(value)
        }
    }

    fun flushCredentials() {
        loginSave?.cancel()
        passwordSave?.cancel()
        viewModelScope.launch {
            container.settingsRepository.setYandexLogin(_login.value)
            container.settingsRepository.setYandexPassword(_password.value)
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setAutoSync(enabled)
            if (!enabled) return@launch
            // При включении: если на устройстве уже есть платежи — считаем их свежими
            // и при следующем запуске выгрузим. Если данных нет — сначала загрузим из облака.
            val hasLocalData = container.paymentRepository.getCards().isNotEmpty()
            container.settingsRepository.setCloudDirty(hasLocalData)
        }
    }

    fun setKeepCopies(count: Int) {
        viewModelScope.launch { container.settingsRepository.setCloudKeepCopies(count) }
    }

    fun checkCloudConnection() {
        val loginValue = _login.value
        val passwordValue = _password.value
        val folder = cloudSettings.value.cloudFolder
        viewModelScope.launch {
            container.settingsRepository.setYandexLogin(loginValue)
            container.settingsRepository.setYandexPassword(passwordValue)
            // Сама проверка идёт в общем скоупе приложения: уход с экрана её не отменит.
            container.cloudCoordinator.checkInBackground(loginValue, passwordValue, folder)
        }
    }

    // Обмен с облаком идёт в общем скоупе приложения: уход с экрана его не отменит.
    fun pushToCloud() = container.cloudCoordinator.pushInBackground()

    fun pullFromCloud() = container.cloudCoordinator.pullInBackground()

    fun pushIconsToCloud() = container.cloudCoordinator.iconsPushInBackground()

    fun restoreIconsFromCloud() = container.cloudCoordinator.iconsRestoreInBackground()

    private suspend fun rescheduleReminders() {
        val cards = container.paymentRepository.getCards()
        val payments = container.paymentRepository.getPayments()
        Reminders.rescheduleAll(container.appContext, cards, payments)
    }
}
